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
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.CommandResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Empty implementation of {@link ITestDevice}.
 * <p/>
 * Needed in order to handle the EasyMock andDelegateTo operation.
 */
public class StubTestDevice implements IManagedTestDevice {

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        // ignore
    }

    @Override
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    @Override
    public IDevice getIDevice() {
        // ignore
        return null;
    }

    @Override
    public String getSerialNumber() {
        // ignore
        return "stub";
    }

    @Override
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    @Override
    public boolean runInstrumentationTestsAsUser(IRemoteAndroidTestRunner runner, int userId,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    @Override
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    @Override
    public boolean runInstrumentationTestsAsUser(IRemoteAndroidTestRunner runner, int userId,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    @Override
    public boolean pullFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException {
        return false;
    }

    @Override
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
        return null;
    }

    @Override
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException {
        return null;
    }

    @Override
    public boolean pushFile(File localFile, String deviceFilePath)
            throws DeviceNotAvailableException {
        return false;
    }

    @Override
    public boolean pushString(String content, String deviceFilePath)
            throws DeviceNotAvailableException {
        return false;
    }

    @Override
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcat() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startLogcat() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopLogcat() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean fastbootEnabled) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String executeAdbCommand(String... commandArgs) throws DeviceNotAvailableException {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException {
        // ignore
        return new CommandResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeLongFastbootCommand(String... commandArgs)
            throws DeviceNotAvailableException {
        // ignore
        return new CommandResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseFastbootErase() {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseFastbootErase(boolean useFastbootErase) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult fastbootWipePartition(String partition)
            throws DeviceNotAvailableException {
        // ignore
        return new CommandResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        // ignore
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean encryptDevice(boolean inplace) throws DeviceNotAvailableException,
            UnsupportedOperationException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unencryptDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unlockDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBootSetup() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reboot() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootUntilOnline() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoBootloader() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceAvailable() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForBootComplete(long timeOut) throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline(long waitTime) throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductType() {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductVariant() {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductType() {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductVariant() {
        // ignore
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIDevice(IDevice device) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceState(TestDeviceState deviceState) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceState getDeviceState() {
        return null;
    }

    @Override
    public void clearLastConnectedWifiNetwork() {
        // ignore
    }

    @Override
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    @Override
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disconnectFromWifi() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWifiEnabled() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableNetworkMonitor() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */

    @Override
    public boolean disableNetworkMonitor() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkConnectivity() throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceNotAvailable(long waitTime) {
        // ignore
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(File packageFile, boolean reinstall, String... extraArgs)
            throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uninstallPackage(String packageName) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMountPoint(String mountName) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MountPointInfo> getMountPointInfo() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MountPointInfo getMountPointInfo(String mountpoint) throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getBugreport() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecovery(IDeviceRecovery recovery) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IFileEntry getFileEntry(String path) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            int maxTimeToOutputShellResponse, int retryAttempts)
            throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            long maxTimeToOutputShellResponse, TimeUnit timeUnit, int retryAttempts)
            throws DeviceNotAvailableException {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceInRecovery(long waitTime) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoRecovery() throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildAlias() {
        return getBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() {
        return IBuildInfo.UNKNOWN_BUILD_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIpAddress() throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String switchToAdbTcp() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchToAdbUsb() throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdbTcp() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverDevice() throws DeviceNotAvailableException {
        throw new DeviceNotAvailableException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEmulatorProcess(Process p) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process getEmulatorProcess() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBasebandVersion() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearLogcat() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecoveryMode(RecoveryMode mode) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecoveryMode getRecoveryMode() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nonBlockingReboot() throws DeviceNotAvailableException {
        // ignore

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProperty(String name) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public String getPropertySync(String name) throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOptions(TestDeviceOptions options) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushDir(File localDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdbRoot() throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceOptions getOptions() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcatDump() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcat(int maxBytes) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getApiLevel() throws DeviceNotAvailableException {
        return UNKNOWN_API_LEVEL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceShell(long waitTime) {
        return false;
    }

    @Override
    public DeviceAllocationState getAllocationState() {
        return null;
    }

    @Override
    public IDeviceStateMonitor getMonitor() {
        return null;
    }

    @Override
    public DeviceEventResponse handleAllocationEvent(DeviceEvent event) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDate(Date date) throws DeviceNotAvailableException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultiUserSupported() throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name) throws DeviceNotAvailableException {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeUser(int userId) throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startUser(int userId) throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopUser(int userId) throws DeviceNotAvailableException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getEmulatorOutput() {
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopEmulatorOutput() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
            throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remountSystemWritable() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRuntimePermissionSupported() throws DeviceNotAvailableException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getPrimaryUserId() throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildSigningKeys() throws DeviceNotAvailableException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceClass() {
        return null;
    }
}
