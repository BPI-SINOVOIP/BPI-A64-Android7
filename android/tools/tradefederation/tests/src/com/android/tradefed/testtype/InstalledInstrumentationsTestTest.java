/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.util.Map;

/**
 * Unit tests for {@link InstalledInstrumentationsTestTest}.
 */
public class InstalledInstrumentationsTestTest extends TestCase {

    private static final String TEST_PKG = "com.example.tests";
    private static final String TEST_COVERAGE_TARGET = "com.example";
    private static final String TEST_RUNNER = "android.support.runner.AndroidJUnitRunner";
    private static final String ABI = "forceMyAbiSettingPlease";
    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;
    private MockInstrumentationTest mMockInstrumentationTest;
    private InstalledInstrumentationsTest mInstalledInstrTest;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("foo");
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockInstrumentationTest = new MockInstrumentationTest();

        mInstalledInstrTest = new InstalledInstrumentationsTest() {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mMockInstrumentationTest;
            }
        };
        mInstalledInstrTest.setDevice(mMockTestDevice);
    }

    /**
     * Test the run normal case. Simple verification that expected data is passed along, etc.
     */
    public void testRun() throws Exception {
        injectListInstrResponse();
        mMockListener.testRunStarted(TEST_PKG, 0);
        Capture<Map<String, String>> captureMetrics = new Capture<Map<String, String>>();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captureMetrics));
        ArgsOptionParser p = new ArgsOptionParser(mInstalledInstrTest);
        p.parse("--size", "small", "--force-abi", ABI);
        mInstalledInstrTest.setSendCoverage(true);
        EasyMock.replay(mMockTestDevice, mMockListener);
        mInstalledInstrTest.run(mMockListener);
        assertEquals(mMockListener, mMockInstrumentationTest.getListener());
        assertEquals(TEST_PKG, mMockInstrumentationTest.getPackageName());
        assertEquals(TEST_RUNNER, mMockInstrumentationTest.getRunnerName());
        assertEquals(TEST_COVERAGE_TARGET, captureMetrics.getValue().get(
                InstalledInstrumentationsTest.COVERAGE_TARGET_KEY));
        assertEquals("small", mMockInstrumentationTest.getTestSize());
        assertEquals(ABI, mMockInstrumentationTest.getForceAbi());
    }

    private void injectListInstrResponse() throws DeviceNotAvailableException {
        injectShellResponse(String.format("instrumentation:%s/%s (target=%s)\r\n", TEST_PKG,
                TEST_RUNNER, TEST_COVERAGE_TARGET));
    }

    @SuppressWarnings("unchecked")
    private void injectShellResponse(final String shellResponse)
            throws DeviceNotAvailableException {
        IAnswer<Object> shellAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                IShellOutputReceiver receiver =
                        (IShellOutputReceiver)EasyMock.getCurrentArguments()[1];
                byte[] bytes = shellResponse.getBytes();
                receiver.addOutput(bytes, 0, bytes.length);
                receiver.flush();
                return null;
            }
        };
        mMockTestDevice.executeShellCommand(EasyMock.<String>anyObject(),
                EasyMock.<IShellOutputReceiver>anyObject());
        EasyMock.expectLastCall().andAnswer(shellAnswer);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws Exception {
        injectListInstrResponse();
        mInstalledInstrTest.setDevice(null);
        try {
            mInstalledInstrTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertNull(mMockInstrumentationTest.getPackageName());
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run when no instrumentations
     * are present.
     */
    public void testRun_noInstr() throws Exception {
        injectShellResponse("");
        try {
            mInstalledInstrTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertNull(mMockInstrumentationTest.getPackageName());
    }
}
