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
package com.android.tradefed.result;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Unit tests for the {@link SnapshotInputStreamSource} class
 */
public class SnapshotInputStreamSourceTest extends TestCase {
    private static final String mFileContents = "These are file contents!";
    private InputStream mInputStream = null;

    @Override
    public void setUp() throws Exception {
        mInputStream = new ByteArrayInputStream(mFileContents.getBytes());
    }

    /**
     * Ensure that the {@link SnapshotInputStreamSource#cancel()} method cleans up the backing file
     * as expected
     */
    @SuppressWarnings("serial")
    public void testCancel() {
        final String deletedMsg = "File was deleted";
        final File fakeFile = new File("noexist") {
            @Override
            public boolean delete() {
                throw new RuntimeException(deletedMsg);
            }
        };

        InputStreamSource source = new SnapshotInputStreamSource(mInputStream) {
            @Override
            File createBackingFile(InputStream stream) {
                return fakeFile;
            }
        };

        try {
            source.cancel();
            fail("Fake file was not deleted");
        } catch (RuntimeException e) {
            if (!deletedMsg.equals(e.getMessage())) {
                // This is not the droi^WRuntimeException you're searching for
                throw e;
            }
        }
    }
}

