/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import org.jetbrains.idea.svn.svnkit.lowLevel.ApplicationLevelNumberConnectionsGuardImpl;
import org.jetbrains.idea.svn.svnkit.lowLevel.CachingSvnRepositoryPool;
import org.jetbrains.idea.svn.svnkit.lowLevel.SvnIdeaRepositoryPoolManager;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.Map;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/12
 * Time: 5:37 PM
 */
public class SvnCachingRepositoryPoolTest extends FileBasedTest {

  @Test
  public void testRepositoriesAreClosed() throws Exception {
    final SvnIdeaRepositoryPoolManager poolManager = new SvnIdeaRepositoryPoolManager(true, null, null);
    testBigFlow(poolManager, true);
  }

  @Test
  public void testCloseWorker() throws Exception {
    final SvnIdeaRepositoryPoolManager poolManager = new SvnIdeaRepositoryPoolManager(true, null, null);
    final ApplicationLevelNumberConnectionsGuardImpl guard = SvnIdeaRepositoryPoolManager.getOurGuard();
    guard.setDelay(20);
    ((CachingSvnRepositoryPool) poolManager.getPool()).setConnectionTimeout(20);
    testBigFlow(poolManager, false);
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      //
    }
    Assert.assertEquals(0, guard.getCurrentlyActiveConnections());
    final CachingSvnRepositoryPool pool = (CachingSvnRepositoryPool) poolManager.getPool();
    Map<String,CachingSvnRepositoryPool.RepoGroup> groups = pool.getGroups();
    Assert.assertEquals(1, groups.size());
    CachingSvnRepositoryPool.RepoGroup group = groups.values().iterator().next();
    Assert.assertEquals(0, group.getUsedSize());
    Assert.assertEquals(0, group.getInactiveSize());  // !!!
    poolManager.dispose();
    checkAfterDispose(poolManager);
  }

  @Test
  public void testCancel() throws Exception {
    final SvnIdeaRepositoryPoolManager poolManager = new SvnIdeaRepositoryPoolManager(true, null, null, 1, 1);
    final SVNURL url = SVNURL.parseURIEncoded("http://a.b.c");
    poolManager.setCreator(new ThrowableConvertor<SVNURL, SVNRepository, SVNException>() {
      @Override
      public SVNRepository convert(SVNURL svnurl) throws SVNException {
        return new MockSvnRepository(svnurl, ISVNSession.DEFAULT);
      }
    });
    final MockSvnRepository repository1 = (MockSvnRepository)poolManager.createRepository(url, true);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    poolManager.setCreator(new ThrowableConvertor<SVNURL, SVNRepository, SVNException>() {
      @Override
      public SVNRepository convert(SVNURL svnurl) throws SVNException {
        semaphore.waitFor();
        return new MockSvnRepository(svnurl, ISVNSession.DEFAULT);
      }
    });
    final SVNException[] exc = new SVNException[1];
    final Runnable target = new Runnable() {
      @Override
      public void run() {
        try {
          final MockSvnRepository repository = (MockSvnRepository)poolManager.createRepository(url, true);
          repository.fireConnectionClosed();
        }
        catch (SVNException e) {
          e.printStackTrace();
          exc[0] = e;
        }
      }
    };
    final EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(target, indicator);
      }
    });
    thread.start();

    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      //
    }
    Assert.assertTrue(thread.isAlive());
    indicator.cancel();
    final Object obj = new Object();
    while (! timeout(System.currentTimeMillis()) && thread.isAlive()) {
      synchronized (obj) {
        try {
          obj.wait(300);
        } catch (InterruptedException e) {
          //
        }
      }
    }
    Assert.assertTrue(!thread.isAlive());
    Assert.assertNotNull(exc[0]);
    //repository1.fireConnectionClosed(); // also test that used are also closed.. in dispose

    poolManager.dispose();
    checkAfterDispose(poolManager);
  }

  private void checkAfterDispose(SvnIdeaRepositoryPoolManager poolManager) {
    final ApplicationLevelNumberConnectionsGuardImpl guard = SvnIdeaRepositoryPoolManager.getOurGuard();
    Assert.assertEquals(0, guard.getCurrentlyActiveConnections());

    final CachingSvnRepositoryPool pool = (CachingSvnRepositoryPool) poolManager.getPool();
    Map<String,CachingSvnRepositoryPool.RepoGroup> groups = pool.getGroups();
    Assert.assertEquals(1, groups.size());
    CachingSvnRepositoryPool.RepoGroup group = groups.values().iterator().next();
    Assert.assertEquals(0, group.getUsedSize());

    Assert.assertEquals(0, guard.getInstanceCount());
    Assert.assertEquals(0, guard.getCurrentlyOpenedCount());

    Assert.assertEquals(0, group.getUsedSize());
    Assert.assertEquals(0, group.getInactiveSize());
  }

  private void testBigFlow(final SvnIdeaRepositoryPoolManager poolManager, boolean disposeAfter) throws SVNException {
    poolManager.setCreator(new ThrowableConvertor<SVNURL, SVNRepository, SVNException>() {
      @Override
      public SVNRepository convert(SVNURL svnurl) throws SVNException {
        return new MockSvnRepository(svnurl, ISVNSession.DEFAULT);
      }
    });
    final SVNURL url = SVNURL.parseURIEncoded("http://a.b.c");
    final Random random = new Random(System.currentTimeMillis() & 0x00ff);
    final int[] cnt = new int[1];
    cnt[0] = 25;
    final SVNException[] exc = new SVNException[1];
    for (int i = 0; i < 25; i++) {
      Runnable target = new Runnable() {
        @Override
        public void run() {
          MockSvnRepository repository = null;
          try {
            repository = (MockSvnRepository)poolManager.createRepository(url, true);
          }
          catch (SVNException e) {
            e.printStackTrace();
            exc[0] = e;
            return;
          }
          repository.fireConnectionOpened();
          try {
            Thread.sleep(random.nextInt(10));
          }
          catch (InterruptedException e) {
            //
          }
          repository.fireConnectionClosed();
          synchronized (cnt) {
            -- cnt[0];
          }
        }
      };
      Thread thread = new Thread(target);
      thread.start();
    }

    final long start = System.currentTimeMillis();
    synchronized (cnt) {
      while (cnt[0] > 0 && ! timeout(start)) {
        try {
          cnt.wait(5);
        } catch (InterruptedException e) {
          //
        }
      }
    }
    Assert.assertEquals(0, cnt[0]);
    // test no open repositories, but may have inactive
    final ApplicationLevelNumberConnectionsGuardImpl guard = SvnIdeaRepositoryPoolManager.getOurGuard();
    Assert.assertEquals(0, guard.getCurrentlyActiveConnections());

    final CachingSvnRepositoryPool pool = (CachingSvnRepositoryPool) poolManager.getPool();
    Map<String,CachingSvnRepositoryPool.RepoGroup> groups = pool.getGroups();
    Assert.assertEquals(1, groups.size());
    CachingSvnRepositoryPool.RepoGroup group = groups.values().iterator().next();
    Assert.assertEquals(0, group.getUsedSize());

    if (disposeAfter) {
      poolManager.dispose();
      Assert.assertEquals(0, guard.getCurrentlyActiveConnections());
      Assert.assertEquals(0, guard.getInstanceCount());

      Assert.assertEquals(0, group.getUsedSize());
      Assert.assertEquals(0, group.getInactiveSize());
    }
  }

  private boolean timeout(long start) {
    return System.currentTimeMillis() - start > 10000;
  }
}
