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

package com.android.ota.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A test that will perform repeated flash + install OTA actions on a device.
 * <p/>
 * adb must have root.
 * <p/>
 * Expects a {@link OtaDeviceBuildInfo}.
 * <p/>
 * Note: this test assumes that the {@link ITargetPreparer}s included in this test's
 * {@link IConfiguration} will flash the device back to a baseline build, and prepare the device to
 * receive the OTA to a new build.
 */
@OptionClass(alias = "ota-stability")
public class SideloadOtaStabilityTest implements IDeviceTest, IBuildReceiver,
        IConfigurationReceiver, IShardableTest, IResumableTest {

    private static final String CACHE_OTA_PATH = "/cache/recovery/otatest/update.zip";
    private static final String RECOVERY_COMMAND_PATH = "/cache/recovery/command";

    private OtaDeviceBuildInfo mOtaDeviceBuild;
    private IConfiguration mConfiguration;
    private ITestDevice mDevice;

    @Option(name = "run-name", description =
            "The name of the ota stability test run. Used to report metrics.")
    private String mRunName = "ota-stability";

    @Option(name = "iterations", description =
            "Number of ota stability 'flash + wait for ota' iterations to run.")
    private int mIterations = 20;

    @Option(name = "shards", description = "Optional number of shards to split test into. "
            + "Iterations will be split evenly among shards.", importance = Importance.IF_UNSET)
    private Integer mShards = null;

    @Option(name = "resume", description = "Resume the ota test run if an device setup error "
            + "stopped the previous test run.")
    private boolean mResumeMode = false;

    @Option(name = "max-install-time", description =
            "The maximum time to wait for an ota to install in seconds.")
    private int mMaxInstallOnlineTimeSec = 5 * 60;

    /** controls if this test should be resumed. Only used if mResumeMode is enabled */
    private boolean mResumable = true;

    private String mExpectedBootloaderVersion, mExpectedBasebandVersion;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mOtaDeviceBuild = (OtaDeviceBuildInfo)buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the run name
     */
    void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * Return the number of iterations.
     * <p/>
     * Exposed for unit testing
     */
    public int getIterations() {
        return mIterations;
    }

    /**
     * Set the iterations
     */
    void setIterations(int iterations) {
        mIterations = iterations;
    }

    /**
     * Set the number of shards
     */
    void setShards(int shards) {
        mShards = shards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards == null || mShards <= 1) {
            return null;
        }
        Collection<IRemoteTest> shards = new ArrayList<IRemoteTest>(mShards);
        int remainingIterations = mIterations;
        for (int i = mShards; i > 0; i--) {
            SideloadOtaStabilityTest testShard = new SideloadOtaStabilityTest();
            // device and configuration will be set by test invoker
            testShard.setRunName(mRunName);
            // attempt to divide iterations evenly among shards with no remainder
            int iterationsForShard = remainingIterations / i;
            if (iterationsForShard > 0) {
                testShard.setIterations(iterationsForShard);
                remainingIterations -= iterationsForShard;
                shards.add(testShard);
            }
        }
        return shards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // started run, turn to off
        mResumable = false;
        checkFields();

        CLog.i("Starting OTA sideload test from %s to %s, for %d iterations",
                mOtaDeviceBuild.getDeviceImageVersion(),
                mOtaDeviceBuild.getOtaBuild().getOtaPackageVersion(), mIterations);

        getBasebandBootloaderVersions(mOtaDeviceBuild.getOtaBuild());

        long startTime = System.currentTimeMillis();
        listener.testRunStarted(mRunName, 0);
        int actualIterations = 0;
        try {
            while (actualIterations < mIterations) {
                if (actualIterations != 0) {
                    // don't need to flash device on first iteration
                    flashDevice();
                }
                installOta(listener, mOtaDeviceBuild.getOtaBuild());
                actualIterations++;
                CLog.i("Device %s successfully OTA-ed to build %s. Iteration: %d of %d",
                        mDevice.getSerialNumber(),
                        mOtaDeviceBuild.getOtaBuild().getOtaPackageVersion(),
                        actualIterations, mIterations);
            }
        } catch (AssertionFailedError error) {
            CLog.e(error);
        } catch (TargetSetupError e) {
            CLog.i("Encountered TargetSetupError, marking this test as resumable");
            mResumable = true;
            CLog.e(e);
            // throw up an exception so this test can be resumed
            Assert.fail(e.toString());
        } catch (BuildError e) {
            CLog.e(e);
        } catch (ConfigurationException e) {
            CLog.e(e);
        } finally {
            Map<String, String> metrics = new HashMap<String, String>(1);
            metrics.put("iterations", Integer.toString(actualIterations));
            metrics.put("failed_iterations", Integer.toString(mIterations - actualIterations));
            long endTime = System.currentTimeMillis() - startTime;
            listener.testRunEnded(endTime, metrics);
        }
    }

    /**
     * Flash the device back to baseline build.
     * <p/>
     * Currently does this by re-running {@link ITargetPreparer#setUp(ITestDevice, IBuildInfo)}
     *
     * @throws DeviceNotAvailableException
     * @throws BuildError
     * @throws TargetSetupError
     * @throws ConfigurationException
     */
    private void flashDevice() throws TargetSetupError, BuildError, DeviceNotAvailableException,
            ConfigurationException {
        // assume the target preparers will flash the device back to device build
        for (ITargetPreparer preparer : mConfiguration.getTargetPreparers()) {
            preparer.setUp(mDevice, mOtaDeviceBuild);
        }
    }

    /**
     * Get the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed so unit tests can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    private void checkFields() {
        if (mDevice == null) {
            throw new IllegalArgumentException("missing device");
        }
        if (mConfiguration == null) {
            throw new IllegalArgumentException("missing configuration");
        }
        if (mOtaDeviceBuild == null) {
            throw new IllegalArgumentException("missing build info");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        return mResumeMode && mResumable;
    }

    private void installOta(ITestInvocationListener listener, IDeviceBuildInfo otaBuild)
            throws DeviceNotAvailableException {
        Assert.assertTrue(mDevice.pushFile(otaBuild.getOtaPackageFile(), CACHE_OTA_PATH));
        String installOtaCmd = String.format("--update_package=%s", CACHE_OTA_PATH);
        mDevice.pushString(installOtaCmd, RECOVERY_COMMAND_PATH);

        try {
            mDevice.rebootIntoRecovery();
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s did not boot into recovery", mDevice.getSerialNumber());
            throw e;
        }

        try {
            mDevice.waitForDeviceOnline(mMaxInstallOnlineTimeSec * 1000);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s did not come back online after recovery", mDevice.getSerialNumber());
            sendRecoveryLog(listener);
            throw e;
        }
        try {
            mDevice.waitForDeviceAvailable();
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s did not boot up successfully after installing OTA",
                    mDevice.getSerialNumber());
            throw e;
        }
        Assert.assertEquals("build id does not equal expected value after OTA",
                mDevice.getBuildId(), otaBuild.getOtaPackageVersion());
        Assert.assertEquals("bootloader version does not equal expected value after OTA",
                mDevice.getBootloaderVersion(), mExpectedBootloaderVersion);
        Assert.assertEquals("baseband version does not equal expected value after OTA",
                mDevice.getBasebandVersion(), mExpectedBasebandVersion);

    }

    private void sendRecoveryLog(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        File destFile = null;
        InputStreamSource destSource = null;
        try {
            // get recovery log
            destFile = FileUtil.createTempFile("recovery", "log");
            boolean gotFile = mDevice.pullFile("/tmp/recovery.log", destFile);
            if (gotFile) {
                destSource = new SnapshotInputStreamSource(new FileInputStream(destFile));
                listener.testLog("recovery_log", LogDataType.TEXT, destSource);
            }
        } catch (IOException e) {
            CLog.e("Failed to get recovery log from device %s", mDevice.getSerialNumber());
            CLog.e(e);
        } finally {
            if (destSource != null) {
                destSource.cancel();
            }
            FileUtil.deleteFile(destFile);
        }
    }

    private void getBasebandBootloaderVersions(IDeviceBuildInfo otaBuild) {
        try {
            FlashingResourcesParser parser = new FlashingResourcesParser(
                    otaBuild.getDeviceImageFile());
            mExpectedBootloaderVersion = parser.getRequiredBootloaderVersion();
            mExpectedBasebandVersion = parser.getRequiredBasebandVersion();
        } catch (TargetSetupError e) {
            throw new RuntimeException("Error when trying to set up OTA version info");
        }
    }
}
