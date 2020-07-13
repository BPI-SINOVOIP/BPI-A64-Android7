/*
 * Copyright (C) 2010 The Android Open Source Project
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

/**
 * Represents the data type of log data.
 */
public enum LogDataType {

    TEXT("txt", false, true),
    XML("xml", false, true),
    HTML("html", true, true),
    PNG("png", true, false),
    ZIP("zip", true, false),
    JPEG("jpeg", true, false),
    GZIP("gz", true, false),
    COVERAGE("ec", false, false),  /* Emma coverage file */
    /* Specific text file types */
    BUGREPORT("txt", false, true),
    LOGCAT("txt", false, true),
    KERNEL_LOG("txt", false, true),
    MONKEY_LOG("txt", false, true),
    PROCRANK("txt", false, true),
    MEM_INFO("txt", false, true),
    TOP("txt", false, true),
    DUMPSYS("txt", false, true),
    COMPACT_MEMINFO("txt", false, true),  // dumpsys meminfo -c
    SERVICES("txt", false, true),  // dumpsys activity services
    GFX_INFO("txt", false, true),  // dumpsys gfxinfo
    CPU_INFO("txt", false, true),  // dumpsys cpuinfo
    /* Unknown file type */
    UNKNOWN("dat", false, false);

    private final String mFileExt;
    private final boolean mIsCompressed;
    private final boolean mIsText;

    LogDataType(String fileExt, boolean compressed, boolean text) {
        mFileExt = fileExt;
        mIsCompressed = compressed;
        mIsText = text;
    }

    public String getFileExt() {
        return mFileExt;
    }

    /**
     * @return <code>true</code> if data type is a compressed format.
     */
    public boolean isCompressed() {
        return mIsCompressed;
    }

    /**
     * @return <code>true</code> if data type is a textual format.
     */
    public boolean isText() {
        return mIsText;
    }
}
