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
package com.android.tradefed;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link IRemoteTest} for running tests against a separate TF installation.
 * <p/>
 * Launches an external java process to run the tests. Used for running the TF unit tests
 * continuously.
 */
public class TfTestLauncher implements IRemoteTest, IBuildReceiver {

    @Option(name = "max-run-time", description =
        "the maximum time in minutes to allow for a TF test run.")
    private int mMaxTfRunTimeMin = 20;

    @Option(name = "remote-debug", description =
            "start the TF java process in remote debug mode.")
    private boolean mRemoteDebug = false;

    private IBuildInfo mBuildInfo;

    @Option(name = "config-name", description = "the config that runs the TF tests")
    private String mConfigName;

    private static final String TF_GLOBAL_CONFIG = "TF_GLOBAL_CONFIG";
    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mBuildInfo);
        Assert.assertNotNull(mConfigName);
        IFolderBuildInfo tfBuild = (IFolderBuildInfo)mBuildInfo;
        String jarClasspath = FileUtil.getPath(tfBuild.getRootDir().getAbsolutePath(), "*");
        List<String> args = new ArrayList<String>();
        args.add("java");
        if (mRemoteDebug) {
            args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=10088");
        }
        args.add("-cp");
        args.add(jarClasspath);
        args.add("com.android.tradefed.command.CommandRunner");
        args.add(mConfigName);
        args.add("-n");
        args.add("--build-id");
        args.add(mBuildInfo.getBuildId());
        args.add("--test-tag");
        args.add(mBuildInfo.getTestTag());
        args.add("--build-target");
        args.add(mBuildInfo.getBuildTargetName());
        if (mBuildInfo.getBuildBranch() != null) {
            args.add("--branch");
            args.add(mBuildInfo.getBuildBranch());
        }
        if (mBuildInfo.getBuildFlavor() != null) {
            args.add("--build-flavor");
            args.add(mBuildInfo.getBuildFlavor());
        }

        IRunUtil runUtil = new RunUtil();
        // clear the TF_GLOBAL_CONFIG env, so another tradefed will not reuse the global config file
        runUtil.unsetEnvVariable(TF_GLOBAL_CONFIG);
        CommandResult result = runUtil.runTimedCmd(mMaxTfRunTimeMin * 60 * 1000,
                args.toArray(new String[0]));
        if (result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.d("Successfully ran TF tests for build %s", mBuildInfo.getBuildId());
        } else {
            CLog.w("Failed ran TF tests for build %s, status %s",
                    mBuildInfo.getBuildId(), result.getStatus());
        }
        CLog.v("TF tests output:\nstdout:\n%s\nstderror:\n%s",
            result.getStdout(), result.getStderr());
    }

    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
