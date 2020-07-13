/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncException.SyncError;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.result.StubTestRunListener;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SizeLimitedOutputStream;
import com.android.tradefed.util.StreamUtil;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.GuardedBy;
import javax.imageio.ImageIO;

/**
 * Default implementation of a {@link ITestDevice}
 * Non-full stack android devices.
 */
public class AndroidNativeDevice implements IManagedTestDevice {

    /** the default number of command retry attempts to perform */
    static final int MAX_RETRY_ATTEMPTS = 2;
    private static final String BUGREPORT_CMD = "bugreport";
    /** command to test input dispatch readiness **/
    private static final String TEST_INPUT_CMD = "dumpsys input";
    static final String LIST_PACKAGES_CMD = "pm list packages -f";
    private static final Pattern PACKAGE_REGEX = Pattern.compile("package:(.*)=(.*)");
    /** regex to match input dispatch readiness line **/
    static final Pattern INPUT_DISPATCH_STATE_REGEX =
            Pattern.compile("DispatchEnabled:\\s?([01])");
    /** regex to match build signing key type */
    private static final Pattern KEYS_PATTERN = Pattern.compile("^.*-keys$");
    private static final Pattern DF_PATTERN = Pattern.compile(
            //Fs 1K-blks Used    Available Use%      Mounted on
            "^[/a-z]+\\s+\\d+\\s+\\d+\\s+(\\d+)\\s+\\d+%\\s+[/a-z]+$", Pattern.MULTILINE);

    /**
     * Allow pauses of up to 2 minutes while receiving bugreport.  Note that dumpsys may pause up to
     * a minute while waiting for unresponsive components, but should bail after that minute, if it
     *  will ever terminate on its own.
     */
    private static final int BUGREPORT_TIMEOUT = 2 * 60 * 1000;

    private static final long MAX_HOST_DEVICE_TIME_OFFSET = 5 * 1000;

    /** The password for encrypting and decrypting the device. */
    private static final String ENCRYPTION_PASSWORD = "android";
    /** Encrypting with inplace can take up to 2 hours. */
    private static final int ENCRYPTION_INPLACE_TIMEOUT_MIN = 2 * 60;
    /** Encrypting with wipe can take up to 20 minutes. */
    private static final long ENCRYPTION_WIPE_TIMEOUT_MIN = 20;
    /** Timeout to wait for input dispatch to become ready **/
    private static final long INPUT_DISPATCH_READY_TIMEOUT = 5 * 1000;
    /** Beginning of the string returned by vdc for "vdc cryptfs enablecrypto". */
    private static final String ENCRYPTION_SUPPORTED_CODE = "500";
    /** Message in the string returned by vdc for "vdc cryptfs enablecrypto". */
    private static final String ENCRYPTION_SUPPORTED_USAGE = "Usage: ";

    /** The time in ms to wait before starting logcat for a device */
    private int mLogStartDelay = 5*1000;

    /** The time in ms to wait for a device to become unavailable. Should usually be short */
    private static final int DEFAULT_UNAVAILABLE_TIMEOUT = 20 * 1000;
    /** The time in ms to wait for a recovery that we skip because of the NONE mode */
    static final int NONE_RECOVERY_MODE_DELAY = 1000;
    /** number of attempts made to clear dialogs */
    private static final int NUM_CLEAR_ATTEMPTS = 5;
    /** the command used to dismiss a error dialog. Currently sends a DPAD_CENTER key event */
    static final String DISMISS_DIALOG_CMD = "input keyevent 23";

    static final String BUILD_ID_PROP = "ro.build.version.incremental";
    private static final String PRODUCT_NAME_PROP = "ro.product.name";
    private static final String BUILD_TYPE_PROP = "ro.build.type";
    private static final String BUILD_ALIAS_PROP = "ro.build.id";
    private static final String BUILD_FLAVOR = "ro.build.flavor";
    static final String BUILD_CODENAME_PROP = "ro.build.version.codename";
    static final String BUILD_TAGS = "ro.build.tags";


    /** The network monitoring interval in ms. */
    private static final int NETWORK_MONITOR_INTERVAL = 10 * 1000;

    /** Wifi reconnect check interval in ms. */
    private static final int WIFI_RECONNECT_CHECK_INTERVAL = 1 * 1000;

    /** Wifi reconnect timeout in ms. */
    private static final int WIFI_RECONNECT_TIMEOUT = 60 * 1000;

    /** The time in ms to wait for a command to complete. */
    private int mCmdTimeout = 2 * 60 * 1000;
    /** The time in ms to wait for a 'long' command to complete. */
    private long mLongCmdTimeout = 25 * 60 * 1000;

    private static final int FLAG_PRIMARY = 1; // From the UserInfo class

    private IDevice mIDevice;
    private IDeviceRecovery mRecovery = new WaitDeviceRecovery();
    private final IDeviceStateMonitor mStateMonitor;
    private TestDeviceState mState = TestDeviceState.ONLINE;
    private final ReentrantLock mFastbootLock = new ReentrantLock();
    private LogcatReceiver mLogcatReceiver;
    private boolean mFastbootEnabled = true;

    private TestDeviceOptions mOptions = new TestDeviceOptions();
    private Process mEmulatorProcess;
    private SizeLimitedOutputStream mEmulatorOutput;

    private RecoveryMode mRecoveryMode = RecoveryMode.AVAILABLE;

    private Boolean mIsEncryptionSupported = null;
    private ReentrantLock mAllocationStateLock = new ReentrantLock();
    @GuardedBy("mAllocationStateLock")
    private DeviceAllocationState mAllocationState = DeviceAllocationState.Unknown;
    private IDeviceMonitor mAllocationMonitor = null;

    private String mLastConnectedWifiSsid = null;
    private String mLastConnectedWifiPsk = null;
    private boolean mNetworkMonitorEnabled = false;

    /**
     * Interface for a generic device communication attempt.
     */
    private abstract interface DeviceAction {

        /**
         * Execute the device operation.
         *
         * @return <code>true</code> if operation is performed successfully, <code>false</code>
         *         otherwise
         * @throws Exception if operation terminated abnormally
         */
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException;
    }

    /**
     * A {@link DeviceAction} for running a OS 'adb ....' command.
     */
    private class AdbAction implements DeviceAction {
        /** the output from the command */
        String mOutput = null;
        private String[] mCmd;

        AdbAction(String[] cmd) {
            mCmd = cmd;
        }

        @Override
        public boolean run() throws TimeoutException, IOException {
            CommandResult result = getRunUtil().runTimedCmd(getCommandTimeout(), mCmd);
            // TODO: how to determine device not present with command failing for other reasons
            if (result.getStatus() == CommandStatus.EXCEPTION) {
                throw new IOException();
            } else if (result.getStatus() == CommandStatus.TIMED_OUT) {
                throw new TimeoutException();
            } else if (result.getStatus() == CommandStatus.FAILED) {
                // interpret as communication failure
                throw new IOException();
            }
            mOutput = result.getStdout();
            return true;
        }
    }

    /**
     * Creates a {@link TestDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     *            Can be null
     */
    public AndroidNativeDevice(IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        throwIfNull(device);
        throwIfNull(stateMonitor);
        mIDevice = device;
        mStateMonitor = stateMonitor;
        mAllocationMonitor = allocationMonitor;
    }

    /**
     * Get the {@link RunUtil} instance to use.
     * <p/>
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOptions(TestDeviceOptions options) {
        throwIfNull(options);
        mOptions = options;
        mStateMonitor.setDefaultOnlineTimeout(options.getOnlineTimeout());
        mStateMonitor.setDefaultAvailableTimeout(options.getAvailableTimeout());
    }

    /**
     * Sets the max size of a tmp logcat file.
     *
     * @param size max byte size of tmp file
     */
    void setTmpLogcatSize(long size) {
        mOptions.setMaxLogcatDataSize(size);
    }

    /**
     * Sets the time in ms to wait before starting logcat capture for a online device.
     *
     * @param delay the delay in ms
     */
    void setLogStartDelay(int delay) {
        mLogStartDelay = delay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice getIDevice() {
        synchronized (mIDevice) {
            return mIDevice;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIDevice(IDevice newDevice) {
        throwIfNull(newDevice);
        IDevice currentDevice = mIDevice;
        if (!getIDevice().equals(newDevice)) {
            synchronized (currentDevice) {
                mIDevice = newDevice;
            }
            mStateMonitor.setIDevice(mIDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }

    private boolean nullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Fetch a device property, from the ddmlib cache by default, and falling back to either
     * `adb shell getprop` or `fastboot getvar` depending on whether the device is in Fastboot or
     * not.
     *
     * @param propName The name of the device property as returned by `adb shell getprop`
     * @param fastbootVar The name of the equivalent fastboot variable to query. if {@code null},
     * fastboot query will not be attempted
     * @param description A simple description of the variable.  First letter should be capitalized.
     * @return A string, possibly {@code null} or empty, containing the value of the given property
     */
    private String internalGetProperty(String propName, String fastbootVar, String description)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        String propValue = getIDevice().getProperty(propName);
        if (propValue != null) {
            return propValue;
        } else if (TestDeviceState.FASTBOOT.equals(getDeviceState()) &&
                fastbootVar != null) {
            CLog.i("%s for device %s is null, re-querying in fastboot", description,
                    getSerialNumber());
            return getFastbootVariable(fastbootVar);
        } else {
            CLog.d("property collection for device %s is null, re-querying for prop %s",
                    getSerialNumber(), description);
            return getProperty(propName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProperty(final String name) throws DeviceNotAvailableException {
        final String[] result = new String[1];
        DeviceAction propAction = new DeviceAction() {

            @Override
            public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException, InstallException, SyncException {
                try {
                    result[0] = getIDevice().getSystemProperty(name).get();
                } catch (InterruptedException | ExecutionException e) {
                    // getProperty will stash the original exception inside
                    // ExecutionException.getCause
                    // throw the specific original exception if available in case TF ever does
                    // specific handling for different exceptions
                    if (e.getCause() instanceof IOException) {
                        throw (IOException)e.getCause();
                    } else if (e.getCause() instanceof TimeoutException) {
                        throw (TimeoutException)e.getCause();
                    } else if (e.getCause() instanceof AdbCommandRejectedException) {
                        throw (AdbCommandRejectedException)e.getCause();
                    } else if (e.getCause() instanceof ShellCommandUnresponsiveException) {
                        throw (ShellCommandUnresponsiveException)e.getCause();
                    }
                    else {
                        throw new IOException(e);
                    }
                }
                return true;
            }

        };
        performDeviceAction("getprop", propAction, MAX_RETRY_ATTEMPTS);
        return result[0];
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public String getPropertySync(final String name) throws DeviceNotAvailableException {
        return getProperty(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() throws UnsupportedOperationException,
            DeviceNotAvailableException {
        return internalGetProperty("ro.bootloader", "version-bootloader", "Bootloader");
    }

    @Override
    public String getBasebandVersion() throws DeviceNotAvailableException {
        return internalGetProperty("gsm.version.baseband", "version-baseband", "Baseband");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductType() throws DeviceNotAvailableException {
        return internalGetProductType(MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@see getProductType()}
     *
     * @param retryAttempts The number of times to try calling {@see recoverDevice()} if the
     *        device's product type cannot be found.
     */
    private String internalGetProductType(int retryAttempts) throws DeviceNotAvailableException {
        String productType = internalGetProperty("ro.hardware", "product", "Product type");

        // Things will likely break if we don't have a valid product type.  Try recovery (in case
        // the device is only partially booted for some reason), and if that doesn't help, bail.
        if (nullOrEmpty(productType)) {
            if (retryAttempts > 0) {
                recoverDevice();
                productType = internalGetProductType(retryAttempts - 1);
            }

            if (nullOrEmpty(productType)) {
                throw new DeviceNotAvailableException(String.format(
                        "Could not determine product type for device %s.", getSerialNumber()));
            }
        }

        return productType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductType()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return getFastbootVariable("product");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductVariant() throws DeviceNotAvailableException {
        return internalGetProperty("ro.product.device", "variant", "Product variant");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductVariant()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return getFastbootVariable("variant");
    }

    private String getFastbootVariable(String variableName)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        CommandResult result = executeFastbootCommand("getvar", variableName);
        if (result.getStatus() == CommandStatus.SUCCESS) {
            Pattern fastbootProductPattern = Pattern.compile(variableName + ":\\s(.*)\\s");
            // fastboot is weird, and may dump the output on stderr instead of stdout
            String resultText = result.getStdout();
            if (resultText == null || resultText.length() < 1) {
                resultText = result.getStderr();
            }
            Matcher matcher = fastbootProductPattern.matcher(resultText);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildAlias() throws DeviceNotAvailableException {
        String alias = getProperty(BUILD_ALIAS_PROP);
        if (alias == null || alias.isEmpty()) {
            return getBuildId();
        }
        return alias;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() throws DeviceNotAvailableException {
        String bid = getProperty(BUILD_ID_PROP);
        if (bid == null) {
            CLog.w("Could not get device %s build id.", getSerialNumber());
            return IBuildInfo.UNKNOWN_BUILD_ID;
        }
        return bid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() throws DeviceNotAvailableException {
        String buildFlavor = getProperty(BUILD_FLAVOR);
        if (buildFlavor != null && !buildFlavor.isEmpty()) {
            return buildFlavor;
        }
        String productName = getProperty(PRODUCT_NAME_PROP);
        String buildType = getProperty(BUILD_TYPE_PROP);
        if (productName == null || buildType == null) {
            CLog.w("Could not get device %s build flavor.", getSerialNumber());
            return null;
        }
        return String.format("%s-%s", productName, buildType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver,
                        mCmdTimeout, TimeUnit.MILLISECONDS);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver,
            final int maxTimeToOutputShellResponse, int retryAttempts)
            throws DeviceNotAvailableException {
        executeShellCommand(command, receiver,
                maxTimeToOutputShellResponse, TimeUnit.MILLISECONDS, retryAttempts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver,
            final long maxTimeToOutputShellResponse, final TimeUnit timeUnit,
            final int retryAttempts) throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver,
                        maxTimeToOutputShellResponse, timeUnit);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, retryAttempts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(command, receiver);
        String output = receiver.getOutput();
        CLog.v("%s on %s returned %s", command, getSerialNumber(), output);
        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runInstrumentationTests(final IRemoteAndroidTestRunner runner,
            final Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
        RunFailureListener failureListener = new RunFailureListener();
        listeners.add(failureListener);
        DeviceAction runTestsAction = new DeviceAction() {
            @Override
            public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException, InstallException, SyncException {
                runner.run(listeners);
                return true;
            }

        };
        boolean result = performDeviceAction(String.format("run %s instrumentation tests",
                runner.getPackageName()), runTestsAction, 0);
        if (failureListener.isRunFailure()) {
            // run failed, might be system crash. Ensure device is up
            if (mStateMonitor.waitForDeviceAvailable(5 * 1000) == null) {
                // device isn't up, recover
                recoverDevice();
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runInstrumentationTestsAsUser(final IRemoteAndroidTestRunner runner,
            int userId, final Collection<ITestRunListener> listeners)
                    throws DeviceNotAvailableException {
        String oldRunTimeOptions = appendUserRunTimeOptionToRunner(runner, userId);
        boolean result = runInstrumentationTests(runner, listeners);
        resetUserRunTimeOptionToRunner(runner, oldRunTimeOptions);
        return result;
    }

    /**
     * Helper method to add user run time option to {@link RemoteAndroidTestRunner}
     *
     * @param runner {@link IRemoteAndroidTestRunner}
     * @param userId the integer of the user id to run as.
     * @return original run time options.
     */
    private String appendUserRunTimeOptionToRunner(final IRemoteAndroidTestRunner runner, int userId) {
        if (runner instanceof RemoteAndroidTestRunner) {
            String original = ((RemoteAndroidTestRunner) runner).getRunOptions();
            String userRunTimeOption = String.format("--user %s", Integer.toString(userId));
            ((RemoteAndroidTestRunner) runner).setRunOptions(userRunTimeOption);
            return original;
        } else {
            throw new IllegalStateException(String.format("%s runner does not support multi-user",
                    runner.getClass().getName()));
        }
    }

    /**
     * Helper method to reset the run time options to {@link RemoteAndroidTestRunner}
     *
     * @param runner {@link IRemoteAndroidTestRunner}
     * @param oldRunTimeOptions
     */
    private void resetUserRunTimeOptionToRunner(final IRemoteAndroidTestRunner runner,
            String oldRunTimeOptions) {
        if (runner instanceof RemoteAndroidTestRunner) {
            if (oldRunTimeOptions != null) {
                ((RemoteAndroidTestRunner) runner).setRunOptions(oldRunTimeOptions);
            }
        } else {
            throw new IllegalStateException(String.format("%s runner does not support multi-user",
                    runner.getClass().getName()));
        }
    }

    private static class RunFailureListener extends StubTestRunListener {
        private boolean mIsRunFailure = false;

        @Override
        public void testRunFailed(String message) {
            mIsRunFailure = true;
        }

        public boolean isRunFailure() {
            return mIsRunFailure;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runInstrumentationTests(IRemoteAndroidTestRunner runner,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
        List<ITestRunListener> listenerList = new ArrayList<ITestRunListener>();
        listenerList.addAll(Arrays.asList(listeners));
        return runInstrumentationTests(runner, listenerList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runInstrumentationTestsAsUser(IRemoteAndroidTestRunner runner, int userId,
            ITestRunListener... listeners) throws DeviceNotAvailableException {
        String oldRunTimeOptions = appendUserRunTimeOptionToRunner(runner, userId);
        boolean result = runInstrumentationTests(runner, listeners);
        resetUserRunTimeOptionToRunner(runner, oldRunTimeOptions);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRuntimePermissionSupported() throws DeviceNotAvailableException {
        //TODO: only keep API Level check once M is official
        if (getApiLevel() > 22) {
            return true;
        }
        String codeName = getProperty(BUILD_CODENAME_PROP).trim();
        if (!"REL".equals(codeName)) {
            // this is a development platform, check code name, if less than M, then not supported
            if (codeName.charAt(0) < 'M') {
                return false;
            }
        } else {
            // released platform, none supports runtime permission yet
            return false;
        }
        try {
            long buildNumber = Long.parseLong(getBuildId());
            // for platform commit 429270c3ed1da02914efb476be977dc3829d4c30
            return buildNumber >= 1837705;
        } catch (NumberFormatException nfe) {
            // build id field is not a number, probably an eng build since we've already checked
            // code name, assuming supported
            return true;
        }
    }

    /**
     * helper method to throw exception if runtime permission isn't supported
     * @throws DeviceNotAvailableException
     */
    private void ensureRuntimePermissionSupported() throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        if (!runtimePermissionSupported) {
            throw new UnsupportedOperationException(
                    "platform on device does not support runtime permission granting!");
        }
    }

    /**
     * Core implementation of package installation, with retries around
     * {@link IDevice#installPackage(String, boolean, String...)}
     * @param packageFile
     * @param reinstall
     * @param extraArgs
     * @return the response from the installation
     * @throws DeviceNotAvailableException
     */
    private String internalInstallPackage(
            final File packageFile, final boolean reinstall, final List<String> extraArgs)
                    throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction installAction = new DeviceAction() {
            @Override
            public boolean run() throws InstallException {
                String result = getIDevice().installPackage(packageFile.getAbsolutePath(),
                        reinstall, extraArgs.toArray(new String[]{}));
                response[0] = result;
                return result == null;
            }
        };
        performDeviceAction(String.format("install %s", packageFile.getAbsolutePath()),
                installAction, MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(final File packageFile, final boolean reinstall,
            final String... extraArgs) throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        // grant all permissions by default if feature is supported
        if (runtimePermissionSupported) {
            args.add("-g");
        }
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException {
        ensureRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            args.add("-g");
        }
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        // grant all permissions by default if feature is supported
        if (runtimePermissionSupported) {
            args.add("-g");
        }
        args.add("--user");
        args.add(Integer.toString(userId));
        return internalInstallPackage(packageFile, reinstall, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
                    throws DeviceNotAvailableException {
        ensureRuntimePermissionSupported();
        List<String> args = new ArrayList<>(Arrays.asList(extraArgs));
        if (grantPermissions) {
            args.add("-g");
        }
        args.add("--user");
        args.add(Integer.toString(userId));
        return internalInstallPackage(packageFile, reinstall, args);
    }

    public String installPackage(final File packageFile, final File certFile,
            final boolean reinstall, final String... extraArgs) throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction installAction = new DeviceAction() {
            @Override
            public boolean run() throws InstallException, SyncException, IOException,
            TimeoutException, AdbCommandRejectedException {
                // TODO: create a getIDevice().installPackage(File, File...) method when the dist
                // cert functionality is ready to be open sourced
                String remotePackagePath = getIDevice().syncPackageToDevice(
                        packageFile.getAbsolutePath());
                String remoteCertPath = getIDevice().syncPackageToDevice(
                        certFile.getAbsolutePath());
                // trick installRemotePackage into issuing a 'pm install <apk> <cert>' command,
                // by adding apk path to extraArgs, and using cert as the 'apk file'
                String[] newExtraArgs = new String[extraArgs.length + 1];
                System.arraycopy(extraArgs, 0, newExtraArgs, 0, extraArgs.length);
                newExtraArgs[newExtraArgs.length - 1] = String.format("\"%s\"", remotePackagePath);
                try {
                    response[0] = getIDevice().installRemotePackage(remoteCertPath, reinstall,
                            newExtraArgs);
                } finally {
                    getIDevice().removeRemotePackage(remotePackagePath);
                    getIDevice().removeRemotePackage(remoteCertPath);
                }
                return true;
            }
        };
        performDeviceAction(String.format("install %s", packageFile.getAbsolutePath()),
                installAction, MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uninstallPackage(final String packageName) throws DeviceNotAvailableException {
        // use array to store response, so it can be returned to caller
        final String[] response = new String[1];
        DeviceAction uninstallAction = new DeviceAction() {
            @Override
            public boolean run() throws InstallException {
                CLog.d("Uninstalling %s", packageName);
                String result = getIDevice().uninstallPackage(packageName);
                response[0] = result;
                return result == null;
            }
        };
        performDeviceAction(String.format("uninstall %s", packageName), uninstallAction,
                MAX_RETRY_ATTEMPTS);
        return response[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pullFile(final String remoteFilePath, final File localFile)
            throws DeviceNotAvailableException {

        DeviceAction pullAction = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.pullFile(interpolatePathVariables(remoteFilePath),
                            localFile.getAbsolutePath(), SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to pull %s from %s to %s. Message %s", remoteFilePath,
                            getSerialNumber(), localFile.getAbsolutePath(), e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("pull %s to %s", remoteFilePath,
                localFile.getAbsolutePath()), pullAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
        File localFile = null;
        boolean success = false;
        try {
            localFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
            if (pullFile(remoteFilePath, localFile)) {
                success = true;
                return localFile;
            }
        } catch (IOException e) {
            CLog.w("Encountered IOException while trying to pull '%s': %s", remoteFilePath, e);
        } finally {
            if (!success) {
                FileUtil.deleteFile(localFile);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException {
        String externalPath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String fullPath = (new File(externalPath, remoteFilePath)).getPath();
        return pullFile(fullPath);
    }

    /**
     * Helper function that watches for the string "${EXTERNAL_STORAGE}" and replaces it with the
     * pathname of the EXTERNAL_STORAGE mountpoint.  Specifically intended to be used for pathnames
     * that are being passed to SyncService, which does not support variables inside of filenames.
     */
    String interpolatePathVariables(String path) {
        final String esString = "${EXTERNAL_STORAGE}";
        if (path.contains(esString)) {
            final String esPath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            path = path.replace(esString, esPath);
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushFile(final File localFile, final String remoteFilePath)
            throws DeviceNotAvailableException {
        DeviceAction pushAction = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.pushFile(localFile.getAbsolutePath(),
                        interpolatePathVariables(remoteFilePath),
                        SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to push %s to %s on device %s. Message %s",
                           localFile.getAbsolutePath(), remoteFilePath, getSerialNumber(),
                           e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("push %s to %s", localFile.getAbsolutePath(),
                remoteFilePath), pushAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushString(final String contents, final String remoteFilePath)
            throws DeviceNotAvailableException {
        File tmpFile = null;
        try {
            tmpFile = FileUtil.createTempFile("temp", ".txt");
            FileUtil.writeToFile(contents, tmpFile);
            return pushFile(tmpFile, remoteFilePath);
        } catch (IOException e) {
            CLog.e(e);
            return false;
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean doesFileExist(String destPath) throws DeviceNotAvailableException {
        String lsGrep = executeShellCommand(String.format("ls \"%s\"", destPath));
        return !lsGrep.contains("No such file or directory");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException {
        CLog.i("Checking free space for %s", getSerialNumber());
        String externalStorePath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String output = getDfOutput(externalStorePath);
        // Try coreutils/toybox style output first.
        Long available = parseFreeSpaceFromModernOutput(output);
        if (available != null) {
            return available;
        }
        // Then the two legacy toolbox formats.
        available = parseFreeSpaceFromAvailable(output);
        if (available != null) {
            return available;
        }
        available = parseFreeSpaceFromFree(externalStorePath, output);
        if (available != null) {
            return available;
        }

        CLog.e("free space command output \"%s\" did not match expected patterns", output);
        return 0;
    }

    /**
     * Run the 'df' shell command and return output, making multiple attempts if necessary.
     *
     * @param externalStorePath the path to check
     * @return the output from 'shell df path'
     * @throws DeviceNotAvailableException
     */
    private String getDfOutput(String externalStorePath) throws DeviceNotAvailableException {
        for (int i=0; i < MAX_RETRY_ATTEMPTS; i++) {
            String output = executeShellCommand(String.format("df %s", externalStorePath));
            if (output.trim().length() > 0) {
                return output;
            }
        }
        throw new DeviceUnresponsiveException(String.format(
                "Device %s not returning output from df command after %d attempts",
                getSerialNumber(), MAX_RETRY_ATTEMPTS));
    }

    /**
     * Parses a partition's available space from the legacy output of a 'df' command, used
     * pre-gingerbread.
     * <p/>
     * Assumes output format of:
     * <br>/
     * <code>
     * [partition]: 15659168K total, 51584K used, 15607584K available (block size 32768)
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    private Long parseFreeSpaceFromAvailable(String dfOutput) {
        final Pattern freeSpacePattern = Pattern.compile("(\\d+)K available");
        Matcher patternMatcher = freeSpacePattern.matcher(dfOutput);
        if (patternMatcher.find()) {
            String freeSpaceString = patternMatcher.group(1);
            try {
                return Long.parseLong(freeSpaceString);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    /**
     * Parses a partition's available space from the 'table-formatted' output of a toolbox 'df'
     * command, used from gingerbread to lollipop.
     * <p/>
     * Assumes output format of:
     * <br/>
     * <code>
     * Filesystem             Size   Used   Free   Blksize
     * <br/>
     * [partition]:              3G   790M  2G     4096
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    Long parseFreeSpaceFromFree(String externalStorePath, String dfOutput) {
        Long freeSpace = null;
        final Pattern freeSpaceTablePattern = Pattern.compile(String.format(
                //fs   Size         Used         Free
                "%s\\s+[\\w\\d\\.]+\\s+[\\w\\d\\.]+\\s+([\\d\\.]+)(\\w)", externalStorePath));
        Matcher tablePatternMatcher = freeSpaceTablePattern.matcher(dfOutput);
        if (tablePatternMatcher.find()) {
            String numericValueString = tablePatternMatcher.group(1);
            String unitType = tablePatternMatcher.group(2);
            try {
                Float freeSpaceFloat = Float.parseFloat(numericValueString);
                if (unitType.equals("M")) {
                    freeSpaceFloat = freeSpaceFloat * 1024;
                } else if (unitType.equals("G")) {
                    freeSpaceFloat = freeSpaceFloat * 1024 * 1024;
                }
                freeSpace = freeSpaceFloat.longValue();
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return freeSpace;
    }

    /**
     * Parses a partition's available space from the modern coreutils/toybox 'df' output, used
     * after lollipop.
     * <p/>
     * Assumes output format of:
     * <br/>
     * <code>
     * Filesystem      1K-blocks	Used  Available Use% Mounted on
     * <br/>
     * /dev/fuse        11585536    1316348   10269188  12% /mnt/shell/emulated
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    Long parseFreeSpaceFromModernOutput(String dfOutput) {
        Matcher matcher = DF_PATTERN.matcher(dfOutput);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMountPoint(String mountName) {
        return mStateMonitor.getMountPoint(mountName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MountPointInfo> getMountPointInfo() throws DeviceNotAvailableException {
        final String mountInfo = executeShellCommand("cat /proc/mounts");
        final String[] mountInfoLines = mountInfo.split("\r?\n");
        List<MountPointInfo> list = new ArrayList<MountPointInfo>(mountInfoLines.length);

        for (String line : mountInfoLines) {
            // We ignore the last two fields
            // /dev/block/mtdblock4 /cache yaffs2 rw,nosuid,nodev,relatime 0 0
            final String[] parts = line.split("\\s+", 5);
            list.add(new MountPointInfo(parts[0], parts[1], parts[2], parts[3]));
        }

        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MountPointInfo getMountPointInfo(String mountpoint) throws DeviceNotAvailableException {
        // The overhead of parsing all of the lines should be minimal
        List<MountPointInfo> mountpoints = getMountPointInfo();
        for (MountPointInfo info : mountpoints) {
            if (mountpoint.equals(info.mountpoint)) return info;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IFileEntry getFileEntry(String path) throws DeviceNotAvailableException {
        path = interpolatePathVariables(path);
        String[] pathComponents = path.split(FileListingService.FILE_SEPARATOR);
        FileListingService service = getFileListingService();
        IFileEntry rootFile = new FileEntryWrapper(this, service.getRoot());
        return FileEntryWrapper.getDescendant(rootFile, Arrays.asList(pathComponents));
    }

    /**
     * Retrieve the {@link FileListingService} for the {@link IDevice}, making multiple attempts
     * and recovery operations if necessary.
     * <p/>
     * This is necessary because {@link IDevice#getFileListingService()} can return
     * <code>null</code> if device is in fastboot.  The symptom of this condition is that the
     * current {@link #getIDevice()} is a {@link StubDevice}.
     *
     * @return the {@link FileListingService}
     * @throws DeviceNotAvailableException if device communication is lost.
     */
    private FileListingService getFileListingService() throws DeviceNotAvailableException  {
        final FileListingService[] service = new FileListingService[1];
        DeviceAction serviceAction = new DeviceAction() {
            @Override
            public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException, InstallException, SyncException {
                service[0] = getIDevice().getFileListingService();
                if (service[0] == null) {
                    // could not get file listing service - must be a stub device - enter recovery
                    throw new IOException("Could not get file listing service");
                }
                return true;
            }
        };
        performDeviceAction("getFileListingService", serviceAction, MAX_RETRY_ATTEMPTS);
        return service[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushDir(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        if (!localFileDir.isDirectory()) {
            CLog.e("file %s is not a directory", localFileDir.getAbsolutePath());
            return false;
        }
        File[] childFiles = localFileDir.listFiles();
        if (childFiles == null) {
            CLog.e("Could not read files in %s", localFileDir.getAbsolutePath());
            return false;
        }
        for (File childFile : childFiles) {
            String remotePath = String.format("%s/%s", deviceFilePath, childFile.getName());
            if (childFile.isDirectory()) {
                executeShellCommand(String.format("mkdir %s", remotePath));
                if (!pushDir(childFile, remotePath)) {
                    return false;
                }
            } else if (childFile.isFile()) {
                if (!pushFile(childFile, remotePath)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        if (localFileDir == null || deviceFilePath == null) {
            throw new IllegalArgumentException("syncFiles does not take null arguments");
        }
        CLog.i("Syncing %s to %s on device %s",
                localFileDir.getAbsolutePath(), deviceFilePath, getSerialNumber());
        if (!localFileDir.isDirectory()) {
            CLog.e("file %s is not a directory", localFileDir.getAbsolutePath());
            return false;
        }
        // get the real destination path. This is done because underlying syncService.push
        // implementation will add localFileDir.getName() to destination path
        deviceFilePath = String.format("%s/%s", interpolatePathVariables(deviceFilePath),
                localFileDir.getName());
        if (!doesFileExist(deviceFilePath)) {
            executeShellCommand(String.format("mkdir -p %s", deviceFilePath));
        }
        IFileEntry remoteFileEntry = getFileEntry(deviceFilePath);
        if (remoteFileEntry == null) {
            CLog.e("Could not find remote file entry %s ", deviceFilePath);
            return false;
        }

        return syncFiles(localFileDir, remoteFileEntry);
    }

    /**
     * Recursively sync newer files.
     *
     * @param localFileDir the local {@link File} directory to sync
     * @param remoteFileEntry the remote destination {@link IFileEntry}
     * @return <code>true</code> if files were synced successfully
     * @throws DeviceNotAvailableException
     */
    private boolean syncFiles(File localFileDir, final IFileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        CLog.d("Syncing %s to %s on %s", localFileDir.getAbsolutePath(),
                remoteFileEntry.getFullPath(), getSerialNumber());
        // find newer files to sync
        File[] localFiles = localFileDir.listFiles(new NoHiddenFilesFilter());
        ArrayList<String> filePathsToSync = new ArrayList<String>();
        for (File localFile : localFiles) {
            IFileEntry entry = remoteFileEntry.findChild(localFile.getName());
            if (entry == null) {
                CLog.d("Detected missing file path %s", localFile.getAbsolutePath());
                filePathsToSync.add(localFile.getAbsolutePath());
            } else if (localFile.isDirectory()) {
                // This directory exists remotely. recursively sync it to sync only its newer files
                // contents
                if (!syncFiles(localFile, entry)) {
                    return false;
                }
            } else if (isNewer(localFile, entry)) {
                CLog.d("Detected newer file %s", localFile.getAbsolutePath());
                filePathsToSync.add(localFile.getAbsolutePath());
            }
        }

        if (filePathsToSync.size() == 0) {
            CLog.d("No files to sync");
            return true;
        }
        final String files[] = filePathsToSync.toArray(new String[filePathsToSync.size()]);
        DeviceAction syncAction = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.push(files, remoteFileEntry.getFileEntry(),
                            SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to sync files to %s on device %s. Message %s",
                            remoteFileEntry.getFullPath(), getSerialNumber(), e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("sync files %s", remoteFileEntry.getFullPath()),
                syncAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Queries the file listing service for a given directory
     *
     * @param remoteFileEntry
     * @throws DeviceNotAvailableException
     */
    FileEntry[] getFileChildren(final FileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        // time this operation because its known to hang
        FileQueryAction action = new FileQueryAction(remoteFileEntry,
                getIDevice().getFileListingService());
        performDeviceAction("buildFileCache", action, MAX_RETRY_ATTEMPTS);
        return action.mFileContents;
    }

    private class FileQueryAction implements DeviceAction {

        FileEntry[] mFileContents = null;
        private final FileEntry mRemoteFileEntry;
        private final FileListingService mService;

        FileQueryAction(FileEntry remoteFileEntry, FileListingService service) {
            throwIfNull(remoteFileEntry);
            throwIfNull(service);
            mRemoteFileEntry = remoteFileEntry;
            mService = service;
        }

        @Override
        public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException {
            mFileContents = mService.getChildrenSync(mRemoteFileEntry);
            return true;
        }
    }

    /**
     * A {@link FilenameFilter} that rejects hidden (ie starts with ".") files.
     */
    private static class NoHiddenFilesFilter implements FilenameFilter {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return !name.startsWith(".");
        }
    }

    /**
     * Return <code>true</code> if local file is newer than remote file.
     */
    private boolean isNewer(File localFile, IFileEntry entry) {
        // remote times are in GMT timezone
        final String entryTimeString = String.format("%s %s GMT", entry.getDate(), entry.getTime());
        try {
            // expected format of a FileEntry's date and time
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm zzz");
            Date remoteDate = format.parse(entryTimeString);
            // localFile.lastModified has granularity of ms, but remoteDate.getTime only has
            // granularity of minutes. Shift remoteDate.getTime() backward by one minute so newly
            // modified files get synced
            return localFile.lastModified() > (remoteDate.getTime() - 60 * 1000);
        } catch (ParseException e) {
            CLog.e("Error converting remote time stamp %s for %s on device %s", entryTimeString,
                    entry.getFullPath(), getSerialNumber());
        }
        // sync file by default
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String executeAdbCommand(String... cmdArgs) throws DeviceNotAvailableException {
        final String[] fullCmd = buildAdbCommand(cmdArgs);
        AdbAction adbAction = new AdbAction(fullCmd);
        performDeviceAction(String.format("adb %s", cmdArgs[0]), adbAction, MAX_RETRY_ATTEMPTS);
        return adbAction.mOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(getCommandTimeout(), cmdArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeLongFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(getLongCommandTimeout(), cmdArgs);
    }

    /**
     * @param cmdArgs
     * @throws DeviceNotAvailableException
     */
    private CommandResult doFastbootCommand(final long timeout, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(String.format(
                    "Attempted to fastboot on device %s , but fastboot is not available. Aborting.",
                    getSerialNumber()));
        }
        final String[] fullCmd = buildFastbootCommand(cmdArgs);
        for (int i = 0; i < MAX_RETRY_ATTEMPTS; i++) {
            CommandResult result = new CommandResult(CommandStatus.EXCEPTION);
            // block state changes while executing a fastboot command, since
            // device will disappear from fastboot devices while command is being executed
            mFastbootLock.lock();
            try {
                result = getRunUtil().runTimedCmd(timeout, fullCmd);
            } finally {
                mFastbootLock.unlock();
            }
            if (!isRecoveryNeeded(result)) {
                return result;
            }
            CLog.w("Recovery needed after executing fastboot command");
            if (result != null) {
                CLog.v("fastboot command output:\nstdout: %s\nstderr:%s",
                        result.getStdout(), result.getStderr());
            }
            recoverDeviceFromBootloader();
        }
        throw new DeviceUnresponsiveException(String.format("Attempted fastboot %s multiple "
                + "times on device %s without communication success. Aborting.", cmdArgs[0],
                getSerialNumber()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseFastbootErase() {
        return mOptions.getUseFastbootErase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseFastbootErase(boolean useFastbootErase) {
        mOptions.setUseFastbootErase(useFastbootErase);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult fastbootWipePartition(String partition)
            throws DeviceNotAvailableException {
        if (mOptions.getUseFastbootErase()) {
            return executeLongFastbootCommand("erase", partition);
        } else {
            return executeLongFastbootCommand("format", partition);
        }
    }

    /**
     * Evaluate the given fastboot result to determine if recovery mode needs to be entered
     *
     * @param fastbootResult the {@link CommandResult} from a fastboot command
     * @return <code>true</code> if recovery mode should be entered, <code>false</code> otherwise.
     */
    private boolean isRecoveryNeeded(CommandResult fastbootResult) {
        if (fastbootResult.getStatus().equals(CommandStatus.TIMED_OUT)) {
            // fastboot commands always time out if devices is not present
            return true;
        } else {
            // check for specific error messages in result that indicate bad device communication
            // and recovery mode is needed
            if (fastbootResult.getStderr() == null ||
                fastbootResult.getStderr().contains("data transfer failure (Protocol error)") ||
                fastbootResult.getStderr().contains("status read failed (No such device)")) {
                CLog.w("Bad fastboot response from device %s. stderr: %s. Entering recovery",
                        getSerialNumber(), fastbootResult.getStderr());
                return true;
            }
        }
        return false;
    }

    /**
     * Get the max time allowed in ms for commands.
     */
    int getCommandTimeout() {
        return mCmdTimeout;
    }

    /**
     * Set the max time allowed in ms for commands.
     */
    void setLongCommandTimeout(long timeout) {
        mLongCmdTimeout = timeout;
    }

    /**
     * Get the max time allowed in ms for commands.
     */
    long getLongCommandTimeout() {
        return mLongCmdTimeout;
    }

    /**
     * Set the max time allowed in ms for commands.
     */
    void setCommandTimeout(int timeout) {
        mCmdTimeout = timeout;
    }

    /**
     * Builds the OS command for the given adb command and args
     */
    private String[] buildAdbCommand(String... commandArgs) {
        return ArrayUtil.buildArray(new String[] {"adb", "-s", getSerialNumber()},
                commandArgs);
    }

    /**
     * Builds the OS command for the given fastboot command and args
     */
    private String[] buildFastbootCommand(String... commandArgs) {
        return ArrayUtil.buildArray(new String[] {"fastboot", "-s", getSerialNumber()},
                commandArgs);
    }

    /**
     * Performs an action on this device. Attempts to recover device and optionally retry command
     * if action fails.
     *
     * @param actionDescription a short description of action to be performed. Used for logging
     *            purposes only.
     * @param action the action to be performed
     * @param retryAttempts the retry attempts to make for action if it fails but
     *            recovery succeeds
     * @returns <code>true</code> if action was performed successfully
     * @throws DeviceNotAvailableException if recovery attempt fails or max attempts done without
     *             success
     */
    private boolean performDeviceAction(String actionDescription, final DeviceAction action,
            int retryAttempts) throws DeviceNotAvailableException {

        for (int i = 0; i < retryAttempts + 1; i++) {
            try {
                return action.run();
            } catch (TimeoutException e) {
                logDeviceActionException(actionDescription, e);
            } catch (IOException e) {
                logDeviceActionException(actionDescription, e);
            } catch (InstallException e) {
                logDeviceActionException(actionDescription, e);
            } catch (SyncException e) {
                logDeviceActionException(actionDescription, e);
                // a SyncException is not necessarily a device communication problem
                // do additional diagnosis
                if (!e.getErrorCode().equals(SyncError.BUFFER_OVERRUN) &&
                        !e.getErrorCode().equals(SyncError.TRANSFER_PROTOCOL_ERROR)) {
                    // this is a logic problem, doesn't need recovery or to be retried
                    return false;
                }
            } catch (AdbCommandRejectedException e) {
                logDeviceActionException(actionDescription, e);
            } catch (ShellCommandUnresponsiveException e) {
                CLog.w("Device %s stopped responding when attempting %s", getSerialNumber(),
                        actionDescription);
            }
            // TODO: currently treat all exceptions the same. In future consider different recovery
            // mechanisms for time out's vs IOExceptions
            recoverDevice();
        }
        if (retryAttempts > 0) {
            throw new DeviceUnresponsiveException(String.format("Attempted %s multiple times "
                    + "on device %s without communication success. Aborting.", actionDescription,
                    getSerialNumber()));
        }
        return false;
    }

    /**
     * Log an entry for given exception
     *
     * @param actionDescription the action's description
     * @param e the exception
     */
    private void logDeviceActionException(String actionDescription, Exception e) {
        CLog.w("%s (%s) when attempting %s on device %s", e.getClass().getSimpleName(),
                getExceptionMessage(e), actionDescription, getSerialNumber());
    }

    /**
     * Make a best effort attempt to retrieve a meaningful short descriptive message for given
     * {@link Exception}
     *
     * @param e the {@link Exception}
     * @return a short message
     */
    private String getExceptionMessage(Exception e) {
        StringBuilder msgBuilder = new StringBuilder();
        if (e.getMessage() != null) {
            msgBuilder.append(e.getMessage());
        }
        if (e.getCause() != null) {
            msgBuilder.append(" cause: ");
            msgBuilder.append(e.getCause().getClass().getSimpleName());
            if (e.getCause().getMessage() != null) {
                msgBuilder.append(" (");
                msgBuilder.append(e.getCause().getMessage());
                msgBuilder.append(")");
            }
        }
        return msgBuilder.toString();
    }

    /**
     * Attempts to recover device communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    @Override
    public void recoverDevice() throws DeviceNotAvailableException {
        if (mRecoveryMode.equals(RecoveryMode.NONE)) {
            CLog.i("Skipping recovery on %s", getSerialNumber());
            getRunUtil().sleep(NONE_RECOVERY_MODE_DELAY);
            return;
        }
        CLog.i("Attempting recovery on %s", getSerialNumber());
        mRecovery.recoverDevice(mStateMonitor, mRecoveryMode.equals(RecoveryMode.ONLINE));
        if (mRecoveryMode.equals(RecoveryMode.AVAILABLE)) {
            // turn off recovery mode to prevent reentrant recovery
            // TODO: look for a better way to handle this, such as doing postBootUp steps in
            // recovery itself
            mRecoveryMode = RecoveryMode.NONE;
            // this might be a runtime reset - still need to run post boot setup steps
            if (isEncryptionSupported() && isDeviceEncrypted()) {
                unlockDevice();
            }
            postBootSetup();
            mRecoveryMode = RecoveryMode.AVAILABLE;
        } else if (mRecoveryMode.equals(RecoveryMode.ONLINE)) {
            // turn off recovery mode to prevent reentrant recovery
            // TODO: look for a better way to handle this, such as doing postBootUp steps in
            // recovery itself
            mRecoveryMode = RecoveryMode.NONE;
            enableAdbRoot();
            mRecoveryMode = RecoveryMode.ONLINE;
        }
        CLog.i("Recovery successful for %s", getSerialNumber());
    }

    /**
     * Attempts to recover device fastboot communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    private void recoverDeviceFromBootloader() throws DeviceNotAvailableException {
        CLog.i("Attempting recovery on %s in bootloader", getSerialNumber());
        mRecovery.recoverDeviceBootloader(mStateMonitor);
        CLog.i("Bootloader recovery successful for %s", getSerialNumber());
    }

    private void recoverDeviceInRecovery() throws DeviceNotAvailableException {
        CLog.i("Attempting recovery on %s in recovery", getSerialNumber());
        mRecovery.recoverDeviceRecovery(mStateMonitor);
        CLog.i("Recovery mode recovery successful for %s", getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startLogcat() {
        if (mLogcatReceiver != null) {
            CLog.d("Already capturing logcat for %s, ignoring", getSerialNumber());
            return;
        }
        mLogcatReceiver = createLogcatReceiver();
        mLogcatReceiver.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcat() {
        if (mLogcatReceiver == null) {
            CLog.w("Not capturing logcat for %s in background, returning a logcat dump",
                    getSerialNumber());
            return getLogcatDump();
        } else {
            return mLogcatReceiver.getLogcatData();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcat(int maxBytes) {
        if (mLogcatReceiver == null) {
            CLog.w("Not capturing logcat for %s in background, returning a logcat dump "
                    + "ignoring size", getSerialNumber());
            return getLogcatDump();
        } else {
            return mLogcatReceiver.getLogcatData(maxBytes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcatDump() {
        byte[] output = new byte[0];
        try {
            // use IDevice directly because we don't want callers to handle
            // DeviceNotAvailableException for this method
            CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
            // add -d parameter to make this a non blocking call
            getIDevice().executeShellCommand(LogcatReceiver.LOGCAT_CMD + " -d", receiver);
            output = receiver.getOutput();
        } catch (IOException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        } catch (TimeoutException e) {
            CLog.w("Failed to get logcat dump from %s: timeout", getSerialNumber());
        } catch (AdbCommandRejectedException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        } catch (ShellCommandUnresponsiveException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        }
        return new ByteArrayInputStreamSource(output);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.stop();
            mLogcatReceiver = null;
        } else {
            CLog.w("Attempting to stop logcat when not capturing for %s", getSerialNumber());
        }
    }

    /**
     * Factory method to create a {@link LogcatReceiver}.
     * <p/>
     * Exposed for unit testing.
     */
    LogcatReceiver createLogcatReceiver() {
        String logcatOptions = mOptions.getLogcatOptions();
        if (logcatOptions == null) {
            return new LogcatReceiver(this, mOptions.getMaxLogcatDataSize(), mLogStartDelay);
        } else {
            return new LogcatReceiver(this,
                    String.format("%s %s", LogcatReceiver.LOGCAT_CMD, logcatOptions),
                    mOptions.getMaxLogcatDataSize(), mLogStartDelay);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getBugreport() {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        try {
            executeShellCommand(BUGREPORT_CMD, receiver, BUGREPORT_TIMEOUT, 0 /* don't retry */);
        } catch (DeviceNotAvailableException e) {
            // Log, but don't throw, so the caller can get the bugreport contents even if the device
            // goes away
            CLog.e("Device %s became unresponsive while retrieving bugreport", getSerialNumber());
        }

        return new ByteArrayInputStreamSource(receiver.getOutput());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException {
        return getScreenshot("PNG");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException {
        if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")){
            CLog.e("Screenshot: Format %s is not supported, defaulting to PNG.", format);
            format = "PNG";
        }
        ScreenshotAction action = new ScreenshotAction();
        if (performDeviceAction("screenshot", action, MAX_RETRY_ATTEMPTS)) {
            byte[] imageData = compressRawImage(action.mRawScreenshot, format.toUpperCase());
            if (imageData != null) {
                return new ByteArrayInputStreamSource(imageData);
            }
        }
        return null;
    }

    private class ScreenshotAction implements DeviceAction {

        RawImage mRawScreenshot;

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException {
            mRawScreenshot = getIDevice().getScreenshot();
            return mRawScreenshot != null;
        }
    }

    private byte[] compressRawImage(RawImage rawImage, String format) {
        BufferedImage image = null;

        if ("JPEG".equalsIgnoreCase(format)) {
            //JPEG does not support ARGB without a special encoder
            image = new BufferedImage(rawImage.width, rawImage.height,
                    BufferedImage.TYPE_3BYTE_BGR);
        }
        else {
            image = new BufferedImage(rawImage.width, rawImage.height,
                    BufferedImage.TYPE_INT_ARGB);
        }

        // borrowed conversion logic from platform/sdk/screenshot/.../Screenshot.java
        int index = 0;
        int IndexInc = rawImage.bpp >> 3;
        for (int y = 0 ; y < rawImage.height ; y++) {
            for (int x = 0 ; x < rawImage.width ; x++) {
                int value = rawImage.getARGB(index);
                index += IndexInc;
                image.setRGB(x, y, value);
            }
        }

        // Rescale to reduce size if needed
        // Screenshot default format is 1080 x 1920, 8-bit/color RGBA
        // By cutting in half we can easily keep good quality and smaller size
        int shortEdge = Math.min(image.getHeight(), image.getWidth());
        if (shortEdge > 720) {
            Image resized = image.getScaledInstance(image.getWidth() / 2, image.getHeight() / 2,
                    Image.SCALE_SMOOTH);
            image = new BufferedImage(image.getWidth() / 2, image.getHeight() / 2,
                    Image.SCALE_REPLICATE);
            image.getGraphics().drawImage(resized, 0, 0, null);
        }

        // store compressed image in memory, and let callers write to persistent storage
        // use initial buffer size of 128K
        byte[] imageData = null;
        ByteArrayOutputStream imageOut = new ByteArrayOutputStream(128*1024);
        try {
            if (ImageIO.write(image, format, imageOut)) {
                imageData = imageOut.toByteArray();
            } else {
                CLog.e("Failed to compress screenshot to png");
            }
        } catch (IOException e) {
            CLog.e("Failed to compress screenshot to png");
            CLog.e(e);
        }
        StreamUtil.close(imageOut);
        return imageData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearLastConnectedWifiNetwork() {
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {

        // Clears the last connected wifi network.
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;

        // Connects to wifi network. It retries up to {@link TestDeviceOptions@getWifiAttempts()}
        // times and uses binary exponential back-offs when retrying.
        Random rnd = new Random();
        int backoffSlotCount = 2;
        IWifiHelper wifi = createWifiHelper();
        for (int i = 1; i <= mOptions.getWifiAttempts(); i++) {
            CLog.i("Connecting to wifi network %s on %s", wifiSsid, getSerialNumber());
            boolean success = wifi.connectToNetwork(wifiSsid, wifiPsk,
                    mOptions.getConnCheckUrl());
            final Map<String, String> wifiInfo = wifi.getWifiInfo();
            if (success) {
                CLog.i("Successfully connected to wifi network %s(%s) on %s",
                        wifiSsid, wifiInfo.get("bssid"), getSerialNumber());

                mLastConnectedWifiSsid = wifiSsid;
                mLastConnectedWifiPsk = wifiPsk;

                return true;
            } else {
                CLog.w("Failed to connect to wifi network %s(%s) on %s on attempt %d of %d",
                        wifiSsid, wifiInfo.get("bssid"), getSerialNumber(), i,
                        mOptions.getWifiAttempts());
            }

            if (i < mOptions.getWifiAttempts()) {
                int waitTime = rnd.nextInt(backoffSlotCount) * mOptions.getWifiRetryWaitTime();
                backoffSlotCount *= 2;
                CLog.i("Waiting for %d ms before reconnecting to %s...", waitTime, wifiSsid);
                getRunUtil().sleep(waitTime);
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkConnectivity() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        return wifi.checkConnectivity(mOptions.getConnCheckUrl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        if (!checkConnectivity())  {
            return connectToWifiNetwork(wifiSsid, wifiPsk);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWifiEnabled() throws DeviceNotAvailableException {
        try {
            final IWifiHelper wifi = createWifiHelper();
            return wifi.isWifiEnabled();
        } catch (RuntimeException e) {
            CLog.w("Failed to create WifiHelper: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Checks that the device is currently successfully connected to given wifi SSID.
     *
     * @param wifiSSID the wifi ssid
     * @return <code>true</code> if device is currently connected to wifiSSID and has network
     *         connectivity. <code>false</code> otherwise
     * @throws DeviceNotAvailableException if connection with device was lost
     */
    boolean checkWifiConnection(String wifiSSID) throws DeviceNotAvailableException {
        CLog.i("Checking connection with wifi network %s on %s", wifiSSID, getSerialNumber());
        final IWifiHelper wifi = createWifiHelper();
        // getSSID returns SSID as "SSID"
        final String quotedSSID = String.format("\"%s\"", wifiSSID);

        boolean test = wifi.isWifiEnabled();
        CLog.v("%s: wifi enabled? %b", getSerialNumber(), test);

        if (test) {
            final String actualSSID = wifi.getSSID();
            test = quotedSSID.equals(actualSSID);
            CLog.v("%s: SSID match (%s, %s, %b)", getSerialNumber(),
                    quotedSSID, actualSSID, test);
        }
        if (test) {
            test = wifi.hasValidIp();
            CLog.v("%s: validIP? %b", getSerialNumber(), test);
        }
        if (test) {
            test = checkConnectivity();
            CLog.v("%s: checkConnectivity returned %b", getSerialNumber(), test);
        }
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disconnectFromWifi() throws DeviceNotAvailableException {
        CLog.i("Disconnecting from wifi on %s", getSerialNumber());
        // Clears the last connected wifi network.
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;

        IWifiHelper wifi = createWifiHelper();
        return wifi.disconnectFromNetwork();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIpAddress() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        return wifi.getIpAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableNetworkMonitor() throws DeviceNotAvailableException {
        mNetworkMonitorEnabled = false;

        IWifiHelper wifi = createWifiHelper();
        wifi.stopMonitor();
        if (wifi.startMonitor(NETWORK_MONITOR_INTERVAL, mOptions.getConnCheckUrl())) {
            mNetworkMonitorEnabled = true;
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disableNetworkMonitor() throws DeviceNotAvailableException {
        mNetworkMonitorEnabled = false;

        IWifiHelper wifi = createWifiHelper();
        List<Long> samples = wifi.stopMonitor();
        if (!samples.isEmpty()) {
            int failures = 0;
            long totalLatency = 0;
            for (Long sample : samples) {
                if (sample < 0) {
                    failures += 1;
                } else {
                    totalLatency += sample;
                }
            }
            double failureRate = failures * 100.0 / samples.size();
            double avgLatency = 0.0;
            if (failures < samples.size()) {
                avgLatency = totalLatency / (samples.size() - failures);
            }
            CLog.d("[metric] url=%s, window=%ss, failure_rate=%.2f%%, latency_avg=%.2f",
                    mOptions.getConnCheckUrl(), samples.size() * NETWORK_MONITOR_INTERVAL / 1000,
                    failureRate, avgLatency);
        }
        return true;
    }

    /**
     * Create a {@link WifiHelper} to use
     * <p/>
     * Exposed so unit tests can mock
     */
    IWifiHelper createWifiHelper() throws DeviceNotAvailableException {
        return new WifiHelper(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        // attempt to clear error dialogs multiple times
        for (int i = 0; i < NUM_CLEAR_ATTEMPTS; i++) {
            int numErrorDialogs = getErrorDialogCount();
            if (numErrorDialogs == 0) {
                return true;
            }
            doClearDialogs(numErrorDialogs);
        }
        if (getErrorDialogCount() > 0) {
            // at this point, all attempts to clear error dialogs completely have failed
            // it might be the case that the process keeps showing new dialogs immediately after
            // clearing. There's really no workaround, but to dump an error
            CLog.e("error dialogs still exist on %s.", getSerialNumber());
            return false;
        }
        return true;
    }

    /**
     * Detects the number of crash or ANR dialogs currently displayed.
     * <p/>
     * Parses output of 'dump activity processes'
     *
     * @return count of dialogs displayed
     * @throws DeviceNotAvailableException
     */
    private int getErrorDialogCount() throws DeviceNotAvailableException {
        int errorDialogCount = 0;
        Pattern crashPattern = Pattern.compile(".*crashing=true.*AppErrorDialog.*");
        Pattern anrPattern = Pattern.compile(".*notResponding=true.*AppNotRespondingDialog.*");
        String systemStatusOutput = executeShellCommand("dumpsys activity processes");
        Matcher crashMatcher = crashPattern.matcher(systemStatusOutput);
        while (crashMatcher.find()) {
            errorDialogCount++;
        }
        Matcher anrMatcher = anrPattern.matcher(systemStatusOutput);
        while (anrMatcher.find()) {
            errorDialogCount++;
        }

        return errorDialogCount;
    }

    private void doClearDialogs(int numDialogs) throws DeviceNotAvailableException {
        CLog.i("Attempted to clear %d dialogs on %s", numDialogs, getSerialNumber());
        for (int i=0; i < numDialogs; i++) {
            // send DPAD_CENTER
            executeShellCommand(DISMISS_DIALOG_CMD);
        }
    }

    IDeviceStateMonitor getDeviceStateMonitor() {
        return mStateMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBootSetup() throws DeviceNotAvailableException  {
        enableAdbRoot();
        if (mOptions.isDisableKeyguard()) {
            disableKeyguard();
        }
        for (String command : mOptions.getPostBootCommands()) {
            executeShellCommand(command);
        }
    }

    /**
     * Ensure wifi connection is re-established after boot. This is intended to be called after TF
     * initiated reboots(ones triggered by {@link #reboot()}) only.
     *
     * @throws DeviceNotAvailableException
     */
    void postBootWifiSetup() throws DeviceNotAvailableException {
        if (mLastConnectedWifiSsid != null) {
            reconnectToWifiNetwork();
        }
        if (mNetworkMonitorEnabled) {
            if (!enableNetworkMonitor()) {
                CLog.w("Failed to enable network monitor on %s after reboot", getSerialNumber());
            }
        }
    }

    void reconnectToWifiNetwork() throws DeviceNotAvailableException {
        // First, wait for wifi to re-connect automatically.
        long startTime = System.currentTimeMillis();
        boolean isConnected = checkConnectivity();
        while (!isConnected && (System.currentTimeMillis() - startTime) < WIFI_RECONNECT_TIMEOUT) {
            getRunUtil().sleep(WIFI_RECONNECT_CHECK_INTERVAL);
            isConnected = checkConnectivity();
        }

        if (isConnected) {
            return;
        }

        // If wifi is still not connected, try to re-connect on our own.
        final String wifiSsid = mLastConnectedWifiSsid;
        if (!connectToWifiNetworkIfNeeded(mLastConnectedWifiSsid, mLastConnectedWifiPsk)) {
            throw new NetworkNotAvailableException(
                    String.format("Failed to connect to wifi network %s on %s after reboot",
                            wifiSsid, getSerialNumber()));
        }
    }

    /**
     * Gets the adb shell command to disable the keyguard for this device.
     * <p/>
     * Exposed for unit testing.
     */
    String getDisableKeyguardCmd() {
        return mOptions.getDisableKeyguardCmd();
    }

    /**
     * Attempts to disable the keyguard.
     * <p>
     * First wait for the input dispatch to become ready, this happens around the same time when the
     * device reports BOOT_COMPLETE, apparently asynchronously, because current framework
     * implementation has occasional race condition. Then command is sent to dismiss keyguard (works
     * on non-secure ones only)
     * @throws DeviceNotAvailableException
     */
    void disableKeyguard() throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        while (true) {
            Boolean ready = isDeviceInputReady();
            if (ready == null) {
                // unsupported API level, bail
                break;
            }
            if (ready) {
                // input dispatch is ready, bail
                break;
            }
            long timeSpent = System.currentTimeMillis() - start;
            if (timeSpent > INPUT_DISPATCH_READY_TIMEOUT) {
                CLog.w("Timeout after waiting %dms on enabling of input dispatch", timeSpent);
                // break & proceed anyway
                break;
            } else {
                getRunUtil().sleep(1000);
            }
        }
        CLog.i("Attempting to disable keyguard on %s using %s", getSerialNumber(),
                getDisableKeyguardCmd());
        executeShellCommand(getDisableKeyguardCmd());
    }

    /**
     * Tests the device to see if input dispatcher is ready
     * @return <code>null</code> if not supported by platform, or the actual readiness state
     * @throws DeviceNotAvailableException
     */
    Boolean isDeviceInputReady() throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(TEST_INPUT_CMD, receiver);
        String output = receiver.getOutput();
        Matcher m = INPUT_DISPATCH_STATE_REGEX.matcher(output);
        if (!m.find()) {
            // output does not contain the line at all, implying unsupported API level, bail
            return null;
        }
        return "1".equals(m.group(1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoBootloader()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(
                    "Fastboot is not available and cannot reboot into bootloader");
        }
        CLog.i("Rebooting device %s in state %s into bootloader", getSerialNumber(),
                getDeviceState());
        if (TestDeviceState.FASTBOOT.equals(getDeviceState())) {
            CLog.i("device %s already in fastboot. Rebooting anyway", getSerialNumber());
            executeFastbootCommand("reboot-bootloader");
        } else {
            CLog.i("Booting device %s into bootloader", getSerialNumber());
            doAdbRebootBootloader();
        }
        if (!mStateMonitor.waitForDeviceBootloader(mOptions.getFastbootTimeout())) {
            recoverDeviceFromBootloader();
        }
    }

    private void doAdbRebootBootloader() throws DeviceNotAvailableException {
        doAdbReboot("bootloader");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reboot() throws DeviceNotAvailableException {
        rebootUntilOnline();

        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);

        if (isEncryptionSupported() && isDeviceEncrypted()) {
            unlockDevice();
        }

        setRecoveryMode(cachedRecoveryMode);

        if (mStateMonitor.waitForDeviceAvailable(mOptions.getRebootTimeout()) != null) {
            postBootSetup();
            postBootWifiSetup();
            return;
        } else {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootUntilOnline() throws DeviceNotAvailableException {
        doReboot();
        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);
        if (mStateMonitor.waitForDeviceOnline() != null) {
            enableAdbRoot();
        } else {
            recoverDevice();
        }
        setRecoveryMode(cachedRecoveryMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoRecovery() throws DeviceNotAvailableException {
        if (TestDeviceState.FASTBOOT == getDeviceState()) {
            CLog.w("device %s in fastboot when requesting boot to recovery. " +
                    "Rebooting to userspace first.", getSerialNumber());
            rebootUntilOnline();
        }
        doAdbReboot("recovery");
        if (!waitForDeviceInRecovery(mOptions.getAdbRecoveryTimeout())) {
            recoverDeviceInRecovery();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nonBlockingReboot() throws DeviceNotAvailableException {
        doReboot();
    }

    /**
     * Exposed for unit testing.
     *
     * @throws DeviceNotAvailableException
     */
    void doReboot() throws DeviceNotAvailableException, UnsupportedOperationException {
        if (TestDeviceState.FASTBOOT == getDeviceState()) {
            CLog.i("device %s in fastboot. Rebooting to userspace.", getSerialNumber());
            executeFastbootCommand("reboot");
        } else {
            CLog.i("Rebooting device %s", getSerialNumber());
            doAdbReboot(null);
            waitForDeviceNotAvailable("reboot", DEFAULT_UNAVAILABLE_TIMEOUT);
        }
    }

    /**
     * Performs an reboot via framework power manager
     *
     * Must have root access, device must be API Level 18 or above
     *
     * @param into the mode to reboot into, currently supported: bootloader, recovery, leave it
     *         null for a plain reboot
     * @return <code>true</code> if the device rebooted, <code>false</code> if not successful or
     *          unsupported
     * @throws DeviceNotAvailableException
     */
    private boolean doAdbFrameworkReboot(final String into) throws DeviceNotAvailableException {
        // use framework reboot when:
        // 1. device API level >= 18
        // 2. has adb root
        // 3. framework is running
        if (!isEnableAdbRoot()) {
            CLog.i("framework reboot is not supported; when enable root is disabled");
            return false;
        }
        enableAdbRoot();
        if (getApiLevel() >= 18 && isAdbRoot()) {
            try {
                // check framework running
                String output = executeShellCommand("pm path android");
                if (output == null || !output.contains("package:")) {
                    CLog.v("framework reboot: can't detect framework running");
                    return false;
                }
                String command = "svc power reboot";
                if (into != null && !into.isEmpty()) {
                    command = String.format("%s %s", command, into);
                }
                executeShellCommand(command);
            } catch (DeviceUnresponsiveException due) {
                CLog.v("framework reboot: device unresponsive to shell command, using fallback");
                return false;
            }
            return waitForDeviceNotAvailable(30 * 1000);
        } else {
            CLog.v("framework reboot: not supported");
            return false;
        }
    }

    /**
     * Perform a adb reboot.
     *
     * @param into the bootloader name to reboot into, or <code>null</code> to just reboot the
     *            device.
     * @throws DeviceNotAvailableException
     */
    private void doAdbReboot(final String into) throws DeviceNotAvailableException {
        // emulator doesn't support reboot, try just resetting framework and hoping for the best
        if (getIDevice().isEmulator()) {
            CLog.i("since emulator, performing shell stop & start instead of reboot");
            executeShellCommand("stop");
            executeShellCommand(String.format("setprop %s 0",
                    DeviceStateMonitor.BOOTCOMPLETE_PROP));
            executeShellCommand("start");
            return;
        }
        if (!doAdbFrameworkReboot(into)) {
            DeviceAction rebootAction = new DeviceAction() {
                @Override
                public boolean run() throws TimeoutException, IOException,
                        AdbCommandRejectedException {
                    getIDevice().reboot(into);
                    return true;
                }
            };
            performDeviceAction("reboot", rebootAction, MAX_RETRY_ATTEMPTS);
        }
    }

    private void waitForDeviceNotAvailable(String operationDesc, long time) {
        // TODO: a bit of a race condition here. Would be better to start a
        // before the operation
        if (!mStateMonitor.waitForDeviceNotAvailable(time)) {
            // above check is flaky, ignore till better solution is found
            CLog.w("Did not detect device %s becoming unavailable after %s", getSerialNumber(),
                    operationDesc);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        // adb root is a relatively intensive command, so do a brief check first to see
        // if its necessary or not
        if (isAdbRoot()) {
            CLog.i("adb is already running as root on %s", getSerialNumber());
            return true;
        }
        // Don't enable root if user requested no root
        if (!isEnableAdbRoot()) {
            CLog.i("\"enable-root\" set to false; ignoring 'adb root' request");
            return false;
        }
        CLog.i("adb root on device %s", getSerialNumber());
        int attempts = MAX_RETRY_ATTEMPTS + 1;
        for (int i=1; i <= attempts; i++) {
            String output = executeAdbCommand("root");
            // wait for device to disappear from adb
            waitForDeviceNotAvailable("root", 20 * 1000);
            // wait for device to be back online
            waitForDeviceOnline();

            if (isAdbRoot()) {
                return true;
            }
            CLog.w("'adb root' on %s unsuccessful on attempt %d of %d. Output: '%s'",
                    getSerialNumber(), i, attempts, output);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdbRoot() throws DeviceNotAvailableException {
        String output = executeShellCommand("id");
        return output.contains("uid=0(root)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean encryptDevice(boolean inplace) throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't encrypt device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (isDeviceEncrypted()) {
            CLog.d("Device %s is already encrypted, skipping", getSerialNumber());
            return true;
        }

        enableAdbRoot();

        String encryptMethod;
        long timeout;
        if (inplace) {
            encryptMethod = "inplace";
            timeout = ENCRYPTION_INPLACE_TIMEOUT_MIN;
        } else {
            encryptMethod = "wipe";
            timeout = ENCRYPTION_WIPE_TIMEOUT_MIN;
        }

        CLog.i("Encrypting device %s via %s", getSerialNumber(), encryptMethod);

        // enable crypto takes one of the following formats:
        // cryptfs enablecrypto <wipe|inplace> <passwd>
        // cryptfs enablecrypto <wipe|inplace> default|password|pin|pattern [passwd]
        // Try the first one first, if it outputs "500 0 Usage: ...", try the second.
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        String command = String.format("vdc cryptfs enablecrypto %s \"%s\"", encryptMethod,
                ENCRYPTION_PASSWORD);
        executeShellCommand(command, receiver, timeout, TimeUnit.MINUTES, 1);
        if (receiver.getOutput().startsWith("500 0 Usage:")) {
            command = String.format("vdc cryptfs enablecrypto %s default", encryptMethod);
            executeShellCommand(command, new NullOutputReceiver(), timeout, TimeUnit.MINUTES, 1);
        }

        waitForDeviceNotAvailable("reboot", getCommandTimeout());
        waitForDeviceOnline();  // Device will not become available until the user data is unlocked.

        return isDeviceEncrypted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unencryptDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't unencrypt device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (!isDeviceEncrypted()) {
            CLog.d("Device %s is already unencrypted, skipping", getSerialNumber());
            return true;
        }

        CLog.i("Unencrypting device %s", getSerialNumber());

        // If the device supports fastboot format, then we're done.
        if (!mOptions.getUseFastbootErase()) {
            rebootIntoBootloader();
            fastbootWipePartition("userdata");
            rebootUntilOnline();
            waitForDeviceAvailable(ENCRYPTION_WIPE_TIMEOUT_MIN * 60 * 1000);
            return true;
        }

        // Determine if we need to format partition instead of wipe.
        boolean format = false;
        String output = executeShellCommand("vdc volume list");
        String[] splitOutput;
        if (output != null) {
            splitOutput = output.split("\r?\n");
            for (String line : splitOutput) {
                if (line.startsWith("110 ") && line.contains("sdcard /mnt/sdcard") &&
                        !line.endsWith("0")) {
                    format = true;
                }
            }
        }

        rebootIntoBootloader();
        fastbootWipePartition("userdata");

        // If the device requires time to format the filesystem after fastboot erase userdata, wait
        // for the device to reboot a second time.
        if (mOptions.getUnencryptRebootTimeout() > 0) {
            rebootUntilOnline();
            if (waitForDeviceNotAvailable(mOptions.getUnencryptRebootTimeout())) {
                waitForDeviceOnline();
            }
        }

        if (format) {
            CLog.d("Need to format sdcard for device %s", getSerialNumber());

            RecoveryMode cachedRecoveryMode = getRecoveryMode();
            setRecoveryMode(RecoveryMode.ONLINE);

            output = executeShellCommand("vdc volume format sdcard");
            if (output == null) {
                CLog.e("Command vdc volume format sdcard failed will no output for device %s:\n%s",
                        getSerialNumber());
                setRecoveryMode(cachedRecoveryMode);
                return false;
            }
            splitOutput = output.split("\r?\n");
            if (!splitOutput[splitOutput.length - 1].startsWith("200 ")) {
                CLog.e("Command vdc volume format sdcard failed for device %s:\n%s",
                        getSerialNumber(), output);
                setRecoveryMode(cachedRecoveryMode);
                return false;
            }

            setRecoveryMode(cachedRecoveryMode);
        }

        reboot();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unlockDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't unlock device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (!isDeviceEncrypted()) {
            CLog.d("Device %s is not encrypted, skipping", getSerialNumber());
            return true;
        }

        CLog.i("Unlocking device %s", getSerialNumber());

        enableAdbRoot();

        // FIXME: currently, vcd checkpw can return an empty string when it never should.  Try 3
        // times.
        String output;
        int i = 0;
        do {
            // Enter the password. Output will be:
            // "200 [X] -1" if the password has already been entered correctly,
            // "200 [X] 0" if the password is entered correctly,
            // "200 [X] N" where N is any positive number if the password is incorrect,
            // any other string if there is an error.
            output = executeShellCommand(String.format("vdc cryptfs checkpw \"%s\"",
                    ENCRYPTION_PASSWORD)).trim();

            if (output.startsWith("200 ") && output.endsWith(" -1")) {
                return true;
            }

            if (!output.isEmpty() && !(output.startsWith("200 ") && output.endsWith(" 0"))) {
                CLog.e("checkpw gave output '%s' while trying to unlock device %s",
                        output, getSerialNumber());
                return false;
            }

            getRunUtil().sleep(500);
        } while (output.isEmpty() && ++i < 3);

        if (output.isEmpty()) {
            CLog.e("checkpw gave no output while trying to unlock device %s");
        }

        // Restart the framework. Output will be:
        // "200 [X] 0" if the user data partition can be mounted,
        // "200 [X] -1" if the user data partition can not be mounted (no correct password given),
        // any other string if there is an error.
        output = executeShellCommand("vdc cryptfs restart").trim();

        if (!(output.startsWith("200 ") &&  output.endsWith(" 0"))) {
            CLog.e("restart gave output '%s' while trying to unlock device %s", output,
                    getSerialNumber());
            return false;
        }

        waitForDeviceAvailable();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
        String output = getPropertySync("ro.crypto.state");

        if (output == null && isEncryptionSupported()) {
            CLog.w("Property ro.crypto.state is null on device %s", getSerialNumber());
        }

        return "encrypted".equals(output);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
        if (!isEnableAdbRoot()) {
            CLog.i("root is required for encryption");
            mIsEncryptionSupported = false;
            return mIsEncryptionSupported;
        }
        if (mIsEncryptionSupported != null) {
            return mIsEncryptionSupported.booleanValue();
        }
        enableAdbRoot();
        String output = executeShellCommand("vdc cryptfs enablecrypto").trim();
        mIsEncryptionSupported = (output != null && output.startsWith(ENCRYPTION_SUPPORTED_CODE) &&
                output.contains(ENCRYPTION_SUPPORTED_USAGE));
        return mIsEncryptionSupported;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline(long waitTime) throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceOnline(waitTime) == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceOnline() == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceAvailable(waitTime) == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceAvailable() throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceAvailable() == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceNotAvailable(long waitTime) {
        return mStateMonitor.waitForDeviceNotAvailable(waitTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceInRecovery(long waitTime) {
        return mStateMonitor.waitForDeviceInRecovery(waitTime);
    }

    /**
     * Small helper function to throw an NPE if the passed arg is null.  This should be used when
     * some value will be stored and used later, in which case it'll avoid hard-to-trace
     * asynchronous NullPointerExceptions by throwing the exception synchronously.  This is not
     * intended to be used where the NPE would be thrown synchronously -- just let the jvm take care
     * of it in that case.
     */
    private void throwIfNull(Object obj) {
        if (obj == null) throw new NullPointerException();
    }

    /**
     * Retrieve this device's recovery mechanism.
     * <p/>
     * Exposed for unit testing.
     */
    IDeviceRecovery getRecovery() {
        return mRecovery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecovery(IDeviceRecovery recovery) {
        throwIfNull(recovery);
        mRecovery = recovery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecoveryMode(RecoveryMode mode) {
        throwIfNull(mRecoveryMode);
        mRecoveryMode = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecoveryMode getRecoveryMode() {
        return mRecoveryMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean fastbootEnabled) {
        mFastbootEnabled = fastbootEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceState(final TestDeviceState deviceState) {
        if (!deviceState.equals(getDeviceState())) {
            // disable state changes while fastboot lock is held, because issuing fastboot command
            // will disrupt state
            if (getDeviceState().equals(TestDeviceState.FASTBOOT) && mFastbootLock.isLocked()) {
                return;
            }
            mState = deviceState;
            CLog.d("Device %s state is now %s", getSerialNumber(), deviceState);
            mStateMonitor.setState(deviceState);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceState getDeviceState() {
        return mState;
    }

    @Override
    public boolean isAdbTcp() {
        return mStateMonitor.isAdbTcp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String switchToAdbTcp() throws DeviceNotAvailableException {
        String ipAddress = getIpAddress();
        if (ipAddress == null) {
            CLog.e("connectToTcp failed: Device %s doesn't have an IP", getSerialNumber());
            return null;
        }
        String port = "5555";
        executeAdbCommand("tcpip", port);
        // TODO: analyze result? wait for device offline?
        return String.format("%s:%s", ipAddress, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchToAdbUsb() throws DeviceNotAvailableException {
        executeAdbCommand("usb");
        // TODO: analyze result? wait for device offline?
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEmulatorProcess(Process p) {
        mEmulatorProcess = p;

    }

    /**
     * For emulator set {@link SizeLimitedOutputStream} to log output
     * @param output to log the output
     */
    public void setEmulatorOutputStream(SizeLimitedOutputStream output) {
        mEmulatorOutput = output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopEmulatorOutput() {
        if (mEmulatorOutput != null) {
            mEmulatorOutput.delete();
            mEmulatorOutput = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getEmulatorOutput() {
        if (getIDevice().isEmulator()) {
            if (mEmulatorOutput == null) {
                CLog.w("Emulator output for %s was not captured in background",
                        getSerialNumber());
            } else {
                try {
                    return new SnapshotInputStreamSource(mEmulatorOutput.getData());
                } catch (IOException e) {
                    CLog.e("Failed to get %s data.", getSerialNumber());
                    CLog.e(e);
                }
            }
        }
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process getEmulatorProcess() {
        return mEmulatorProcess;
    }

    /**
     * @return <code>true</code> if adb root should be enabled on device
     */
    public boolean isEnableAdbRoot() {
        return mOptions.isEnableAdbRoot();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException {
        return getInstalledPackageNames(new PkgFilter() {
            @Override
            public boolean accept(String pkgName, String apkPath) {
                return true;
            }
        });
    }

    /**
     * A {@link DeviceAction} for retrieving package system service info, and do retries on
     * failures.
     */
    private class DumpPkgAction implements DeviceAction {

        Map<String, PackageInfo> mPkgInfoMap;

        DumpPkgAction() {
        }

        @Override
        public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException, InstallException, SyncException {
            DumpsysPackageReceiver receiver = new DumpsysPackageReceiver();
            getIDevice().executeShellCommand("dumpsys package p", receiver);
            mPkgInfoMap = receiver.getPackages();
            if (mPkgInfoMap.size() == 0) {
                // Package parsing can fail if package manager is currently down. throw exception
                // to retry
                CLog.w("no packages found from dumpsys package p.");
                throw new IOException();
            }
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException {
        DumpPkgAction action = new DumpPkgAction();
        performDeviceAction("dumpsys package p", action, MAX_RETRY_ATTEMPTS);

        Set<String> pkgs = new HashSet<String>();
        for (PackageInfo pkgInfo : action.mPkgInfoMap.values()) {
            if (!pkgInfo.isSystemApp() || pkgInfo.isUpdatedSystemApp()) {
                CLog.d("Found uninstallable package %s", pkgInfo.getPackageName());
                pkgs.add(pkgInfo.getPackageName());
            }
        }
        return pkgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException {
        DumpPkgAction action = new DumpPkgAction();
        performDeviceAction("dumpsys package", action, MAX_RETRY_ATTEMPTS);
        return action.mPkgInfoMap.get(packageName);
    }

    private static interface PkgFilter {
        boolean accept(String pkgName, String apkPath);
    }

    // TODO: convert this to use DumpPkgAction
    private Set<String> getInstalledPackageNames(PkgFilter filter)
            throws DeviceNotAvailableException {
        Set<String> packages= new HashSet<String>();
        String output = executeShellCommand(LIST_PACKAGES_CMD);
        if (output != null) {
            Matcher m = PACKAGE_REGEX.matcher(output);
            while (m.find()) {
                String packagePath = m.group(1);
                String packageName = m.group(2);
                if (filter.accept(packageName, packagePath)) {
                    packages.add(packageName);
                }
            }
        }
        return packages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceOptions getOptions() {
        return mOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getApiLevel() throws DeviceNotAvailableException {
        int apiLevel = UNKNOWN_API_LEVEL;
        try {
            String prop = getProperty("ro.build.version.sdk");
            apiLevel = Integer.parseInt(prop);
        } catch (NumberFormatException nfe) {
            // ignore, return unknown instead
        }
        return apiLevel;
    }

    @Override
    public IDeviceStateMonitor getMonitor() {
        return mStateMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceShell(long waitTime) {
        return mStateMonitor.waitForDeviceShell(waitTime);
    }

    @Override
    public DeviceAllocationState getAllocationState() {
        return mAllocationState;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Process the DeviceEvent, which may or may not transition this device to a new allocation
     * state.
     * </p>
     */
    @Override
    public DeviceEventResponse handleAllocationEvent(DeviceEvent event) {

        // keep track of whether state has actually changed or not
        boolean stateChanged = false;
        DeviceAllocationState newState;
        DeviceAllocationState oldState = mAllocationState;
        mAllocationStateLock.lock();
        try {
            // update oldState here, just in case in changed before we got lock
            oldState = mAllocationState;
            newState = mAllocationState.handleDeviceEvent(event);
            if (oldState != newState) {
                // state has changed! record this fact, and store the new state
                stateChanged = true;
                mAllocationState = newState;
            }
        } finally {
            mAllocationStateLock.unlock();
        }
        if (stateChanged && mAllocationMonitor != null) {
            // state has changed! Lets inform the allocation monitor listener
            mAllocationMonitor.notifyDeviceStateChange(getSerialNumber(), oldState, newState);
        }
        return new DeviceEventResponse(newState, stateChanged);
    }

    private long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
        String deviceTimeString = executeShellCommand("date +%s");
        Long deviceTime = null;
        long offset = 0;

        try {
            deviceTime = Long.valueOf(deviceTimeString.trim());
        } catch (NumberFormatException nfe) {
            CLog.i("Invalid device time: \"%s\", ignored.");
            return 0;
        }
        if (date == null) {
            date = new Date();
        }

        offset = date.getTime() - deviceTime * 1000;
        CLog.d("Time offset = " + offset);
        return offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDate(Date date) throws DeviceNotAvailableException {
        if (date == null) {
            date = new Date();
        }
        long timeOffset = getDeviceTimeOffset(date);
        // no need to set date
        if (Math.abs(timeOffset) <= MAX_HOST_DEVICE_TIME_OFFSET) {
            return;
        }
        String dateString = null;
        if (getApiLevel() < 23) {
            // set date in epoch format
            dateString = Long.toString(date.getTime() / 1000); //ms to s
        } else {
            // set date with POSIX like params
            SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "MMddHHmmyyyy.ss");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            dateString = sdf.format(date);
        }
        // best effort, no verification
        executeShellCommand("date -u " + dateString);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForBootComplete(long timeOut) throws DeviceNotAvailableException {
        return mStateMonitor.waitForBootComplete(timeOut);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        ArrayList<String[]> users = tokenizeListUsers();
        if (users == null) {
            return null;
        }
        ArrayList<Integer> userIds = new ArrayList<Integer>(users.size());
        for (String[] user : users) {
            userIds.add(Integer.parseInt(user[1]));
        }
        return userIds;
    }

    /**
     * Tokenizes the output of 'pm list users'.
     * The returned tokens for each user have the form: {"\tUserInfo", Integer.toString(id), name,
     * Integer.toHexString(flag), "[running]"}; (the last one being optional)
     * @return a list of arrays of strings, each element of the list representing the tokens
     * for a user, or {@code null} if there was an error while tokenizing the adb command output.
     */
    private ArrayList<String[]> tokenizeListUsers() throws DeviceNotAvailableException {
        String command = "pm list users";
        String commandOutput = executeShellCommand(command);
        // Extract the id of all existing users.
        String[] lines = commandOutput.split("\\r?\\n");
        if (lines.length < 1) {
            CLog.e("%s should contain at least one line", commandOutput);
            return null;
        }
        if (!lines[0].equals("Users:")) {
            CLog.e("%s in not a valid output for 'pm list users'", commandOutput);
            return null;
        }
        ArrayList<String[]> users = new ArrayList<String[]>(lines.length - 1);
        for (int i = 1; i < lines.length; i++) {
            // Individual user is printed out like this:
            // \tUserInfo{$id$:$name$:$Integer.toHexString(flags)$} [running]
            String[] tokens = lines[i].split("\\{|\\}|:");
            if (tokens.length != 4 && tokens.length != 5) {
                CLog.e("%s doesn't contain 4 or 5 tokens", lines[i]);
                return null;
            }
            users.add(tokens);
        }
        return users;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException {
        String command = "pm get-max-users";
        String commandOutput = executeShellCommand(command);
        try {
            return Integer.parseInt(commandOutput.substring(commandOutput.lastIndexOf(" ")).trim());
        } catch (NumberFormatException e) {
            CLog.e("Failed to parse result: %s", commandOutput);
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultiUserSupported() throws DeviceNotAvailableException {
        return getMaxNumberOfUsersSupported() > 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name) throws DeviceNotAvailableException, IllegalStateException {
        final String output = executeShellCommand(String.format("pm create-user %s", name));
        if (output.startsWith("Success")) {
            try {
                return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", output);
            }
        } else {
            CLog.e("Failed to create user: %s", output);
        }
        throw new IllegalStateException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeUser(int userId) throws DeviceNotAvailableException {
        final String output = executeShellCommand(String.format("pm remove-user %s", userId));
        if (output.startsWith("Error")) {
            CLog.e("Failed to remove user: %s", output);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startUser(int userId) throws DeviceNotAvailableException {
        final String output = executeShellCommand(String.format("am start-user %s", userId));
        if (output.startsWith("Error")) {
            CLog.e("Failed to start user: %s", output);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopUser(int userId) throws DeviceNotAvailableException {
        // No error or status code is returned.
        executeShellCommand(String.format("am stop-user %s", userId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remountSystemWritable() throws DeviceNotAvailableException {
        String verity = getProperty("partition.system.verified");
        // have the property set (regardless state) implies verity is enabled, so we send adb
        // command to disable verity
        if (verity != null && !verity.isEmpty()) {
            executeAdbCommand("disable-verity");
            reboot();
        }
        executeAdbCommand("remount");
        waitForDeviceAvailable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getPrimaryUserId() throws DeviceNotAvailableException {
        ArrayList<String[]> users = tokenizeListUsers();
        if (users == null) {
            return null;
        }
        for (String[] user : users) {
            int flag = Integer.parseInt(user[3], 16);
            if ((flag & FLAG_PRIMARY) != 0) {
                return Integer.parseInt(user[1]);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildSigningKeys() throws DeviceNotAvailableException {
        String buildTags = getProperty(BUILD_TAGS);
        if (buildTags != null) {
            String[] tags = buildTags.split(",");
            for (String tag : tags) {
                Matcher m = KEYS_PATTERN.matcher(tag);
                if (m.matches()) {
                    return tag;
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceClass() {
        IDevice device = getIDevice();
        if (device == null) {
            CLog.w("No IDevice instance, cannot determine device class.");
            return "";
        }
        return device.getClass().getSimpleName();
    }
}
