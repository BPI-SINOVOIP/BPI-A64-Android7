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

package com.android.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.StubTestInvocationListener;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UiAutomatorTest implements IRemoteTest, IDeviceTest {

    public enum LoggingOption {
        AFTER_TEST,
        AFTER_FAILURE,
        OFF,
    }

    public enum TestFailureAction {
        BUGREPORT,
        SCREENSHOT,
        BUGREPORT_AND_SCREENSHOT,
    }

    private static final String SHELL_EXE_BASE = "/data/local/tmp/";

    private ITestDevice mDevice = null;
    private IRemoteAndroidTestRunner mRunner = null;

    @Option(name = "jar-path", description = "path to jars containing UI Automator test cases and"
            + " dependencies; May be repeated. " +
            "If unspecified will use all jars found in /data/local/tmp/")
    private List<String> mJarPaths = new ArrayList<String>();

    @Option(name = "class",
            description = "test class to run, may be repeated; multiple classess will be run"
                    + " in the same order as provided in command line")
    private List<String> mClasses = new ArrayList<String>();

    @Option(name = "sync-time", description = "time to allow for initial sync, in ms")
    private long mSyncTime = 0;

    @Option(name = "run-arg",
            description = "Additional test specific arguments to provide.")
    private Map<String, String> mArgMap = new LinkedHashMap<String, String>();

    @Option(name = "timeout",
            description = "Aborts the test run if any test takes longer than the specified number "
                    + "of milliseconds. For no timeout, set to 0.")
    private int mTestTimeout = 30 * 60 * 1000;  // default to 30 minutes

    @Option(name = "capture-logs", description =
            "capture bugreport and screenshot as specified.")
    private LoggingOption mLoggingOption = LoggingOption.AFTER_FAILURE;

    @Option(name = "runner-path", description = "path to uiautomator runner; may be null and "
            + "default will be used in this case")
    private String mRunnerPath = null;

    @Option(name = "on-test-failure",
            description = "sets the action to perform if a test fails")
    private TestFailureAction mFailureAction = TestFailureAction.BUGREPORT_AND_SCREENSHOT;

    @Option(name = "ignore-sighup",
            description = "allows uiautomator test to ignore SIGHUP signal")
    private boolean mIgnoreSighup = false;

    @Option(name = "run-name",
            description = "the run name to use when reporting test results.")
    private String mRunName = "uiautomator";

    @Option(name = "instrumentation",
            description = "the specified test should be driven with instrumentation."
            + "jar-path, runner-path, ignore-sighup are ignored when this is set.")
    private boolean mInstrumentation = false;

    @Option(name = "package",
            description = "The manifest package name of the UI test package."
            + "Only applies when 'instrumentation' option is set.")
    private String mPackage = null;

    @Option(name = "runner",
            description="The instrumentation based test runner class name to use."
            + "Only applies when 'instrumentation' option is set.")
    private String mRunnerName =
        "android.support.test.uiautomator.UiAutomatorInstrumentationTestRunner";

    @Option(name="final-screenshot", description="Take a screenshot at the end of a test")
    private boolean mFinalScreenshot = false;

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

    public void setLoggingOption(LoggingOption loggingOption) {
        mLoggingOption = loggingOption;
    }

    /**
     * @deprecated use {@link #setLoggingOption(LoggingOption)} instead.
     * <p/>
     * Retained for compatibility with cts-tradefed
     */
    @Deprecated
    public void setCaptureLogs(boolean captureLogs) {
        if (captureLogs) {
            setLoggingOption(LoggingOption.AFTER_FAILURE);
        } else {
            setLoggingOption(LoggingOption.OFF);
        }
    }

    public void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (!isInstrumentationTest()) {
            buildJarPaths();
        }
        mRunner = createTestRunner();
        if (!mClasses.isEmpty()) {
            getTestRunner().setClassNames(mClasses.toArray(new String[]{}));
        }
        getTestRunner().setRunName(mRunName);
        preTestSetup();
        getRunUtil().sleep(getSyncTime());
        getTestRunner().setMaxTimeToOutputResponse(mTestTimeout, TimeUnit.MILLISECONDS);
        for (Map.Entry<String, String> entry : getTestRunArgMap().entrySet()) {
            getTestRunner().addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        if (!isInstrumentationTest()) {
            ((UiAutomatorRunner)getTestRunner()).setIgnoreSighup(mIgnoreSighup);
        }
        if (mLoggingOption != LoggingOption.OFF) {
            getDevice().runInstrumentationTests(getTestRunner(), listener,
                    new LoggingWrapper(listener));
        } else {
            getDevice().runInstrumentationTests(getTestRunner(), listener);
        }

        if (mFinalScreenshot) {
            saveScreenshot(listener, "final_screenshot");
        }
    }

    protected IRemoteAndroidTestRunner createTestRunner() {
        if (isInstrumentationTest()) {
            if (mPackage == null) {
                throw new IllegalArgumentException("package name has not been set");
            }
            IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mPackage, mRunnerName,
                    getDevice().getIDevice());
            return runner;
        } else {
            return new UiAutomatorRunner(getDevice().getIDevice(),
                    getTestJarPaths().toArray(new String[]{}), mRunnerPath);
        }
    }

    private void buildJarPaths() throws DeviceNotAvailableException {
        if (mJarPaths.isEmpty()) {
            String rawFileString =
                    getDevice().executeShellCommand(String.format("ls %s", SHELL_EXE_BASE));
            String[] rawFiles = rawFileString.split("\r?\n");
            for (String rawFile : rawFiles) {
                if (rawFile.endsWith(".jar")) {
                    mJarPaths.add(rawFile);
                }
            }
            Assert.assertFalse(String.format("could not find jars in %s", SHELL_EXE_BASE),
                    mJarPaths.isEmpty());
            CLog.d("built jar paths %s", mJarPaths);
        }
    }

    /**
     * Add an argument to provide when running the UI Automator tests
     *
     * @param key the argument name
     * @param value the argument value
     */
    public void addRunArg(String key, String value) {
        getTestRunArgMap().put(key, value);
    }

    /**
     * Checks if the UI Automator components are present on device
     *
     * @throws DeviceNotAvailableException
     */
    protected void preTestSetup() throws DeviceNotAvailableException {
        if (!isInstrumentationTest()) {
            String runnerPath = ((UiAutomatorRunner)getTestRunner()).getRunnerPath();
            if (!getDevice().doesFileExist(runnerPath)) {
                throw new RuntimeException("Missing UI Automator runner: " + runnerPath);
            }
            for (String jarPath : getTestJarPaths()) {
                if (!jarPath.startsWith(FileListingService.FILE_SEPARATOR)) {
                    jarPath = SHELL_EXE_BASE + jarPath;
                }
                if (!getDevice().doesFileExist(jarPath)) {
                    throw new RuntimeException("Missing UI Automator test jar on device: "
                            + jarPath);
                }
            }
        }
    }

    protected void onScreenshotAndBugreport(ITestDevice device, ITestInvocationListener listener,
            String prefix) {
        onScreenshotAndBugreport(device, listener, prefix, null);
    }

    protected void onScreenshotAndBugreport(ITestDevice device, ITestInvocationListener listener,
            String prefix, TestFailureAction overrideAction) {
        if (overrideAction == null) {
            overrideAction = mFailureAction;
        }
        // get screen shot
        if (overrideAction == TestFailureAction.SCREENSHOT ||
                overrideAction == TestFailureAction.BUGREPORT_AND_SCREENSHOT) {
            InputStreamSource screenshot = null;
            try {
                screenshot = device.getScreenshot();
                listener.testLog(prefix + "_screenshot", LogDataType.PNG, screenshot);
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
            } finally {
                if (screenshot != null) {
                    screenshot.cancel();
                }
            }
        }
        // get bugreport
        if (overrideAction == TestFailureAction.BUGREPORT ||
                overrideAction == TestFailureAction.BUGREPORT_AND_SCREENSHOT) {
            InputStreamSource data = null;
            data = device.getBugreport();
            listener.testLog(prefix + "_bugreport", LogDataType.BUGREPORT, data);
            if (data != null) {
                data.cancel();
            }
        }
    }

    /**
     * Wraps an existing listener, capture some data in case of test failure
     */
    // TODO replace this once we have a generic event triggered reporter like
    // BugReportCollector
    private class LoggingWrapper extends StubTestInvocationListener {

        ITestInvocationListener mListener;
        private boolean mLoggedTestFailure = false;
        private boolean mLoggedTestRunFailure = false;

        public LoggingWrapper(ITestInvocationListener listener) {
            mListener = listener;
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            captureFailureLog(test);
        }

        @Override
        public void testAssumptionFailure(TestIdentifier test, String trace) {
            captureFailureLog(test);
        }

        private void captureFailureLog(TestIdentifier test) {
            if (mLoggingOption == LoggingOption.AFTER_FAILURE) {
                onScreenshotAndBugreport(getDevice(), mListener, String.format("%s_%s_failure",
                        test.getClassName(), test.getTestName()));
                // set the flag so that we don't log again when test finishes
                mLoggedTestFailure = true;
            }
        }

        @Override
        public void testRunFailed(String errorMessage) {
            if (mLoggingOption == LoggingOption.AFTER_FAILURE) {
                onScreenshotAndBugreport(getDevice(), mListener, "test_run_failure");
                // set the flag so that we don't log again when test run finishes
                mLoggedTestRunFailure = true;
            }
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            if (!mLoggedTestFailure && mLoggingOption == LoggingOption.AFTER_TEST) {
                onScreenshotAndBugreport(getDevice(), mListener, String.format("%s_%s_final",
                        test.getClassName(), test.getTestName()));
            }
        }

        @Override
        public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
            if (!mLoggedTestRunFailure && mLoggingOption == LoggingOption.AFTER_TEST) {
                onScreenshotAndBugreport(getDevice(), mListener, "test_run_final");
            }
        }
    }

    private void saveScreenshot(ITestInvocationListener listener, String name) {
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * @return the time allocated for the tests to sync.
     */
    public long getSyncTime() {
        return mSyncTime;
    }

    /**
     * @param syncTime the time for the tests files to sync.
     */
    public void setSyncTime(long syncTime) {
        mSyncTime = syncTime;
    }

    /**
     * @return the test runner.
     */
    public IRemoteAndroidTestRunner getTestRunner() {
        return mRunner;
    }

    /**
     * @return the test jar path.
     */
    public List<String> getTestJarPaths() {
        return mJarPaths;
    }

    /**
     * @param jarPaths the locations of the test jars.
     */
    public void setTestJarPaths(List<String> jarPaths) {
        mJarPaths = jarPaths;
    }

    /**
     * @return the arguments map to pass to the UiAutomatorRunner.
     */
    public Map<String, String> getTestRunArgMap() {
        return mArgMap;
    }

    /**
     * @param runArgMap the arguments to pass to the UiAutomatorRunner.
     */
    public void setTestRunArgMap(Map<String, String> runArgMap) {
        mArgMap = runArgMap;
    }

    /**
     * Add a test class name to run.
     */
    public void addClassName(String className) {
        mClasses.add(className);
    }

    /**
     * Add a test class name collection to run.
     */
    public void addClassNames(Collection<String> classNames) {
        mClasses.addAll(classNames);
    }

    public boolean isInstrumentationTest() {
        return mInstrumentation;
    }

    public void setRunnerName(String runnerName) {
        mRunnerName = runnerName;
    }

    /**
     * Gets the list of test class names that the harness is configured to run
     * @return list of test class names
     */
    public List<String> getClassNames() {
        return mClasses;
    }
}
