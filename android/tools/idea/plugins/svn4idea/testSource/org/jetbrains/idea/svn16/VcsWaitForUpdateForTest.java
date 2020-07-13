/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn16;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.EnsureUpToDateFromNonAWTThread;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;


public class VcsWaitForUpdateForTest extends Svn16TestCase {
  @Test
  public void testRefreshes() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final Object lock = new Object();

    final Ref<Boolean> done = new Ref<Boolean>();
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        new EnsureUpToDateFromNonAWTThread(myProject).execute();
        done.set(Boolean.TRUE);
        synchronized (lock) {
          lock.notifyAll();
        }
      }
    });

    thread.start();
    synchronized (lock) {
      final long start = System.currentTimeMillis();
      final int timeout = 3000;

      while ((System.currentTimeMillis() - start < timeout) && (! Boolean.TRUE.equals(done.get()))) {
        try {
          lock.wait(timeout);
        }
        catch (InterruptedException e) {
          //
        }
      }
    }

    assert Boolean.TRUE.equals(done.get());
  }
}
