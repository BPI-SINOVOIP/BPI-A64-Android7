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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Collections;
import java.util.Map;

/**
 * Unit tests for {@link DeviceTestCase}.
 */
public class DeviceTestCaseTest extends TestCase {

    public static class MockTest extends DeviceTestCase {

        public void test1() {};
        public void test2() {};
    }

    public static class MockAbortTest extends DeviceTestCase {

        private static final String EXCEP_MSG = "failed";

        public void test1() throws DeviceNotAvailableException {
            throw new DeviceNotAvailableException(EXCEP_MSG);
        }
    };

    /**
     * Verify that calling run on a DeviceTestCase will run all test methods.
     */
    @SuppressWarnings("unchecked")
    public void testRun_suite() throws Exception {
        MockTest test = new MockTest();

        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 2);
        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        final TestIdentifier test2 = new TestIdentifier(MockTest.class.getName(), "test2");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testStarted(test2);
        listener.testEnded(test2, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Regression test to verify a single test can still be run.
     */
    @SuppressWarnings("unchecked")
    public void testRun_singleTest() throws DeviceNotAvailableException {
        MockTest test = new MockTest();
        test.setName("test1");

        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 1);
        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that a device not available exception is thrown up.
     */
    @SuppressWarnings("unchecked")
    public void testRun_deviceNotAvail() {
        MockAbortTest test = new MockAbortTest();
        // create a mock ITestInvocationListener, because results are easier to verify
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);

        final TestIdentifier test1 = new TestIdentifier(MockAbortTest.class.getName(), "test1");
        listener.testRunStarted(MockAbortTest.class.getName(), 1);
        listener.testStarted(test1);
        listener.testFailed(EasyMock.eq(test1),
                EasyMock.contains(MockAbortTest.EXCEP_MSG));
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testRunFailed(EasyMock.contains(MockAbortTest.EXCEP_MSG));
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);
        try {
            test.run(listener);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        EasyMock.verify(listener);
    }
}
