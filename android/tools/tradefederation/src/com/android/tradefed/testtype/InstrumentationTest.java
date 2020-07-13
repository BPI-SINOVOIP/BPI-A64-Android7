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

package com.android.tradefed.testtype;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner.TestSize;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StringEscapeUtils;

import junit.framework.Assert;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Test that runs an instrumentation test package on given device.
 */
@OptionClass(alias = "instrumentation")
public class InstrumentationTest implements IDeviceTest, IResumableTest {

    private static final String LOG_TAG = "InstrumentationTest";

    /** max number of attempts to collect list of tests in package */
    private static final int COLLECT_TESTS_ATTEMPTS = 3;
    /** instrumentation test runner argument key used for test execution using a file */
    private static final String TEST_FILE_INST_ARGS_KEY = "testFile";

    static final String DELAY_MSEC_ARG = "delay_msec";
    /** instrumentation test runner argument key used for individual test timeout */
    static final String TEST_TIMEOUT_INST_ARGS_KEY = "timeout_msec";

    @Option(name = "package", shortName = 'p',
            description="The manifest package name of the Android test application to run.",
            importance = Importance.IF_UNSET)
    private String mPackageName = null;

    @Option(name = "runner",
            description="The instrumentation test runner class name to use.")
    private String mRunnerName = "android.test.InstrumentationTestRunner";

    @Option(name = "class", shortName = 'c',
            description="The test class name to run.")
    private String mTestClassName = null;

    @Option(name = "method", shortName = 'm',
            description="The test method name to run.")
    private String mTestMethodName = null;

    @Option(name = "test-package",
            description="Only run tests within this specific java package. " +
            "Will be ignored if --class is set.")
    private String mTestPackageName = null;

    @Deprecated
    @Option(name = "timeout",
            description="Deprecated - Use \"shell-timeout\" or \"test-timeout\" instead.")
    private Integer mTimeout = null;

    @Option(name = "shell-timeout",
            description="The defined timeout (in milliseconds) is used as a maximum waiting time "
                    + "when expecting the command output from the device. At any time, if the "
                    + "shell command does not output anything for a period longer than defined "
                    + "timeout the TF run terminates. For no timeout, set to 0.")
    private long mShellTimeout = 10 * 60 * 1000;  // default to 10 minutes

    @Option(name = "test-timeout",
            description="Sets timeout (in milliseconds) that will be applied to each test. In the "
                    + "event of a test timeout it will log the results and proceed with executing "
                    + "the next test. For no timeout, set to 0.")
    private int mTestTimeout = 5 * 60 * 1000;  // default to 5 minutes

    @Option(name = "size",
            description="Restrict test to a specific test size.")
    private String mTestSize = null;

    @Option(name = "rerun",
            description = "Rerun unexecuted tests individually on same device if test run " +
            "fails to complete.")
    private boolean mIsRerunMode = true;

    @Option(name = "resume",
            description = "Schedule unexecuted tests for resumption on another device " +
            "if first device becomes unavailable.")
    private boolean mIsResumeMode = false;

    @Option(name = "log-delay",
            description="Delay in msec between each test when collecting test information.")
    private int mTestDelay = 15;

    @Option(name = "install-file",
            description="Optional file path to apk file that contains the tests.")
    private File mInstallFile = null;

    @Option(name = "run-name",
            description="Optional custom test run name to pass to listener. " +
            "If unspecified, will use package name.")
    private String mRunName = null;

    @Option(name = "instrumentation-arg",
            description = "Additional instrumentation arguments to provide.")
    private Map<String, String> mInstrArgMap = new HashMap<String, String>();

    @Option(name = "bugreport-on-failure", description = "Sets which failed testcase events " +
            "cause a bugreport to be collected. a bugreport after failed testcases.  Note that " +
            "there is _no feedback mechanism_ between the test runner and the bugreport " +
            "collector, so use the EACH setting with due caution.")
    private BugreportCollector.Freq mBugreportFrequency = null;

    @Option(name = "screenshot-on-failure", description = "Take a screenshot on every test failure")
    private boolean mScreenshotOnFailure = false;

    @Option(name = "logcat-on-failure", description =
            "take a logcat snapshot on every test failure.")
    private boolean mLogcatOnFailure = false;

    @Option(name = "logcat-on-failure-size", description =
            "The max number of logcat data in bytes to capture when --logcat-on-failure is on. " +
            "Should be an amount that can comfortably fit in memory.")
    private int mMaxLogcatBytes = 500 * 1024; // 500K

    @Option(name = "rerun-from-file", description =
            "Use test file instead of separate adb commands for each test " +
            "when re-running instrumentations for tests that failed to run in previous attempts. ")
    private boolean mReRunUsingTestFile = false;

    @Option(name = "rerun-from-file-attempts", description =
            "Max attempts to rerun tests from file. -1 means rerun from file infinitely.")
    private int mReRunUsingTestFileAttempts = -1;

    @Option(name = "fallback-to-serial-rerun", description =
            "Rerun tests serially after rerun from file failed.")
    private boolean mFallbackToSerialRerun = true;

    @Option(name = "reboot-before-rerun", description =
            "Reboot a device before re-running instrumentations.")
    private boolean mRebootBeforeReRun = false;

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    private ITestDevice mDevice = null;

    private IRemoteAndroidTestRunner mRunner;

    private Collection<TestIdentifier> mRemainingTests = null;

    private String mCoverageTarget = null;

    private String mTestFilePathOnDevice = null;

    /**
     * Max time in ms to allow for the 'max time to shell output response' when collecting tests.
     * TODO: currently the collect tests command may take a long time to even start, so this is set
     * to a overly generous value
     */
    private int mCollectTestsShellTimeout = 2 * 60 * 1000;

    private boolean mForceBatchMode = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * Set the Android manifest package to run.
     */
    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    /**
     * Optionally, set the Android instrumentation runner to use.
     */
    public void setRunnerName(String runnerName) {
        mRunnerName = runnerName;
    }

    /**
     * Gets the Android instrumentation runner to be used.
     */
    public String getRunnerName() {
        return mRunnerName;
    }

    /**
     * Optionally, set the test class name to run.
     */
    public void setClassName(String testClassName) {
        mTestClassName = testClassName;
    }

    /**
     * Optionally, set the test method to run.
     */
    public void setMethodName(String testMethodName) {
        mTestMethodName = StringEscapeUtils.escapeShell(testMethodName);
    }

    /**
     * Optionally, set the path to a file located on the device that should contain a list of line
     * separated test classes and methods (format: com.foo.Class#method) to be run.
     * If set, will automatically attempt to re-run tests using this test file
     * via {@link InstrumentationFileTest} instead of executing separate adb commands for each
     * remaining test via {@link InstrumentationSerialTest}"
     */
    public void setTestFilePathOnDevice(String testFilePathOnDevice) {
        mTestFilePathOnDevice = testFilePathOnDevice;
    }

    /**
     * Optionally, set the test size to run.
     */
    public void setTestSize(String size) {
        mTestSize = size;
    }

    /**
     * Get the Android manifest package to run.
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Get the custom test run name that will be provided to listener
     */
    public String getRunName() {
        return mRunName;
    }

    /**
     * Set the custom test run name that will be provided to listener
     */
    public void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * Set the collection of tests that should be executed by this InstrumentationTest.
     *
     * @param tests the tests to run
     * @param forceBatchMode if true, the first attempt to run the tests will proceed as normal
     * with the InstrumentationTest attempting to run all tests in the package. If false, the given
     * tests will be run one by one with separate adb commands.
     */
    public void setTestsToRun(Collection<TestIdentifier> tests, boolean forceBatchMode) {
        mRemainingTests = tests;
        mForceBatchMode = forceBatchMode;
    }

    /**
     * Get the class name to run.
     */
    String getClassName() {
        return mTestClassName;
    }

    /**
     * Get the test method to run.
     */
    String getMethodName() {
        return mTestMethodName;
    }

    /**
     * Get the path to a file that contains class#method combinations to be run
     */
    String getTestFilePathOnDevice() {
        return mTestFilePathOnDevice;
    }

    /**
     * Get the test java package to run.
     */
    String getTestPackageName() {
        return mTestPackageName;
    }

    /**
     * Sets the test package filter.
     * <p/>
     * If non-null, only tests within the given java package will be executed.
     * <p/>
     * Will be ignored if a non-null value has been provided to {@link #setClassName(String)}
     */
    public void setTestPackageName(String testPackageName) {
        mTestPackageName = testPackageName;
    }

    /**
     * Get the test size to run. Returns <code>null</code> if no size has been set.
     */
    String getTestSize() {
        return mTestSize;
    }

    /**
     * Optionally, set the maximum time (in milliseconds) expecting shell output from the device.
     */
    public void setShellTimeout(long timeout) {
        mShellTimeout = timeout;
    }

    /**
     * Optionally, set the maximum time (in milliseconds) for each individual test run.
     */
    public void setTestTimeout(int timeout) {
        mTestTimeout = timeout;
    }

    /**
     * Set the coverage target of this test.
     * <p/>
     * Currently unused. This method is just present so coverageTarget can be later retrieved via
     * {@link #getCoverageTarget()}
     */
    public void setCoverageTarget(String coverageTarget) {
        mCoverageTarget = coverageTarget;
    }

    /**
     * Get the coverageTarget previously set via {@link #setCoverageTarget(String)}.
     */
    public String getCoverageTarget() {
        return mCoverageTarget;
    }

    /**
     * Return <code>true</code> if rerun mode is on.
     */
    boolean isRerunMode() {
        return mIsRerunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        // hack to not resume if tests were never run
        // TODO: fix this properly in TestInvocation
        if (mRemainingTests == null) {
            return false;
        }
        return mIsResumeMode;
    }

    /**
     * Optionally, set the rerun mode.
     */
    public void setRerunMode(boolean rerun) {
        mIsRerunMode = rerun;
    }

    /**
     * Optionally, set the resume mode.
     */
    public void setResumeMode(boolean resume) {
        mIsResumeMode = resume;
    }

    /**
     * Get the shell timeout in ms.
     */
    long getShellTimeout() {
        return mShellTimeout;
    }

    /**
     * Get the test timeout in ms.
     */
    int getTestTimeout() {
        return mTestTimeout;
    }

    /**
     * Get the delay in ms between each test when collecting test info.
     */
    long getTestDelay() {
        return mTestDelay;
    }

    /**
     * Set the optional file to install that contains the tests.
     *
     * @param installFile the installable {@link File}
     */
    public void setInstallFile(File installFile) {
        mInstallFile = installFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
    /**
     * Set the max time in ms to allow for the 'max time to shell output response' when collecting
     * tests.
     * <p/>
     * Exposed for testing.
     */
    public void setCollectsTestsShellTimeout(int timeout) {
        mCollectTestsShellTimeout = timeout;
    }

    /**
     * Set the frequency with which to automatically collect bugreports after test failures.
     * <p />
     * Note that there is _no feedback mechanism_ between the test runner and the bugreport
     * collector, so use the EACH setting with due caution: if a large quantity of failures happen
     * in rapid succession, the bugreport for a given one of the failures could end up being
     * collected tens of minutes or hours after the respective failure occurred.
     */
    public void setBugreportFrequency(BugreportCollector.Freq freq) {
        mBugreportFrequency = freq;
    }

    /**
     * Add an argument to provide when running the instrumentation tests
     *
     * @param key the argument name
     * @param value the argument value
     */
    public void addInstrumentationArg(String key, String value) {
        mInstrArgMap.put(key, value);
    }


    /**
     * Sets force-abi option.
     * @param abi
     */
    public void setForceAbi(String abi) {
        mForceAbi = abi;
    }

    public String getForceAbi() {
        return mForceAbi;
    }

    public void setScreenshotOnFailure(boolean screenshotOnFailure) {
        mScreenshotOnFailure = screenshotOnFailure;
    }

    public void setLogcatOnFailure(boolean logcatOnFailure) {
        mLogcatOnFailure = logcatOnFailure;
    }

    public void setLogcatOnFailureSize(int logcatOnFailureSize) {
        mMaxLogcatBytes = logcatOnFailureSize;
    }

    public void setReRunUsingTestFile(boolean reRunUsingTestFile) {
        mReRunUsingTestFile = reRunUsingTestFile;
    }

    public void setFallbackToSerialRerun(boolean reRunSerially) {
        mFallbackToSerialRerun = reRunSerially;
    }

    public void setRebootBeforeReRun(boolean rebootBeforeReRun) {
        mRebootBeforeReRun = rebootBeforeReRun;
    }

    /**
     * @return the {@link IRemoteAndroidTestRunner} to use.
     * @throws DeviceNotAvailableException
     */
    IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName, String runnerName,
            IDevice device) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                packageName, runnerName, device);
        if (mForceAbi != null && !mForceAbi.isEmpty()) {
            String abi = AbiFormatter.getDefaultAbi(mDevice, mForceAbi);
            if (abi != null) {
                runner.setRunOptions(String.format("--abi %s", abi));
            } else {
                throw new RuntimeException(
                        String.format("Cannot find abi for force-abi %s", mForceAbi));
            }
        }
        return runner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mPackageName == null) {
            throw new IllegalArgumentException("package name has not been set");
        }
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        mRunner = createRemoteAndroidTestRunner(mPackageName, mRunnerName,
                mDevice.getIDevice());
        setRunnerArgs(mRunner);
        if (mInstallFile != null) {
            Assert.assertNull(mDevice.installPackage(mInstallFile, true));
            doTestRun(listener);
            mDevice.uninstallPackage(mPackageName);
        } else {
            doTestRun(listener);
        }
    }

    protected void setRunnerArgs(IRemoteAndroidTestRunner runner) {
        if (mTestClassName != null) {
            if (mTestMethodName != null) {
                runner.setMethodName(mTestClassName, mTestMethodName);
            } else {
                runner.setClassName(mTestClassName);
            }
        } else if (mTestPackageName != null) {
            runner.setTestPackageName(mTestPackageName);
        }
        if (mTestFilePathOnDevice != null) {
            addInstrumentationArg(TEST_FILE_INST_ARGS_KEY, mTestFilePathOnDevice);
        }
        if (mTestSize != null) {
            runner.setTestSize(TestSize.getTestSize(mTestSize));
        }
        addTimeoutsToRunner(runner);
        if (mRunName != null) {
            runner.setRunName(mRunName);
        }
        for (Map.Entry<String, String> argEntry : mInstrArgMap.entrySet()) {
            runner.addInstrumentationArg(argEntry.getKey(), argEntry.getValue());
        }
    }

    /**
     * Helper method to add test-timeout & shell-timeout timeouts to  given runner
     */
    private void addTimeoutsToRunner(IRemoteAndroidTestRunner runner) {
        if (mTimeout != null) {
            CLog.w("\"timeout\" argument is deprecated and should not be used! \"shell-timeout\""
                    + " argument value is overwritten with %d ms", mTimeout);
            setShellTimeout(mTimeout);
        }
        if (mTestTimeout < 0) {
            throw new IllegalArgumentException(
                    String.format("test-timeout %d cannot be negative", mTestTimeout));
        }
        if (mShellTimeout <= mTestTimeout) {
            // set shell timeout to 110% of test timeout
            mShellTimeout = mTestTimeout + mTestTimeout / 10;
            CLog.w(String.format("shell-timeout should be larger than test-timeout %d; "
                    + "NOTE: extending shell-timeout to %d, please consider fixing this!",
                    mTestTimeout, mShellTimeout));
        }
        runner.setMaxTimeToOutputResponse(mShellTimeout, TimeUnit.MILLISECONDS);
        addInstrumentationArg(TEST_TIMEOUT_INST_ARGS_KEY, Long.toString(mTestTimeout));
    }

    /**
     * Execute test run.
     *
     * @param listener the test result listener
     * @throws DeviceNotAvailableException if device stops communicating
     */
    private void doTestRun(ITestInvocationListener listener)
            throws DeviceNotAvailableException {

        if (mRemainingTests != null && !mForceBatchMode) {
            // have remaining tests! This must be a rerun - rerun them individually
            rerunTests(listener);
            return;
        }
        if (mRemainingTests == null) {
            mRemainingTests = collectTestsToRun(mRunner);
        }
        if (mBugreportFrequency != null) {
            // Collect a bugreport after EACH/FIRST failed testcase
            BugreportCollector.Predicate pred = new BugreportCollector.Predicate(
                    BugreportCollector.Relation.AFTER,
                    mBugreportFrequency,
                    BugreportCollector.Noun.FAILED_TESTCASE);
            BugreportCollector collector  = new BugreportCollector(listener, mDevice);
            collector.addPredicate(pred);
            listener = collector;
        }
        if (mScreenshotOnFailure) {
            FailedTestScreenshotGenerator screenListener = new FailedTestScreenshotGenerator(
                    listener, getDevice());
            listener = screenListener;
        }
        if (mLogcatOnFailure) {
            FailedTestLogcatGenerator logcatListener = new FailedTestLogcatGenerator(
                    listener, getDevice(), mMaxLogcatBytes);
            listener = logcatListener;
        }

        if (mRemainingTests == null) {
            // failed to collect the tests or collection is off. Just try to run them all
            mDevice.runInstrumentationTests(mRunner, listener);
        } else if (mRemainingTests.size() != 0) {
            runWithRerun(listener, mRemainingTests);

        } else {
            Log.i(LOG_TAG, String.format("No tests expected for %s, skipping", mPackageName));
        }
    }

    /**
     * Execute the test run, but re-run incomplete tests individually if run fails to complete.
     *
     * @param listener the {@link ITestInvocationListener}
     * @param expectedTests the full set of expected tests in this run.
     */
    private void runWithRerun(final ITestInvocationListener listener,
            Collection<TestIdentifier> expectedTests) throws DeviceNotAvailableException {
        CollectingTestListener testTracker = new CollectingTestListener();
        mRemainingTests = expectedTests;
        try {
            mDevice.runInstrumentationTests(mRunner, new ResultForwarder(listener, testTracker));
        } finally {
            calculateRemainingTests(mRemainingTests, testTracker);
        }
        rerunTests(listener);
    }

    /**
     * Rerun any <var>mRemainingTests</var>
     *
     * @param listener the {@link ITestInvocationListener}
     * @throws DeviceNotAvailableException
     */
    private void rerunTests(final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (mRemainingTests.size() > 0) {
            if (mRebootBeforeReRun) {
                mDevice.reboot();
            }
            if (mReRunUsingTestFile) {
                reRunTestsFromFile(listener);
            } else {
                reRunTestsSerially(listener);
            }
        }
    }

    /**
     * re-runs tests from test file via {@link InstrumentationFileTest}
     */
    private void reRunTestsFromFile(final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.i("Running individual tests using a test file");
        try {
            InstrumentationFileTest testReRunner = new InstrumentationFileTest(this,
                    mRemainingTests, mFallbackToSerialRerun, mReRunUsingTestFileAttempts);
            CollectingTestListener testTracker = new CollectingTestListener();
            try {
                testReRunner.run(new ResultForwarder(listener, testTracker));
            } finally {
                calculateRemainingTests(mRemainingTests, testTracker);
            }
        } catch (ConfigurationException e) {
            CLog.e("Failed to create InstrumentationFileTest: %s", e.getMessage());
        }
    }

    /**
     * re-runs tests one by one via {@link InstrumentationSerialTest}
     */
    private void reRunTestsSerially(final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.i("Running individual tests serially");
        // Since the same runner is reused we must ensure TEST_FILE_INST_ARGS_KEY is not set.
        // Otherwise, the runner will attempt to execute tests from file.
        if (mInstrArgMap != null && mInstrArgMap.containsKey(TEST_FILE_INST_ARGS_KEY)) {
            mInstrArgMap.remove(TEST_FILE_INST_ARGS_KEY);
        }
        try {
            InstrumentationSerialTest testReRunner = new InstrumentationSerialTest(this, mRemainingTests);
            CollectingTestListener testTracker = new CollectingTestListener();
            try {
                testReRunner.run(new ResultForwarder(listener, testTracker));
            } finally {
                calculateRemainingTests(mRemainingTests, testTracker);
            }
        } catch (ConfigurationException e) {
            CLog.e("Failed to create InstrumentationSerialTest: %s", e.getMessage());
        }
    }

    /**
     * Remove the set of tests collected by testTracker from the set of expectedTests
     *
     * @param expectedTests
     * @param testTracker
     */
    private void calculateRemainingTests(Collection<TestIdentifier> expectedTests,
            CollectingTestListener testTracker) {
        expectedTests.removeAll(testTracker.getCurrentRunResults().getCompletedTests());
    }

    /**
     * Collect the list of tests that should be executed by this test run.
     * <p/>
     * This will be done by executing the test run in 'logOnly' mode, and recording the list of
     * tests.
     *
     * @param runner the {@link IRemoteAndroidTestRunner} to use to run the tests.
     * @return a {@link Collection} of {@link TestIdentifier}s that represent all tests to be
     * executed by this run
     * @throws DeviceNotAvailableException
     */
    private Collection<TestIdentifier> collectTestsToRun(final IRemoteAndroidTestRunner runner)
            throws DeviceNotAvailableException {
        if (isRerunMode()) {
            Log.d(LOG_TAG, String.format("Collecting test info for %s on device %s",
                    mPackageName, mDevice.getSerialNumber()));
            runner.setTestCollection(true);
            // try to collect tests multiple times, in case device is temporarily not available
            // on first attempt
            Collection<TestIdentifier>  tests = collectTestsAndRetry(runner);
            // done with "logOnly" mode, restore proper test timeout before real test execution
            addTimeoutsToRunner(runner);
            runner.setTestCollection(false);
            return tests;
        }
        return null;
    }

    /**
     * Performs the actual work of collecting tests, making multiple attempts if necessary
     * @param runner
     * @return the collection of tests, or <code>null</code> if tests could not be collected
     * @throws DeviceNotAvailableException if communication with the device was lost
     */
    private Collection<TestIdentifier> collectTestsAndRetry(final IRemoteAndroidTestRunner runner)
            throws DeviceNotAvailableException {
        boolean communicationFailure = false;
        for (int i=0; i < COLLECT_TESTS_ATTEMPTS; i++) {
            CollectingTestListener listener = new CollectingTestListener();
            boolean instrResult = mDevice.runInstrumentationTests(runner, listener);
            TestRunResult runResults = listener.getCurrentRunResults();
            if (!instrResult || !runResults.isRunComplete()) {
                // communication failure with device, retry
                Log.w(LOG_TAG, String.format(
                        "No results when collecting tests to run for %s on device %s. Retrying",
                        mPackageName, mDevice.getSerialNumber()));
                communicationFailure = true;
            } else if (runResults.isRunFailure()) {
                // not a communication failure, but run still failed.
                // TODO: should retry be attempted
                CLog.w("Run failure %s when collecting tests to run for %s on device %s.",
                        runResults.getRunFailureMessage(), mPackageName,
                        mDevice.getSerialNumber());
                return null;
            } else {
                // success!
                return runResults.getCompletedTests();
            }
        }
        if (communicationFailure) {
            // TODO: find a better way to handle this
            // throwing DeviceUnresponsiveException is not always ideal because a misbehaving
            // instrumentation can hang, even though device is responsive. Would be nice to have
            // a louder signal for this situation though than just logging an error
//            throw new DeviceUnresponsiveException(String.format(
//                    "Communication failure when attempting to collect tests %s on device %s",
//                    mPackageName, mDevice.getSerialNumber()));
            CLog.w("Ignoring repeated communication failure when collecting tests %s for device %s",
                    mPackageName, mDevice.getSerialNumber());
        }
        CLog.e("Failed to collect tests to run for %s on device %s.",
                mPackageName, mDevice.getSerialNumber());
        return null;
    }

    /**
     * A {@link ResultForwarder} that will forward a screenshot on test failures.
     */
    private static class FailedTestScreenshotGenerator extends ResultForwarder {
        private ITestDevice mDevice;

        public FailedTestScreenshotGenerator(ITestInvocationListener listener,
                ITestDevice device) {
            super(listener);
            mDevice = device;
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            super.testFailed(test, trace);

            try {
                InputStreamSource screenSource = mDevice.getScreenshot();
                super.testLog(String.format("screenshot-%s_%s", test.getClassName(),
                        test.getTestName()), LogDataType.PNG, screenSource);
                screenSource.cancel();
            } catch (DeviceNotAvailableException e) {
                // TODO: rethrow this somehow
                CLog.e("Device %s became unavailable while capturing screenshot, %s",
                        mDevice.getSerialNumber(), e.toString());
            }
        }
    }

    /**
     * A {@link ResultForwarder} that will forward a logcat snapshot on each failed test.
     */
    private static class FailedTestLogcatGenerator extends ResultForwarder {
        private ITestDevice mDevice;
        private int mNumLogcatBytes;

        public FailedTestLogcatGenerator(ITestInvocationListener listener, ITestDevice device,
                int maxLogcatBytes) {
            super(listener);
            mDevice = device;
            mNumLogcatBytes = maxLogcatBytes;
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            super.testFailed(test, trace);
            captureLog(test);
        }

        @Override
        public void testAssumptionFailure(TestIdentifier test, String trace) {
            super.testAssumptionFailure(test, trace);
            captureLog(test);
        }

        private void captureLog(TestIdentifier test) {
            // sleep a small amount of time to ensure test failure stack trace makes it into logcat
            // capture
            RunUtil.getDefault().sleep(10);
            InputStreamSource logSource = mDevice.getLogcat(mNumLogcatBytes);
            super.testLog(String.format("logcat-%s_%s", test.getClassName(), test.getTestName()),
                    LogDataType.TEXT, logSource);
            logSource.cancel();
        }
    }
}
