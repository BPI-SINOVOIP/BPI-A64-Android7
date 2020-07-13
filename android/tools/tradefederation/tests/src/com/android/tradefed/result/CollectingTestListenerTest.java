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
import com.android.tradefed.build.BuildInfo;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Unit tests for {@link CollectingTestListener}.
 */
public class CollectingTestListenerTest extends TestCase {

    private static final String METRIC_VALUE = "value";
    private static final String TEST_KEY = "key";
    private static final String METRIC_VALUE2 = "value2";
    private static final String RUN_KEY = "key2";


    private CollectingTestListener mCollectingTestListener;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCollectingTestListener = new CollectingTestListener();
        mCollectingTestListener.invocationStarted(new BuildInfo());
    }

    /**
     * Test the listener under a single normal test run.
     */
    public void testSingleRun() {
        final TestIdentifier test = injectTestRun("run", "testFoo", METRIC_VALUE);
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertTrue(runResult.isRunComplete());
        assertFalse(runResult.isRunFailure());
        assertEquals(1, mCollectingTestListener.getNumTotalTests());
        assertEquals(TestStatus.PASSED,
                runResult.getTestResults().get(test).getStatus());
        assertTrue(runResult.getTestResults().get(test).getStartTime() > 0);
        assertTrue(runResult.getTestResults().get(test).getEndTime() >=
            runResult.getTestResults().get(test).getStartTime());
    }

    /**
     * Test the listener where test run has failed.
     */
    @SuppressWarnings("unchecked")
    public void testRunFailed() {
        mCollectingTestListener.testRunStarted("foo", 1);
        mCollectingTestListener.testRunFailed("error");
        mCollectingTestListener.testRunEnded(0, Collections.EMPTY_MAP);
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertTrue(runResult.isRunComplete());
        assertTrue(runResult.isRunFailure());
        assertEquals("error", runResult.getRunFailureMessage());
    }

    /**
     * Test the listener when invocation is composed of two test runs.
     */
    public void testTwoRuns() {
        final TestIdentifier test1 = injectTestRun("run1", "testFoo1", METRIC_VALUE);
        final TestIdentifier test2 = injectTestRun("run2", "testFoo2", METRIC_VALUE2);
        assertEquals(2, mCollectingTestListener.getNumTotalTests());
        assertEquals(2, mCollectingTestListener.getNumTestsInState(TestStatus.PASSED));
        assertEquals(2, mCollectingTestListener.getRunResults().size());
        Iterator<TestRunResult> runIter = mCollectingTestListener.getRunResults().iterator();
        final TestRunResult runResult1 = runIter.next();
        final TestRunResult runResult2 = runIter.next();

        assertEquals("run1", runResult1.getName());
        assertEquals("run2", runResult2.getName());
        assertEquals(TestStatus.PASSED,
                runResult1.getTestResults().get(test1).getStatus());
        assertEquals(TestStatus.PASSED,
                runResult2.getTestResults().get(test2).getStatus());
        assertEquals(METRIC_VALUE,
                runResult1.getRunMetrics().get(RUN_KEY));
        assertEquals(METRIC_VALUE,
                runResult1.getTestResults().get(test1).getMetrics().get(TEST_KEY));
        assertEquals(METRIC_VALUE2,
                runResult2.getTestResults().get(test2).getMetrics().get(TEST_KEY));
    }

    /**
     * Test the listener when invocation is composed of a re-executed test run.
     */
    public void testReRun() {
        final TestIdentifier test1 = injectTestRun("run", "testFoo1", METRIC_VALUE);
        final TestIdentifier test2 = injectTestRun("run", "testFoo2", METRIC_VALUE2);
        assertEquals(2, mCollectingTestListener.getNumTotalTests());
        assertEquals(2, mCollectingTestListener.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, mCollectingTestListener.getRunResults().size());
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertEquals(2, runResult.getNumTestsInState(TestStatus.PASSED));
        assertTrue(runResult.getCompletedTests().contains(test1));
        assertTrue(runResult.getCompletedTests().contains(test2));
    }

    /**
     * Test the listener when invocation is composed of a re-executed test run, containing the same
     * tests
     */
    public void testReRun_overlap() {
        injectTestRun("run", "testFoo1", METRIC_VALUE);
        injectTestRun("run", "testFoo1", METRIC_VALUE2, true);
        assertEquals(1, mCollectingTestListener.getNumTotalTests());
        assertEquals(0, mCollectingTestListener.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, mCollectingTestListener.getNumTestsInState(TestStatus.FAILURE));
        assertEquals(1, mCollectingTestListener.getRunResults().size());
        TestRunResult runResult = mCollectingTestListener.getCurrentRunResults();
        assertEquals(0, runResult.getNumTestsInState(TestStatus.PASSED));
        assertEquals(1, runResult.getNumTestsInState(TestStatus.FAILURE));
        assertEquals(1, runResult.getNumTests());
    }

    /**
     * Test run with incomplete tests
     */
    @SuppressWarnings("unchecked")
    public void testSingleRun_incomplete() {
        mCollectingTestListener.testRunStarted("run", 1);
        mCollectingTestListener.testStarted(new TestIdentifier("FooTest", "incomplete"));
        mCollectingTestListener.testRunEnded(0, Collections.EMPTY_MAP);
        assertEquals(1, mCollectingTestListener.getNumTestsInState(TestStatus.INCOMPLETE));
    }

    /**
     * Test aggregating of metrics with long values
     */
    public void testRunEnded_aggregateLongMetrics() {
        mCollectingTestListener.setIsAggregrateMetrics(true);
        injectTestRun("run", "testFoo1", "1");
        injectTestRun("run", "testFoo1", "1");
        assertEquals("2", mCollectingTestListener.getCurrentRunResults().getRunMetrics().get(
                RUN_KEY));
    }

    /**
     * Test aggregating of metrics with double values
     */
    public void testRunEnded_aggregateDoubleMetrics() {
        mCollectingTestListener.setIsAggregrateMetrics(true);
        injectTestRun("run", "testFoo1", "1.1");
        injectTestRun("run", "testFoo1", "1.1");
        assertEquals("2.2", mCollectingTestListener.getCurrentRunResults().getRunMetrics().get(
                RUN_KEY));
    }

    /**
     * Test aggregating of metrics with different data types
     */
    public void testRunEnded_aggregateMixedMetrics() {
        mCollectingTestListener.setIsAggregrateMetrics(true);
        injectTestRun("run", "testFoo1", "1");
        injectTestRun("run", "testFoo1", "1.1");
        mCollectingTestListener.invocationEnded(0);
        assertEquals("2.1", mCollectingTestListener.getCurrentRunResults().getRunMetrics().get(
                RUN_KEY));
    }

    /**
     * Test aggregating of metrics when new metric isn't a number
     */
    public void testRunEnded_aggregateNewStringMetrics() {
        mCollectingTestListener.setIsAggregrateMetrics(true);
        injectTestRun("run", "testFoo1", "1");
        injectTestRun("run", "testFoo1", "bar");
        mCollectingTestListener.invocationEnded(0);
        assertEquals("bar", mCollectingTestListener.getCurrentRunResults().getRunMetrics().get(
                RUN_KEY));
    }

    /**
     * Test aggregating of metrics when existing metric isn't a number
     */
    public void testRunEnded_aggregateExistingStringMetrics() {
        mCollectingTestListener.setIsAggregrateMetrics(true);
        injectTestRun("run", "testFoo1", "bar");
        injectTestRun("run", "testFoo1", "1");
        mCollectingTestListener.invocationEnded(0);
        assertEquals("1", mCollectingTestListener.getCurrentRunResults().getRunMetrics().get(
                RUN_KEY));
    }

    /**
     * Injects a single test run with 1 passed test into the {@link CollectingTestListener} under
     * test
     * @return the {@link TestIdentifier} of added test
     */
    private TestIdentifier injectTestRun(String runName, String testName, String metricValue) {
        return injectTestRun(runName, testName, metricValue, false);
    }

    /**
     * Injects a single test run with 1 test into the {@link CollectingTestListener} under
     * test.
     * @return the {@link TestIdentifier} of added test
     */
    private TestIdentifier injectTestRun(String runName, String testName, String metricValue,
            boolean failtest) {
        Map<String, String> runMetrics = new HashMap<String, String>(1);
        runMetrics.put(RUN_KEY, metricValue);
        Map<String, String> testMetrics = new HashMap<String, String>(1);
        testMetrics.put(TEST_KEY, metricValue);

        mCollectingTestListener.testRunStarted(runName, 1);
        final TestIdentifier test = new TestIdentifier("FooTest", testName);
        mCollectingTestListener.testStarted(test);
        if (failtest) {
            mCollectingTestListener.testFailed(test, "trace");
        }
        mCollectingTestListener.testEnded(test, testMetrics);
        mCollectingTestListener.testRunEnded(0, runMetrics);
        return test;
    }
}
