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

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A Test that runs a native test package on given device.
 */
@OptionClass(alias = "gtest")
public class GTest implements IDeviceTest, IRemoteTest, ITestFilterReceiver, IRuntimeHintProvider {

    private static final String LOG_TAG = "GTest";
    static final String DEFAULT_NATIVETEST_PATH = "/data/nativetest";

    private ITestDevice mDevice = null;
    private boolean mRunDisabledTests = false;

    @Option(name = "native-test-device-path",
            description="The path on the device where native tests are located.")
    private String mNativeTestDevicePath = DEFAULT_NATIVETEST_PATH;

    @Option(name = "module-name",
            description="The name of the native test module to run.")
    private String mTestModule = null;

    @Option(name = "positive-testname-filter",
            description="The GTest-based positive filter of the test name to run.")
    private String mTestNamePositiveFilter = null;
    @Option(name = "negative-testname-filter",
            description="The GTest-based negative filter of the test name to run.")
    private String mTestNameNegativeFilter = null;

    @Option(name = "include-filter",
            description="The GTest-based positive filter of the test names to run.")
    private List<String> mIncludeFilters = new ArrayList<>();
    @Option(name = "exclude-filter",
            description="The GTest-based negative filter of the test names to run.")
    private List<String> mExcludeFilters = new ArrayList<>();

    @Option(name = "native-test-timeout", description =
            "The max time in ms for a gtest to run. " +
            "Test run will be aborted if any test takes longer.")
    private int mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "send-coverage",
            description = "Send coverage target info to test listeners.")
    private boolean mSendCoverage = true;

    @Option(name ="prepend-filename",
            description = "Prepend filename as part of the classname for the tests.")
    private boolean mPrependFileName = false;

    @Option(name = "before-test-cmd",
            description = "adb shell command(s) to run before GTest.")
    private List<String> mBeforeTestCmd = new ArrayList<>();

    @Option(name = "after-test-cmd",
            description = "adb shell command(s) to run after GTest.")
    private List<String> mAfterTestCmd = new ArrayList<>();

    @Option(name = "ld-library-path",
            description = "LD_LIBRARY_PATH value to include in the GTest execution command.")
    private String mLdLibraryPath = null;

    @Option(name = "native-test-flag", description =
            "Additional flag values to pass to the native test's shell command. " +
            "Flags should be complete, including any necessary dashes: \"--flag=value\"")
    private List<String> mGTestFlags = new ArrayList<>();

    @Option(name = "runtime-hint",
            isTimeVal=true,
            description="The hint about the test's runtime.")
    private long mRuntimeHint = 60000;// 1 minute

    /** coverage target value. Just report all gtests as 'native' for now */
    private static final String COVERAGE_TARGET = "Native";

    // GTest flags...
    private static final String GTEST_FLAG_PRINT_TIME = "--gtest_print_time";
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
    private static final String GTEST_FLAG_RUN_DISABLED_TESTS = "--gtest_also_run_disabled_tests";

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
     * Set the Android native test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName(String moduleName) {
        return mTestModule;
    }

    /**
     * Set whether GTest should run disabled tests.
     */
    public void setRunDisabled(boolean runDisabled) {
        mRunDisabledTests = runDisabled;
    }

    /**
     * Get whether GTest should run disabled tests.
     *
     * @return True if disabled tests should be run, false otherwise
     */
    public boolean getRunDisabledTests() {
        return mRunDisabledTests;
    }

    /**
     * Set the max time in ms for a gtest to run.
     * <p/>
     * Exposed for unit testing
     */
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * Helper to get the adb gtest filter of test to run.
     *
     * Note that filters filter on the function name only (eg: Google Test "Test"); all Google Test
     * "Test Cases" will be considered.
     *
     * @return the full filter flag to pass to the Gtest, or an empty string if none have been
     * specified
     */
    private String getGTestFilters() {
        StringBuilder filter = new StringBuilder();
        if (mTestNamePositiveFilter != null) {
            mIncludeFilters.add(mTestNamePositiveFilter);
        }
        if (mTestNameNegativeFilter != null) {
            mExcludeFilters.add(mTestNameNegativeFilter);
        }
        if (!mIncludeFilters.isEmpty() || !mExcludeFilters.isEmpty()) {
            filter.append(GTEST_FLAG_FILTER);
            filter.append("=");
            if (!mIncludeFilters.isEmpty()) {
              filter.append(ArrayUtil.join(":", mIncludeFilters));
            }
            if (!mExcludeFilters.isEmpty()) {
              filter.append("-");
              filter.append(ArrayUtil.join(":", mExcludeFilters));
          }
        }
        return filter.toString();
    }

    /**
     * Helper to get all the GTest flags to pass into the adb shell command.
     *
     * @return the {@link String} of all the GTest flags that should be passed to the GTest
     */
    private String getAllGTestFlags() {
        String flags = String.format("%s %s", GTEST_FLAG_PRINT_TIME, getGTestFilters());

        if (mRunDisabledTests) {
            flags = String.format("%s %s", flags, GTEST_FLAG_RUN_DISABLED_TESTS);
        }

        for (String gTestFlag : mGTestFlags) {
            flags = String.format("%s %s", flags, gTestFlag);
        }
        return flags;
    }

    /**
     * Gets the path where native tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mNativeTestDevicePath);
        if (mTestModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(mTestModule);
        }
        return testPath.toString();
    }

    /**
     * Executes all native tests in a folder as well as in all subfolders recursively.
     * <p/>
     * Exposed for unit testing.
     *
     * @param rootEntry The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listener the {@link ITestRunListener)
     * @throws DeviceNotAvailableException
     */
    void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            ITestRunListener listener) throws DeviceNotAvailableException {

        if (rootEntry.isDirectory()) {
            // recursively run tests in all subdirectories
            for (IFileEntry childEntry : rootEntry.getChildren(false)) {
                doRunAllTestsInSubdirectory(childEntry, testDevice, listener);
            }
        } else {
            // assume every file is a valid gtest binary.
            IShellOutputReceiver resultParser = createResultParser(rootEntry.getName(), listener);
            String fullPath = rootEntry.getFullEscapedPath();
            String flags = getAllGTestFlags();
            Log.i(LOG_TAG, String.format("Running gtest %s %s on %s", fullPath, flags,
                    mDevice.getSerialNumber()));
            // force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", fullPath));
            runTest(testDevice, resultParser, fullPath, flags);
        }
    }

    /**
     * Run the given gtest binary
     *
     * @param testDevice the {@link ITestDevice}
     * @param resultParser the test run output parser
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @throws DeviceNotAvailableException
     */
    private void runTest(final ITestDevice testDevice, final IShellOutputReceiver resultParser,
            final String fullPath, final String flags) throws DeviceNotAvailableException {
        // TODO: add individual test timeout support, and rerun support
        try {
            for (String cmd : mBeforeTestCmd) {
                testDevice.executeShellCommand(cmd);
            }
            String cmd = getGTestCmdLine(fullPath, flags);
            testDevice.executeShellCommand(cmd, resultParser,
                    mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                    TimeUnit.MILLISECONDS,
                    0 /* retryAttempts */);
        } catch (DeviceNotAvailableException e) {
            // TODO: consider moving the flush of parser data on exceptions to TestDevice or
            // AdbHelper
            resultParser.flush();
            throw e;
        } catch (RuntimeException e) {
            resultParser.flush();
            throw e;
        } finally {
            for (String cmd : mAfterTestCmd) {
                testDevice.executeShellCommand(cmd);
            }
        }
    }

    /**
     * Helper method to build the gtest command to run.
     *
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @return the shell command line to run for the gtest
     */
    protected String getGTestCmdLine(String fullPath, String flags) {
        StringBuilder gTestCmdLine = new StringBuilder();
        if (mLdLibraryPath != null) {
            gTestCmdLine.append(String.format("LD_LIBRARY_PATH=%s ", mLdLibraryPath));
        }
        gTestCmdLine.append(String.format("%s %s", fullPath, flags));
        return gTestCmdLine.toString();
    }

    /**
     * Factory method for creating a {@link IShellOutputReceiver} that parses test output and
     * forwards results to the result listener.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param listener
     * @param runName
     * @return a {@link IShellOutputReceiver}
     */
    IShellOutputReceiver createResultParser(String runName, ITestRunListener listener) {
        GTestResultParser resultParser = new GTestResultParser(runName, listener);
        resultParser.setPrependFileName(mPrependFileName);
        // TODO: find a better solution for sending coverage info
        if (mSendCoverage) {
            resultParser.setCoverageTarget(COVERAGE_TARGET);
        }
        return resultParser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // @TODO: add support for rerunning tests
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        IFileEntry nativeTestDirectory = mDevice.getFileEntry(testPath);
        if (nativeTestDirectory == null) {
            Log.w(LOG_TAG, String.format("Could not find native test directory %s in %s!",
                    testPath, mDevice.getSerialNumber()));
            return;
        }
        doRunAllTestsInSubdirectory(nativeTestDirectory, mDevice, listener);
    }
}
