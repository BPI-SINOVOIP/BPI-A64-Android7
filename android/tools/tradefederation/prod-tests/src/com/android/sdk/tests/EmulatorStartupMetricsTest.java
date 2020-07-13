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

package com.android.sdk.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.SdkAvdPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

public class EmulatorStartupMetricsTest implements IDeviceTest, IRemoteTest, IBuildReceiver {

    private ITestDevice mTestDevice = null;
    private ISdkBuildInfo mBuildInfo = null;

    @Option(name = "gpu", description = "launch emulator with GPU on")
    private boolean mGpu = false;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        Assert.assertNotNull(mTestDevice);
        Assert.assertNotNull(mBuildInfo);

        SdkAvdPreparer sdkAvdPreparer = new SdkAvdPreparer();
        sdkAvdPreparer.setGpu(mGpu);

        Map<String, String> runMetrics = new HashMap<String, String>();

        try {
            String avdName = sdkAvdPreparer.createAvd(mBuildInfo);

            long firstBootDuration, secondBootDuration;
            // measuring first boot time for a given avd
            long launchTime = System.currentTimeMillis();
            sdkAvdPreparer.launchEmulatorForAvd(mBuildInfo, mTestDevice, avdName);
            long availableTime = System.currentTimeMillis();
            firstBootDuration = availableTime - launchTime;

            getDeviceManager().killEmulator(mTestDevice);

            // measuring second boot time for a given avd
            launchTime = System.currentTimeMillis();
            sdkAvdPreparer.launchEmulatorForAvd(mBuildInfo, mTestDevice, avdName);
            availableTime = System.currentTimeMillis();
            secondBootDuration = availableTime - launchTime;

            runMetrics.put("fboot", Double.toString(firstBootDuration/1000.0));
            runMetrics.put("sboot", Double.toString(secondBootDuration/1000.0));
            reportMetrics(listener, "emulator_boottime", runMetrics);
        } catch (TargetSetupError tse) {
            CLog.e(tse);
            Assert.fail("Emulator failed to launch");
        } catch(BuildError be) {
            CLog.e(be);
            Assert.fail("Emulator failed to launch");
        }
    }

    private IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getDeviceManagerInstance();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        Assert.assertTrue("Provided build is not a ISdkBuildInfo",
                buildInfo instanceof ISdkBuildInfo);
        mBuildInfo = (ISdkBuildInfo) buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param runName the test name
     * @param metrics the {@link Map} that contains metrics for the given test
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }
}
