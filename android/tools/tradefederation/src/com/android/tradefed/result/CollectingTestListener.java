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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ITestInvocationListener} that will collect all test results.
 * <p/>
 * Although the data structures used in this object are thread-safe, the
 * {@link ITestInvocationListener} callbacks must be called in the correct order.
 */
public class CollectingTestListener implements ITestInvocationListener {

    // Stores the test results
    // Uses a synchronized map to make thread safe.
    // Uses a LinkedHashmap to have predictable iteration order
    private Map<String, TestRunResult> mRunResultsMap =
        Collections.synchronizedMap(new LinkedHashMap<String, TestRunResult>());
    private TestRunResult mCurrentResults =  new TestRunResult();

    /** represents sums of tests in each TestStatus state for all runs.
     * Indexed by TestStatus.ordinal() */
    private int[] mStatusCounts = new int[TestStatus.values().length];
    /** tracks if mStatusCounts is accurate, or if it needs to be recalculated */
    private boolean mIsCountDirty = true;

    @Option(name = "aggregate-metrics", description =
        "attempt to add test metrics values for test runs with the same name." )
    private boolean mIsAggregateMetrics = false;

    private IBuildInfo mBuildInfo;

    /**
     * Toggle the 'aggregate metrics' option
     * <p/>
     * Exposed for unit testing
     */
    void setIsAggregrateMetrics(boolean aggregate) {
        mIsAggregateMetrics = aggregate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Return the build info that was reported via {@link #invocationStarted(IBuildInfo)}
     */
    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /**
     * Set the build info.
     */
    public void setBuildInfo(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        if (mRunResultsMap.containsKey(name)) {
            // rerun of previous run. Add test results to it
            mCurrentResults = mRunResultsMap.get(name);
        } else {
            // new run
            mCurrentResults = new TestRunResult();
            mCurrentResults.setAggregateMetrics(mIsAggregateMetrics);

            mRunResultsMap.put(name, mCurrentResults);
        }
        mCurrentResults.testRunStarted(name, numTests);
        mIsCountDirty = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mIsCountDirty = true;
        mCurrentResults.testStarted(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        mIsCountDirty = true;
        mCurrentResults.testEnded(test, testMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        mIsCountDirty = true;
        mCurrentResults.testFailed(test, trace);
    }

    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        mIsCountDirty = true;
        mCurrentResults.testAssumptionFailure(test, trace);

    }

    @Override
    public void testIgnored(TestIdentifier test) {
        mIsCountDirty = true;
        mCurrentResults.testIgnored(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        mIsCountDirty = true;
        mCurrentResults.testRunEnded(elapsedTime, runMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        mIsCountDirty = true;
        mCurrentResults.testRunFailed(errorMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        mIsCountDirty = true;
        mCurrentResults.testRunStopped(elapsedTime);
    }

    /**
     * Gets the results for the current test run.
     * <p/>
     * Note the results may not be complete. It is recommended to test the value of {@link
     * TestRunResult#isRunComplete()} and/or (@link TestRunResult#isRunFailure()} as appropriate
     * before processing the results.
     *
     * @return the {@link TestRunResult} representing data collected during last test run
     */
    public TestRunResult getCurrentRunResults() {
        return mCurrentResults;
    }

    /**
     * Gets the results for all test runs.
     */
    public Collection<TestRunResult> getRunResults() {
        return mRunResultsMap.values();
    }

    /**
     * Gets the total number of complete tests for all runs.
     */
    public int getNumTotalTests() {
        int total = 0;
        // force test count
        getNumTestsInState(TestStatus.PASSED);
        for (TestStatus s : TestStatus.values()) {
            total += mStatusCounts[s.ordinal()];
        }
        return total;
    }

    /**
     * Gets the number of tests in given state for this run.
     */
    public int getNumTestsInState(TestStatus status) {
        if (mIsCountDirty) {
            for (TestRunResult result : mRunResultsMap.values()) {
                for (TestStatus s : TestStatus.values()) {
                    mStatusCounts[s.ordinal()] += result.getNumTestsInState(s);
                }
            }
            mIsCountDirty = false;
        }
        return mStatusCounts[status.ordinal()];
    }

    /**
     * @return true if invocation had any failed or assumption failed tests.
     */
    public boolean hasFailedTests() {
        return getNumAllFailedTests() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        // ignore
    }

    /**
     * Return total number of tests in a failure state (failed, assumption failure)
     * @return
     */
    public int getNumAllFailedTests() {
        return getNumTestsInState(TestStatus.FAILURE) +
                getNumTestsInState(TestStatus.ASSUMPTION_FAILURE);
    }
}
