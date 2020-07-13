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

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * A class that relies on fastboot to flash an image on physical Android hardware.
 */
public class FastbootDeviceFlasher implements IDeviceFlasher  {
    public static final String BASEBAND_IMAGE_NAME = "radio";

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_SLEEP = 2 * 1000; // 2s sleep between retries

    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.FLASH;

    private IFlashingResourcesRetriever mResourceRetriever;

    private ITestsZipInstaller mTestsZipInstaller = null;

    private Collection<String> mDataWipeSkipList = null;

    private boolean mForceSystemFlash;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFlashingResourcesRetriever(IFlashingResourcesRetriever retriever) {
        mResourceRetriever = retriever;
    }

    protected IFlashingResourcesRetriever getFlashingResourcesRetriever() {
        return mResourceRetriever;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        mUserDataFlashOption = flashOption;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDataFlashOption getUserDataFlashOption() {
        return mUserDataFlashOption;
    }

    void setTestsZipInstaller(ITestsZipInstaller testsZipInstaller) {
        mTestsZipInstaller = testsZipInstaller;
    }

    ITestsZipInstaller getTestsZipInstaller() {
        // Lazily initialize the TestZipInstaller.
        if (mTestsZipInstaller == null) {
            if (mDataWipeSkipList == null) {
                mDataWipeSkipList = new ArrayList<String> ();
            }
            if (mDataWipeSkipList.isEmpty()) {
                // To maintain backwards compatibility. Keep media by default.
                // TODO: deprecate and remove this.
                mDataWipeSkipList.add("media");
            }
            mTestsZipInstaller = new DefaultTestsZipInstaller(mDataWipeSkipList);
        }
        return mTestsZipInstaller;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild) throws TargetSetupError,
            DeviceNotAvailableException {

        CLog.i("Flashing device %s with build %s", device.getSerialNumber(),
                deviceBuild.getDeviceBuildId());

        // get system build id and build flavor before booting into fastboot
        String systemBuildId = device.getBuildId();
        String systemBuildFlavor = device.getBuildFlavor();

        device.rebootIntoBootloader();

        downloadFlashingResources(device, deviceBuild);

        checkAndFlashBootloader(device, deviceBuild);
        checkAndFlashBaseband(device, deviceBuild);
        flashUserData(device, deviceBuild);
        wipeCache(device);
        checkAndFlashSystem(device, systemBuildId, systemBuildFlavor, deviceBuild);
    }

    /**
     * Flash an individual partition of a device
     *
     * @param device the {@link ITestDevice} to flash
     * @param imgFile a {@link File} pointing to the image to be flashed
     * @param partition the name of the partition to be flashed
     */
    protected void flashPartition(ITestDevice device, File imgFile, String partition)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.d("fastboot flash %s %s", partition, imgFile.getAbsolutePath());
        executeLongFastbootCmd(device, "flash", partition, imgFile.getAbsolutePath());
    }

    /**
     * Wipe the specified partition with `fastboot erase &lt;name&gt;`
     *
     * @param device the {@link ITestDevice} to operate on
     * @param partition the name of the partition to be wiped
     */
    protected void wipePartition(ITestDevice device, String partition)
            throws DeviceNotAvailableException, TargetSetupError {
        String wipeMethod = device.getUseFastbootErase() ? "erase" : "format";
        CLog.d("fastboot %s %s", wipeMethod, partition);
        CommandResult result = device.fastbootWipePartition(partition);
        handleFastbootResult(device, result, wipeMethod, partition);
    }

    /**
     * Downloads extra flashing image files needed
     *
     * @param device the {@link ITestDevice} to download resources for
     * @param localBuild the {@link IDeviceBuildInfo} to populate. Assumes device image file is
     * already set
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to retrieve resources
     */
    protected void downloadFlashingResources(ITestDevice device, IDeviceBuildInfo localBuild)
            throws TargetSetupError, DeviceNotAvailableException {
        IFlashingResourcesParser resourceParser = createFlashingResourcesParser(localBuild);

        if (resourceParser.getRequiredBoards() == null) {
            throw new TargetSetupError(String.format("Build %s is missing required board info.",
                    localBuild.getDeviceBuildId()));
        }
        String deviceProductType = device.getProductType();
        if (deviceProductType == null) {
            // treat this as a fatal device error
            throw new DeviceNotAvailableException(String.format(
                    "Could not determine product type for device %s", device.getSerialNumber()));
        }
        verifyRequiredBoards(device, resourceParser, deviceProductType);

        String bootloaderVersion = resourceParser.getRequiredBootloaderVersion();
        // only set bootloader image if this build doesn't have one already
        // TODO: move this logic to the BuildProvider step
        if (bootloaderVersion != null && localBuild.getBootloaderImageFile() == null) {
           localBuild.setBootloaderImageFile(getFlashingResourcesRetriever().retrieveFile(
                   getBootloaderFilePrefix(device), bootloaderVersion), bootloaderVersion);
        }
        String basebandVersion = resourceParser.getRequiredBasebandVersion();
        // only set baseband image if this build doesn't have one already
        if (basebandVersion != null && localBuild.getBasebandImageFile() == null) {
            localBuild.setBasebandImage(getFlashingResourcesRetriever().retrieveFile(
                    BASEBAND_IMAGE_NAME, basebandVersion), basebandVersion);
        }
        downloadExtraImageFiles(resourceParser, getFlashingResourcesRetriever(), localBuild);
    }

    /**
     * Verify that the device's product type supports the build-to-be-flashed.
     * <p/>
     * The base implementation will verify that the deviceProductType is included in the
     * {@link IFlashingResourcesParser#getRequiredBoards()} collection. Subclasses may override
     * as desired.
     *
     * @param device the {@link ITestDevice} to be flashed
     * @param resourceParser the {@link IFlashingResourcesParser}
     * @param deviceProductType the <var>device</var>'s product type
     * @throws TargetSetupError if the build's required board info did not match the device
     */
    protected void verifyRequiredBoards(ITestDevice device, IFlashingResourcesParser resourceParser,
            String deviceProductType) throws TargetSetupError {
        if (!resourceParser.getRequiredBoards().contains(deviceProductType)) {
            throw new TargetSetupError(String.format("Device %s is %s. Expected %s",
                    device.getSerialNumber(), deviceProductType,
                    resourceParser.getRequiredBoards()));
        }
    }

    /**
     * Hook to allow subclasses to download extra custom image files if needed.
     *
     * @param resourceParser the {@link IFlashingResourcesParser}
     * @param retriever the {@link IFlashingResourcesRetriever}
     * @param localBuild the {@link IDeviceBuildInfo}
     * @throws TargetSetupError
     */
    protected void downloadExtraImageFiles(IFlashingResourcesParser resourceParser,
            IFlashingResourcesRetriever retriever, IDeviceBuildInfo localBuild)
            throws TargetSetupError {
    }

    /**
     * Factory method for creating a {@link IFlashingResourcesParser}.
     * <p/>
     * Exposed for unit testing.
     *
     * @param localBuild the {@link IDeviceBuildInfo} to parse
     * @return
     * @throws TargetSetupError
     */
    protected IFlashingResourcesParser createFlashingResourcesParser(IDeviceBuildInfo localBuild)
            throws TargetSetupError {
        return new FlashingResourcesParser(localBuild.getDeviceImageFile());
    }

    /**
     * If needed, flash the bootloader image on device.
     * <p/>
     * Will only flash bootloader if current version on device != required version.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the bootloader image to flash
     * @return <code>true</code> if bootloader was flashed, <code>false</code> if it was skipped
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash bootloader
     */
    protected boolean checkAndFlashBootloader(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBootloaderVersion = getImageVersion(device, "bootloader");
        if (deviceBuild.getBootloaderVersion() != null &&
                !deviceBuild.getBootloaderVersion().equals(currentBootloaderVersion)) {
            CLog.i("Flashing bootloader %s", deviceBuild.getBootloaderVersion());
            flashBootloader(device, deviceBuild.getBootloaderImageFile());
            return true;
        } else {
            CLog.i("Bootloader is already version %s, skipping flashing", currentBootloaderVersion);
            return false;
        }
    }

    /**
     * Flashes the given bootloader image and reboots back into bootloader
     *
     * @param device the {@link ITestDevice} to flash
     * @param bootloaderImageFile the bootloader image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash
     */
    protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        // bootloader images are small, and flash quickly. so use the 'normal' timeout
        executeFastbootCmd(device, "flash", getBootPartitionName(),
                bootloaderImageFile.getAbsolutePath());
        device.rebootIntoBootloader();
    }

    /**
     * Get the boot partition name for this device flasher.
     * <p/>
     * Defaults to 'hboot'. Subclasses should override if necessary.
     */
    protected String getBootPartitionName() {
        return "hboot";
    }

    /**
     * Get the bootloader file prefix.
     * <p/>
     * Defaults to {@link #getBootPartitionName()}. Subclasses should override if necessary.
     *
     * @param device the {@link ITestDevice} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to get prefix
     */
    protected String getBootloaderFilePrefix(ITestDevice device) throws TargetSetupError,
            DeviceNotAvailableException {
        return getBootPartitionName();
    }

    /**
     * If needed, flash the baseband image on device. Will only flash baseband if current version
     * on device != required version
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the baseband image to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    protected void checkAndFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, "baseband");
        if (checkShouldFlashBaseband(device, deviceBuild)) {
            CLog.i("Flashing baseband %s", deviceBuild.getBasebandVersion());
            flashBaseband(device, deviceBuild.getBasebandImageFile());
        } else {
            CLog.i("Baseband is already version %s, skipping flashing", currentBasebandVersion);
        }
    }

    /**
     * Check if the baseband on the provided device needs to be flashed.
     *
     * @param device the {@link ITestDevice} to check
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the baseband image to check
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    protected boolean checkShouldFlashBaseband(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        String currentBasebandVersion = getImageVersion(device, "baseband");
        return (deviceBuild.getBasebandVersion() != null &&
                !deviceBuild.getBasebandVersion().equals(currentBasebandVersion));
    }

    /**
     * Flashes the given baseband image and reboot back into bootloader
     *
     * @param device the {@link ITestDevice} to flash
     * @param basebandImageFile the baseband image {@link File}
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash baseband
     */
    protected void flashBaseband(ITestDevice device, File basebandImageFile)
            throws DeviceNotAvailableException, TargetSetupError {
        flashPartition(device, basebandImageFile, BASEBAND_IMAGE_NAME);
        device.rebootIntoBootloader();
    }

    /**
     * Wipe the cache partition on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash cache
     */
    protected void wipeCache(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        // only wipe cache if user data is being wiped
        if (!mUserDataFlashOption.equals(UserDataFlashOption.RETAIN)) {
            CLog.i("Wiping cache on %s", device.getSerialNumber());
            wipePartition(device, "cache");
        } else {
            CLog.d("Skipping cache wipe on %s", device.getSerialNumber());
        }
    }

    /**
     * Flash userdata partition on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the files to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash user data
     */
    protected void flashUserData(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        switch (mUserDataFlashOption) {
            case FLASH:
                CLog.i("Flashing %s with userdata %s", device.getSerialNumber(),
                        deviceBuild.getUserDataImageFile().getAbsolutePath());
                flashPartition(device, deviceBuild.getUserDataImageFile(), "userdata");
                break;
            case FLASH_IMG_ZIP:
                flashUserDataFromDeviceImageFile(device, deviceBuild);
                break;
            case FORCE_WIPE:  // intentional fallthrough
            case WIPE:
                CLog.i("Wiping userdata %s", device.getSerialNumber());
                wipePartition(device, "userdata");
                break;

            case TESTS_ZIP:
                device.rebootUntilOnline(); // required to install tests
                if (device.isEncryptionSupported() && device.isDeviceEncrypted()) {
                    device.unlockDevice();
                }
                getTestsZipInstaller().pushTestsZipOntoData(device, deviceBuild);
                // Reboot into bootloader to continue the flashing process
                device.rebootIntoBootloader();
                break;

            case WIPE_RM:
                device.rebootUntilOnline(); // required to install tests
                if (device.isEncryptionSupported() && device.isDeviceEncrypted()) {
                    // TODO: move this logic into rebootUntilOnline
                    device.unlockDevice();
                }
                getTestsZipInstaller().deleteData(device);
                // Reboot into bootloader to continue the flashing process
                device.rebootIntoBootloader();
                break;

            default:
                CLog.d("Skipping userdata flash for %s", device.getSerialNumber());
        }
    }

    /**
     * Extracts the userdata.img from device image file and flashes it onto device
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the files to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to extract or flash user data
     */
    protected void flashUserDataFromDeviceImageFile(ITestDevice device,
            IDeviceBuildInfo deviceBuild) throws DeviceNotAvailableException, TargetSetupError {
        File userdataImg = null;
        try {
            try {
                userdataImg = ZipUtil.extractFileFromZip(
                        new ZipFile(deviceBuild.getDeviceImageFile()), "userdata.img");
            } catch (IOException ioe) {
                throw new TargetSetupError("failed to extract userdata.img from image file", ioe);
            }
            CLog.i("Flashing %s with userdata %s", device.getSerialNumber(), userdataImg);
            flashPartition(device, userdataImg, "userdata");
        } finally {
            FileUtil.deleteFile(userdataImg);
        }
    }

    /**
     * If needed, flash the system image on device.
     * <p/>
     * Please look at {@link #shouldFlashSystem(String, String, IDeviceBuildInfo)}
     * <p/>
     * Regardless of path chosen, after method execution device should be booting into userspace.
     *
     * @param device the {@link ITestDevice} to flash
     * @param systemBuildId the current build id running on the device
     * @param systemBuildFlavor the current build flavor running on the device
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the system image to flash
     * @return <code>true</code> if system was flashed, <code>false</code> if it was skipped
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if failed to flash bootloader
     */
    protected boolean checkAndFlashSystem(ITestDevice device, String systemBuildId,
            String systemBuildFlavor, IDeviceBuildInfo deviceBuild)
                    throws DeviceNotAvailableException, TargetSetupError {
       if (shouldFlashSystem(systemBuildId, systemBuildFlavor, deviceBuild)) {
            CLog.i("Flashing system %s", deviceBuild.getDeviceBuildId());
            flashSystem(device, deviceBuild);
            return true;
       }
       CLog.i("System is already version %s and build flavor %s, skipping flashing",
               systemBuildId, systemBuildFlavor);
       // reboot
       device.rebootUntilOnline();
       return false;
    }

    /**
     * Helper method used to determine if we need to flash the system image.
     *
     * @param systemBuildId the current build id running on the device
     * @param systemBuildFlavor the current build flavor running on the device
     * @param deviceBuild the {@link IDeviceBuildInfo} that contains the system image to flash
     * @return <code>true</code> if we should flash the system, <code>false</code> otherwise.
     */
    boolean shouldFlashSystem(String systemBuildId, String systemBuildFlavor,
            IDeviceBuildInfo deviceBuild) {
        if (mForceSystemFlash) {
            // Flag overrides all logic.
            return true;
        }
        // Err on the side of caution, if we failed to get the build id or build flavor, force a
        // flash of the system.
        if (systemBuildFlavor == null || systemBuildId == null) {
            return true;
        }
        // If we have the same build id and build flavor we don't need to flash it.
        if (systemBuildId.equals(deviceBuild.getDeviceBuildId()) &&
                systemBuildFlavor.equalsIgnoreCase(deviceBuild.getBuildFlavor())) {
            return false;
        }
        return true;
    }

    /**
     * Flash the system image on device.
     *
     * @param device the {@link ITestDevice} to flash
     * @param deviceBuild the {@link IDeviceBuildInfo} to flash
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected void flashSystem(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.i("Flashing %s with update %s", device.getSerialNumber(),
                deviceBuild.getDeviceImageFile().getAbsolutePath());
        // give extra time to the update cmd
        executeLongFastbootCmd(device, "update",
                deviceBuild.getDeviceImageFile().getAbsolutePath());
    }

    /**
     * Helper method to get the current image version on device.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param imageName the name of image to get.
     * @return String the stdout output from command
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails or version could not be determined
     */
    protected String getImageVersion(ITestDevice device, String imageName)
            throws DeviceNotAvailableException, TargetSetupError {
        int attempts = 0;
        String versionQuery = String.format("version-%s", imageName);
        String patternString = String.format("%s:\\s(.*)\\s", versionQuery);
        Pattern versionOutputPattern = Pattern.compile(patternString);

        while (attempts < MAX_RETRY_ATTEMPTS) {
            String queryOutput = executeFastbootCmd(device, "getvar", versionQuery);
            Matcher matcher = versionOutputPattern.matcher(queryOutput);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                attempts++;
                CLog.w("Could not find version for '%s'. Output '%s', retrying.",
                            imageName, queryOutput);
                RunUtil.getDefault().sleep(RETRY_SLEEP * (attempts - 1)
                        + new Random(System.currentTimeMillis()).nextInt(RETRY_SLEEP));
                continue;
            }
        }
        throw new TargetSetupError(String.format(
                "Could not find version for '%s' after %d retry attempts", imageName, attempts));
    }

    /**
     * Helper method to execute fastboot command.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param cmdArgs the arguments to provide to fastboot
     * @return String the stderr output from command if non-empty. Otherwise returns the stdout
     * Some fastboot commands are weird in that they dump output to stderr on success case
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected String executeFastbootCmd(ITestDevice device, String... cmdArgs)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.v("Executing short fastboot command %s", java.util.Arrays.toString(cmdArgs));
        CommandResult result = device.executeFastbootCommand(cmdArgs);
        return handleFastbootResult(device, result, cmdArgs);
    }

    /**
     * Helper method to execute a long-running fastboot command.
     * <p/>
     * Note: Most fastboot commands normally execute within the timeout allowed by
     * {@link ITestDevice#executeFastbootCommand(String...)}. However, when multiple devices are
     * flashing devices at once, fastboot commands can take much longer than normal.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param cmdArgs the arguments to provide to fastboot
     * @return String the stderr output from command if non-empty. Otherwise returns the stdout
     * Some fastboot commands are weird in that they dump output to stderr on success case
     *
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    protected String executeLongFastbootCmd(ITestDevice device, String... cmdArgs)
            throws DeviceNotAvailableException, TargetSetupError {
        CommandResult result = device.executeLongFastbootCommand(cmdArgs);
        return handleFastbootResult(device, result, cmdArgs);
    }

    /**
     * Interpret the result of a fastboot command
     *
     * @param device
     * @param result
     * @param cmdArgs
     * @return the stderr output from command if non-empty. Otherwise returns the stdout
     * @throws TargetSetupError
     */
    private String handleFastbootResult(ITestDevice device, CommandResult result, String... cmdArgs)
            throws TargetSetupError {
        CLog.v("fastboot stdout: " + result.getStdout());
        CLog.v("fastboot stderr: " + result.getStderr());
        // TODO: consider re-trying
        if (result.getStatus() != CommandStatus.SUCCESS || result.getStderr().contains("FAILED")) {
            throw new TargetSetupError(String.format(
                    "fastboot command %s failed in device %s. stdout: %s, stderr: %s", cmdArgs[0],
                    device.getSerialNumber(), result.getStdout(), result.getStderr()));
        }
        if (result.getStderr().length() > 0) {
            return result.getStderr();
        } else {
            return result.getStdout();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideDeviceOptions(ITestDevice device) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setForceSystemFlash(boolean forceSystemFlash) {
        mForceSystemFlash = forceSystemFlash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDataWipeSkipList(Collection<String> dataWipeSkipList) {
        if (dataWipeSkipList == null) {
            dataWipeSkipList = new ArrayList<String> ();
        }
        if(dataWipeSkipList.isEmpty()) {
            // To maintain backwards compatibility.
            // TODO: deprecate and remove.
            dataWipeSkipList.add("media");
        }
        mDataWipeSkipList = dataWipeSkipList;
    }
}
