/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Unit tests for {@link InstrumentationFileTest}.
 */
public class InstrumentationFileTestTest extends TestCase {

    private static final String TEST_PACKAGE_VALUE = "com.foo";

    /** The {@link InstrumentationFileTest} under test, with all dependencies mocked out */
    private InstrumentationFileTest mInstrumentationFileTest;

    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;

    private File mTestFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestFile = null;

        IDevice mockIDevice = EasyMock.createMock(IDevice.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        EasyMock.expect(mMockTestDevice.getIDevice()).andStubReturn(mockIDevice);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("serial");
    }

    /**
     * Test normal run scenario with a single test.
     */
    @SuppressWarnings("unchecked")
    public void testRun_singleSuccessfulTest() throws DeviceNotAvailableException, IOException,
            ConfigurationException {
        final Collection<TestIdentifier> testsList = new ArrayList<>(1);
        final TestIdentifier test = new TestIdentifier("ClassFoo", "methodBar");
        testsList.add(test);

        // verify the mock listener is passed through to the runner
        RunTestAnswer runTestResponse = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner,
                    ITestRunListener listener) {
                listener.testRunStarted(TEST_PACKAGE_VALUE, 1);
                listener.testStarted(test);
                listener.testEnded(test, Collections.EMPTY_MAP);
                listener.testRunEnded(0, Collections.EMPTY_MAP);
                return true;
            }
        };
        setRunTestExpectations(runTestResponse);

        // mock out InstrumentationTest that will be used to create InstrumentationFileTest
        final InstrumentationTest mockITest = new InstrumentationTest();
        mockITest.setDevice(mMockTestDevice);
        mockITest.setPackageName(TEST_PACKAGE_VALUE);

        mInstrumentationFileTest = new InstrumentationFileTest(mockITest, testsList, true, -1) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
            @Override
            boolean pushFileToTestDevice(File file, String destinationPath)
                    throws DeviceNotAvailableException {
                // simulate successful push and store created file
                mTestFile = file;
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(testsList);
                return true;
            }
            @Override
            void deleteTestFileFromDevice(String pathToFile) throws DeviceNotAvailableException {
                //ignore
            }
        };

        // mock successful test run lifecycle
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 1);
        mMockListener.testStarted(test);
        mMockListener.testEnded(test, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationFileTest.run(mMockListener);
        assertEquals(mMockTestDevice, mockITest.getDevice());
    }

    /**
     * Test re-run scenario when 1 out of 3 tests fails to complete but is successful after re-run
     */
    @SuppressWarnings("unchecked")
    public void testRun_reRunOneFailedToCompleteTest()
            throws DeviceNotAvailableException, IOException, ConfigurationException {
        final Collection<TestIdentifier> testsList = new ArrayList<>(1);
        final TestIdentifier test1 = new TestIdentifier("ClassFoo1", "methodBar1");
        final TestIdentifier test2 = new TestIdentifier("ClassFoo2", "methodBar2");
        final TestIdentifier test3 = new TestIdentifier("ClassFoo3", "methodBar3");
        testsList.add(test1);
        testsList.add(test2);
        testsList.add(test3);

        // verify the test1 is completed and test2 was started but never finished
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // first test started and ended successfully
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(test1);
                listener.testEnded(test1, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                // second test started but never finished
                listener.testStarted(test2);
                return true;
            }
        };
        setRunTestExpectations(firstRunAnswer);

        // now expect second run to rerun remaining test3 and test2
        RunTestAnswer secondRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // third test started and ended successfully
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(test3);
                listener.testEnded(test3, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                // second test is rerun but completed successfully this time
                listener.testStarted(test2);
                listener.testEnded(test2, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                return true;
            }
        };
        setRunTestExpectations(secondRunAnswer);

        // mock out InstrumentationTest that will be used to create InstrumentationFileTest
        final InstrumentationTest mockITest = new InstrumentationTest();
        mockITest.setDevice(mMockTestDevice);
        mockITest.setPackageName(TEST_PACKAGE_VALUE);

        mInstrumentationFileTest = new InstrumentationFileTest(mockITest, testsList, true, -1) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
            @Override
            boolean pushFileToTestDevice(File file, String destinationPath)
                    throws DeviceNotAvailableException {
                // simulate successful push and store created file
                mTestFile = file;
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(testsList);
                return true;
            }
            @Override
            void deleteTestFileFromDevice(String pathToFile) throws DeviceNotAvailableException {
                //ignore
            }
        };

        // First run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 2);
        // expect test1 to start and finish successfully
        mMockListener.testStarted(test1);
        mMockListener.testEnded(test1, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        // expect test2 to start but never finish
        mMockListener.testStarted(test2);
        // Second run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 2);
        // expect test3 to start and finish successfully
        mMockListener.testStarted(test3);
        mMockListener.testEnded(test3, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        // expect to rerun test2 successfully
        mMockListener.testStarted(test2);
        mMockListener.testEnded(test2, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationFileTest.run(mMockListener);
        assertEquals(mMockTestDevice, mockITest.getDevice());
    }

    /**
     * Test re-run scenario when 2 remaining tests fail to complete and need to be run serially
     */
    @SuppressWarnings("unchecked")
    public void testRun_serialReRunOfTwoFailedToCompleteTests()
            throws DeviceNotAvailableException, IOException, ConfigurationException {
        final Collection<TestIdentifier> testsList = new ArrayList<>(1);
        final TestIdentifier test1 = new TestIdentifier("ClassFoo1", "methodBar1");
        final TestIdentifier test2 = new TestIdentifier("ClassFoo2", "methodBar2");
        testsList.add(test1);
        testsList.add(test2);

        // verify the test1 and test2 started but never completed
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // first and second tests started but never completed
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(test1);
                listener.testStarted(test2);
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(testsList);
                return true;
            }
        };
        setRunTestExpectations(firstRunAnswer);

        // verify successful serial execution of test1
        RunTestAnswer firstSerialRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // first test started and ended successfully in serial mode
                listener.testRunStarted(TEST_PACKAGE_VALUE, 1);
                listener.testStarted(test1);
                listener.testEnded(test1, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                return true;
            }
        };
        setRunTestExpectations(firstSerialRunAnswer);

        // verify successful serial execution of test2
        RunTestAnswer secdondSerialRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // Second test started and ended successfully in serial mode
                listener.testRunStarted(TEST_PACKAGE_VALUE, 1);
                listener.testStarted(test2);
                listener.testEnded(test2, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                return true;
            }
        };
        setRunTestExpectations(secdondSerialRunAnswer);

        // mock out InstrumentationTest that will be used to create InstrumentationFileTest
        final InstrumentationTest mockITest = new InstrumentationTest();
        mockITest.setDevice(mMockTestDevice);
        mockITest.setPackageName(TEST_PACKAGE_VALUE);

        mInstrumentationFileTest = new InstrumentationFileTest(mockITest, testsList, true, -1) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
            @Override
            boolean pushFileToTestDevice(File file, String destinationPath)
                    throws DeviceNotAvailableException {
                // simulate successful push and store created file
                mTestFile = file;
                return true;
            }
            @Override
            void deleteTestFileFromDevice(String pathToFile) throws DeviceNotAvailableException {
                //ignore
            }
        };

        // First run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 2);
        // expect test1 and test 2 to start but never finish
        mMockListener.testStarted(test1);
        mMockListener.testStarted(test2);

        // re-run test1 and test2 serially
        // first serial re-run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 1);
        // expect test1 to start and finish successfully
        mMockListener.testStarted(test1);
        mMockListener.testEnded(test1, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        // first serial re-run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 1);
        // expect test2 to start and finish successfully
        mMockListener.testStarted(test2);
        mMockListener.testEnded(test2, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationFileTest.run(mMockListener);
        assertEquals(mMockTestDevice, mockITest.getDevice());
        // test file is expected to be null since we defaulted to serial test execution
        assertEquals(null, mockITest.getTestFilePathOnDevice());
    }

    /**
     * Test no serial re-run tests fail to complete.
     */
    @SuppressWarnings("unchecked")
    public void testRun_noSerialReRun()
            throws DeviceNotAvailableException, IOException, ConfigurationException {
        final Collection<TestIdentifier> testsList = new ArrayList<>(1);
        final TestIdentifier test1 = new TestIdentifier("ClassFoo1", "methodBar1");
        final TestIdentifier test2 = new TestIdentifier("ClassFoo2", "methodBar2");
        testsList.add(test1);
        testsList.add(test2);

        // verify the test1 and test2 started but never completed
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // first and second tests started but never completed
                listener.testRunStarted(TEST_PACKAGE_VALUE, 2);
                listener.testStarted(test1);
                listener.testStarted(test2);
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(testsList);
                return true;
            }
        };
        setRunTestExpectations(firstRunAnswer);

        // mock out InstrumentationTest that will be used to create InstrumentationFileTest
        final InstrumentationTest mockITest = new InstrumentationTest();
        mockITest.setDevice(mMockTestDevice);
        mockITest.setPackageName(TEST_PACKAGE_VALUE);

        mInstrumentationFileTest = new InstrumentationFileTest(mockITest, testsList, false, -1) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
            @Override
            boolean pushFileToTestDevice(File file, String destinationPath)
                    throws DeviceNotAvailableException {
                // simulate successful push and store created file
                mTestFile = file;
                return true;
            }
            @Override
            void deleteTestFileFromDevice(String pathToFile) throws DeviceNotAvailableException {
                //ignore
            }
        };

        // First run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 2);
        // expect test1 and test 2 to start but never finish
        mMockListener.testStarted(test1);
        mMockListener.testStarted(test2);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationFileTest.run(mMockListener);
        assertEquals(mMockTestDevice, mockITest.getDevice());
    }

    /**
     * Test attempting times exceed max attempts.
     */
    @SuppressWarnings("unchecked")
    public void testRun_exceedMaxAttempts()
            throws DeviceNotAvailableException, IOException, ConfigurationException {
        final ArrayList<TestIdentifier> testsList = new ArrayList<>(1);
        final TestIdentifier test1 = new TestIdentifier("ClassFoo1", "methodBar1");
        final TestIdentifier test2 = new TestIdentifier("ClassFoo2", "methodBar2");
        final TestIdentifier test3 = new TestIdentifier("ClassFoo3", "methodBar3");
        final TestIdentifier test4 = new TestIdentifier("ClassFoo4", "methodBar4");
        final TestIdentifier test5 = new TestIdentifier("ClassFoo5", "methodBar5");
        final TestIdentifier test6 = new TestIdentifier("ClassFoo6", "methodBar6");

        testsList.add(test1);
        testsList.add(test2);
        testsList.add(test3);
        testsList.add(test4);
        testsList.add(test5);
        testsList.add(test6);

        final ArrayList<TestIdentifier> expectedTestsList = new ArrayList<>(testsList);

        // test1 fininshed, test2 started but not finished.
        RunTestAnswer firstRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                listener.testRunStarted(TEST_PACKAGE_VALUE, 6);
                // first test started and ended successfully
                listener.testStarted(test1);
                listener.testEnded(test1, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                // second test started but never finished
                listener.testStarted(test2);
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(expectedTestsList);
                return true;
            }
        };
        setRunTestExpectations(firstRunAnswer);

        // test2 finished, test3 started but not finished.
        RunTestAnswer secondRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // test2 started and ended successfully
                listener.testRunStarted(TEST_PACKAGE_VALUE, 5);
                listener.testStarted(test2);
                listener.testEnded(test2, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                // test3 started but never finished
                listener.testStarted(test3);
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(expectedTestsList.subList(1, expectedTestsList.size()));
                return true;
            }
        };
        setRunTestExpectations(secondRunAnswer);

        // test3 finished, test4 started but not finished.
        RunTestAnswer thirdRunAnswer = new RunTestAnswer() {
            @Override
            public Boolean answer(IRemoteAndroidTestRunner runner, ITestRunListener listener) {
                // test3 started and ended successfully
                listener.testRunStarted(TEST_PACKAGE_VALUE, 4);
                listener.testStarted(test3);
                listener.testEnded(test3, Collections.EMPTY_MAP);
                listener.testRunEnded(1, Collections.EMPTY_MAP);
                // test4 started but never finished
                listener.testStarted(test4);
                // verify that the content of the testFile contains all expected tests
                verifyTestFile(expectedTestsList.subList(2, expectedTestsList.size()));
                return true;
            }
        };
        setRunTestExpectations(thirdRunAnswer);

        // mock out InstrumentationTest that will be used to create InstrumentationFileTest
        final InstrumentationTest mockITest = new InstrumentationTest();
        mockITest.setDevice(mMockTestDevice);
        mockITest.setPackageName(TEST_PACKAGE_VALUE);

        mInstrumentationFileTest = new InstrumentationFileTest(mockITest, testsList, false, 3) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
            @Override
            boolean pushFileToTestDevice(File file, String destinationPath)
                    throws DeviceNotAvailableException {
                // simulate successful push and store created file
                mTestFile = file;
                return true;
            }
            @Override
            void deleteTestFileFromDevice(String pathToFile) throws DeviceNotAvailableException {
                //ignore
            }
        };

        // First run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 6);
        mMockListener.testStarted(test1);
        mMockListener.testEnded(test1, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        mMockListener.testStarted(test2);

        // Second run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 5);
        mMockListener.testStarted(test2);
        mMockListener.testEnded(test2, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        mMockListener.testStarted(test3);

        // Third run:
        mMockListener.testRunStarted(TEST_PACKAGE_VALUE, 4);
        mMockListener.testStarted(test3);
        mMockListener.testEnded(test3, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);
        mMockListener.testStarted(test4);

        // MAX_ATTEMPTS is 3, so there will be no forth run.

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationFileTest.run(mMockListener);
        assertEquals(mMockTestDevice, mockITest.getDevice());
    }

    /**
     * Helper class that verifies tetFile's content match the expected list of test to be run
     * @param testsList  list of test to be executed
     */
    private void verifyTestFile(Collection<TestIdentifier> testsList) {
        // fail if the file was never created
        assertNotNull(mTestFile);

        try (BufferedReader br = new BufferedReader(new FileReader(mTestFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] str = line.split("#");
                TestIdentifier test = new TestIdentifier(str[0], str[1]);
                assertTrue(String.format(
                        "Test with class name: %s and method name: %s does not exists",
                        test.getClassName(), test.getTestName()), testsList.contains(test));
            }
        } catch (IOException e) {
            // fail if the file is corrupt in any way
            fail("failed reading test file");
        }
    }

    /**
     * Helper class for providing an EasyMock {@link IAnswer} to a
     * {@link ITestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} call.
     */
    private static abstract class RunTestAnswer implements IAnswer<Boolean> {
        @Override
        public Boolean answer() throws Throwable {
            Object[] args = EasyMock.getCurrentArguments();
            return answer((IRemoteAndroidTestRunner) args[0], (ITestRunListener) args[1]);
        }

        public abstract Boolean answer(IRemoteAndroidTestRunner runner,
                ITestRunListener listener) throws DeviceNotAvailableException;
    }

    private void setRunTestExpectations(RunTestAnswer runTestResponse)
            throws DeviceNotAvailableException {

        EasyMock.expect(mMockTestDevice
                .runInstrumentationTests((IRemoteAndroidTestRunner) EasyMock.anyObject(),
                        (ITestRunListener) EasyMock.anyObject())).andAnswer(runTestResponse);
    }
}
