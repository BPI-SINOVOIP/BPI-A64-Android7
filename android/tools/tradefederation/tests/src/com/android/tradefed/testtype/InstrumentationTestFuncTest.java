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

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.TestAppConstants;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import java.io.IOException;
import java.util.Map;

/**
 * Functional tests for {@link InstrumentationTest}.
 */
public class InstrumentationTestFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "InstrumentationTestFuncTest";

    /** The {@link InstrumentationTest} under test */
    private InstrumentationTest mInstrumentationTest;

    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentationTest = new InstrumentationTest();
        mInstrumentationTest.setPackageName(TestAppConstants.TESTAPP_PACKAGE);
        mInstrumentationTest.setDevice(getDevice());
        // use no timeout by default
        mInstrumentationTest.setShellTimeout(-1);
        // set to no rerun by default
        mInstrumentationTest.setRerunMode(false);
        mMockListener = EasyMock.createStrictMock(ITestInvocationListener.class);
    }

    /**
     * Test normal run scenario with a single passed test result.
     */
    @SuppressWarnings("unchecked")
    public void testRun() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun");
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.PASSED_TEST_METHOD);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                    (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a single failed test result.
     */
    @SuppressWarnings("unchecked")
    public void testRun_testFailed() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testFailed");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.FAILED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.FAILED_TEST_METHOD);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        // TODO: add stricter checking on stackTrace
        mMockListener.testFailed(EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                    (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where test process crashes.
     */
    @SuppressWarnings("unchecked")
    public void testRun_testCrash() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testCrash");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_TEST_METHOD);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                    (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test run scenario where test run hangs indefinitely, and times out.
     */
    @SuppressWarnings("unchecked")
    public void testRun_testTimeout() throws DeviceNotAvailableException {
        Log.i(LOG_TAG, "testRun_testTimeout");

        final int timeout = 1000;
        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setShellTimeout(timeout);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test run scenario where device reboots during test run.
     */
    @SuppressWarnings("unchecked")
    public void testRun_deviceReboot() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceReboot");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                    (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        // fork off a thread to do the reboot
        Thread rebootThread = new Thread() {
            @Override
            public void run() {
                // wait for test run to begin
                try {
                    Thread.sleep(2000);
                    Runtime.getRuntime().exec(
                            String.format("adb -s %s reboot", getDevice().getIDevice()
                                    .getSerialNumber()));
                } catch (InterruptedException e) {
                    Log.w(LOG_TAG, "interrupted");
                } catch (IOException e) {
                    Log.w(LOG_TAG, "IOException when rebooting");
                }
            }
        };
        rebootThread.start();
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
        // now run the ui tests and verify success
        // done to ensure keyguard is cleared after reboot
        InstrumentationTest uiTest = new InstrumentationTest();
        uiTest.setPackageName(TestAppConstants.UITESTAPP_PACKAGE);
        uiTest.setDevice(getDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        uiTest.run(uilistener);
        assertFalse(uilistener.hasFailedTests());
        assertEquals(TestAppConstants.UI_TOTAL_TESTS,
                uilistener.getNumTestsInState(TestStatus.PASSED));
    }

    /**
     * Test run scenario where device runtime resets during test run.
     * <p/>
     * TODO: this test probably belongs more in TestDeviceFuncTest
     */
    @SuppressWarnings("unchecked")
    public void testRun_deviceRuntimeReset() throws Exception {
        Log.i(LOG_TAG, "testRun_deviceRuntimeReset");

        TestIdentifier expectedTest = new TestIdentifier(TestAppConstants.TESTAPP_CLASS,
                TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mMockListener.testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        mMockListener.testStarted(EasyMock.eq(expectedTest));
        mMockListener.testFailed(EasyMock.eq(expectedTest),
                (String)EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.eq(expectedTest),
                    (Map<String, String>)EasyMock.anyObject());
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mMockListener);
        // fork off a thread to do the runtime reset
        Thread resetThread = new Thread() {
            @Override
            public void run() {
                // wait for test run to begin
                try {
                    Thread.sleep(2000);
                    Runtime.getRuntime().exec(
                            String.format("adb -s %s shell stop", getDevice().getIDevice()
                                    .getSerialNumber()));
                    Thread.sleep(500);
                    Runtime.getRuntime().exec(
                            String.format("adb -s %s shell start", getDevice().getIDevice()
                                    .getSerialNumber()));
                } catch (InterruptedException e) {
                    Log.w(LOG_TAG, "interrupted");
                } catch (IOException e) {
                    Log.w(LOG_TAG, "IOException when rebooting");
                }
            }
        };
        resetThread.start();
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockListener);
        // now run the ui tests and verify success
        // done to ensure keyguard is cleared after runtime reset
        InstrumentationTest uiTest = new InstrumentationTest();
        uiTest.setPackageName(TestAppConstants.UITESTAPP_PACKAGE);
        uiTest.setDevice(getDevice());
        CollectingTestListener uilistener = new CollectingTestListener();
        uiTest.run(uilistener);
        assertFalse(uilistener.hasFailedTests());
        assertEquals(TestAppConstants.UI_TOTAL_TESTS,
                uilistener.getNumTestsInState(TestStatus.PASSED));
    }

    /**
     * Test running all the tests with rerun on. At least one method will cause run to stop
     * (currently TIMEOUT_TEST_METHOD and CRASH_TEST_METHOD). Verify that results are recorded for
     * all tests in the suite.
     */
    public void testRun_rerun() throws Exception {
        Log.i(LOG_TAG, "testRun_rerun");

        // run all tests in class
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setRerunMode(true);
        mInstrumentationTest.setShellTimeout(1000);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(listener);
        assertEquals(TestAppConstants.TOTAL_TEST_CLASS_TESTS, listener.getNumTotalTests());
        assertEquals(TestAppConstants.TOTAL_TEST_CLASS_PASSED_TESTS,
                listener.getNumTestsInState(TestStatus.PASSED));
    }

    /**
     * Test a run that crashes when collecting tests.
     * <p/>
     * Expect run to proceed, but be reported as a run failure
     */
    public void testRun_rerunCrash() throws Exception {
        Log.i(LOG_TAG, "testRun_rerunCrash");

        mInstrumentationTest.setClassName(TestAppConstants.CRASH_ON_INIT_TEST_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_ON_INIT_TEST_METHOD);
        mInstrumentationTest.setRerunMode(true);
        mInstrumentationTest.setShellTimeout(1000);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(listener);
        assertEquals(0, listener.getNumTotalTests());
        assertNotNull(listener.getCurrentRunResults());
        assertEquals(TestAppConstants.TESTAPP_PACKAGE, listener.getCurrentRunResults().getName());
        assertTrue(listener.getCurrentRunResults().isRunFailure());
        assertTrue(listener.getCurrentRunResults().isRunComplete());
    }

    /**
     * Test a run that hangs when collecting tests.
     * <p/>
     * Expect a run failure to be reported
     */
    public void testRun_rerunHang() throws Exception {
        Log.i(LOG_TAG, "testRun_rerunHang");

        mInstrumentationTest.setClassName(TestAppConstants.HANG_ON_INIT_TEST_CLASS);
        mInstrumentationTest.setRerunMode(true);
        mInstrumentationTest.setShellTimeout(1000);
        mInstrumentationTest.setCollectsTestsShellTimeout(2 * 1000);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(listener);
        assertEquals(0, listener.getNumTotalTests());
        assertTrue(listener.getCurrentRunResults().isRunFailure());
    }
}
