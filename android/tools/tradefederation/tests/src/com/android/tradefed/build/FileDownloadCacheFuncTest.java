/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.build;

import com.android.ddmlib.Log;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Longer running, concurrency based tests for {@link FileDownloadCache}.
 */
public class FileDownloadCacheFuncTest extends TestCase {

    private static final String REMOTE_PATH = "path";
    private static final String DOWNLOADED_CONTENTS = "downloaded contents";
    protected static final String LOG_TAG = "FileDownloadCacheFuncTest";

    private IFileDownloader mMockDownloader;

    private FileDownloadCache mCache;
    private List<File> mReturnedFiles;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDownloader = EasyMock.createStrictMock(IFileDownloader.class);
        mCache = new FileDownloadCache(FileUtil.createTempDir("functest"));
        mReturnedFiles = new ArrayList<File>(2);
    }

    @Override
    protected void tearDown() throws Exception {
        for (File file : mReturnedFiles) {
            file.delete();
        }
        super.tearDown();
    }

    /**
     * Test {@link FileDownloadCache#fetchRemoteFile(IFileDownloader, String)} being called
     * concurrently by two separate threads.
     */
    @SuppressWarnings("unchecked")
    public void testFetchRemoteFile_concurrent() throws Exception {
        // Simulate a relatively slow file download
        IAnswer<Object> slowDownloadAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                Thread.sleep(500);
                File fileArg =  (File) EasyMock.getCurrentArguments()[1];
                FileUtil.writeToFile(DOWNLOADED_CONTENTS, fileArg);
                return null;
            }
        };
        mMockDownloader.downloadFile(EasyMock.eq(REMOTE_PATH),
                EasyMock.<File>anyObject());
        EasyMock.expectLastCall().andAnswer(slowDownloadAnswer);
        EasyMock.replay(mMockDownloader);
        Thread downloadThread1 = new Thread() {
          @Override
        public void run() {
            try {
                mReturnedFiles.add(mCache.fetchRemoteFile(mMockDownloader, REMOTE_PATH));
            } catch (BuildRetrievalError e) {
                Log.e(LOG_TAG, e);
            }
          }
        };
        Thread downloadThread2 = new Thread() {
            @Override
            public void run() {
              try {
                  mReturnedFiles.add(mCache.fetchRemoteFile(mMockDownloader, REMOTE_PATH));
              } catch (BuildRetrievalError e) {
                  Log.e(LOG_TAG, e);
              }
            }
          };
        downloadThread1.start();
        downloadThread2.start();
        downloadThread1.join();
        downloadThread2.join();
        assertNotNull(mCache.getCachedFile(REMOTE_PATH));
        assertEquals(2, mReturnedFiles.size());
        // returned files should be identical in content, but be different files
        assertTrue(mReturnedFiles.get(0) != mReturnedFiles.get(1));
        assertEquals(DOWNLOADED_CONTENTS, StreamUtil.getStringFromStream(new FileInputStream(
                mReturnedFiles.get(0))));
        assertEquals(DOWNLOADED_CONTENTS, StreamUtil.getStringFromStream(new FileInputStream(
                mReturnedFiles.get(1))));
        EasyMock.verify(mMockDownloader);
    }

    /**
     * Verify the cache is built from disk contents on creation
     */
    public void testConstructor_createCache() throws Exception {
        // create cache contents on disk
        File cacheRoot = FileUtil.createTempDir("constructorTest");
        try {
            final String filecontents = "these are the file contents";
            File file1 = new File(cacheRoot, REMOTE_PATH);
            FileUtil.writeToFile(filecontents, file1);
            // this is lame, but sleep for a small amount to ensure nestedFile has later timestamp
            // TODO: use mock File instead
            Thread.sleep(1000);
            File nestedDir = new File(cacheRoot, "aa");
            nestedDir.mkdir();
            File nestedFile = new File(nestedDir, "anotherpath");
            FileUtil.writeToFile(filecontents, nestedFile);

            FileDownloadCache cache = new FileDownloadCache(cacheRoot);
            assertNotNull(cache.getCachedFile(REMOTE_PATH));
            assertNotNull(cache.getCachedFile("aa/anotherpath"));
            assertEquals(REMOTE_PATH, cache.getOldestEntry());
        } finally {
            FileUtil.recursiveDelete(cacheRoot);
        }
    }

    /**
     * Test scenario where an already too large cache is built from disk contents.
     */
    public void testConstructor_cacheExceeded() throws Exception {
        File cacheRoot = FileUtil.createTempDir("testConstructor_cacheExceeded");
        try {
            // create a couple existing files in cache
            final String filecontents = "these are the file contents";
            final File file1 = new File(cacheRoot, REMOTE_PATH);
            FileUtil.writeToFile(filecontents, file1);
            // sleep for a small amount to ensure file2 has later timestamp
            // TODO: use mock File instead
            Thread.sleep(1000);
            final File file2 = new File(cacheRoot, "anotherpath");
            FileUtil.writeToFile(filecontents, file2);

            new FileDownloadCache(cacheRoot) {
                @Override
                long getMaxFileCacheSize() {
                    return file2.length() + 1;
                }
            };
            // expect cache to be cleaned on startup, with oldest file1 deleted, but newest file
            // retained
            assertFalse(file1.exists());
            assertTrue(file2.exists());

        } finally {
            FileUtil.recursiveDelete(cacheRoot);
        }
    }
}
