/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.framework.tests;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult;
import com.android.loganalysis.item.BugreportItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.parser.BugreportParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Test that instruments a stress test package, gathers iterations metrics, and posts the results.
 */
public class FrameworkStressTest implements IDeviceTest, IRemoteTest {
    public static final String BUGREPORT_LOG_NAME = "bugreport_stress.txt";

    ITestDevice mTestDevice = null;

    @Option(name = "test-package-name", description = "Android test package name.")
    private String mTestPackageName;

    @Option(name = "test-class-name", description = "Test class name.")
    private String mTestClassName;

    @Option(name = "dashboard-test-label",
            description = "Test label to use when posting to dashboard.")
    private String mDashboardTestLabel;

    @Option(name = "setup-shell-command",
            description = "Setup shell command to run before the test, if any.")
    private String mSetupShellCommand;

    private static final String CURRENT_ITERATION_LABEL= "currentiterations";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        if (mSetupShellCommand != null) {
            mTestDevice.executeShellCommand(mSetupShellCommand);
        }
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackageName,
                mTestDevice.getIDevice());
        runner.setClassName(mTestClassName);
        CollectingTestListener collectingListener = new CollectingTestListener();
        mTestDevice.runInstrumentationTests(runner, collectingListener, listener);
        // Retrieve bugreport
        BugreportParser parser = new BugreportParser();
        BugreportItem bugreport = null;
        InputStreamSource bugSource = mTestDevice.getBugreport();

        try {
            listener.testLog(BUGREPORT_LOG_NAME, LogDataType.BUGREPORT, bugSource);
            bugreport = parser.parse(new BufferedReader(new InputStreamReader(
                    bugSource.createInputStream())));

            Assert.assertNotNull(bugreport);
            Assert.assertNotNull(bugreport.getSystemLog());
        } catch (IOException e) {
            Assert.fail(String.format("Failed to fetch and parse bugreport for device %s: %s",
                    mTestDevice.getSerialNumber(), e));
        } finally {
            bugSource.cancel();
        }

        Map<String, String> stressTestMetrics = new HashMap<String, String>();
        Integer numIterations = 0;
        Integer numSuccessfulIterations = 0;

        LogcatItem systemLog = bugreport.getSystemLog();
        Integer numAnrs = systemLog.getAnrs().size();
        Integer numJavaCrashes = systemLog.getJavaCrashes().size();
        Integer numNativeCrashes = systemLog.getNativeCrashes().size();

        // Fetch the last iteration count from the InstrumentationTestResult. We only expect to have
        // one test, and we take the result from the first test result.
        Collection<TestResult> testResults =
            collectingListener.getCurrentRunResults().getTestResults().values();
        if (testResults != null && testResults.iterator().hasNext()) {
            Map<String, String> testMetrics = testResults.iterator().next().getMetrics();
            if (testMetrics != null) {
                CLog.d(testMetrics.toString());
                // We want to report all test metrics as well.
                for (String metric : testMetrics.keySet()) {
                    if (metric.equalsIgnoreCase(CURRENT_ITERATION_LABEL)) {
                        String test_iterations = testMetrics.get(metric);
                        numIterations = Integer.parseInt(test_iterations);
                    } else {
                        stressTestMetrics.put(metric, testMetrics.get(metric));
                    }
                }
            }
        }

        // Calculate the number of successful iterations.
        numSuccessfulIterations = numIterations - numAnrs - numJavaCrashes - numNativeCrashes;

        // Report other metrics from bugreport.
        stressTestMetrics.put("anrs", numAnrs.toString());
        stressTestMetrics.put("java_crashes", numJavaCrashes.toString());
        stressTestMetrics.put("native_crashes", numNativeCrashes.toString());
        stressTestMetrics.put("iterations", numSuccessfulIterations.toString());

        // Post everything to the dashboard.
        reportMetrics(listener, mDashboardTestLabel, stressTestMetrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in.
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

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }
}
