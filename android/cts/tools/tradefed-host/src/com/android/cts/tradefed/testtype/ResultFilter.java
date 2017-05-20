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
package com.android.cts.tradefed.testtype;

import com.android.cts.tradefed.testtype.CtsTest.TestPackage;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ITestInvocationListener} that filters test results based on the set of expected tests
 * in CTS test package xml files.
 * <p/>
 * It will only report test results for expected tests, and at end of invocation, will report the
 * set of expected tests that were not executed.
 */
class ResultFilter extends ResultForwarder {

    private final Set<TestIdentifier> mKnownTests;
    private final Set<TestIdentifier> mRemainingTests;
    private final String mTestRun;

    /**
     * Create a {@link ResultFilter}.
     *
     * @param listener the real {@link ITestInvocationListener} to forward results to
     */
    ResultFilter(ITestInvocationListener listener, TestPackage testPackage) {
        super(listener);
        mTestRun = testPackage.getTestRunName();
        Collection<TestIdentifier> tests = testPackage.getKnownTests();
        mKnownTests = new HashSet<TestIdentifier>(tests);
        // use LinkedHashMap for predictable test order
        mRemainingTests = new LinkedHashSet<TestIdentifier>(tests);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String runName, int testCount) {
        if (mTestRun.equals(runName)) {
            super.testRunStarted(runName, testCount);
        } else {
            CLog.d("Skipping reporting unknown test run %s", runName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        if (isKnownTest(test)) {
            super.testStarted(test);
        } else {
            CLog.d("Skipping reporting unknown test %s", test);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        if (isKnownTest(test)) {
            super.testFailed(test, trace);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (isKnownTest(test)) {
            super.testEnded(test, testMetrics);
            removeExecutedTest(test);
        }
    }

    /**
     * @param test
     * @return
     */
    private boolean isKnownTest(TestIdentifier test) {
        return mKnownTests.contains(test);
    }

    /**
     * Remove given test from the 'remaining tests' data structure.
     * @param test
     */
    private void removeExecutedTest(TestIdentifier test) {
        mRemainingTests.remove(test);
    }

    /**
     * Report the set of expected tests that were not executed
     */
    public void reportUnexecutedTests() {
        if (mRemainingTests.isEmpty()) {
            return;
        }
        super.testRunStarted(mTestRun, mRemainingTests.size());
        for (TestIdentifier test : mRemainingTests) {
            // an unexecuted test is currently reported as a 'testStarted' event without a
            // 'testEnded'. TODO: consider adding an explict API for reporting an unexecuted
            // test
            super.testStarted(test);
        }
        super.testRunEnded(0, new HashMap<String, String>());
    }

    /** @return the number of known tests */
    public int getKnownTestCount() {
        return mKnownTests.size();
    }
}
