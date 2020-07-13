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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;

@OptionClass(alias = "run-command")
public class RunCommandTargetPreparer implements ITargetCleaner {
    @Option(name = "run-command", description = "adb shell command to run")
    private List<String> mCommands = new ArrayList<String>();

    @Option(name = "teardown-command", description = "adb shell command to run at teardown time")
    private List<String> mTeardownCommands = new ArrayList<String>();

    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = false;

    @Option(name = "delay-after-commands",
            description = "Time to delay after running commands, in msecs")
    private long mDelayMsecs = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable) return;
        for (String cmd : mCommands) {
            // If the command had any output, the executeShellCommand method will log it at the
            // VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run setup command on device %s: %s", device.getSerialNumber(), cmd);
            device.executeShellCommand(cmd);
        }

        CLog.d("Sleeping %d msecs on device %s", mDelayMsecs, device.getSerialNumber());
        RunUtil.getDefault().sleep(mDelayMsecs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) return;
        for (String cmd : mTeardownCommands) {
            // If the command had any output, the executeShellCommand method will log it at the
            // VERBOSE level; so no need to do any logging from here.
            CLog.d("About to run tearDown command on device %s: %s", device.getSerialNumber(), cmd);
            device.executeShellCommand(cmd);
        }
    }
}

