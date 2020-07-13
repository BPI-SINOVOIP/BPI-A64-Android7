/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

/**
 * An {@link ITargetPreparer} that adds fault injection configuration to an OTA package ZIP.
 */
public class OtaFaultInjectionPreparer implements ITargetPreparer {

    protected static final String CFG_BASE = ".libotafault";

    @Option(name = "read-fault-file", description = "the filename to trigger a read fault")
    protected String mReadFaultFile = null;

    @Option(name = "write-fault-file", description = "the filename to trigger a write fault")
    protected String mWriteFaultFile = null;

    @Option(name = "fsync-fault-file", description = "the filename to trigger a fsync fault")
    protected String mFsyncFaultFile = null;

    @Option(name = "hit-cache", description = "whether or not to hit /cache/saved.file instead of "
            + "the targeted file")
    protected boolean mShouldHitCache = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new TargetSetupError(
                    "OtaFaultInjectionPreparer must receive an IDeviceBuildInfo");
        }
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo) buildInfo;
        ZipOutputStream otaPackageStream = null;
        try {
            otaPackageStream = new ZipOutputStream(
                    new FileOutputStream(deviceBuild.getOtaPackageFile()));
            if (mReadFaultFile != null) {
                addToConfig(otaPackageStream, "READ", mReadFaultFile);
            }
            if (mWriteFaultFile != null) {
                addToConfig(otaPackageStream, "WRITE", mWriteFaultFile);
            }
            if (mFsyncFaultFile != null) {
                addToConfig(otaPackageStream, "FSYNC", mFsyncFaultFile);
            }
            if (mShouldHitCache) {
                addToConfig(otaPackageStream, "CACHE", "");
            }
        } catch (IOException e) {
            throw new TargetSetupError("Could not add config files to OTA zip", e);
        } finally {
            try {
                if (otaPackageStream != null) {
                    otaPackageStream.close();
                }
            } catch (IOException e) {
                throw new TargetSetupError("Could not close OTA zip file", e);
            }
        }
    }

    private void addToConfig(ZipOutputStream packageStream, String ioType, String targetFile)
            throws IOException {
        File cfgFile = new File(ioType);
        try {
            FileWriter cfgWriter = new FileWriter(cfgFile);
            cfgWriter.write(targetFile, 0, targetFile.length());
            cfgWriter.close();
            ZipUtil.addToZip(packageStream, cfgFile,
                    ArrayUtil.list(CFG_BASE, File.separator));
        } finally {
            cfgFile.delete();
        }
    }
}