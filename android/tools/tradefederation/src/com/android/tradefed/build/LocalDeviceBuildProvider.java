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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.google.common.io.PatternFilenameFilter;

import java.io.File;

/**
 * A {@link IBuildProvider} that constructs a {@link IDeviceBuildInfo} based on a provided
 * filesystem directory path.
 * <p/>
 * Specific device build files are recognized based on a configurable set of patterns.
 */
@OptionClass(alias = "local-device-build")
public class LocalDeviceBuildProvider extends StubBuildProvider {

    private static final String BUILD_DIR_OPTION_NAME = "build-dir";

    @Option(name = BUILD_DIR_OPTION_NAME, description = "the directory containing device files.",
            mandatory = true, importance = Importance.ALWAYS)
    private File mBuildDir = null;

    @Option(name = "device-img-pattern", description =
            "the regex use to find device system image zip file within --build-dir.")
    private String mImgPattern = ".*-img-.*\\.zip";

    @Option(name = "test-dir-pattern", description =
            "the regex use to find optional test artifact directory within --build-dir.")
    private String mTestDirPattern = ".*-tests-.*";

    @Option(name = "bootloader-pattern", description =
            "the regex use to find device bootloader image file within --build-dir.")
    private String mBootloaderPattern = "boot.*\\.img";

    @Option(name = "radio-pattern", description =
            "the regex use to find device radio image file within --build-dir.")
    private String mRadioPattern = "radio.*\\.img";

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        if (!mBuildDir.exists()) {
            throw new BuildRetrievalError(String.format("Directory '%s' does not exist. " +
                    "Please provide a valid path via --%s", mBuildDir.getAbsolutePath(),
                    BUILD_DIR_OPTION_NAME));
        }
        if (!mBuildDir.isDirectory()) {
            throw new BuildRetrievalError(String.format("Path '%s' is not a directory. " +
                    "Please provide a valid path via --%s", mBuildDir.getAbsolutePath(),
                    BUILD_DIR_OPTION_NAME));
        }
        CLog.d("Using device build files from %s", mBuildDir.getAbsolutePath());

        BuildInfo stubBuild = (BuildInfo)super.getBuild();
        DeviceBuildInfo buildInfo = new DeviceBuildInfo(stubBuild.getBuildId(),
                stubBuild.getTestTag(), stubBuild.getBuildTargetName());
        buildInfo.addAllBuildAttributes(stubBuild);

        findDeviceImageFile(buildInfo);
        findTestsDir(buildInfo);
        // TODO: enable these once the proper version can be determined
        //findBootloader(buildInfo);
        //findRadio(buildInfo);

        return buildInfo;
    }

    private void findDeviceImageFile(DeviceBuildInfo buildInfo) throws BuildRetrievalError {
        File deviceImgFile = findFileInDir(mImgPattern);
        if (deviceImgFile == null) {
            throw new BuildRetrievalError(String.format(
                    "Could not find device image file matching matching '%s' in '%s'.", mImgPattern,
                    mBuildDir.getAbsolutePath()));
        }
        buildInfo.setDeviceImageFile(deviceImgFile, buildInfo.getBuildId());
    }

    @SuppressWarnings("unused")
    private void findRadio(DeviceBuildInfo buildInfo) throws BuildRetrievalError {
        File radioImgFile = findFileInDir(mRadioPattern);
        if (radioImgFile != null) {
            buildInfo.setBasebandImage(radioImgFile, buildInfo.getBuildId());
        }
    }

    @SuppressWarnings("unused")
    private void findBootloader(DeviceBuildInfo buildInfo) throws BuildRetrievalError {
        File bootloaderImgFile = findFileInDir(mBootloaderPattern);
        if (bootloaderImgFile != null) {
            buildInfo.setBootloaderImageFile(bootloaderImgFile, buildInfo.getBuildId());
        }
    }

    private void findTestsDir(DeviceBuildInfo buildInfo) throws BuildRetrievalError {
        File testsDir = findFileInDir(mTestDirPattern);
        if (testsDir != null) {
            buildInfo.setTestsDir(testsDir, buildInfo.getBuildId());
        }
    }

    private File findFileInDir(String regex) throws BuildRetrievalError {
        File[] files = mBuildDir.listFiles(new PatternFilenameFilter(regex));
        if (files.length == 0) {
            return null;
        } else if (files.length > 1) {
            throw new BuildRetrievalError(String.format(
                    "Found more than one file matching '%s' in '%s'.", regex,
                    mBuildDir.getAbsolutePath()));
        }
        return files[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
