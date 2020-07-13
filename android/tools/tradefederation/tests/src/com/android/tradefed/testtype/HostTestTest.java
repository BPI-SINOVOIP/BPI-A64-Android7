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
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.easymock.EasyMock;

import java.util.Map;

/**
 * Unit tests for {@link HostTest}.
 */
@SuppressWarnings("unchecked")
public class HostTestTest extends TestCase {

    private HostTest mHostTest;
    private ITestInvocationListener mListener;

    public static class SuccessTestCase extends TestCase {
        public SuccessTestCase() {
        }

        public SuccessTestCase(String name) {
            super(name);
        }

        public void testPass() {
        }

        public void testPass2() {
        }
    }

    public static class AnotherTestCase extends TestCase {
        public AnotherTestCase() {
        }

        public AnotherTestCase(String name) {
            super(name);
        }

        public void testPass3() {
        }

        public void testPass4() {
        }
    }

    public static class SuccessTestSuite extends TestSuite {
        public SuccessTestSuite() {
            super(SuccessTestCase.class);
        }
    }

    public static class SuccessHierarchySuite extends TestSuite {
        public SuccessHierarchySuite() {
            super();
            addTestSuite(SuccessTestCase.class);
        }
    }

    public static class SuccessDeviceTest extends DeviceTestCase {
        public SuccessDeviceTest() {
            super();
        }

        public void testPass() {
            assertNotNull(getDevice());
        }
    }

    /** Non-public class; should fail to load. */
    private static class PrivateTest extends TestCase {
        public void testPrivate() {
        }
    }

    /** class without default constructor; should fail to load */
    public static class NoConstructorTest extends TestCase {
        public NoConstructorTest(String name) {
            super(name);
        }
        public void testNoConstructor() {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHostTest = new HostTest();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestCase}.
     */
    public void testRun_testcase() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestSuite}.
     */
    public void testRun_testSuite() throws Exception {
        mHostTest.setClassName(SuccessTestSuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * hierarchy of {@link TestSuite}s.
     */
    public void testRun_testHierarchySuite() throws Exception {
        mHostTest.setClassName(SuccessHierarchySuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(TestResult)}, where test to run is a
     * {@link TestCase} and methodName is set.
     */
    public void testRun_testMethod() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setMethodName("testPass");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, where className is not set.
     */
    public void testRun_missingClass() throws Exception {
        try {
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for an invalid class.
     */
    public void testRun_invalidClass() throws Exception {
        try {
            mHostTest.setClassName("foo");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a valid class that is not a {@link Test}.
     */
    public void testRun_notTestClass() throws Exception {
        try {
            mHostTest.setClassName(String.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a private class.
     */
    public void testRun_privateClass() throws Exception {
        try {
            mHostTest.setClassName(PrivateTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a test class with no default constructor.
     */
    public void testRun_noConstructorClass() throws Exception {
        try {
            mHostTest.setClassName(NoConstructorTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for multiple test classes.
     */
    public void testRun_multipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test3 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        TestIdentifier test4 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(4));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for multiple test classes with a method name.
     */
    public void testRun_multipleClassAndMethodName() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mHostTest);
            setter.setOptionValue("class", SuccessTestCase.class.getName());
            setter.setOptionValue("class", AnotherTestCase.class.getName());
            mHostTest.setMethodName("testPass3");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a {@link DeviceTest}.
     */
    public void testRun_deviceTest() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(device);

        TestIdentifier test1 = new TestIdentifier(SuccessDeviceTest.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(TestResult)}, for a {@link DeviceTest} where no device has been
     * provided.
     */
    public void testRun_missingDevice() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        try {
            mHostTest.run(mListener);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#countTestCases(TestResult)}
     */
    public void testCountTestCases() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }
}
