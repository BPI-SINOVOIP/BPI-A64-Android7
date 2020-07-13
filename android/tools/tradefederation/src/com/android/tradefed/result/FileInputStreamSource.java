/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link InputStreamSource} that takes an input file.
 * <p/>
 * Caller is responsible for deleting the file
 */
public class FileInputStreamSource implements InputStreamSource {

    private final File mFile;
    private boolean mIsCancelled = false;

    public FileInputStreamSource(File file) {
        mFile = file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized InputStream createInputStream() {
        if (mIsCancelled) {
            return null;
        }
        try {
            return new FileInputStream(mFile);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void cancel() {
        mIsCancelled = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        return mFile.length();
    }
}

