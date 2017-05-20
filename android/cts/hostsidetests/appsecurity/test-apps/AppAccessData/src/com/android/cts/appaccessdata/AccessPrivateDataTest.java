/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.cts.appaccessdata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.AndroidTestCase;

/**
 * Test that another app's private data cannot be accessed, while its public data can.
 *
 * Assumes that {@link #APP_WITH_DATA_PKG} has already created the private and public data.
 */
public class AccessPrivateDataTest extends AndroidTestCase {

    /**
     * The Android package name of the application that owns the data
     */
    private static final String APP_WITH_DATA_PKG = "com.android.cts.appwithdata";

    /**
     * Name of private file to access. This must match the name of the file created by
     * {@link #APP_WITH_DATA_PKG}.
     */
    private static final String PRIVATE_FILE_NAME = "private_file.txt";
    /**
     * Name of public file to access. This must match the name of the file created by
     * {@link #APP_WITH_DATA_PKG}.
     */
    private static final String PUBLIC_FILE_NAME = "public_file.txt";

    /**
     * Tests that another app's private data cannot be accessed. It includes file
     * and detailed traffic stats.
     * @throws IOException
     */
    public void testAccessPrivateData() throws IOException {
        try {
            // construct the absolute file path to the app's private file
            ApplicationInfo applicationInfo = getApplicationInfo(APP_WITH_DATA_PKG);
            File privateFile = new File(applicationInfo.dataDir, "files/" + PRIVATE_FILE_NAME);
            FileInputStream inputStream = new FileInputStream(privateFile);
            inputStream.read();
            inputStream.close();
            fail("Was able to access another app's private data");
        } catch (FileNotFoundException | SecurityException e) {
            // expected
        }
        accessPrivateTrafficStats();
    }

    private ApplicationInfo getApplicationInfo(String packageName) {
        try {
            return mContext.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Expected package not found: " + e);
        }
    }

    /**
     * Tests that another app's public file can be accessed
     * @throws IOException
     */
    public void testAccessPublicData() throws IOException {
        try {
            // construct the absolute file path to the other app's public file
            ApplicationInfo applicationInfo = getApplicationInfo(APP_WITH_DATA_PKG);
            File publicFile = new File(applicationInfo.dataDir, "files/" + PUBLIC_FILE_NAME);
            FileInputStream inputStream = new FileInputStream(publicFile);
            inputStream.read();
            inputStream.close();
            fail("Was able to access another app's public file");
        } catch (FileNotFoundException | SecurityException e) {
            // expected
        }
    }

    private void accessPrivateTrafficStats() throws IOException {
        int otherAppUid = -1;
        try {
            otherAppUid = getContext()
                    .createPackageContext(APP_WITH_DATA_PKG, 0 /*flags*/)
                    .getApplicationInfo().uid;
        } catch (NameNotFoundException e) {
            fail("Was not able to find other app");
        }
        try {
            BufferedReader qtaguidReader = new BufferedReader(new FileReader("/proc/net/xt_qtaguid/stats"));
            String line;
            while ((line = qtaguidReader.readLine()) != null) {
                String tokens[] = line.split(" ");
                if (tokens.length > 3 && tokens[3].equals(String.valueOf(otherAppUid))) {
                    // CreatePrivateDataTest:testCreatePrivateData ensures we can access our own stats data
                    fail("Other apps detailed traffic stats leaked");
                }
            }
            qtaguidReader.close();
        } catch (FileNotFoundException e) {
            fail("Was not able to access qtaguid/stats: " + e);
        }
    }
}
