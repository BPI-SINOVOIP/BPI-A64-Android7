/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Semaphore;

/**
 * A {@link ITargetPreparer} that flashes an image on physical Android hardware.
 */
public abstract class DeviceFlashPreparer implements ITargetCleaner {

    /**
     * Enum of options for handling the encryption of userdata image
     */
    public static enum EncryptionOptions {ENCRYPT, IGNORE};

    private static final int BOOT_POLL_TIME_MS = 5 * 1000;

    @Option(name = "device-boot-time", description = "max time in ms to wait for device to boot.")
    private long mDeviceBootTime = 5 * 60 * 1000;

    @Option(name = "userdata-flash", description =
        "specify handling of userdata partition.")
    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.FLASH;

    @Option(name = "encrypt-userdata", description = "specify if userdata partition should be "
            + "encrypted; defaults to IGNORE, where no actions will be taken.")
    private EncryptionOptions mEncryptUserData = EncryptionOptions.IGNORE;

    @Option(name = "force-system-flash", description =
        "specify if system should always be flashed even if already running desired build.")
    private boolean mForceSystemFlash = false;

    /*
     * A temporary workaround for special builds. Should be removed after changes from build team.
     * Bug: 18078421
     */
    @Option(name = "skip-post-flash-flavor-check", description =
            "specify if system flavor should not be checked after flash")
    private boolean mSkipPostFlashFlavorCheck = false;

    @Option(name = "wipe-skip-list", description =
        "list of /data subdirectories to NOT wipe when doing UserDataFlashOption.TESTS_ZIP")
    private Collection<String> mDataWipeSkipList = new ArrayList<String>();

    @Option(name = "concurrent-flasher-limit", description =
        "The maximum number of concurrent flashers (may be useful to avoid memory constraints)")
    private Integer mConcurrentFlashLimit = null;

    @Option(name = "skip-post-flashing-setup",
            description = "whether or not to skip post-flashing setup steps")
    private boolean mSkipPostFlashingSetup = false;

    private static Semaphore sConcurrentFlashLock = null;

    /**
     * This serves both as an indication of whether the flash lock should be used, and as an
     * indicator of whether or not the flash lock has been initialized -- if this is true
     * and {@code mConcurrentFlashLock} is {@code null}, then it has not yet been initialized.
     */
    private static Boolean sShouldCheckFlashLock = true;

    /**
     * Sets the device boot time
     * <p/>
     * Exposed for unit testing
     */
    void setDeviceBootTime(long bootTime) {
        mDeviceBootTime = bootTime;
    }

    /**
     * Gets the interval between device boot poll attempts.
     * <p/>
     * Exposed for unit testing
     */
    int getDeviceBootPollTimeMs() {
        return BOOT_POLL_TIME_MS;
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Set the userdata-flash option
     *
     * @param flashOption
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        mUserDataFlashOption = flashOption;
    }

    /**
     * Set the state of the concurrent flash limit implementation
     *
     * Exposed for unit testing
     */
    void setConcurrentFlashSettings(Integer limit, Semaphore flashLock, boolean shouldCheck) {
        synchronized(sShouldCheckFlashLock) {
            // Make a minimal attempt to avoid having things get into an inconsistent state
            if (sConcurrentFlashLock != null && mConcurrentFlashLimit != null) {
                int curLimit = mConcurrentFlashLimit;
                int curAvail = sConcurrentFlashLock.availablePermits();
                if (curLimit != curAvail) {
                    throw new IllegalStateException(String.format("setConcurrentFlashSettings may " +
                            "not be called while any permits are active.  The flasher limit is %d, " +
                            "but there are only %d permits available.", curLimit, curAvail));
                }
            }

            mConcurrentFlashLimit = limit;
            sConcurrentFlashLock = flashLock;
            sShouldCheckFlashLock = shouldCheck;
        }
    }

    Semaphore getConcurrentFlashLock() {
        return sConcurrentFlashLock;
    }

    /**
     * Request permission to flash.  If the number of concurrent flashers is limited, this will
     * wait in line in order to remain under the flash limit count.
     *
     * Exposed for unit testing.
     */
    void takeFlashingPermit() {
        if (!sShouldCheckFlashLock) return;

        // The logic below is to avoid multi-thread race conditions while initializing
        // mConcurrentFlashLock when we hit this condition.
        if (sConcurrentFlashLock == null) {
            // null with mShouldCheckFlashLock == true means initialization hasn't been done yet
            synchronized(sShouldCheckFlashLock) {
                // Check all state again, since another thread might have gotten here first
                if (!sShouldCheckFlashLock) return;

                if (mConcurrentFlashLimit == null) {
                    sShouldCheckFlashLock = false;
                    return;
                }

                if (sConcurrentFlashLock == null) {
                    sConcurrentFlashLock = new Semaphore(mConcurrentFlashLimit, true /* fair */);
                }
            }
        }

        sConcurrentFlashLock.acquireUninterruptibly();
    }

    /**
     * Restore a flashing permit that we acquired previously
     *
     * Exposed for unit testing.
     */
    void returnFlashingPermit() {
        if (sConcurrentFlashLock != null) {
            sConcurrentFlashLock.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        CLog.i("Performing setup on %s", device.getSerialNumber());
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a IDeviceBuildInfo");
        }
        // don't allow interruptions during flashing operations.
        getRunUtil().allowInterrupt(false);
        try {
            IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo)buildInfo;
            device.setRecoveryMode(RecoveryMode.ONLINE);
            IDeviceFlasher flasher = createFlasher(device);
            // only surround fastboot related operations with flashing permit restriction
            try {
                takeFlashingPermit();

                flasher.overrideDeviceOptions(device);
                flasher.setUserDataFlashOption(mUserDataFlashOption);
                flasher.setForceSystemFlash(mForceSystemFlash);
                flasher.setDataWipeSkipList(mDataWipeSkipList);
                preEncryptDevice(device, flasher);
                flasher.flash(device, deviceBuild);
            } finally {
                returnFlashingPermit();
            }
            if (mSkipPostFlashingSetup) {
                return;
            }
            device.waitForDeviceOnline();
            // device may lose date setting if wiped, update with host side date in case anything on
            // device side malfunction with an invalid date
            if (device.enableAdbRoot()) {
                device.setDate(null);
            }
            checkBuild(device, deviceBuild);
            postEncryptDevice(device, flasher);
            // only want logcat captured for current build, delete any accumulated log data
            device.clearLogcat();
            try {
                device.setRecoveryMode(RecoveryMode.AVAILABLE);
                device.waitForDeviceAvailable(mDeviceBootTime);
            } catch (DeviceUnresponsiveException e) {
                // assume this is a build problem
                throw new DeviceFailedToBootError(String.format(
                        "Device %s did not become available after flashing %s",
                        device.getSerialNumber(), deviceBuild.getDeviceBuildId()));
            }
            device.postBootSetup();
        } finally {
            getRunUtil().allowInterrupt(true);
        }
    }

    /**
     * Verifies the expected build matches the actual build on device after flashing
     * @throws DeviceNotAvailableException
     */
    private void checkBuild(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException {
        // Need to use deviceBuild.getDeviceBuildId instead of getBuildId because the build info
        // could be an AppBuildInfo and return app build id. Need to be more explicit that we
        // check for the device build here.
        checkBuildAttribute(deviceBuild.getDeviceBuildId(), device.getBuildId());
        if (!mSkipPostFlashFlavorCheck) {
            checkBuildAttribute(deviceBuild.getBuildFlavor(), device.getBuildFlavor());
        }
        // TODO: check bootloader and baseband versions too
    }

    private void checkBuildAttribute(String expectedBuildAttr, String actualBuildAttr)
            throws DeviceNotAvailableException {
        if (expectedBuildAttr == null || actualBuildAttr == null ||
                !expectedBuildAttr.equals(actualBuildAttr)) {
            // throw DNAE - assume device hardware problem - we think flash was successful but
            // device is not running right bits
            throw new DeviceNotAvailableException(
                    String.format("Unexpected build after flashing. Expected %s, actual %s",
                            expectedBuildAttr, actualBuildAttr));
        }
    }

    /**
     * Create {@link IDeviceFlasher} to use. Subclasses can override
     * @throws DeviceNotAvailableException
     */
    protected abstract IDeviceFlasher createFlasher(ITestDevice device)
            throws DeviceNotAvailableException;

    /**
     * Handle encrypting of the device pre-flash.
     *
     * @see #postEncryptDevice(ITestDevice, IDeviceFlasher)
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError if the device could not be encrypted or unlocked.
     */
    private void preEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException, TargetSetupError {
        switch (mEncryptUserData) {
            case IGNORE:
                return;
            case ENCRYPT:
                if (!device.isEncryptionSupported()) {
                    throw new TargetSetupError("Encryption is not supported");
                }
                if (!device.isDeviceEncrypted()) {
                    switch(flasher.getUserDataFlashOption()) {
                        case TESTS_ZIP: // Intentional fall through.
                        case WIPE_RM:
                            // a new filesystem will not be created by the flasher, but the userdata
                            // partition is expected to be cleared anyway, so we encrypt the device
                            // with wipe
                            if (!device.encryptDevice(false)) {
                                throw new TargetSetupError("Failed to encrypt device");
                            }
                            if (!device.unlockDevice()) {
                                throw new TargetSetupError("Failed to unlock device");
                            }
                            break;
                        case RETAIN:
                            // original filesystem must be retained, so we encrypt in place
                            if (!device.encryptDevice(true)) {
                                throw new TargetSetupError("Failed to encrypt device");
                            }
                            if (!device.unlockDevice()) {
                                throw new TargetSetupError("Failed to unlock device");
                            }
                            break;
                        default:
                            // Do nothing, userdata will be encrypted post-flash.
                    }
                }
                break;
            default:
                // should not get here
                return;
        }
    }

    /**
     * Handle encrypting of the device post-flash.
     * <p>
     * This method handles encrypting the device after a flash in cases where a flash would undo any
     * encryption pre-flash, such as when the device is flashed or wiped.
     * </p>
     *
     * @see #preEncryptDevice(ITestDevice, IDeviceFlasher)
     * @param device
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError If the device could not be encrypted or unlocked.
     */
    private void postEncryptDevice(ITestDevice device, IDeviceFlasher flasher)
            throws DeviceNotAvailableException, TargetSetupError {
        switch (mEncryptUserData) {
            case IGNORE:
                return;
            case ENCRYPT:
                if (!device.isEncryptionSupported()) {
                    throw new TargetSetupError("Encryption is not supported");
                }
                switch(flasher.getUserDataFlashOption()) {
                    case FLASH:
                        if (!device.encryptDevice(true)) {
                            throw new TargetSetupError("Failed to encrypt device");
                        }
                        break;
                    case WIPE: // Intentional fall through.
                    case FORCE_WIPE:
                        // since the device was just wiped, encrypt with wipe
                        if (!device.encryptDevice(false)) {
                            throw new TargetSetupError("Failed to encrypt device");
                        }
                        break;
                    default:
                        // Do nothing, userdata was encrypted pre-flash.
                }
                if (!device.unlockDevice()) {
                    throw new TargetSetupError("Failed to unlock device");
                }
                break;
            default:
                // should not get here
                return;
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mEncryptUserData == EncryptionOptions.ENCRYPT
                && mUserDataFlashOption != UserDataFlashOption.RETAIN) {
            if (e instanceof DeviceNotAvailableException) {
                CLog.e("Device was encrypted but now unavailable. may need manual cleanup");
            } else if (device.isDeviceEncrypted()) {
                if (!device.unencryptDevice()) {
                    throw new RuntimeException("Failed to unencrypt device");
                }
            }
        }
    }
}
