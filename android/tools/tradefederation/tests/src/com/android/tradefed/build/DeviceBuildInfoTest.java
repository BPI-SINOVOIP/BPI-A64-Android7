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
 * Unit tests for {@link DeviceBuildInfo}.
 */
public class DeviceBuildInfoTest extends TestCase {

    private static final String VERSION = "1";
    private DeviceBuildInfo mBuildInfo;
    private File mImageFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuildInfo = new DeviceBuildInfo("2", "build", "target");
        mImageFile = FileUtil.createTempFile("image", "tmp");
        FileUtil.writeToFile("filedata", mImageFile);
        mBuildInfo.setBasebandImage(mImageFile, VERSION);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mImageFile != null && mImageFile.exists()) {
            mImageFile.delete();
        }
    }

    /**
     * Test method for {@link DeviceBuildInfo#clone()}.
     */
    public void testClone() throws Exception {
        DeviceBuildInfo copy = (DeviceBuildInfo)mBuildInfo.clone();
        try {
            // ensure a copy of mImageFile was created
            assertEquals(VERSION, copy.getBasebandVersion());
            assertTrue(!mImageFile.getAbsolutePath().equals(copy.getBasebandImageFile()));
            assertTrue(FileUtil.compareFileContents(mImageFile, copy.getBasebandImageFile()));
        } finally {
            if (copy.getBasebandImageFile() != null) {
                copy.getBasebandImageFile().delete();
            }
        }
    }

    /**
     * Test method for {@link DeviceBuildInfo#cleanUp()}.
     */
    public void testCleanUp() {
        assertTrue(mBuildInfo.getBasebandImageFile().exists());
        mBuildInfo.cleanUp();
        assertNull(mBuildInfo.getBasebandImageFile());
        assertFalse(mImageFile.exists());
    }
}
