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

package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.TimeUtil;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Provides an reliable and slightly higher level API to a ddmlib {@link IDevice}.
 * <p/>
 * Retries device commands for a configurable amount, and provides a device recovery
 * interface for devices which are unresponsive.
 */
public interface ITestDevice {

    /**
     * Default value when API Level cannot be detected
     */
    public final static int UNKNOWN_API_LEVEL = -1;

    public enum RecoveryMode {
        /** don't attempt to recover device. */
        NONE,
        /** recover device to online state only */
        ONLINE,
        /**
         * Recover device into fully testable state - framework is up, and external storage is
         * mounted.
         */
        AVAILABLE
    }

    /**
     * A simple struct class to store information about a single mountpoint
     */
    public static class MountPointInfo {
        public String filesystem;
        public String mountpoint;
        public String type;
        public List<String> options;

        /** Simple constructor */
        public MountPointInfo() {}

        /**
         * Convenience constructor to set all members
         */
        public MountPointInfo(String filesystem, String mountpoint, String type,
                List<String> options) {
            this.filesystem = filesystem;
            this.mountpoint = mountpoint;
            this.type = type;
            this.options = options;
        }

        public MountPointInfo(String filesystem, String mountpoint, String type, String optString) {
            this(filesystem, mountpoint, type, splitMountOptions(optString));
        }

        public static List<String> splitMountOptions(String options) {
            List<String> list = Arrays.asList(options.split(","));
            return list;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s %s", this.filesystem, this.mountpoint, this.type,
                    this.options);
        }
    }

    /**
     * Set the {@link IDeviceRecovery} to use for this device. Should be set when device is first
     * allocated.
     *
     * @param recovery the {@link IDeviceRecovery}
     */
    public void setRecovery(IDeviceRecovery recovery);

    /**
     * Set the current recovery mode to use for the device.
     * <p/>
     * Used to control what recovery method to use when a device communication problem is
     * encountered. Its recommended to only use this method sparingly when needed (for example,
     * when framework is down, etc
     *
     * @param mode whether 'recover till online only' mode should be on or not.
     */
    public void setRecoveryMode(RecoveryMode mode);

    /**
     * Get the current recovery mode used for the device.
     *
     * @return the current recovery mode used for the device.
     */
    public RecoveryMode getRecoveryMode();

    /**
     * Get the device class.
     *
     * @return the {@link String} device class.
     */
    public String getDeviceClass();

    /**
     * Returns a reference to the associated ddmlib {@link IDevice}.
     * <p/>
     * A new {@link IDevice} may be allocated by DDMS each time the device disconnects and
     * reconnects from adb. Thus callers should not keep a reference to the {@link IDevice},
     * because that reference may become stale.
     *
     * @return the {@link IDevice}
     */
    public IDevice getIDevice();

    /**
     * Convenience method to get serial number of this device.
     *
     * @return the {@link String} serial number
     */
    public String getSerialNumber();

    /**
     * Convenience method to get the product type of this device.
     * <p/>
     * This method will work if device is in either adb or fastboot mode.
     *
     * @return the {@link String} product type name. Will not be null
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered, or if product type can not be determined
     */
    public String getProductType() throws DeviceNotAvailableException;

    /**
     * Convenience method to get the product variant of this device.
     * <p/>
     * This method will work if device is in either adb or fastboot mode.
     *
     * @return the {@link String} product variant name or <code>null</code> if it cannot be
     *         determined
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getProductVariant() throws DeviceNotAvailableException;

    /**
     * Convenience method to get the product type of this device when its in fastboot mode.
     * <p/>
     * This method should only be used if device should be in fastboot. Its a bit safer variant
     * than the generic {@link #getProductType()} method in this case, because ITestDevice
     * will know to recover device into fastboot if device is in incorrect state or is
     * unresponsive.
     *
     * @return the {@link String} product type name or <code>null</code> if it cannot be determined
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getFastbootProductType() throws DeviceNotAvailableException;

    /**
     * Convenience method to get the product type of this device when its in fastboot mode.
     * <p/>
     * This method should only be used if device should be in fastboot. Its a bit safer variant
     * than the generic {@link #getProductType()} method in this case, because ITestDevice
     * will know to recover device into fastboot if device is in incorrect state or is
     * unresponsive.
     *
     * @return the {@link String} product type name or <code>null</code> if it cannot be determined
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getFastbootProductVariant() throws DeviceNotAvailableException;

    /**
     * Convenience method to get the bootloader version of this device.
     * <p/>
     * Will attempt to retrieve bootloader version from the device's current state. (ie if device
     * is in fastboot mode, it will attempt to retrieve version from fastboot)
     *
     * @return the {@link String} bootloader version or <code>null</code> if it cannot be found
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getBootloaderVersion() throws DeviceNotAvailableException;


    /**
     * Convenience method to get baseband (radio) version of this device. Getting the radio version
     * is device specific, so it might not return the correct information for all devices. This
     * method relies on the gsm.version.baseband propery to return the correct version information.
     * This is not accurate for some CDMA devices and the version returned here might not match
     * the version reported from fastboot and might not return the version for the CDMA radio.
     * TL;DR this method only reports accurate version if the gsm.version.baseband property is the
     * same as the version returned by <code>fastboot getvar version-baseband</code>.
     *
     * @return the {@link String} baseband version or <code>null</code> if it cannot be determined
     *          (device has no radio or version string cannot be read)
     * @throws DeviceNotAvailableException if the connection with the device is lost and cannot
     *          be recovered.
     */
    public String getBasebandVersion() throws DeviceNotAvailableException;

    /**
     * Retrieve the alias of the build that the device is currently running.
     *
     * <p>Build alias is usually a more readable string than build id (typically a number for
     * Nexus builds). For example, final Android 4.2 release has build alias JDQ39, and build id
     * 573038
     * @return the build alias or fall back to build id if it could not be retrieved
     * @throws DeviceNotAvailableException
     */
    public String getBuildAlias() throws DeviceNotAvailableException;

    /**
     * Retrieve the build the device is currently running.
     *
     * @return the build id or {@link IBuildInfo#UNKNOWN_BUILD_ID} if it could not be retrieved
     * @throws DeviceNotAvailableException
     */
    public String getBuildId() throws DeviceNotAvailableException;

    /**
     * Retrieve the build flavor for the device.
     *
     * @return the build flavor or null if it could not be retrieved
     * @throws DeviceNotAvailableException
     */
    public String getBuildFlavor() throws DeviceNotAvailableException;

    /**
     * Retrieve the given property value from the device.
     *
     * @param name the property name
     * @return the property value or <code>null</code> if it does not exist
     * @throws DeviceNotAvailableException
     */
    public String getProperty(String name) throws DeviceNotAvailableException;

    /**
     * Retrieve the given property value from the device.
     *
     * @param name the property name
     * @return the property value or <code>null</code> if it does not exist
     * @throws DeviceNotAvailableException
     * @deprecated use {@link getProperty(String)}
     */
    @Deprecated
    public String getPropertySync(String name) throws DeviceNotAvailableException;

    /**
     * Executes the given adb shell command, retrying multiple times if command fails.
     * <p/>
     * A simpler form of {@link #executeShellCommand(String, IShellOutputReceiver, int, int)} with
     * default values.
     *
     * @param command the adb shell command to run
     * @param receiver the {@link IShellOutputReceiver} to direct shell output to.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
        throws DeviceNotAvailableException;

    /**
     * Executes a adb shell command, with more parameters to control command behavior.
     *
     * @see {@link IDevice#executeShellCommand(String, IShellOutputReceiver, int)}
     * @param command the adb shell command to run
     * @param receiver the {@link IShellOutputReceiver} to direct shell output to.
     * @param maxTimeToOutputShellResponse the maximum amount of time during which the command is
     *            allowed to not output any response; unit as specified in <code>timeUnit</code>
     * @param timeUnit unit for <code>maxTimeToOutputShellResponse</code>, see {@link TimeUtil}
     * @param retryAttempts the maximum number of times to retry command if it fails due to a
     *            exception. DeviceNotResponsiveException will be thrown if <var>retryAttempts</var>
     *            are performed without success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            long maxTimeToOutputShellResponse, TimeUnit timeUnit, int retryAttempts)
                    throws DeviceNotAvailableException;

    /**
     * Executes a adb shell command, with more parameters to control command behavior.
     *
     * @see {@link IDevice#executeShellCommand(String, IShellOutputReceiver, int)}
     * @param command the adb shell command to run
     * @param receiver the {@link IShellOutputReceiver} to direct shell output to.
     * @param maxTimeToOutputShellResponse the maximum amount of time during which the command is
     *            allowed to not output any response.
     * @param retryAttempts the maximum number of times to retry command if it fails due to a
     *            exception. DeviceNotResponsiveException will be thrown if <var>retryAttempts</var>
     *            are performed without success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     * @deprecated Use {@link #executeShellCommand(String, IShellOutputReceiver, long, TimeUnit, int)}
     */
    @Deprecated
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            int maxTimeToOutputShellResponse, int retryAttempts) throws DeviceNotAvailableException;

    /**
     * Helper method which executes a adb shell command and returns output as a {@link String}.
     *
     * @param command the adb shell command to run
     * @return the shell output
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public String executeShellCommand(String command) throws DeviceNotAvailableException;

    /**
     * Helper method which executes a adb command as a system command.
     * <p/>
     * {@link #executeShellCommand(String)} should be used instead wherever possible, as that
     * method provides better failure detection and performance.
     *
     * @param commandArgs the adb command and arguments to run
     * @return the stdout from command. <code>null</code> if command failed to execute.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public String executeAdbCommand(String... commandArgs) throws DeviceNotAvailableException;

    /**
     * Helper method which executes a fastboot command as a system command.
     * <p/>
     * Expected to be used when device is already in fastboot mode.
     *
     * @param commandArgs the fastboot command and arguments to run
     * @return the CommandResult containing output of command
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public CommandResult executeFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException;

    /**
     * Helper method which executes a long running fastboot command as a system command.
     * <p/>
     * Identical to {@link #executeFastbootCommand(String...)} except uses a longer timeout.
     *
     * @param commandArgs the fastboot command and arguments to run
     * @return the CommandResult containing output of command
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public CommandResult executeLongFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException;

    /**
     * Get whether to use fastboot erase or fastboot format to wipe a partition on the device.
     *
     * @return {@code true} if fastboot erase will be used or {@code false} if fastboot format will
     * be used.
     * @see #fastbootWipePartition(String)
     */
    public boolean getUseFastbootErase();

    /**
     * Set whether to use fastboot erase or fastboot format to wipe a partition on the device.
     *
     * @param useFastbootErase {@code true} if fastboot erase should be used or {@code false} if
     * fastboot format should be used.
     * @see #fastbootWipePartition(String)
     */
    public void setUseFastbootErase(boolean useFastbootErase);

    /**
     * Helper method which wipes a partition for the device.
     * <p/>
     * If {@link #getUseFastbootErase()} is {@code true}, then fastboot erase will be used to wipe
     * the partition. The device must then create a filesystem the next time the device boots.
     * Otherwise, fastboot format is used which will create a new filesystem on the device.
     * <p/>
     * Expected to be used when device is already in fastboot mode.
     *
     * @param partition the partition to wipe
     * @return the CommandResult containing output of command
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public CommandResult fastbootWipePartition(String partition) throws DeviceNotAvailableException;

    /**
     * Runs instrumentation tests, and provides device recovery.
     * <p/>
     * If connection with device is lost before test run completes, and recovery succeeds, all
     * listeners will be informed of testRunFailed and "false" will be returned. The test command
     * will not be rerun. It is left to callers to retry if necessary.
     * <p/>
     * If connection with device is lost before test run completes, and recovery fails, all
     * listeners will be informed of testRunFailed and DeviceNotAvailableException will be thrown.
     *
     * @param runner the {@link IRemoteAndroidTestRunner} which runs the tests
     * @param listeners the test result listeners
     * @return <code>true</code> if test command completed. <code>false</code> if it failed to
     *         complete due to device communication exception, but recovery succeeded
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered. ie test command failed to complete and recovery failed.
     */
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException;

    /**
     * Convenience method for performing
     * {@link #runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} with one or more
     * listeners passed as parameters.
     *
     * @param runner the {@link IRemoteAndroidTestRunner} which runs the tests
     * @param listeners the test result listener(s)
     * @return <code>true</code> if test command completed. <code>false</code> if it failed to
     *         complete, but recovery succeeded
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered. ie test command failed to complete and recovery failed.
     */
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            ITestRunListener... listeners) throws DeviceNotAvailableException;

    /**
     * Same as {@link ITestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * but runs the test for the given user.
     */

    public boolean runInstrumentationTestsAsUser(IRemoteAndroidTestRunner runner, int userId,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException;

    /**
     * Same as
     * {@link ITestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, ITestRunListener...)}
     * but runs the test for a given user.
     */
    public boolean runInstrumentationTestsAsUser(IRemoteAndroidTestRunner runner, int userId,
            ITestRunListener... listeners) throws DeviceNotAvailableException;

    /**
     * Install an Android package on device.
     *
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String installPackage(File packageFile, boolean reinstall, String... extraArgs)
            throws DeviceNotAvailableException;

    /**
     * Install an Android package on device.
     * <p>Note: Only use cases that requires explicit control of granting runtime permission at
     * install time should call this function.
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param grantPermissions if all runtime permissions should be granted at install time
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     * @throws UnsupportedOperationException if runtime permission is not supported by the platform
     *         on device.
     */
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException;

    /**
     * Install an Android package on device for a given user.
     *
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param userId the integer user id to install for.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException;

    /**
     * Install an Android package on device for a given user.
     * <p>Note: Only use cases that requires explicit control of granting runtime permission at
     * install time should call this function.
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param grantPermissions if all runtime permissions should be granted at install time
     * @param userId the integer user id to install for.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     * @throws UnsupportedOperationException if runtime permission is not supported by the platform
     *         on device.
     */
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
                    throws DeviceNotAvailableException;

    /**
     * Uninstall an Android package from device.
     *
     * @param packageName the Android package to uninstall
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String uninstallPackage(String packageName) throws DeviceNotAvailableException;

    /**
     * Returns a mount point.
     * <p/>
     * Queries the device directly if the cached info in {@link IDevice} is not available.
     * <p/>
     * TODO: move this behavior to {@link IDevice#getMountPoint(String)}
     *
     * @param mountName the name of the mount point
     * @return the mount point or <code>null</code>
     * @see {@link IDevice#getMountPoint(String)}
     */
    public String getMountPoint(String mountName);

    /**
     * Returns a parsed version of the information in /proc/mounts on the device
     *
     * @return A {@link List} of {@link MountPointInfo} containing the information in "/proc/mounts"
     */
    public List<MountPointInfo> getMountPointInfo() throws DeviceNotAvailableException;

    /**
     * Returns a {@link MountPointInfo} corresponding to the specified mountpoint path, or
     * <code>null</code> if that path has nothing mounted or otherwise does not appear in
     * /proc/mounts as a mountpoint.
     *
     * @return A {@link List} of {@link MountPointInfo} containing the information in "/proc/mounts"
     * @see {@link getMountPointInfo()}
     */
    public MountPointInfo getMountPointInfo(String mountpoint) throws DeviceNotAvailableException;

    /**
     * Retrieves a bugreport from the device.
     * <p/>
     * The implementation of this is guaranteed to continue to work on a device without an sdcard
     * (or where the sdcard is not yet mounted).
     *
     * @return An {@link InputStreamSource} which will produce the bugreport contents on demand.  In
     *         case of failure, the {@code InputStreamSource} will produce an empty
     *         {@link InputStream}.
     */
    public InputStreamSource getBugreport();

    /**
     * Retrieves a file off device.
     *
     * @param remoteFilePath the absolute path to file on device.
     * @param localFile the local file to store contents in. If non-empty, contents will be
     *            replaced.
     * @return <code>true</code> if file was retrieved successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean pullFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException;

    /**
     * Retrieves a file off device, stores it in a local temporary {@link File}, and returns that
     * {@code File}.
     *
     * @param remoteFilePath the absolute path to file on device.
     * @return A {@link File} containing the contents of the device file, or {@code null} if the
     *         copy failed for any reason (including problems with the host filesystem)
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException;

    /**
     * A convenience method to retrieve a file from the device's external storage, stores it in a
     * local temporary {@link File}, and return a reference to that {@code File}.
     *
     * @param remoteFilePath the path to file on device, relative to the device's external storage
     *        mountpoint
     * @return A {@link File} containing the contents of the device file, or {@code null} if the
     *         copy failed for any reason (including problems with the host filesystem)
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException;

    /**
     * Push a file to device
     *
     * @param localFile the local file to push
     * @param deviceFilePath the remote destination absolute file path
     * @return <code>true</code> if file was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushFile(File localFile, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Push file created from a string to device
     *
     * @param contents the contents of the file to push
     * @param deviceFilePath the remote destination absolute file path
     * @return <code>true</code> if string was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushString(String contents, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Recursively push directory contents to device.
     *
     * @param localDir the local directory to push
     * @param deviceFilePath the absolute file path of the remote destination
     * @return <code>true</code> if file was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushDir(File localDir, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Incrementally syncs the contents of a local file directory to device.
     * <p/>
     * Decides which files to push by comparing timestamps of local files with their remote
     * equivalents. Only 'newer' or non-existent files will be pushed to device. Thus overhead
     * should be relatively small if file set on device is already up to date.
     * <p/>
     * Hidden files (with names starting with ".") will be ignored.
     * <p/>
     * Example usage: syncFiles("/tmp/files", "/sdcard") will created a /sdcard/files directory if
     * it doesn't already exist, and recursively push the /tmp/files contents to /sdcard/files.
     *
     * @param localFileDir the local file directory containing files to recursively push.
     * @param deviceFilePath the remote destination absolute file path root. All directories in thos
     *            file path must be readable. ie pushing to /data/local/tmp when adb is not root
     *            will fail
     * @return <code>true</code> if files were synced successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Helper method to determine if file on device exists.
     *
     * @param deviceFilePath the absolute path of file on device to check
     * @return <code>true</code> if file exists, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException;

    /**
     * Helper method to determine amount of free space on device external storage.
     *
     * @return the amount of free space in KB
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException;

    /**
     * Retrieve a reference to a remote file on device.
     *
     * @param path the file path to retrieve. Can be an absolute path or path relative to '/'. (ie
     *            both "/system" and "system" syntax is supported)
     * @return the {@link IFileEntry} or <code>null</code> if file at given <var>path</var> cannot
     *         be found
     * @throws DeviceNotAvailableException
     */
    public IFileEntry getFileEntry(String path) throws DeviceNotAvailableException;

    /**
     * Start capturing logcat output from device in the background.
     * <p/>
     * Will have no effect if logcat output is already being captured.
     * Data can be later retrieved via getLogcat.
     * <p/>
     * When the device is no longer in use, {@link #stopLogcat()} must be called.
     * <p/>
     * {@link #startLogcat()} and {@link #stopLogcat()} do not normally need to be called when
     * within a TF invocation context, as the TF framework will start and stop logcat.
     */
    public void startLogcat();

    /**
     * Stop capturing logcat output from device, and discard currently saved logcat data.
     * <p/>
     * Will have no effect if logcat output is not being captured.
     */
    public void stopLogcat();

    /**
     * Deletes any accumulated logcat data.
     * <p/>
     * This is useful for cases when you want to ensure {@link ITestDevice#getLogcat()} only returns
     * log data produced after a certain point (such as after flashing a new device build, etc).
     */
    public void clearLogcat();

    /**
     * Grabs a snapshot stream of the logcat data.
     * <p/>
     * Works in two modes:
     * <li>If the logcat is currently being captured in the background, will return up to
     * {@link TestDeviceOptions#getMaxLogcatDataSize()} bytes of the current
     * contents of the background logcat capture
     * <li>Otherwise, will return a static dump of the logcat data if device is currently responding
     */
    public InputStreamSource getLogcat();

    /**
     * Grabs a snapshot stream of the last <code>maxBytes</code> of captured logcat data.
     * <p/>
     * Useful for cases when you want to capture frequent snapshots of the captured logcat data
     * without incurring the potentially big disk space penalty of getting the entire
     * {@link #getLogcat()} snapshot.
     *
     * @param maxBytes the maximum amount of data to return. Should be an amount that can
     *            comfortably fit in memory
     */
    public InputStreamSource getLogcat(int maxBytes);

    /**
    * Get a dump of the current logcat for device. Unlike {@link #getLogcat()}, this method will
    * always return a static dump of the logcat.
    * <p/>
    * Has the disadvantage that nothing will be returned if device is not reachable.
    *
    * @return a {@link InputStreamSource} of the logcat data. An empty stream is returned if fail to
    *         capture logcat data.
    */
    public InputStreamSource getLogcatDump();

    /**
     * Grabs a screenshot from the device.
     *
     * @return a {@link InputStreamSource} of the screenshot in png format, or <code>null</code> if
     *         the screenshot was not successful.
     * @throws DeviceNotAvailableException
     */
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException;

    /**
     * Grabs a screenshot from the device.
     * Recommended to use getScreenshot(format) instead with JPEG encoding for smaller size
     * @param format supported PNG, JPEG
     * @return a {@link InputStreamSource} of the screenshot in format, or <code>null</code> if
     *         the screenshot was not successful.
     * @throws DeviceNotAvailableException
     */
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException;

    /**
     * Clears the last connected wifi network. This should be called when starting a new invocation
     * to avoid connecting to the wifi network used in the previous test after device reboots.
     */
    public void clearLastConnectedWifiNetwork();

    /**
     * Connects to a wifi network.
     * <p/>
     * Turns on wifi and blocks until a successful connection is made to the specified wifi network.
     * Once a connection is made, the instance will try to restore the connection after every reboot
     * until {@link ITestDevice#disconnectFromWifi()} or
     * {@link ITestDevice#clearLastConnectedWifiNetwork()} is called.
     *
     * @param wifiSsid the wifi ssid to connect to
     * @param wifiPsk PSK passphrase or null if unencrypted
     * @return <code>true</code> if connected to wifi network successfully. <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException;

    /**
     * A variant of {@link #connectToWifiNetwork(String, String)} that only connects if device
     * currently does not have network connectivity.
     *
     * @param wifiSsid
     * @param wifiPsk
     * @return <code>true</code> if connected to wifi network successfully. <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException
     */
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException;

    /**
     * Disconnects from a wifi network.
     * <p/>
     * Removes all networks from known networks list and disables wifi.
     *
     * @return <code>true</code> if disconnected from wifi network successfully. <code>false</code>
     *         if disconnect failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean disconnectFromWifi() throws DeviceNotAvailableException;

    /**
     * Test if wifi is enabled.
     * <p/>
     * Checks if wifi is enabled on device. Useful for asserting wifi status before tests that
     * shouldn't run with wifi, e.g. mobile data tests.
     *
     * @return <code>true</code> if wifi is enabled. <code>false</code> if disabled
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean isWifiEnabled() throws DeviceNotAvailableException;

    /**
     * Gets the device's IP address.
     *
     * @return the device's IP address, or <code>null</code> if device has no IP address
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getIpAddress() throws DeviceNotAvailableException;

    /**
     * Enables network monitoring on device.
     *
     * @return <code>true</code> if monitoring is enabled successfully. <code>false</code>
     *         if it failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean enableNetworkMonitor() throws DeviceNotAvailableException;

    /**
     * Disables network monitoring on device.
     *
     * @return <code>true</code> if monitoring is disabled successfully. <code>false</code>
     *         if it failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean disableNetworkMonitor() throws DeviceNotAvailableException;

    /**
     * Check that device has network connectivity.
     *
     * @return <code>true</code> if device has a working network connection,
     *          <code>false</code> overwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *          recovered.
     */
    public boolean checkConnectivity() throws DeviceNotAvailableException;

    /**
     * Attempt to dismiss any error dialogs currently displayed on device UI.
     *
     * @return <code>true</code> if no dialogs were present or dialogs were successfully cleared.
     *         <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean clearErrorDialogs() throws DeviceNotAvailableException;

    /**
     * Reboots the device into bootloader mode.
     * <p/>
     * Blocks until device is in bootloader mode.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void rebootIntoBootloader() throws DeviceNotAvailableException;

    /**
     * Reboots the device into adb mode.
     * <p/>
     * Blocks until device becomes available.
     *
     * @throws DeviceNotAvailableException if device is not available after reboot
     */
    public void reboot() throws DeviceNotAvailableException;

    /**
     * Reboots the device into adb recovery mode.
     * <p/>
     * Blocks until device enters recovery
     *
     * @throws DeviceNotAvailableException if device is not available after reboot
     */
    public void rebootIntoRecovery() throws DeviceNotAvailableException;

    /**
     * An alternate to {@link #reboot()} that only blocks until device is online ie visible to adb.
     *
     * @throws DeviceNotAvailableException if device is not available after reboot
     */
    public void rebootUntilOnline() throws DeviceNotAvailableException;

    /**
     * Issues a command to reboot device and returns on command complete and when device is no
     * longer visible to adb.
     *
     * @throws DeviceNotAvailableException
     */
    public void nonBlockingReboot() throws DeviceNotAvailableException;

    /**
     * Turns on adb root. If the "enable-root" setting is "false", will log a message and
     * return without enabling root.
     * <p/>
     * Enabling adb root may cause device to disconnect from adb. This method will block until
     * device is available.
     *
     * @return <code>true</code> if successful.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean enableAdbRoot() throws DeviceNotAvailableException;

    /**
     * Get the device's state.
     */
    public TestDeviceState getDeviceState();

    /**
     * Encrypts the device.
     * <p/>
     * Encrypting the device may be done inplace or with a wipe.  Inplace encryption will not wipe
     * any data on the device but normally takes a couple orders of magnitude longer than the wipe.
     * <p/>
     * This method will reboot the device if it is not already encrypted and will block until device
     * is online.  Also, it will not decrypt the device after the reboot.  Therefore, the device
     * might not be fully booted and/or ready to be tested when this method returns.
     *
     * @param inplace if the encryption process should take inplace and the device should not be
     * wiped.
     * @return <code>true</code> if successful.
     * @throws DeviceNotAvailableException if device is not available after reboot.
     * @throws UnsupportedOperationException if encryption is not supported on the device.
     */
    public boolean encryptDevice(boolean inplace) throws DeviceNotAvailableException,
            UnsupportedOperationException;

    /**
     * Unencrypts the device.
     * <p/>
     * Unencrypting the device may cause device to be wiped and may reboot device. This method will
     * block until device is available and ready for testing.  Requires fastboot inorder to wipe the
     * userdata partition.
     *
     * @return <code>true</code> if successful.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     * @throws UnsupportedOperationException if encryption is not supported on the device.
     */
    public boolean unencryptDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException;

    /**
     * Unlocks the device if the device is in an encrypted state.
     * </p>
     * This method may restart the framework but will not call {@link #postBootSetup()}. Therefore,
     * the device might not be fully ready to be tested when this method returns.
     *
     * @return <code>true</code> if successful or if the device is unencrypted.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     * @throws UnsupportedOperationException if encryption is not supported on the device.
     */
    public boolean unlockDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException;

    /**
     * Returns if the device is encrypted.
     *
     * @return <code>true</code> if the device is encrypted.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean isDeviceEncrypted() throws DeviceNotAvailableException;

    /**
     * Returns if encryption is supported on the device.
     *
     * @return <code>true</code> if the device supports encryption.
     * @throws DeviceNotAvailableException
     */
    public boolean isEncryptionSupported() throws DeviceNotAvailableException;

    /**
     * Waits for the device to be responsive and available for testing.
     *
     * @param waitTime the time in ms to wait
     * @throws DeviceNotAvailableException if device is still unresponsive after waitTime expires.
     */
    public void waitForDeviceAvailable(final long waitTime) throws DeviceNotAvailableException;

    /**
     * Waits for the device to be responsive and available for testing.  Uses default timeout.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void waitForDeviceAvailable() throws DeviceNotAvailableException;

    /**
     * Blocks until device is visible via adb.
     * <p/>
     * Note the device may not necessarily be responsive to commands on completion. Use
     * {@link #waitForDeviceAvailable()} instead.
     *
     * @param waitTime the time in ms to wait
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void waitForDeviceOnline(final long waitTime) throws DeviceNotAvailableException;

    /**
     * Blocks until device is visible via adb.  Uses default timeout
     * <p/>
     * Note the device may not necessarily be responsive to commands on completion. Use
     * {@link #waitForDeviceAvailable()} instead.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void waitForDeviceOnline() throws DeviceNotAvailableException;

    /**
     * Blocks for the device to be not available ie missing from adb
     *
     * @param waitTime the time in ms to wait
     * @return <code>true</code> if device becomes not available before time expires.
     *         <code>false</code> otherwise
     */
    public boolean waitForDeviceNotAvailable(final long waitTime);

    /**
     * Waits for device to be responsive to a basic adb shell command.
     *
     * @param waitTime the time in ms to wait
     * @return <code>true</code> if device becomes responsive before <var>waitTime</var> elapses.
     */
    public boolean waitForDeviceShell(final long waitTime);

    /**
     * Blocks for the device to be in the 'adb recovery' state (note this is distinct from
     * {@link IDeviceRecovery}).
     *
     * @param waitTime the time in ms to wait
     * @return <code>true</code> if device boots into recovery before time expires.
     *         <code>false</code> otherwise
     */
    public boolean waitForDeviceInRecovery(final long waitTime);

    /**
     * Perform instructions to configure device for testing that after every boot.
     * <p/>
     * Should be called after device is fully booted/available
     * <p/>
     * In normal circumstances this method doesn't need to be called explicitly, as
     * implementations should perform these steps automatically when performing a reboot.
     * <p/>
     * Where it may need to be called is when device reboots due to other events (eg when a
     * fastboot update command has completed)
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public void postBootSetup() throws DeviceNotAvailableException;

    /**
     * @return <code>true</code> if device is connected to adb-over-tcp, <code>false</code>
     * otherwise.
     */
    public boolean isAdbTcp();


    /**
     * @return <code>true</code> if device currently has adb root, <code>false</code> otherwise.
     *
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean isAdbRoot() throws DeviceNotAvailableException;

    /**
     * Switch device to adb-over-tcp mode.
     *
     * @return the tcp serial number or <code>null</code> if device could not be switched
     * @throws DeviceNotAvailableException
     */
    public String switchToAdbTcp() throws DeviceNotAvailableException;

    /**
     * Switch device to adb over usb mode.
     *
     * @return <code>true</code> if switch was successful, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean switchToAdbUsb() throws DeviceNotAvailableException;

    /**
     * Set the {@link TestDeviceOptions} for the device
     */
    public void setOptions(TestDeviceOptions options);

    /**
     * Fetch the test options for the device.
     *
     * @return {@link TestDeviceOptions} related to the device under test.
     */
    public TestDeviceOptions getOptions();

    /**
     * Fetch the application package names present on the device.
     *
     * @return {@link Set} of {@link String} package names currently installed on the device.
     * @throws DeviceNotAvailableException
     */
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException;

    /**
     * Fetch the application package names that can be uninstalled. This is presently defined as
     * non-system packages, and updated system packages.
     *
     * @return {@link Set} of uninstallable {@link String} package names currently installed on the
     *         device.
     * @throws DeviceNotAvailableException
     */
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException;

    /**
     * Fetch information about a package installed on device.
     *
     * @return the {@link PackageInfo} or <code>null</code> if information could not be retrieved
     * @throws DeviceNotAvailableException
     */
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException;

    /**
     * Get the device API Level. Defaults to {@link #UNKNOWN_API_LEVEL}.
     *
     * @return an integer indicating the API Level of device
     * @throws DeviceNotAvailableException
     */
    public int getApiLevel() throws DeviceNotAvailableException;

    /**
     * Sets the date on device
     * <p>
     * Note: setting date on device requires root
     * @param date specify a particular date; will use host date if <code>null</code>
     * @throws DeviceNotAvailableException
     */
    public void setDate(Date date) throws DeviceNotAvailableException;

    /**
     * Blocks until the device's boot complete flag is set.
     *
     * @param timeOut time in msecs to wait for the flag to be set
     * @return true if device's boot complete flag is set within the timeout
     * @throws DeviceNotAvailableException
     */
    public boolean waitForBootComplete(long timeOut) throws DeviceNotAvailableException;

    /**
     * Determines if multi user is supported.
     *
     * @return true if multi user is supported, false otherwise
     * @throws DeviceNotAvailableException
     */
    public boolean isMultiUserSupported() throws DeviceNotAvailableException;

    /**
     * Create a user with a given name.
     *
     * @param name of the user to create on the device
     * @return the integer for the user id created
     * @throws DeviceNotAvailableException
     */
    public int createUser(String name) throws DeviceNotAvailableException, IllegalStateException;

    /**
     * Remove a given user from the device.
     *
     * @param userId of the user to remove
     * @return true if we were succesful in removing the user, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean removeUser(int userId) throws DeviceNotAvailableException;

    /**
     * Gets the list of users on the device. Defaults to null.
     *
     * @return the list of user ids or null if there was an error.
     * @throws DeviceNotAvailableException
     */
    ArrayList<Integer> listUsers() throws DeviceNotAvailableException;

    /**
     * Get the maximum number of supported users. Defaults to 0.
     *
     * @return an integer indicating the number of supported users
     * @throws DeviceNotAvailableException
     */
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException;

    /**
     * Starts a given user in the background if it is currently stopped. If the user is already
     * running in the background, this method is a NOOP.
     * @param userId of the user to start in the background
     * @return true if the user was succesfully started in the background.
     * @throws DeviceNotAvailableException
     */
    public boolean startUser(int userId) throws DeviceNotAvailableException;

    /**
     * Stops a given user. If the user is already stopped, this method is a NOOP.
     * @param userId of the user to stop.
     * @throws DeviceNotAvailableException
     */
    public void stopUser(int userId) throws DeviceNotAvailableException;

    /**
     * Get the stream of emulator stdout and stderr
     * @return emulator output
     */
    public InputStreamSource getEmulatorOutput();

    /**
     * Close and delete the emulator output.
     */
    public void stopEmulatorOutput();

    /**
     * Make the system partition on the device writable. May reboot the device.
     * @throws DeviceNotAvailableException
     */
    public void remountSystemWritable() throws DeviceNotAvailableException;

    /**
     * Check whether platform on device supports runtime permission granting
     * @return
     * @throws DeviceNotAvailableException
     */
    public boolean isRuntimePermissionSupported() throws DeviceNotAvailableException;

    /**
     * Returns the primary user id.
     * @return the userId of the primary user if there is one, and null if there is no primary user.
     * @throws DeviceNotAvailableException
     */
    public Integer getPrimaryUserId() throws DeviceNotAvailableException;

    /**
     * Returns the key type used to sign the device image
     * <p>
     * Typically Android devices may be signed with test-keys (like in AOSP) or release-keys
     * (controlled by individual device manufacturers)
     * @return
     * @throws DeviceNotAvailableException
     */
    public String getBuildSigningKeys() throws DeviceNotAvailableException;
}
