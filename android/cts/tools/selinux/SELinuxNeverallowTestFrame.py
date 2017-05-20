#!/usr/bin/env python

src_header = """/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.cts.security;

import com.android.cts.migration.MigrationHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Neverallow Rules SELinux tests.
 */
public class SELinuxNeverallowRulesTest extends DeviceTestCase implements IBuildReceiver, IDeviceTest {
    private File sepolicyAnalyze;
    private File devicePolicyFile;

    private IBuildInfo mBuild;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        super.setDevice(device);
        mDevice = device;
    }
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sepolicyAnalyze = MigrationHelper.getTestFile(mBuild, "sepolicy-analyze");
        sepolicyAnalyze.setExecutable(true);

        /* obtain sepolicy file from running device */
        devicePolicyFile = File.createTempFile("sepolicy", ".tmp");
        devicePolicyFile.deleteOnExit();
        mDevice.pullFile("/sys/fs/selinux/policy", devicePolicyFile);
    }
"""
src_body = ""
src_footer = """}
"""

src_method = """
    public void testNeverallowRules() throws Exception {
        String neverallowRule = "$NEVERALLOW_RULE_HERE$";

        /* run sepolicy-analyze neverallow check on policy file using given neverallow rules */
        ProcessBuilder pb = new ProcessBuilder(sepolicyAnalyze.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(), "neverallow", "-n",
                neverallowRule);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\\n");
        }
        assertTrue("The following errors were encountered when validating the SELinux"
                   + "neverallow rule:\\n" + neverallowRule + "\\n" + errorString,
                   errorString.length() == 0);
    }
"""
