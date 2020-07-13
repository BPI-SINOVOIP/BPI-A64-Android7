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

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

/**
 * Unit tests for {@link BuildInfo}.
 */
public class BuildInfoTest extends TestCase {
    private static final String VERSION = "2";
    private static final String ATTRIBUTE_KEY = "attribute";
    private static final String FILE_KEY = "file";

    private BuildInfo mBuildInfo;
    private File mFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuildInfo = new BuildInfo("1", "build", "target");
        mBuildInfo.addBuildAttribute(ATTRIBUTE_KEY, "value");
        mFile = FileUtil.createTempFile("image", "tmp");
        FileUtil.writeToFile("filedata", mFile);
        mBuildInfo.setFile(FILE_KEY, mFile, VERSION);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mFile != null && mFile.exists()) {
            mFile.delete();
        }
    }

    /**
     * Test method for {@link BuildInfo#clone()}.
     */
    public void testClone() throws Exception {
        BuildInfo copy = (BuildInfo) mBuildInfo.clone();
        assertEquals(mBuildInfo.getBuildAttributes().get(ATTRIBUTE_KEY),
                copy.getBuildAttributes().get(ATTRIBUTE_KEY));
        try {
            // ensure a copy of mImageFile was created
            assertEquals(VERSION, copy.getVersion(FILE_KEY));
            assertTrue(!mFile.getAbsolutePath().equals(copy.getFile(FILE_KEY)));
            assertTrue(FileUtil.compareFileContents(mFile, copy.getFile(FILE_KEY)));
        } finally {
            FileUtil.deleteFile(copy.getFile(FILE_KEY));
        }
    }

    /**
     * Test method for {@link BuildInfo#cleanUp()}.
     */
    public void testCleanUp() {
        assertTrue(mBuildInfo.getFile(FILE_KEY).exists());
        mBuildInfo.cleanUp();
        assertNull(mBuildInfo.getFile(FILE_KEY));
        assertFalse(mFile.exists());
    }
}
