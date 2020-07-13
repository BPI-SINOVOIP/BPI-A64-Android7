/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * A {@link ITargetPreparer} that pushes all .jar files it finds in the {@link IBuildInfo} to
 * device.
 */
public class TestJarInstaller implements ITargetPreparer, ITargetCleaner {

    // destination path for jars. Chosen because its writable even on user builds
    private static final String DATA_LOCAL_TMP = "/data/local/tmp/";

    @Option(name = "uninstall", description = "remove all jars after test completes.")
    private boolean mUninstall = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        for (VersionedFile buildFile : buildInfo.getFiles()) {
            File file = buildFile.getFile();
            CLog.d("Examining build file %s", file.getName());
            if (isJarFile(file)) {
                String remotePath = String.format("%s%s", DATA_LOCAL_TMP, file.getName());
                CLog.d("Pushing %s to %s", file.getName(), remotePath);
                if (!device.pushFile(file, remotePath)) {
                    throw new TargetSetupError(String.format("Failed to push %s to %s",
                            file.getName(), remotePath));
                }
            }
        }
    }

    private boolean isJarFile(File file) {
        return FileUtil.getExtension(file.getName()).equals(".jar");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mUninstall) {
            // TODO: verify contents removed
            device.executeShellCommand(String.format("rm %s*.jar", DATA_LOCAL_TMP));
        }
    }
}
