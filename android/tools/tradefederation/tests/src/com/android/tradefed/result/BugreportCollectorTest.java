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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.BugreportCollector.Filter;
import com.android.tradefed.result.BugreportCollector.Freq;
import com.android.tradefed.result.BugreportCollector.Noun;
import com.android.tradefed.result.BugreportCollector.Predicate;
import com.android.tradefed.result.BugreportCollector.Relation;
import com.android.tradefed.result.BugreportCollector.SubPredicate;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class BugreportCollectorTest extends TestCase {
    private BugreportCollector mCollector = null;
    private ITestDevice mMockDevice = null;
    private ITestInvocationListener mMockListener = null;
    private InputStreamSource mBugreportISS = null;

    private static final String TEST_KEY = "key";
    private static final String RUN_KEY = "key2";
    private static final String BUGREPORT_STRING = "This is a bugreport\nYeah!\n";
    private static final String STACK_TRACE = "boo-hoo";

    private static class BugreportISS implements InputStreamSource {
        @Override
        public InputStream createInputStream() {
            return new ByteArrayInputStream(BUGREPORT_STRING.getBytes());
        }

        @Override
        public void cancel() {
            // ignore
        }

        @Override
        public long size() {
            return BUGREPORT_STRING.getBytes().length;
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createStrictMock(ITestDevice.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mBugreportISS = new BugreportISS();

        EasyMock.expect(mMockDevice.getBugreport()).andStubReturn(mBugreportISS);
        mCollector = new BugreportCollector(mMockListener, mMockDevice);
    }

    public void testCreatePredicate() throws Exception {
        Predicate foo = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        System.err.format("foo is %s\n", foo.toString());
    }

    public void testPredicateEquals() throws Exception {
        Predicate foo = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        Predicate bar = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        Predicate baz = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);

        assertTrue(foo.equals(bar));
        assertTrue(bar.equals(foo));
        assertFalse(foo.equals(baz));
        assertFalse(baz.equals(bar));
    }

    public void testPredicatePartialMatch() throws Exception {
        Predicate shortP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);
        Predicate longP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION,
                Filter.WITH_ANY, Noun.TESTCASE);
        assertTrue(longP.partialMatch(shortP));
        assertTrue(shortP.partialMatch(longP));
    }

    public void testPredicateFullMatch() throws Exception {
        Predicate shortP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);
        Predicate longP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION,
                Filter.WITH_ANY, Noun.TESTCASE);
        Predicate longP2 = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION,
                Filter.WITH_ANY, Noun.TESTCASE);
        assertFalse(longP.fullMatch(shortP));
        assertFalse(shortP.fullMatch(longP));

        assertTrue(longP.fullMatch(longP2));
        assertTrue(longP2.fullMatch(longP));
    }

    /**
     * A test to verify that invalid predicates are rejected
     */
    public void testInvalidPredicate() throws Exception {
        SubPredicate[][] predicates = new SubPredicate[][] {
                // AT_START_OF (Freq) FAILED_(Noun)
                {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_TESTCASE},
                {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_TESTRUN},
                {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_INVOCATION},
                {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_TESTCASE},
                {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_TESTRUN},
                {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_INVOCATION},
                // (Relation) FIRST [FAILED_]INVOCATION
                {Relation.AT_START_OF, Freq.FIRST, Noun.INVOCATION},
                {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_INVOCATION},
                {Relation.AFTER, Freq.FIRST, Noun.INVOCATION},
                {Relation.AFTER, Freq.FIRST, Noun.FAILED_INVOCATION},
                };

        for (SubPredicate[] pred : predicates) {
            try {
                assertEquals(3, pred.length);
                new Predicate((Relation)pred[0], (Freq)pred[1], (Noun)pred[2]);
                fail(String.format(
                        "Expected IllegalArgumentException for invalid predicate [%s %s %s]",
                        pred[0], pred[1], pred[2]));
            } catch (IllegalArgumentException e) {
                // expected
                // FIXME: validate message
            }
        }
    }

    /**
     * Make sure that BugreportCollector passes events through to its child listener
     */
    public void testPassThrough() throws Exception {
        setListenerTestRunExpectations(mMockListener, "runName", "testName", "value");
        replayMocks();
        injectTestRun("runName", "testName", "value");
        verifyMocks();
    }

    public void testTestFailed() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.FAILED_TESTCASE);
        mCollector.addPredicate(pred);
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        EasyMock.expectLastCall().times(2);
        setListenerTestRunExpectations(mMockListener, "runName1", "testName1", "value",
                true /*failed*/);
        mMockListener.testLog(EasyMock.contains("bug-FAILED-FooTest__testName1."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        setListenerTestRunExpectations(mMockListener, "runName2", "testName2", "value",
                true /*failed*/);
        mMockListener.testLog(EasyMock.contains("bug-FAILED-FooTest__testName2."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        replayMocks();
        injectTestRun("runName1", "testName1", "value", true /*failed*/);
        injectTestRun("runName2", "testName2", "value", true /*failed*/);
        verifyMocks();
    }

    public void testTestEnded() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        mCollector.addPredicate(pred);
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        EasyMock.expectLastCall().times(2);
        setListenerTestRunExpectations(mMockListener, "runName1", "testName1", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName1."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        setListenerTestRunExpectations(mMockListener, "runName2", "testName2", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName2."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        replayMocks();
        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");
        verifyMocks();
    }

    public void testWaitForDevice() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        mCollector.addPredicate(pred);
        mCollector.setDeviceWaitTime(1);

        mMockDevice.waitForDeviceOnline(1000);
        EasyMock.expectLastCall().times(2);  // Once per ending test method
        setListenerTestRunExpectations(mMockListener, "runName1", "testName1", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName1."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        setListenerTestRunExpectations(mMockListener, "runName2", "testName2", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName2."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        replayMocks();
        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");
        verifyMocks();
    }

    public void testTestEnded_firstCase() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.FIRST, Noun.TESTCASE);
        mCollector.addPredicate(pred);
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        EasyMock.expectLastCall().times(2);
        setListenerTestRunExpectations(mMockListener, "runName1", "testName1", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName1."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        setListenerTestRunExpectations(mMockListener, "runName2", "testName2", "value");
        mMockListener.testLog(EasyMock.contains("bug-FooTest__testName2."),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        replayMocks();
        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");
        verifyMocks();
    }

    public void testTestEnded_firstRun() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.FIRST, Noun.TESTRUN);
        mCollector.addPredicate(pred);
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        // Note: only one testLog
        setListenerTestRunExpectations(mMockListener, "runName", "testName", "value");
        mMockListener.testLog(EasyMock.contains(pred.toString()),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        setListenerTestRunExpectations(mMockListener, "runName2", "testName2", "value");
        replayMocks();
        injectTestRun("runName", "testName", "value");
        injectTestRun("runName2", "testName2", "value");
        verifyMocks();
    }

    public void testTestRunEnded() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTRUN);
        mCollector.addPredicate(pred);
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        setListenerTestRunExpectations(mMockListener, "runName", "testName", "value");
        mMockListener.testLog(EasyMock.contains(pred.toString()),
                EasyMock.eq(LogDataType.BUGREPORT), EasyMock.eq(mBugreportISS));
        replayMocks();
        injectTestRun("runName", "testName", "value");
        verifyMocks();
    }

    public void testDescriptiveName() throws Exception {
        final String normalName = "AT_START_OF_FIRST_TESTCASE";
        final String descName = "custom_descriptive_name";
        mMockDevice.waitForDeviceOnline(EasyMock.anyLong());
        EasyMock.expectLastCall().times(2);
        mMockListener.testLog(EasyMock.contains(normalName), EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.eq(mBugreportISS));
        mMockListener.testLog(EasyMock.contains(descName), EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.eq(mBugreportISS));
        replayMocks();
        mCollector.grabBugreport(normalName);
        mCollector.setDescriptiveName(descName);
        mCollector.grabBugreport(normalName);
        verifyMocks();
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
     * Injects a single test run with 1 passed test into the {@link CollectingTestListener} under
     * test
     * @return the {@link TestIdentifier} of added test
     */
    private TestIdentifier injectTestRun(String runName, String testName, String metricValue,
            boolean shouldFail) {
        Map<String, String> runMetrics = new HashMap<String, String>(1);
        runMetrics.put(RUN_KEY, metricValue);
        Map<String, String> testMetrics = new HashMap<String, String>(1);
        testMetrics.put(TEST_KEY, metricValue);

        mCollector.testRunStarted(runName, 1);
        final TestIdentifier test = new TestIdentifier("FooTest", testName);
        mCollector.testStarted(test);
        if (shouldFail) {
            mCollector.testFailed(test, STACK_TRACE);
        }
        mCollector.testEnded(test, testMetrics);
        mCollector.testRunEnded(0, runMetrics);
        return test;
    }

    private void setListenerTestRunExpectations(ITestInvocationListener listener, String runName,
            String testName, String metricValue) {
        setListenerTestRunExpectations(listener, runName, testName, metricValue, false);
    }

    @SuppressWarnings("unchecked")
    private void setListenerTestRunExpectations(ITestInvocationListener listener, String runName,
            String testName, String metricValue, boolean shouldFail) {
        // FIXME: verify metrics
        listener.testRunStarted(EasyMock.eq(runName), EasyMock.eq(1));
        final TestIdentifier test = new TestIdentifier("FooTest", testName);
        listener.testStarted(EasyMock.eq(test));
        if (shouldFail) {
            listener.testFailed(EasyMock.eq(test), EasyMock.eq(STACK_TRACE));
        }
        listener.testEnded(EasyMock.eq(test), (Map<String, String>)EasyMock.anyObject());
        listener.testRunEnded(EasyMock.anyInt(), (Map<String, String>)EasyMock.anyObject());
    }

    /**
     * Convenience method to replay all mocks
     */
    private void replayMocks() {
        EasyMock.replay(mMockDevice, mMockListener);
    }

    /**
     * Convenience method to verify all mocks
     */
    private void verifyMocks() {
        EasyMock.verify(mMockDevice, mMockListener);
    }
}

