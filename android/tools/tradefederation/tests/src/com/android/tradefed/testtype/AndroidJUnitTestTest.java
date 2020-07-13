/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link AndroidJUnitTest}
 */
public class AndroidJUnitTestTest extends TestCase {

    private static final int TEST_TIMEOUT = 0;
    private static final long SHELL_TIMEOUT = 0;
    private static final String TEST_PACKAGE_VALUE = "com.foo";
    private static final TestIdentifier TEST1 = new TestIdentifier("Test", "test1");
    private static final TestIdentifier TEST2 = new TestIdentifier("Test", "test2");
    private static final int COLLECT_TESTS_SHELL_TIMEOUT = 1;

    /** The {@link AndroidJUnitTest} under test, with all dependencies mocked out */
    private AndroidJUnitTest mAndroidJUnitTest;

    // The mock objects.
    private IDevice mMockIDevice;
    private ITestDevice mMockTestDevice;
    private IRemoteAndroidTestRunner mMockRemoteRunner;
    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mMockIDevice);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("serial");
        mMockRemoteRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mAndroidJUnitTest = new AndroidJUnitTest() {
            @Override
            IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName,
                    String runnerName, IDevice device) {
                return mMockRemoteRunner;
            }
        };
        mAndroidJUnitTest.setPackageName(TEST_PACKAGE_VALUE);
        mAndroidJUnitTest.setDevice(mMockTestDevice);
        // default to no rerun, for simplicity
        mAndroidJUnitTest.setRerunMode(false);
        // default to no timeout for simplicity
        mAndroidJUnitTest.setTestTimeout(TEST_TIMEOUT);
        mAndroidJUnitTest.setShellTimeout(SHELL_TIMEOUT);
        mMockRemoteRunner.setMaxTimeToOutputResponse(SHELL_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.addInstrumentationArg(InstrumentationTest.TEST_TIMEOUT_INST_ARGS_KEY,
                Long.toString(SHELL_TIMEOUT));
        mAndroidJUnitTest.setCollectsTestsShellTimeout(COLLECT_TESTS_SHELL_TIMEOUT);
    }

    /**
     * Test list of tests to run is filtered by include filters.
     */
    public void testRun_includeFilterClass() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("class", TEST1.toString());
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addIncludeFilter(TEST1.toString());
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by exclude filters.
     */
    public void testRun_excludeFilterClass() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("notClass", TEST1.toString());
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addExcludeFilter(TEST1.toString());
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by include and exclude filters.
     */
    public void testRun_includeAndExcludeFilterClass() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("class", TEST1.getClassName());
        mMockRemoteRunner.addInstrumentationArg("notClass", TEST2.toString());
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
        mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by include filters.
     */
    public void testRun_includeFilterPackage() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("package", "com.android.test");
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by exclude filters.
     */
    public void testRun_excludeFilterPackage() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("notPackage", "com.android.not");
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by include and exclude filters.
     */
    public void testRun_includeAndExcludeFilterPackage() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("package", "com.android.test");
        mMockRemoteRunner.addInstrumentationArg("notPackage", "com.android.not");
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test list of tests to run is filtered by include and exclude filters.
     */
    public void testRun_includeAndExcludeFilters() throws Exception {
        // expect this call
        mMockRemoteRunner.addInstrumentationArg("class", TEST1.getClassName());
        mMockRemoteRunner.addInstrumentationArg("notClass", TEST2.toString());
        mMockRemoteRunner.addInstrumentationArg("package", "com.android.test");
        mMockRemoteRunner.addInstrumentationArg("notPackage", "com.android.not");
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mAndroidJUnitTest.addIncludeFilter(TEST1.getClassName());
        mAndroidJUnitTest.addExcludeFilter(TEST2.toString());
        mAndroidJUnitTest.addIncludeFilter("com.android.test");
        mAndroidJUnitTest.addExcludeFilter("com.android.not");
        mAndroidJUnitTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    private void setRunTestExpectations() throws DeviceNotAvailableException {
        EasyMock.expect(mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                        (ITestRunListener)EasyMock.anyObject())).andReturn(Boolean.TRUE);
    }

    /**
     * Test isClassOrMethod returns true for <package>.<class> and <package>.<class>#<method> but
     * not for <package>.
     */
    public void testIsClassOrMethod() throws Exception {
        assertFalse("String was just package", mAndroidJUnitTest.isClassOrMethod("android.test"));
        assertTrue("String was class", mAndroidJUnitTest.isClassOrMethod("android.test.Foo"));
        assertTrue("String was method", mAndroidJUnitTest.isClassOrMethod("android.test.Foo#bar"));
    }
}
