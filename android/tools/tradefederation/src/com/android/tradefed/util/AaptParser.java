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
package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that extracts info from apk by parsing output of 'aapt dump badging'.
 * <p/>
 * aapt must be on PATH
 */
public class AaptParser {
    private static final Pattern PKG_PATTERN = Pattern.compile(
            "^package:\\s+name='(.*?)'\\s+versionCode='(\\d+)'\\s+versionName='(.*?)'.*$",
            Pattern.MULTILINE);
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "^application-label:'(.+?)'.*$",
            Pattern.MULTILINE);
    private static final int AAPT_TIMEOUT_MS = 60000;

    private String mPackageName;
    private String mVersionCode;
    private String mVersionName;
    private String mLabel;

    // @VisibleForTesting
    AaptParser() {
    }

    boolean parse(String aaptOut) {
        Matcher m = PKG_PATTERN.matcher(aaptOut);
        if (m.find()) {
            mPackageName = m.group(1);
            mLabel = mPackageName;
            mVersionCode = m.group(2);
            mVersionName = m.group(3);
            m = LABEL_PATTERN.matcher(aaptOut);
            if (m.find()) {
                mLabel = m.group(1);
            }
            return true;
        }
        CLog.e("Failed to parse package and version info from 'aapt dump badging'. stdout: '%s'",
                aaptOut);
        return false;
    }

    /**
     * Parse info from the apk.
     *
     * @param apkFile the apk file
     * @return the {@link AaptParser} or <code>null</code> if failed to extract the information
     */
    public static AaptParser parse(File apkFile) {
        CommandResult result = RunUtil.getDefault().runTimedCmd(AAPT_TIMEOUT_MS,
                "aapt", "dump", "badging", apkFile.getAbsolutePath());

        String stderr = result.getStderr();
        if (stderr != null && stderr.length() > 0) {
            CLog.e("aapt dump badging stderr: %s", stderr);
        }

        if (result.getStatus() == CommandStatus.SUCCESS) {
            AaptParser p = new AaptParser();
            if (p.parse(result.getStdout()))
                return p;
            return null;
        }
        CLog.e("Failed to run aapt on %s", apkFile.getAbsoluteFile());
        return null;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getVersionCode() {
        return mVersionCode;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public String getLabel() {
        return mLabel;
    }
}
