/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tradefed.util;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Functional tests for {@link ZipUtil}
 */
public class ZipUtilFuncTest extends TestCase {
    private Set<File> mTempFiles = new HashSet<File>();

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (File file : mTempFiles) {
            if (file != null && file.exists()) {
                if (file.isDirectory()) {
                    FileUtil.recursiveDelete(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    private File getTestDataFile(String name) throws IOException {
        final InputStream inputStream =
                getClass().getResourceAsStream(String.format("/util/%s.zip", name));
        final File zipFile = createTempFile(name, ".zip");
        FileUtil.writeToFile(inputStream, zipFile);
        return zipFile;
    }

    /**
     * Test that our _simple_ corrupt zip detection heuristics work properly.  It is expected
     * that this check will _fail_ to detect a corrupt but well-formed Zip archive.
     */
    public void testSimpleCorruptZipCheck() throws Exception {
        assertTrue("Falsely detected 'normal.zip' test file as corrupt!",
                ZipUtil.isZipFileValid(getTestDataFile("normal"), false));
        assertFalse("Failed to detect 'truncated.zip' test file as corrupt!",
                ZipUtil.isZipFileValid(getTestDataFile("truncated"), false));
        assertTrue("Unexpectedly detected 'normal.zip' test file as corrupt!  Hope?",
                ZipUtil.isZipFileValid(getTestDataFile("corrupt"), false));
    }

    /**
     * Test that our _thorough_ corrupt zip detection heuristics work properly.
     */
    public void testThoroughCorruptZipCheck() throws Exception {
        assertTrue("Falsely detected 'normal.zip' test file as corrupt with thorough check!",
                ZipUtil.isZipFileValid(getTestDataFile("normal"), true));
        assertFalse("Failed to detect 'truncated.zip' test file as corrupt with thorough check!",
                ZipUtil.isZipFileValid(getTestDataFile("truncated"), true));
        assertFalse("Failed to detect 'normal.zip' test file as corrupt with thorough check!",
                ZipUtil.isZipFileValid(getTestDataFile("corrupt"), true));
    }

    /**
     * Test creating then extracting a zip file
     *
     * @throws IOException
     */
    public void testCreateAndExtractZip() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File zipFile = null;
        File extractedDir = createTempDir("extract-foo");
        try {
            File childDir = new File(tmpParentDir, "foochild");
            assertTrue(childDir.mkdir());
            File subFile = new File(childDir, "foo.txt");
            FileUtil.writeToFile("contents", subFile);
            zipFile = ZipUtil.createZip(tmpParentDir);
            ZipUtil.extractZip(new ZipFile(zipFile), extractedDir);

            // assert all contents of original zipped dir are extracted
            File extractedParentDir = new File(extractedDir, tmpParentDir.getName());
            File extractedChildDir = new File(extractedParentDir, childDir.getName());
            File extractedSubFile = new File(extractedChildDir, subFile.getName());
            assertTrue(extractedParentDir.exists());
            assertTrue(extractedChildDir.exists());
            assertTrue(extractedSubFile.exists());
            assertTrue(FileUtil.compareFileContents(subFile, extractedSubFile));
        } finally {
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }

    /**
     * Test that isZipFileValid returns false if calling with a file that does not exist.
     *
     * @throws IOException
     */
    public void testZipFileDoesNotExist() throws IOException {
        File file = new File("/tmp/this-file-does-not-exist.zip");
        assertFalse(ZipUtil.isZipFileValid(file, true));
        assertFalse(ZipUtil.isZipFileValid(file, false));
    }

    /**
     * Test creating then extracting a a single file from zip file
     *
     * @throws IOException
     */
    public void testCreateAndExtractFileFromZip() throws IOException {
        File tmpParentDir = createTempDir("foo");
        File zipFile = null;
        File extractedSubFile = null;
        try {
            File childDir = new File(tmpParentDir, "foochild");
            assertTrue(childDir.mkdir());
            File subFile = new File(childDir, "foo.txt");
            FileUtil.writeToFile("contents", subFile);
            zipFile = ZipUtil.createZip(tmpParentDir);

            extractedSubFile = ZipUtil.extractFileFromZip(new ZipFile(zipFile),
                    tmpParentDir.getName() + "/foochild/foo.txt");
            assertNotNull(extractedSubFile);
            assertTrue(FileUtil.compareFileContents(subFile, extractedSubFile));
        } finally {
            FileUtil.deleteFile(zipFile);
            FileUtil.deleteFile(extractedSubFile);
        }
    }

    // Helpers
    private File createTempDir(String prefix) throws IOException {
        return createTempDir(prefix, null);
    }

    private File createTempDir(String prefix, File parentDir) throws IOException {
        File tempDir = FileUtil.createTempDir(prefix, parentDir);
        mTempFiles.add(tempDir);
        return tempDir;
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        File tempFile = FileUtil.createTempFile(prefix, suffix);
        mTempFiles.add(tempFile);
        return tempFile;
    }
}
