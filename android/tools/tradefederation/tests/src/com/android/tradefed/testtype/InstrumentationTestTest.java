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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link InstrumentationTest}
 */
public class InstrumentationTestTest extends TestCase {

    private static final int TEST_TIMEOUT = 0;
    private static final long SHELL_TIMEOUT = 0;
    private static final String TEST_PACKAGE_VALUE = "com.foo";
    private static final String TEST_RUNNER_VALUE = ".FooRunner";
    private static final TestIdentifier TEST1 = new TestIdentifier("Test", "test1");
    private static final TestIdentifier TEST2 = new TestIdentifier("Test", "test2");
    private static final String RUN_ERROR_MSG = "error";
    private static final Map<String, String> EMPTY_STRING_MAP = Collections.emptyMap();
    private static final int COLLECT_TESTS_SHELL_TIMEOUT = 1;

    /** The {@link InstrumentationTest} under test, with all dependencies mocked out */
    private InstrumentationTest mInstrumentationTest;

    // The mock objects.
    private IDevice mMockIDevice;
    private ITestDevice mMockTestDevice;
    private IRemoteAndroidTestRunner mMockRemoteRunner;
    private ITestInvocationListener mMockListener;

    /**
     * Helper class for providing an EasyMock {@link IAnswer} to a
     * {@link ITestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, ITestRunListener...)}
     * call.
     */
    private static abstract class CollectTestAnswer implements IAnswer<Boolean> {

        @Override
        public Boolean answer() throws Throwable {
            Object[] args = EasyMock.getCurrentArguments();
            return answer((IRemoteAndroidTestRunner)args[0], (ITestRunListener)args[1]);
        }

        public abstract Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener);
    }

    /**
     * Helper class for providing an EasyMock {@link IAnswer} to a
     * {@link ITestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} call.
     */
    private static abstract class RunTestAnswer implements IAnswer<Boolean> {

        @Override
        public Boolean answer() throws Throwable {
            Object[] args = EasyMock.getCurrentArguments();
            return answer((IRemoteAndroidTestRunner)args[0], (ITestRunListener)args[1]);

        }

        public abstract Boolean answer(IRemoteAndroidTestRunner runner,
                ITestRunListener listener) throws DeviceNotAvailableException;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mMockIDevice);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("serial");
        mMockRemoteRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mInstrumentationTest = new InstrumentationTest() {
            @Override
            IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName,
                    String runnerName, IDevice device) {
                return mMockRemoteRunner;
            }
        };
        mInstrumentationTest.setPackageName(TEST_PACKAGE_VALUE);
        mInstrumentationTest.setRunnerName(TEST_RUNNER_VALUE);
        mInstrumentationTest.setDevice(mMockTestDevice);
        // default to no rerun, for simplicity
        mInstrumentationTest.setRerunMode(false);
        // default to no timeout for simplicity
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mMockRemoteRunner.setMaxTimeToOutputResponse(SHELL_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.addInstrumentationArg(InstrumentationTest.TEST_TIMEOUT_INST_ARGS_KEY,
                Long.toString(SHELL_TIMEOUT));
        mInstrumentationTest.setCollectsTestsShellTimeout(COLLECT_TESTS_SHELL_TIMEOUT);
    }

    /**
     * Test normal run scenario.
     */
    public void testRun() throws Exception {
        // verify the mock listener is passed through to the runner
        RunTestAnswer runTestResponse = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner,
                    ITestRunListener listener) {
                listener.testRunStarted("run", 1);
                return true;
            }
        };
        setRunTestExpectations(runTestResponse);
        mMockListener.testRunStarted("run", 1);
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class specified.
     */
    public void testRun_class() throws Exception {
        final String className = "FooTest";
        mMockRemoteRunner.setClassName(className);
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test normal run scenario with a test class and method specified.
     */
    public void testRun_classMethod() throws Exception {
        final String className = "FooTest";
        final String methodName = "testFoo";
        mMockRemoteRunner.setMethodName(className, methodName);
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.setMethodName(methodName);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test normal run scenario with a test package specified.
     */
    public void testRun_testPackage() throws Exception {
        final String testPackageName = "com.foo";
        // expect this call
        mMockRemoteRunner.setTestPackageName(testPackageName);
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mInstrumentationTest.setTestPackageName(testPackageName);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Verify test package name is not passed to the runner if class name is set
     */
    public void testRun_testPackageAndClass() throws Exception {
        final String testClassName = "FooTest";
        // expect this call
        mMockRemoteRunner.setClassName(testClassName);
        setRunTestExpectations();
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice);
        mInstrumentationTest.setTestPackageName("com.foo");
        mInstrumentationTest.setClassName(testClassName);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting package.
     */
    public void testRun_noPackage() throws Exception {
        mInstrumentationTest.setPackageName(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws Exception {
        mInstrumentationTest.setDevice(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test the rerun mode when test run has no tests.
     */
    public void testRun_rerunEmpty() throws Exception {
        mInstrumentationTest.setRerunMode(true);
        // expect test collection mode run first to collect tests
        mMockRemoteRunner.setTestCollection(true);
        // collect tests run
        CollectTestAnswer collectTestResponse = new CollectTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                listener.testRunStarted(TEST_PACKAGE_VALUE, 0);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                return true;
            }
        };
        setCollectTestsExpectations(collectTestResponse);
        // expect normal mode to be turned back on
        mMockRemoteRunner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.setTestCollection(false);

        // note: expect run to not be reported
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice, mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice, mMockListener);
    }

    /**
     * Test the rerun mode when first test run fails.
     */
    public void testRun_rerun() throws Exception {
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner,
                    ITestRunListener listener) {
                // perform call back on listener to show run failed - only one test
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(TEST1);
                listener.testEnded(TEST1, EMPTY_STRING_MAP);
                listener.testRunFailed(RUN_ERROR_MSG);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                return true;
            }
        };
        setRerunExpectations(firstRunAnswer, false);

        EasyMock.replay(mMockRemoteRunner, mMockTestDevice, mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice, mMockListener);
    }

    /**
     * Test the reboot before re-run option.
     */
    public void testRun_rebootBeforeReRun() throws Exception {
        mInstrumentationTest.setRebootBeforeReRun(true);
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner,
                    ITestRunListener listener) {
                // perform call back on listener to show run failed - only one test
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(TEST1);
                listener.testEnded(TEST1, EMPTY_STRING_MAP);
                listener.testRunFailed(RUN_ERROR_MSG);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                return true;
            }
        };
        setRerunExpectations(firstRunAnswer, true);

        EasyMock.replay(mMockRemoteRunner, mMockTestDevice, mMockListener);
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice, mMockListener);
    }

    /**
     * Test resuming a test run when first run is aborted due to
     * {@link DeviceNotAvailableException}
     */
    public void testRun_resume() throws Exception {
        RunTestAnswer firstRunResponse = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener)
                    throws DeviceNotAvailableException {
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(TEST1);
                listener.testEnded(TEST1, EMPTY_STRING_MAP);
                listener.testRunFailed(RUN_ERROR_MSG);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                throw new DeviceNotAvailableException();
            }
        };
        setRerunExpectations(firstRunResponse, false);
        mMockRemoteRunner.setMaxTimeToOutputResponse(SHELL_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.addInstrumentationArg(InstrumentationTest.TEST_TIMEOUT_INST_ARGS_KEY,
                Long.toString(SHELL_TIMEOUT));
        EasyMock.replay(mMockRemoteRunner, mMockTestDevice, mMockListener);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        mInstrumentationTest.run(mMockListener);
        EasyMock.verify(mMockRemoteRunner, mMockTestDevice, mMockListener);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run with negative timeout args.
     */
    public void testRun_negativeTimeouts() throws Exception {
        mInstrumentationTest.setShellTimeout(-1);
        mInstrumentationTest.setTestTimeout(-2);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Set EasyMock expectations for a run that fails.
     *
     * @param firstRunAnswer the behavior callback of the first run. It should perform callbacks
     * on listeners to indicate only TEST1 was run
     */
    private void setRerunExpectations(RunTestAnswer firstRunAnswer, boolean rebootBeforeReRun)
        throws DeviceNotAvailableException {
        mInstrumentationTest.setRerunMode(true);
        // expect test collection mode run first to collect tests
        mMockRemoteRunner.setTestCollection(true);
        CollectTestAnswer collectTestAnswer = new CollectTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // perform call back on listener to show run of two tests
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(TEST1);
                listener.testEnded(TEST1, EMPTY_STRING_MAP);
                listener.testStarted(TEST2);
                listener.testEnded(TEST2, EMPTY_STRING_MAP);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                return true;
            }
        };
        setCollectTestsExpectations(collectTestAnswer);

        // now expect second run with test collection mode off
        mMockRemoteRunner.setMaxTimeToOutputResponse(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        mMockRemoteRunner.setTestCollection(false);
        setRunTestExpectations(firstRunAnswer);

        if (rebootBeforeReRun) {
          mMockTestDevice.reboot();
        }

        // now expect second run to run remaining test
        RunTestAnswer secondRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner,
                    ITestRunListener listener) {
                // TODO: assert runner has proper class and method name
                // assertEquals(test2.getClassName(), runner.getClassName());
                // assertEquals(test2.getMethodName(), runner.getMethodName());
                // perform call back on listeners to show run remaining test was run
                listener.testRunStarted(TEST_PACKAGE_VALUE, 1);
                listener.testStarted(TEST2);
                listener.testEnded(TEST2, EMPTY_STRING_MAP);
                listener.testRunEnded(1, EMPTY_STRING_MAP);
                return true;
            }
        };
        setRunTestExpectations(secondRunAnswer);

        // expect both TEST1 and TEST2 to be executed
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 2);
        mMockListener.testStarted(TEST1);
        mMockListener.testEnded(TEST1, EMPTY_STRING_MAP);
        mMockListener.testRunFailed(RUN_ERROR_MSG);
        mMockListener.testRunEnded(1, EMPTY_STRING_MAP);
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 1);
        mMockListener.testStarted(TEST2);
        mMockListener.testEnded(TEST2, EMPTY_STRING_MAP);
        mMockListener.testRunEnded(1, EMPTY_STRING_MAP);
    }

    /**
     * Test that IllegalArgumentException is thrown if an invalid test size is provided.
     */
    public void testRun_badTestSize() throws Exception {
        mInstrumentationTest.setTestSize("foo");
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    private void setCollectTestsExpectations(CollectTestAnswer collectTestAnswer)
            throws DeviceNotAvailableException {
        EasyMock.expect(
                mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                        (ITestRunListener)EasyMock.anyObject())).andAnswer(collectTestAnswer);
    }

    private void setRunTestExpectations(RunTestAnswer secondRunAnswer)
            throws DeviceNotAvailableException {
        EasyMock.expect(
                mMockTestDevice.runInstrumentationTests(
                        (IRemoteAndroidTestRunner)EasyMock.anyObject(),
                        (ITestRunListener)EasyMock.anyObject())).andAnswer(
                secondRunAnswer);
    }

    private void setRunTestExpectations() throws DeviceNotAvailableException {
        EasyMock.expect(
                mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                        (ITestRunListener)EasyMock.anyObject()))
                .andReturn(Boolean.TRUE);
    }
}
