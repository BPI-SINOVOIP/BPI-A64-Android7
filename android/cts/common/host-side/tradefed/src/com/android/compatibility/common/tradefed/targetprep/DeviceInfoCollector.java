/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.util.CollectorUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An {@link ApkInstrumentationPreparer} that collects device info.
 */
public class DeviceInfoCollector extends ApkInstrumentationPreparer {

    private static final Map<String, String> BUILD_KEYS = new HashMap<>();
    static {
        BUILD_KEYS.put("cts:build_id", "ro.build.id");
        BUILD_KEYS.put("cts:build_product", "ro.product.name");
        BUILD_KEYS.put("cts:build_device", "ro.product.device");
        BUILD_KEYS.put("cts:build_board", "ro.product.board");
        BUILD_KEYS.put("cts:build_manufacturer", "ro.product.manufacturer");
        BUILD_KEYS.put("cts:build_brand", "ro.product.brand");
        BUILD_KEYS.put("cts:build_model", "ro.product.model");
        BUILD_KEYS.put("cts:build_type", "ro.build.type");
        BUILD_KEYS.put("cts:build_tags", "ro.build.tags");
        BUILD_KEYS.put("cts:build_fingerprint", "ro.build.fingerprint");
        BUILD_KEYS.put("cts:build_abi", "ro.product.cpu.abi");
        BUILD_KEYS.put("cts:build_abi2", "ro.product.cpu.abi2");
        BUILD_KEYS.put("cts:build_abis", "ro.product.cpu.abilist");
        BUILD_KEYS.put("cts:build_abis_32", "ro.product.cpu.abilist32");
        BUILD_KEYS.put("cts:build_abis_64", "ro.product.cpu.abilist64");
        BUILD_KEYS.put("cts:build_serial", "ro.serialno");
        BUILD_KEYS.put("cts:build_version_release", "ro.build.version.release");
        BUILD_KEYS.put("cts:build_version_sdk", "ro.build.version.sdk");
        BUILD_KEYS.put("cts:build_version_base_os", "ro.build.version.base_os");
        BUILD_KEYS.put("cts:build_version_security_patch", "ro.build.version.security_patch");
        BUILD_KEYS.put("cts:build_reference_fingerprint", "ro.build.reference.fingerprint");
    }

    @Option(name = CompatibilityTest.SKIP_DEVICE_INFO_OPTION,
            shortName = 'd',
            description = "Whether device info collection should be skipped")
    private boolean mSkipDeviceInfo = false;

    @Option(name= "src-dir", description = "The directory to copy to the results dir")
    private String mSrcDir;

    @Option(name = "dest-dir", description = "The directory under the result to store the files")
    private String mDestDir;

    @Option(name = "temp-dir", description = "The directory containing host-side device info files")
    private String mTempDir;

    // Temp directory for host-side device info files.
    private File mHostDir;

    // Destination result directory for all device info files.
    private File mResultDir;

    public DeviceInfoCollector() {
        mWhen = When.BOTH;
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        for (Entry<String, String> entry : BUILD_KEYS.entrySet()) {
            buildInfo.addBuildAttribute(entry.getKey(),
                    ArrayUtil.join(",", device.getProperty(entry.getValue())));
        }
        if (mSkipDeviceInfo) {
            return;
        }

        createTempHostDir();
        createResultDir(buildInfo);
        run(device, buildInfo);
        getDeviceInfoFiles(device);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e) {
        if (mSkipDeviceInfo) {
            return;
        }
        if (mHostDir != null && mHostDir.isDirectory() &&
                    mResultDir != null && mResultDir.isDirectory()) {
            CollectorUtil.pullFromHost(mHostDir, mResultDir);
        }
    }

    private void createTempHostDir() {
        try {
            mHostDir = FileUtil.createNamedTempDir(mTempDir);
            if (!mHostDir.isDirectory()) {
                CLog.e("%s is not a directory", mHostDir.getAbsolutePath());
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createResultDir(IBuildInfo buildInfo) {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            mResultDir = buildHelper.getResultDir();
            if (mDestDir != null) {
                mResultDir = new File(mResultDir, mDestDir);
            }
            mResultDir.mkdirs();
            if (!mResultDir.isDirectory()) {
                CLog.e("%s is not a directory", mResultDir.getAbsolutePath());
                return;
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    private void getDeviceInfoFiles(ITestDevice device) {
        if (mResultDir != null && mResultDir.isDirectory()) {
            String mResultPath = mResultDir.getAbsolutePath();
            CollectorUtil.pullFromDevice(device, mSrcDir, mResultPath);
        }
    }
}
