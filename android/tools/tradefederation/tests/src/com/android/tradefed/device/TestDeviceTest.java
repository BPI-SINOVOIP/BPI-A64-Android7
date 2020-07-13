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
package com.android.tradefed.device;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.device.ITestDevice.MountPointInfo;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.common.util.concurrent.SettableFuture;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IExpectationSetters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link TestDevice}.
 */
public class TestDeviceTest extends TestCase {

    private static final String MOCK_DEVICE_SERIAL = "serial";
    private IDevice mMockIDevice;
    private IShellOutputReceiver mMockReceiver;
    private TestDevice mTestDevice;
    private TestDevice mRecoveryTestDevice;
    private TestDevice mNoFastbootTestDevice;
    private IDeviceRecovery mMockRecovery;
    private IDeviceStateMonitor mMockStateMonitor;
    private IRunUtil mMockRunUtil;
    private IWifiHelper mMockWifi;
    private IDeviceMonitor mMockDvcMonitor;

    /**
     * A {@link TestDevice} that is suitable for running tests against
     */
    private class TestableTestDevice extends TestDevice {
        public TestableTestDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public void postBootSetup() {
            // too annoying to mock out postBootSetup actions everyone, so do nothing
        }

        @Override
        IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        void doReboot() throws DeviceNotAvailableException, UnsupportedOperationException {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn(MOCK_DEVICE_SERIAL).anyTimes();
        mMockReceiver = EasyMock.createMock(IShellOutputReceiver.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockDvcMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockWifi = EasyMock.createMock(IWifiHelper.class);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice = new TestableTestDevice() {
            @Override
            public void recoverDevice() throws DeviceNotAvailableException {
                // ignore
            }

            @Override
            IWifiHelper createWifiHelper() {
                return mMockWifi;
            }
        };
        mTestDevice.setRecovery(mMockRecovery);
        mTestDevice.setCommandTimeout(100);
        mTestDevice.setLogStartDelay(-1);

        // TestDevice with intact recoverDevice()
        mRecoveryTestDevice = new TestableTestDevice();
        mRecoveryTestDevice.setRecovery(mMockRecovery);
        mRecoveryTestDevice.setCommandTimeout(100);
        mRecoveryTestDevice.setLogStartDelay(-1);

        // TestDevice without fastboot
        mNoFastbootTestDevice = new TestableTestDevice();
        mNoFastbootTestDevice.setFastbootEnabled(false);
        mNoFastbootTestDevice.setRecovery(mMockRecovery);
        mNoFastbootTestDevice.setCommandTimeout(100);
        mNoFastbootTestDevice.setLogStartDelay(-1);
    }

    /**
     * Test {@link TestDevice#enableAdbRoot()} when adb is already root
     */
    public void testEnableAdbRoot_alreadyRoot() throws Exception {
        injectShellResponse("id", "uid=0(root) gid=0(root)");
        EasyMock.replay(mMockIDevice);
        assertTrue(mTestDevice.enableAdbRoot());
    }

    /**
     * Test {@link TestDevice#enableAdbRoot()} when adb is not root
     */
    public void testEnableAdbRoot_notRoot() throws Exception {
        setEnableAdbRootExpectations();
        EasyMock.replay(mMockIDevice, mMockRunUtil, mMockStateMonitor);
        assertTrue(mTestDevice.enableAdbRoot());
    }

    /**
     * Test {@link TestDevice#enableAdbRoot()} when "enable-root" is "false"
     */
    public void testEnableAdbRoot_noEnableRoot() throws Exception {
        boolean enableRoot = mTestDevice.getOptions().isEnableAdbRoot();
        mTestDevice.getOptions().setEnableAdbRoot(false);
        assertFalse(mTestDevice.enableAdbRoot());
        mTestDevice.getOptions().setEnableAdbRoot(enableRoot);
    }

    /**
     * Configure EasyMock expectations for a successful adb root call
     */
    private void setEnableAdbRootExpectations() throws Exception {
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell)");
        injectShellResponse("id", "uid=0(root) gid=0(root)");
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        EasyMock.expect(mMockStateMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockStateMonitor.waitForDeviceOnline()).andReturn(
                mMockIDevice);
    }

    /**
     * COnfigure EasMock expectations for a successful adb command call
     * @param command the adb command to execute
     * @param result the {@link CommandResult} expected from the adb command execution
     * @throws Exception
     */
    private void setExecuteAdbCommandExpectations(CommandResult result, String command)
            throws Exception {
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                EasyMock.eq("adb"), EasyMock.eq("-s"), EasyMock.eq(MOCK_DEVICE_SERIAL),
                EasyMock.eq(command))).andReturn(result);
    }

    /**
     * Test that {@link TestDevice#enableAdbRoot()} reattempts adb root
     */
    public void testEnableAdbRoot_rootRetry() throws Exception {
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell)");
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell)");
        injectShellResponse("id", "uid=0(root) gid=0(root)");
        CommandResult adbBadResult = new CommandResult(CommandStatus.SUCCESS);
        adbBadResult.setStdout("");
        setExecuteAdbCommandExpectations(adbBadResult, "root");
        CommandResult adbResult = new CommandResult(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        EasyMock.expect(mMockStateMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE).times(2);
        EasyMock.expect(mMockStateMonitor.waitForDeviceOnline()).andReturn(
                mMockIDevice).times(2);
        EasyMock.replay(mMockIDevice, mMockRunUtil, mMockStateMonitor);
        assertTrue(mTestDevice.enableAdbRoot());
    }

    /**
     * Test that {@link TestDevice#isAdbRoot()} for device without adb root.
     */
    public void testIsAdbRootForNonRoot() throws Exception {
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell)");
        EasyMock.replay(mMockIDevice);
        assertFalse(mTestDevice.isAdbRoot());
    }

    /**
     * Test that {@link TestDevice#isAdbRoot()} for device with adb root.
     */
    public void testIsAdbRootForRoot() throws Exception {
        injectShellResponse("id", "uid=0(root) gid=0(root)");
        EasyMock.replay(mMockIDevice);
        assertTrue(mTestDevice.isAdbRoot());
    }

    /**
     * Test {@link TestDevice#getProductType()} when device is in fastboot and IDevice has not
     * cached product type property
     */
    public void testGetProductType_fastboot() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getProperty(EasyMock.<String>anyObject())).andReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: nexusone\n" + "finished. total time: 0.001s");
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject())).andReturn(
                fastbootResult);
        EasyMock.replay(mMockIDevice, mMockRunUtil);
        mRecoveryTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals("nexusone", mRecoveryTestDevice.getProductType());
    }

    /**
     * Test {@link TestDevice#getProductType()} for a device with a non-alphanumeric fastboot
     * product type
     */
    public void testGetProductType_fastbootNonalpha() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getProperty(EasyMock.<String>anyObject())).andReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: foo-bar\n" + "finished. total time: 0.001s");
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject())).andReturn(
                fastbootResult);
        EasyMock.replay(mMockIDevice, mMockRunUtil);
        mRecoveryTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals("foo-bar", mRecoveryTestDevice.getProductType());
    }

    /**
     * Verify that {@link TestDevice#getProductType()} throws an exception if requesting a product
     * type directly fails while the device is in fastboot.
     */
    public void testGetProductType_fastbootFail() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getProperty(EasyMock.<String>anyObject())).andStubReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: \n" + "finished. total time: 0.001s");
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject(),
                        (String)EasyMock.anyObject(), (String)EasyMock.anyObject())).andReturn(
                fastbootResult).anyTimes();
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRunUtil);
        mTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        try {
            String type = mTestDevice.getProductType();
            fail(String.format("DeviceNotAvailableException not thrown; productType was '%s'",
                    type));
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#getProductType()} when device is in adb and IDevice has not cached
     * product type property
     */
    public void testGetProductType_adb() throws Exception {
        EasyMock.expect(mMockIDevice.getProperty("ro.hardware")).andReturn(null);
        final String expectedOutput = "nexusone";
        injectSystemProperty("ro.hardware", expectedOutput);
        EasyMock.replay(mMockIDevice);
        assertEquals(expectedOutput, mTestDevice.getProductType());
    }

    /**
     * Verify that {@link TestDevice#getProductType()} throws an exception if requesting a product
     * type directly still fails.
     */
    public void testGetProductType_adbFail() throws Exception {
        EasyMock.expect(mMockIDevice.getProperty(EasyMock.<String>anyObject())).andStubReturn(null);
        injectSystemProperty("ro.hardware", null).times(3);
        EasyMock.replay(mMockIDevice);
        try {
            mTestDevice.getProductType();
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#clearErrorDialogs()} when both a error and anr dialog are present.
     */
    public void testClearErrorDialogs() throws Exception {
        final String anrOutput = "debugging=false crashing=false null notResponding=true "
                + "com.android.server.am.AppNotRespondingDialog@4534aaa0 bad=false\n blah\n";
        final String crashOutput = "debugging=false crashing=true "
                + "com.android.server.am.AppErrorDialog@45388a60 notResponding=false null bad=false"
                + "blah \n";
        // construct a string with 2 error dialogs of each type to ensure proper detection
        final String fourErrors = anrOutput + anrOutput + crashOutput + crashOutput;
        injectShellResponse(null, fourErrors);
        mMockIDevice.executeShellCommand((String)EasyMock.anyObject(),
                (IShellOutputReceiver)EasyMock.anyObject(),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        // expect 4 key events to be sent - one for each dialog
        // and expect another dialog query - but return nothing
        EasyMock.expectLastCall().times(5);

        EasyMock.replay(mMockIDevice);
        mTestDevice.clearErrorDialogs();
    }

    public void testGetBugreport_deviceUnavail() throws Exception {
        final String testCommand = "bugreport";
        final String expectedOutput = "this is the output\r\n in two lines\r\n";
        // FIXME: this isn't actually causing a DeviceNotAvailableException to be thrown
        injectShellResponse(testCommand, expectedOutput);
        mMockRecovery.recoverDevice(EasyMock.eq(mMockStateMonitor), EasyMock.eq(false));
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());

        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        assertEquals(expectedOutput, StreamUtil.getStringFromStream(
                mTestDevice.getBugreport().createInputStream()));
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)}.
     * <p/>
     * Verify that the shell command is routed to the IDevice.
     */
    public void testExecuteShellCommand_receiver() throws IOException, DeviceNotAvailableException,
            TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.replay(mMockIDevice);
        mTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /**
     * Simple normal case test for
     * {@link TestDevice#executeShellCommand(String)}.
     * <p/>
     * Verify that the shell command is routed to the IDevice, and shell output is collected.
     */
    public void testExecuteShellCommand() throws Exception {
        final String testCommand = "simple command";
        final String expectedOutput = "this is the output\r\n in two lines\r\n";
        injectShellResponse(testCommand, expectedOutput);
        EasyMock.replay(mMockIDevice);
        assertEquals(expectedOutput, mTestDevice.executeShellCommand(testCommand));
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery immediately fails.
     * <p/>
     * Verify that a DeviceNotAvailableException is thrown.
     */
    public void testExecuteShellCommand_recoveryFail() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new IOException());
        mMockRecovery.recoverDevice(EasyMock.eq(mMockStateMonitor), EasyMock.eq(false));
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        EasyMock.replay(mMockIDevice);
        EasyMock.replay(mMockRecovery);
        try {
            mRecoveryTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and device is in recovery until online mode.
     * <p/>
     * Verify that a DeviceNotAvailableException is thrown.
     */
    public void testExecuteShellCommand_recoveryUntilOnline() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mRecoveryTestDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new IOException());
        mMockRecovery.recoverDevice(EasyMock.eq(mMockStateMonitor), EasyMock.eq(true));
        setEnableAdbRootExpectations();
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.replay(mMockIDevice, mMockRecovery, mMockRunUtil, mMockStateMonitor);
        mRecoveryTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery succeeds.
     * <p/>
     * Verify that command is re-tried.
     */
    public void testExecuteShellCommand_recoveryRetry() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new IOException());
        assertRecoverySuccess();
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        replayMocks();
        mTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /** Set expectations for a successful recovery operation
     */
    private void assertRecoverySuccess() throws DeviceNotAvailableException, IOException,
            TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
        mMockRecovery.recoverDevice(EasyMock.eq(mMockStateMonitor), EasyMock.eq(false));
        // expect post boot up steps
        mMockIDevice.executeShellCommand(EasyMock.eq(mTestDevice.getDisableKeyguardCmd()),
                (IShellOutputReceiver)EasyMock.anyObject(),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * command times out and recovery succeeds.
     * <p/>
     * Verify that command is re-tried.
     */
    public void testExecuteShellCommand_recoveryTimeoutRetry() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called - and never return from that call
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new TimeoutException());
        assertRecoverySuccess();
        // now expect shellCommand to be executed again, and succeed
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        replayMocks();
        mTestDevice.executeShellCommand(testCommand, mMockReceiver);
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} repeatedly throws IOException and recovery succeeds.
     * <p/>
     * Verify that DeviceNotAvailableException is thrown.
     */
    public void testExecuteShellCommand_recoveryAttempts() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        mMockIDevice.executeShellCommand(EasyMock.eq(testCommand), EasyMock.eq(mMockReceiver),
                EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new IOException()).times(
                TestDevice.MAX_RETRY_ATTEMPTS+1);
        for (int i=0; i <= TestDevice.MAX_RETRY_ATTEMPTS; i++) {
            assertRecoverySuccess();
        }
        replayMocks();
        try {
            mTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }
    }

    /**
     * Puts all the mock objects into replay mode
     */
    private void replayMocks() {
        EasyMock.replay(mMockIDevice, mMockRecovery, mMockStateMonitor, mMockRunUtil, mMockWifi);
    }

    /**
     * Verify all the mock objects
     */
    private void verifyMocks() {
        EasyMock.verify(mMockIDevice, mMockRecovery, mMockStateMonitor, mMockRunUtil, mMockWifi);
    }


    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify that output of 'adb shell df' command is parsed correctly.
     */
    public void testGetExternalStoreFreeSpace() throws Exception {
        final String dfOutput =
            "/mnt/sdcard: 3864064K total, 1282880K used, 2581184K available (block size 32768)";
        assertGetExternalStoreFreeSpace(dfOutput, 2581184);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify that the table-based output of 'adb shell df' command is parsed correctly.
     */
    public void testGetExternalStoreFreeSpace_table() throws Exception {
        final String dfOutput =
            "Filesystem             Size   Used   Free   Blksize\n" +
            "/mnt/sdcard              3G   787M     2G   4096";
        assertGetExternalStoreFreeSpace(dfOutput, 2 * 1024 * 1024);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify that the coreutils-like output of 'adb shell df' command is parsed correctly.
     */
    public void testGetExternalStoreFreeSpace_toybox() throws Exception {
        final String dfOutput =
            "Filesystem      1K-blocks	Used  Available Use% Mounted on\n" +
            "/dev/fuse        11585536    1316348   10269188  12% /mnt/sdcard";
        assertGetExternalStoreFreeSpace(dfOutput, 10269188);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify that the coreutils-like output of 'adb shell df' command is parsed correctly. This
     * variant tests the fact that the returned mount point in last column of command output may
     * not match the original path provided as parameter to df.
     */
    public void testGetExternalStoreFreeSpace_toybox2() throws Exception {
        final String dfOutput =
            "Filesystem     1K-blocks   Used Available Use% Mounted on\n" +
            "/dev/fuse       27240188 988872  26251316   4% /storage/emulated";
        assertGetExternalStoreFreeSpace(dfOutput, 26251316);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify behavior when 'df' command returns unexpected content
     */
    public void testGetExternalStoreFreeSpace_badOutput() throws Exception {
        final String dfOutput =
            "/mnt/sdcard: blaH";
        assertGetExternalStoreFreeSpace(dfOutput, 0);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     * <p/>
     * Verify behavior when first 'df' attempt returns empty output
     */
    public void testGetExternalStoreFreeSpace_emptyOutput() throws Exception {
        final String mntPoint = "/mnt/sdcard";
        final String expectedCmd = "df " + mntPoint;
        EasyMock.expect(mMockStateMonitor.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).andReturn(
                mntPoint);
        // expect shell command to be called, and return the empty df output
        injectShellResponse(expectedCmd, "");
        final String dfOutput =
                "/mnt/sdcard: 3864064K total, 1282880K used, 2581184K available (block size 32768)";
        injectShellResponse(expectedCmd, dfOutput);
        EasyMock.replay(mMockIDevice, mMockStateMonitor);
        assertEquals(2581184, mTestDevice.getExternalStoreFreeSpace());
    }

    /**
     * Helper method to verify the {@link TestDevice#getExternalStoreFreeSpace()} method under
     * different conditions.
     *
     * @param dfOutput the test output to inject
     * @param expectedFreeSpaceKB the expected free space
     */
    private void assertGetExternalStoreFreeSpace(final String dfOutput, long expectedFreeSpaceKB)
            throws Exception {
        final String mntPoint = "/mnt/sdcard";
        final String expectedCmd = "df " + mntPoint;
        EasyMock.expect(mMockStateMonitor.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).andReturn(
                mntPoint);
        // expect shell command to be called, and return the test df output
        injectShellResponse(expectedCmd, dfOutput);
        EasyMock.replay(mMockIDevice, mMockStateMonitor);
        assertEquals(expectedFreeSpaceKB, mTestDevice.getExternalStoreFreeSpace());
    }

    /**
     * Unit test for {@link TestDevice#syncFiles)}.
     * <p/>
     * Verify behavior when given local file does not exist
     */
    public void testSyncFiles_missingLocal() throws Exception {
        EasyMock.replay(mMockIDevice);
        assertFalse(mTestDevice.syncFiles(new File("idontexist"), "/sdcard"));
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * success case.
     */
    public void testRunInstrumentationTests() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        EasyMock.expect(mockRunner.getPackageName()).andStubReturn("com.example");
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(0);
        mockRunner.setMaxTimeToOutputResponse(EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        // expect runner.run command to be called
        mockRunner.run(listeners);
        EasyMock.replay(mockRunner);
        mTestDevice.runInstrumentationTests(mockRunner, listeners);
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * when recovery fails.
     */
    public void testRunInstrumentationTests_recoveryFails() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
        ITestRunListener listener = EasyMock.createMock(ITestRunListener.class);
        listeners.add(listener);
        mockRunner.setMaxTimeToOutputResponse(EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        mockRunner.run(listeners);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mockRunner.getPackageName()).andReturn("foo");
        listener.testRunFailed((String)EasyMock.anyObject());
        mMockRecovery.recoverDevice(EasyMock.eq(mMockStateMonitor), EasyMock.eq(false));
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        EasyMock.replay(listener, mockRunner, mMockIDevice, mMockRecovery);
        try {
            mRecoveryTestDevice.runInstrumentationTests(mockRunner, listeners);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)}
     * when recovery succeeds.
     */
    public void testRunInstrumentationTests_recoverySucceeds() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>(1);
        ITestRunListener listener = EasyMock.createMock(ITestRunListener.class);
        listeners.add(listener);
        mockRunner.setMaxTimeToOutputResponse(EasyMock.anyLong(), (TimeUnit)EasyMock.anyObject());
        mockRunner.run(listeners);
        EasyMock.expectLastCall().andThrow(new IOException());
        EasyMock.expect(mockRunner.getPackageName()).andReturn("foo");
        listener.testRunFailed((String)EasyMock.anyObject());
        assertRecoverySuccess();
        EasyMock.replay(listener, mockRunner, mMockIDevice, mMockRecovery);
        mTestDevice.runInstrumentationTests(mockRunner, listeners);
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} throws an exception when fastboot
     * is not available.
     */
    public void testExecuteFastbootCommand_nofastboot() throws Exception {
        try {
            mNoFastbootTestDevice.executeFastbootCommand("");
            fail("UnsupportedOperationException not thrown");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#executeLongFastbootCommand(String...)} throws an exception when
     * fastboot is not available.
     */
    public void testExecuteLongFastbootCommand_nofastboot() throws Exception {
        try {
            mNoFastbootTestDevice.executeFastbootCommand("");
            fail("UnsupportedOperationException not thrown");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Test that state changes are ignore while {@link TestDevice#executeFastbootCommand(String...)}
     * is active.
     * @throws InterruptedException
     */
    public void testExecuteFastbootCommand_state() throws InterruptedException {
        // build a fastboot response that will block
        IAnswer<CommandResult> blockResult = new IAnswer<CommandResult>() {
            @Override
            public CommandResult answer() throws Throwable {
                synchronized(this) {
                    // first inform this test that fastboot cmd is executing
                    notifyAll();
                    // now wait for test to unblock us when its done testing logic
                    wait(1000);
                }
                return new CommandResult(CommandStatus.SUCCESS);
            }
        };
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"),EasyMock.eq(MOCK_DEVICE_SERIAL), EasyMock.eq("foo"))).andAnswer(
                        blockResult);

        // expect
        mMockStateMonitor.setState(TestDeviceState.FASTBOOT);
        mMockStateMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        replayMocks();

        mTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals(TestDeviceState.FASTBOOT, mTestDevice.getDeviceState());

        // start fastboot command in background thread
        Thread fastbootThread = new Thread() {
            @Override
            public void run() {
                try {
                    mTestDevice.executeFastbootCommand("foo");
                } catch (DeviceNotAvailableException e) {
                    CLog.e(e);
                }
            }
        };
        fastbootThread.start();
        try {
            synchronized (blockResult) {
                blockResult.wait(1000);
            }
            // expect to ignore this
            mTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
            assertEquals(TestDeviceState.FASTBOOT, mTestDevice.getDeviceState());
        } finally {
            synchronized (blockResult) {
                blockResult.notifyAll();
            }
        }
        fastbootThread.join();
        mTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        assertEquals(TestDeviceState.NOT_AVAILABLE, mTestDevice.getDeviceState());
    }

    /**
     * Test recovery mode is entered when fastboot command fails
     */
    public void testExecuteFastbootCommand_recovery() throws UnsupportedOperationException,
           DeviceNotAvailableException {
        CommandResult result = new CommandResult(CommandStatus.EXCEPTION);
        EasyMock.expect(mMockRunUtil.runTimedCmd(
                EasyMock.anyLong(), EasyMock.eq("fastboot"), EasyMock.eq("-s"),
                EasyMock.eq(MOCK_DEVICE_SERIAL), EasyMock.eq("foo"))).andReturn(result);
        mMockRecovery.recoverDeviceBootloader((IDeviceStateMonitor)EasyMock.anyObject());
        CommandResult successResult = new CommandResult(CommandStatus.SUCCESS);
        successResult.setStderr("");
        successResult.setStdout("");
        // now expect a successful retry
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"),EasyMock.eq(MOCK_DEVICE_SERIAL), EasyMock.eq("foo"))).andReturn(
                        successResult);
        replayMocks();
        mTestDevice.executeFastbootCommand("foo");
        verifyMocks();

    }

    /**
     * Basic test for encryption if encryption is not supported.
     * <p>
     * Calls {@link TestDevice#encryptDevice(boolean)}, {@link TestDevice#unlockDevice()}, and
     * {@link TestDevice#unencryptDevice()} and makes sure that a
     * {@link UnsupportedOperationException} is thrown for each method.
     * </p>
     */
    public void testEncryptionUnsupported() throws Exception {
        setEncryptedUnsupportedExpectations();
        setEncryptedUnsupportedExpectations();
        setEncryptedUnsupportedExpectations();
        EasyMock.replay(mMockIDevice, mMockRunUtil, mMockStateMonitor);

        try {
            mTestDevice.encryptDevice(false);
            fail("encryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            mTestDevice.unlockDevice();
            fail("decryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            mTestDevice.unencryptDevice();
            fail("unencryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        return;
    }

    /**
     * Configure EasyMock for a encryption check call, that returns that encryption is unsupported
     */
    private void setEncryptedUnsupportedExpectations() throws Exception {
        setEnableAdbRootExpectations();
        injectShellResponse("vdc cryptfs enablecrypto", "\r\n");
    }

    /**
     * Simple test for {@link TestDevice#switchToAdbUsb()}
     */
    public void testSwitchToAdbUsb() throws Exception  {
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "usb");
        replayMocks();
        mTestDevice.switchToAdbUsb();
        verifyMocks();
    }

    /**
     * Test for {@link TestDevice#switchToAdbTcp()} when device has no ip address
     */
    public void testSwitchToAdbTcp_noIp() throws Exception {
        EasyMock.expect(mMockWifi.getIpAddress()).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.switchToAdbTcp());
        verifyMocks();
    }

    /**
     * Test normal success case for {@link TestDevice#switchToAdbTcp()}.
     */
    public void testSwitchToAdbTcp() throws Exception {
        EasyMock.expect(mMockWifi.getIpAddress()).andReturn("ip");
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("adb"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("tcpip"),
                EasyMock.eq("5555"))).andReturn(
                        new CommandResult(CommandStatus.SUCCESS));
        replayMocks();
        assertEquals("ip:5555", mTestDevice.switchToAdbTcp());
        verifyMocks();
    }

    /**
     * Test simple success case for
     * {@link TestDevice#installPackage(File, File, boolean, String...)}.
     */
    public void testInstallPackages() throws Exception {
        final String certFile = "foo.dc";
        final String apkFile = "foo.apk";
        EasyMock.expect(mMockIDevice.syncPackageToDevice(EasyMock.contains(certFile))).andReturn(
                certFile);
        EasyMock.expect(mMockIDevice.syncPackageToDevice(EasyMock.contains(apkFile))).andReturn(
                apkFile);
        // expect apk path to be passed as extra arg
        EasyMock.expect(
                mMockIDevice.installRemotePackage(EasyMock.eq(certFile), EasyMock.eq(true),
                        EasyMock.eq("-l"), EasyMock.contains(apkFile))).andReturn(null);
        mMockIDevice.removeRemotePackage(certFile);
        mMockIDevice.removeRemotePackage(apkFile);

        replayMocks();

        assertNull(mTestDevice.installPackage(new File(apkFile), new File(certFile), true, "-l"));
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting LRX22F
     * build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedLmpRelease() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "21");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "REL");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "1642709");
        replayMocks();
        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting LMP MR1
     * dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedLmpMr1Dev() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "REL");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "1844090");
        replayMocks();
        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting random
     * dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedRandom1() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "YADDA");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "XYZ");
        replayMocks();
        assertTrue(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting random
     * mnc dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedMncLocal() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "MNC");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "eng.foo.20150414.190304");
        replayMocks();
        assertTrue(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting (bogus) N
     * based device attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedNycLocal() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "NMC");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "1937204");
        replayMocks();
        assertTrue(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting random
     * dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedNonMncLocal() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "21");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "LMP");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "eng.foo.20150414.190304");
        replayMocks();
        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting early MNC
     * dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedEarlyMnc() throws Exception {
        setMockIDeviceRuntimePermissionNotSupported();
        replayMocks();
        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting early MNC
     * dev build attributes
     * @throws Exception
     */
    public void testRuntimePermissionSupportedMncPostSwitch() throws Exception {
        setMockIDeviceRuntimePermissionSupported();
        replayMocks();
        assertTrue(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Convenience method for setting up mMockIDevice to not support runtime permission
     */
    private void setMockIDeviceRuntimePermissionNotSupported() {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "MNC");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "1816412");
    }

    /**
     * Convenience method for setting up mMockIDevice to support runtime permission
     */
    private void setMockIDeviceRuntimePermissionSupported() {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(TestDevice.BUILD_CODENAME_PROP, "MNC");
        injectSystemProperty(TestDevice.BUILD_ID_PROP, "1844452");
    }

    /**
     * Test default installPackage on device not supporting runtime permission has expected
     * list of args
     * @throws Exception
     */
    public void testInstallPackage_default_runtimePermissionNotSupported() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackage(new File(apkFile), true));
    }

    /**
     * Test default installPackage on device supporting runtime permission has expected list of args
     * @throws Exception
     */
    public void testInstallPackage_default_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true), EasyMock.eq("-g"))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackage(new File(apkFile), true));
    }

    /**
     * Test default installPackageForUser on device not supporting runtime permission has expected
     * list of args
     * @throws Exception
     */
    public void testinstallPackageForUser_default_runtimePermissionNotSupported() throws Exception {
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionNotSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true),
                EasyMock.eq("--user"), EasyMock.eq(Integer.toString(uid)))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, uid));
    }

    /**
     * Test default installPackageForUser on device supporting runtime permission has expected
     * list of args
     * @throws Exception
     */
    public void testinstallPackageForUser_default_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true), EasyMock.eq("-g"),
                EasyMock.eq("--user"), EasyMock.eq(Integer.toString(uid)))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, uid));
    }

    /**
     * Test runtime permission variant of installPackage throws exception on unsupported device
     * platform
     * @throws Exception
     */
    public void testInstallPackage_throw() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();
        replayMocks();
        try {
            mTestDevice.installPackage(new File(apkFile), true, true);
        } catch (UnsupportedOperationException uoe) {
            // ignore, exception thrown here is expected
            return;
        }
        fail("installPackage did not throw IllegalArgumentException");
    }

    /**
     * Test runtime permission variant of installPackage has expected list of args on a supported
     * device when granting
     * @throws Exception
     */
    public void testInstallPackage_grant_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true), EasyMock.eq("-g"))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackage(new File(apkFile), true, true));
    }

    /**
     * Test runtime permission variant of installPackage has expected list of args on a supported
     * device when not granting
     * @throws Exception
     */
    public void testInstallPackage_noGrant_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackage(new File(apkFile), true, false));
    }

    /**
     * Test grant permission variant of installPackageForUser throws exception on unsupported
     * device platform
     * @throws Exception
     */
    public void testInstallPackageForUser_throw() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();
        replayMocks();
        try {
            mTestDevice.installPackageForUser(new File(apkFile), true, true, 123);
        } catch (UnsupportedOperationException uoe) {
            // ignore, exception thrown here is expected
            return;
        }
        fail("installPackage did not throw IllegalArgumentException");
    }

    /**
     * Test grant permission variant of installPackageForUser has expected list of args on a
     * supported device when granting
     * @throws Exception
     */
    public void testInstallPackageForUser_grant_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true), EasyMock.eq("-g"),
                EasyMock.eq("--user"), EasyMock.eq(Integer.toString(uid)))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, true, uid));
    }

    /**
     * Test grant permission variant of installPackageForUser has expected list of args on a
     * supported device when not granting
     * @throws Exception
     */
    public void testInstallPackageForUser_noGrant_runtimePermissionSupported() throws Exception {
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        EasyMock.expect(mMockIDevice.installPackage(
                EasyMock.contains(apkFile), EasyMock.eq(true),
                EasyMock.eq("--user"), EasyMock.eq(Integer.toString(uid)))).andReturn(null);
        replayMocks();
        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, false, uid));
    }

    /**
     * Helper method to build a response to a executeShellCommand call
     *
     * @param expectedCommand the shell command to expect or null to skip verification of command
     * @param response the response to simulate
     */
    private void injectShellResponse(final String expectedCommand, final String response)
            throws Exception {
        injectShellResponse(expectedCommand, response, false);
    }

    /**
     * Helper method to build a response to a executeShellCommand call
     *
     * @param expectedCommand the shell command to expect or null to skip verification of command
     * @param response the response to simulate
     * @param asStub whether to set a single expectation or a stub expectation
     */
    private void injectShellResponse(final String expectedCommand, final String response,
            boolean asStub) throws Exception {
        IAnswer<Object> shellAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                IShellOutputReceiver receiver =
                    (IShellOutputReceiver)EasyMock.getCurrentArguments()[1];
                byte[] inputData = response.getBytes();
                receiver.addOutput(inputData, 0, inputData.length);
                return null;
            }
        };
        if (expectedCommand != null) {
            mMockIDevice.executeShellCommand(EasyMock.eq(expectedCommand),
                    EasyMock.<IShellOutputReceiver>anyObject(),
                    EasyMock.anyLong(), EasyMock.<TimeUnit>anyObject());
        } else {
            mMockIDevice.executeShellCommand(EasyMock.<String>anyObject(),
                    EasyMock.<IShellOutputReceiver>anyObject(),
                    EasyMock.anyLong(), EasyMock.<TimeUnit>anyObject());

        }
        if (asStub) {
            EasyMock.expectLastCall().andStubAnswer(shellAnswer);
        } else {
            EasyMock.expectLastCall().andAnswer(shellAnswer);
        }
    }

    /**
     * Helper method to inject a response to {@link TestDevice#getProperty(String)} calls
     * @param property property name
     * @param value property value
     * @return preset {@link IExpectationSetters} returned by {@link EasyMock} where further
     * expectations can be added
     */
    private IExpectationSetters<Future<String>> injectSystemProperty(
            final String property, final String value) {
        SettableFuture<String> valueResponse = SettableFuture.create();
        valueResponse.set(value);
        return EasyMock.expect(mMockIDevice.getSystemProperty(property)).andReturn(valueResponse);
    }

    /**
     * Helper method to build response to a reboot call
     * @throws Exception
     */
    private void setRebootExpectations() throws Exception {
        EasyMock.expect(mMockStateMonitor.waitForDeviceOnline()).andReturn(
                mMockIDevice);
        setEnableAdbRootExpectations();
        setEncryptedUnsupportedExpectations();
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockIDevice);
    }

    /**
     * Test normal success case for {@link TestDevice#reboot()}
     */
    public void testReboot() throws Exception {
        setRebootExpectations();
        replayMocks();
        mTestDevice.reboot();
        verifyMocks();
    }

    /**
     * Test {@link TestDevice#reboot()} attempts a recovery upon failure
     */
    public void testRebootRecovers() throws Exception {
        EasyMock.expect(mMockStateMonitor.waitForDeviceOnline()).andReturn(
                mMockIDevice);
        setEnableAdbRootExpectations();
        setEncryptedUnsupportedExpectations();
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(null);
        mMockRecovery.recoverDevice(mMockStateMonitor, false);
        replayMocks();
        mRecoveryTestDevice.reboot();
        verifyMocks();
    }

    /**
     * Unit test for {@link TestDevice#getInstalledPackageNames()}.
     */
    public void testGetInstalledPackageNames() throws Exception {
        final String output = "package:/system/app/LiveWallpapers.apk=com.android.wallpaper\n" +
                "package:/system/app/LiveWallpapersPicker.apk=com.android.wallpaper.livepicker";
        injectShellResponse(TestDevice.LIST_PACKAGES_CMD, output);
        EasyMock.replay(mMockIDevice, mMockStateMonitor);
        Set<String> actualPkgs = mTestDevice.getInstalledPackageNames();
        assertEquals(2, actualPkgs.size());
        assertTrue(actualPkgs.contains("com.android.wallpaper"));
        assertTrue(actualPkgs.contains("com.android.wallpaper.livepicker"));
    }

    /**
     * Unit test for {@link TestDevice#getInstalledPackageNames()}.
     * <p/>
     * Test bad output.
     */
    public void testGetInstalledPackageNamesForBadOutput() throws Exception {
        final String output = "junk output";
        injectShellResponse(TestDevice.LIST_PACKAGES_CMD, output);
        EasyMock.replay(mMockIDevice, mMockStateMonitor);
        Set<String> actualPkgs = mTestDevice.getInstalledPackageNames();
        assertEquals(0, actualPkgs.size());
    }

    /**
     * Unit test to make sure that the simple convenience constructor for
     * {@link ITestDevice#MountPointInfo} works as expected.
     */
    public void testMountInfo_simple() throws Exception {
        List<String> empty = Collections.emptyList();
        MountPointInfo info = new MountPointInfo("filesystem", "mountpoint", "type", empty);
        assertEquals("filesystem", info.filesystem);
        assertEquals("mountpoint", info.mountpoint);
        assertEquals("type", info.type);
        assertEquals(empty, info.options);
    }

    /**
     * Unit test to make sure that the mount-option-parsing convenience constructor for
     * {@link ITestDevice#MountPointInfo} works as expected.
     */
    public void testMountInfo_parseOptions() throws Exception {
        MountPointInfo info = new MountPointInfo("filesystem", "mountpoint", "type", "rw,relatime");
        assertEquals("filesystem", info.filesystem);
        assertEquals("mountpoint", info.mountpoint);
        assertEquals("type", info.type);

        // options should be parsed
        assertNotNull(info.options);
        assertEquals(2, info.options.size());
        assertEquals("rw", info.options.get(0));
        assertEquals("relatime", info.options.get(1));
    }

    /**
     * A unit test to ensure {@link TestDevice#getMountPointInfo()} works as expected.
     */
    public void testGetMountPointInfo() throws Exception {
        injectShellResponse("cat /proc/mounts", ArrayUtil.join("\r\n",
                "rootfs / rootfs ro,relatime 0 0",
                "tmpfs /dev tmpfs rw,nosuid,relatime,mode=755 0 0",
                "devpts /dev/pts devpts rw,relatime,mode=600 0 0",
                "proc /proc proc rw,relatime 0 0",
                "sysfs /sys sysfs rw,relatime 0 0",
                "none /acct cgroup rw,relatime,cpuacct 0 0",
                "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0",
                "tmpfs /mnt/obb tmpfs rw,relatime,mode=755,gid=1000 0 0",
                "none /dev/cpuctl cgroup rw,relatime,cpu 0 0",
                "/dev/block/vold/179:3 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev," +
                    "noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702," +
                    "allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed," +
                    "utf8,errors=remount-ro 0 0",
                "tmpfs /storage/sdcard0/.android_secure tmpfs " +
                    "ro,relatime,size=0k,mode=000 0 0"));
        replayMocks();
        List<MountPointInfo> info = mTestDevice.getMountPointInfo();
        verifyMocks();
        assertEquals(11, info.size());

        // spot-check
        MountPointInfo mpi = info.get(0);
        assertEquals("rootfs", mpi.filesystem);
        assertEquals("/", mpi.mountpoint);
        assertEquals("rootfs", mpi.type);
        assertEquals(2, mpi.options.size());
        assertEquals("ro", mpi.options.get(0));
        assertEquals("relatime", mpi.options.get(1));

        mpi = info.get(9);
        assertEquals("/dev/block/vold/179:3", mpi.filesystem);
        assertEquals("/mnt/secure/asec", mpi.mountpoint);
        assertEquals("vfat", mpi.type);
        assertEquals(16, mpi.options.size());
        assertEquals("dirsync", mpi.options.get(1));
        assertEquals("errors=remount-ro", mpi.options.get(15));
    }

    /**
     * A unit test to ensure {@link TestDevice#getMountPointInfo(String)} works as expected.
     */
    public void testGetMountPointInfo_filter() throws Exception {
        injectShellResponse("cat /proc/mounts", ArrayUtil.join("\r\n",
                "rootfs / rootfs ro,relatime 0 0",
                "tmpfs /dev tmpfs rw,nosuid,relatime,mode=755 0 0",
                "devpts /dev/pts devpts rw,relatime,mode=600 0 0",
                "proc /proc proc rw,relatime 0 0",
                "sysfs /sys sysfs rw,relatime 0 0",
                "none /acct cgroup rw,relatime,cpuacct 0 0",
                "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0",
                "tmpfs /mnt/obb tmpfs rw,relatime,mode=755,gid=1000 0 0",
                "none /dev/cpuctl cgroup rw,relatime,cpu 0 0",
                "/dev/block/vold/179:3 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev," +
                    "noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702," +
                    "allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed," +
                    "utf8,errors=remount-ro 0 0",
                "tmpfs /storage/sdcard0/.android_secure tmpfs " +
                    "ro,relatime,size=0k,mode=000 0 0"),
                true /* asStub */);
        replayMocks();
        MountPointInfo mpi = mTestDevice.getMountPointInfo("/mnt/secure/asec");
        assertEquals("/dev/block/vold/179:3", mpi.filesystem);
        assertEquals("/mnt/secure/asec", mpi.mountpoint);
        assertEquals("vfat", mpi.type);
        assertEquals(16, mpi.options.size());
        assertEquals("dirsync", mpi.options.get(1));
        assertEquals("errors=remount-ro", mpi.options.get(15));

        assertNull(mTestDevice.getMountPointInfo("/a/mountpoint/too/far"));
    }

    public void testParseFreeSpaceFromFree() throws Exception {
        assertNotNull("Failed to parse free space size with decimal point",
                mTestDevice.parseFreeSpaceFromFree("/storage/emulated/legacy",
                "/storage/emulated/legacy    13.2G   296.4M    12.9G   4096"));
        assertNotNull("Failed to parse integer free space size",
                mTestDevice.parseFreeSpaceFromFree("/storage/emulated/legacy",
                "/storage/emulated/legacy     13G   395M    12G   4096"));
    }

    public void testIsDeviceInputReady_Ready() throws Exception {
        injectShellResponse("dumpsys input", ArrayUtil.join("\r\n", getDumpsysInputHeader(),
                "  DispatchEnabled: 1",
                "  DispatchFrozen: 0",
                "  FocusedApplication: <null>",
                "  FocusedWindow: name='Window{2920620f u0 com.android.launcher/"
                + "com.android.launcher2.Launcher}'",
                "  TouchStates: <no displays touched>"
                ));
        replayMocks();
        assertTrue(mTestDevice.isDeviceInputReady());
    }

    public void testIsDeviceInputReady_NotReady() throws Exception {
        injectShellResponse("dumpsys input", ArrayUtil.join("\r\n", getDumpsysInputHeader(),
                "  DispatchEnabled: 0",
                "  DispatchFrozen: 0",
                "  FocusedApplication: <null>",
                "  FocusedWindow: name='Window{2920620f u0 com.android.launcher/"
                + "com.android.launcher2.Launcher}'",
                "  TouchStates: <no displays touched>"
                ));
        replayMocks();
        assertFalse(mTestDevice.isDeviceInputReady());
    }

    public void testIsDeviceInputReady_NotSupported() throws Exception {
        injectShellResponse("dumpsys input", ArrayUtil.join("\r\n",
                "foo",
                "bar",
                "foobar",
                "barfoo"
                ));
        replayMocks();
        assertNull(mTestDevice.isDeviceInputReady());
    }

    private static String getDumpsysInputHeader() {
        return ArrayUtil.join("\r\n",
                "INPUT MANAGER (dumpsys input)",
                "",
                "Event Hub State:",
                "  BuiltInKeyboardId: -2",
                "  Devices:",
                "    -1: Virtual",
                "      Classes: 0x40000023",
                "Input Dispatcher State:"
                );
    }

    /**
     * Simple test for {@link TestDevice#handleAllocationEvent(DeviceEvent)}
     */
    public void testHandleAllocationEvent() {
        EasyMock.expect(mMockIDevice.getSerialNumber()).andStubReturn(MOCK_DEVICE_SERIAL);
        EasyMock.replay(mMockIDevice);

        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE));
        assertEquals(DeviceAllocationState.Checking_Availability, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_PASSED));
        assertEquals(DeviceAllocationState.Available, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST));
        assertEquals(DeviceAllocationState.Allocated, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE));
        assertEquals(DeviceAllocationState.Available, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED));
        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST));
        assertEquals(DeviceAllocationState.Allocated, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN));
        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());
    }

    public void testCheckConnectivity() throws Exception {
        EasyMock.expect(mMockWifi.checkConnectivity("http://www.google.com")).andReturn(true);
        replayMocks();
        assertTrue(mTestDevice.checkConnectivity());
    }

    public void testCheckConnectivity_NoConnectivity() throws Exception {
        EasyMock.expect(mMockWifi.checkConnectivity("http://www.google.com")).andReturn(false);
        replayMocks();
        assertFalse(mTestDevice.checkConnectivity());
    }

    /**
     * Test that a single user is handled by {@link TestDevice#listUsers()}.
     */
    public void testListUsers_oneUser() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(listUsersCommand, ArrayUtil.join("\r\n",
                "Users:",
                "UserInfo{0:Foo:13} running"));
        replayMocks();
        ArrayList<Integer> actual = mTestDevice.listUsers();
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals(0, actual.get(0).intValue());
    }

    /**
     * Test that invalid output is handled by {@link TestDevice#listUsers()}.
     */
    public void testListUsers_invalidOutput() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(listUsersCommand, "not really what we are looking for");
        replayMocks();
        ArrayList<Integer> actual = mTestDevice.listUsers();
        assertNull(actual);
    }

    /**
     * Test that multiple user is handled by {@link TestDevice#listUsers()}.
     */
    public void testListUsers_multiUsers() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(listUsersCommand, ArrayUtil.join("\r\n",
                "Users:",
                "UserInfo{0:Foo:13} running",
                "UserInfo{3:FooBar:14}"));
        replayMocks();
        ArrayList<Integer> actual = mTestDevice.listUsers();
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(0, actual.get(0).intValue());
        assertEquals(3, actual.get(1).intValue());
    }

    /**
     * Test that multi user output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    public void testMaxNumberOfUsersSupported() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 4");
        replayMocks();
        assertEquals(4, mTestDevice.getMaxNumberOfUsersSupported());
    }

    /**
     * Test that invalid output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    public void testMaxNumberOfUsersSupported_invalid() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "not the output we expect");
        replayMocks();
        assertEquals(0, mTestDevice.getMaxNumberOfUsersSupported());
    }

    /**
     * Test that single user output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    public void testIsMultiUserSupported_singleUser() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 1");
        replayMocks();
        assertFalse(mTestDevice.isMultiUserSupported());
    }

    /**
     * Test that {@link TestDevice#isMultiUserSupported()} works.
     */
    public void testIsMultiUserSupported() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 4");
        replayMocks();
        assertTrue(mTestDevice.isMultiUserSupported());
    }

    /**
     * Test that invalid output is handled by {@link TestDevice#isMultiUserSupported()}.
     */
    public void testIsMultiUserSupported_invalidOutput() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "not the output we expect");
        replayMocks();
        assertFalse(mTestDevice.isMultiUserSupported());
    }

    /**
     * Test that successful user creation is handled by {@link TestDevice#createUser()}.
     */
    public void testCreateUser() throws Exception {
        final String createUserCommand = "pm create-user foo";
        injectShellResponse(createUserCommand, "Success: created user id 10");
        replayMocks();
        assertEquals(10, mTestDevice.createUser("foo"));
    }

    /**
     * Test that a failure to create a user is handled by {@link TestDevice#createUser()}.
     */
    public void testCreateUser_failed() throws Exception {
        final String createUserCommand = "pm create-user foo";
        injectShellResponse(createUserCommand, "Error");
        replayMocks();
        try {
            mTestDevice.createUser("foo");
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    /**
     * Test that successful user removal is handled by {@link TestDevice#removeUser()}.
     */
    public void testRemoveUser() throws Exception {
        final String removeUserCommand = "pm remove-user 10";
        injectShellResponse(removeUserCommand, "Success: removed user\n");
        replayMocks();
        assertTrue(mTestDevice.removeUser(10));
    }

    /**
     * Test that a failure to remove a user is handled by {@link TestDevice#removeUser()}.
     */
    public void testRemoveUser_failed() throws Exception {
        final String removeUserCommand = "pm remove-user 10";
        injectShellResponse(removeUserCommand, "Error: couldn't remove user id 10");
        replayMocks();
        assertFalse(mTestDevice.removeUser(10));
    }

    /**
     * Test that trying to run a test with a user with
     * {@link TestDevice#runInstrumentationTestsAsUser(IRemoteAndroidTestRunner, int, Collection)}
     * fails if the {@link IRemoteAndroidTestRunner} is not an instance of
     * {@link RemoteAndroidTestRunner}.
     */
    public void testrunInstrumentationTestsAsUser_failed() throws Exception {
        IRemoteAndroidTestRunner mockRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        EasyMock.expect(mockRunner.getPackageName()).andStubReturn("com.example");
        Collection<ITestRunListener> listeners = new ArrayList<ITestRunListener>();
        EasyMock.replay(mockRunner);
        try {
            mTestDevice.runInstrumentationTestsAsUser(mockRunner, 12, listeners);
            fail("IllegalStateException not thrown.");
        } catch (IllegalStateException e) {
            //expected
        }
    }

     /**
     * Test that successful user start is handled by {@link TestDevice#startUser()}.
     */
    public void testStartUser() throws Exception {
        final String startUserCommand = "am start-user 10";
        injectShellResponse(startUserCommand, "Success: user started\n");
        replayMocks();
        assertTrue(mTestDevice.startUser(10));
    }

    /**
     * Test that a failure to start user is handled by {@link TestDevice#startUser()}.
     */
    public void testStartUser_failed() throws Exception {
        final String startUserCommand = "am start-user 10";
        injectShellResponse(startUserCommand, "Error: could not start user\n");
        replayMocks();
        assertFalse(mTestDevice.startUser(10));
    }

    /**
     * Test that remount works as expected on a device not supporting dm verity
     * @throws Exception
     */
    public void testRemount_verityUnsupported() throws Exception {
        injectSystemProperty("partition.system.verified", "");
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable()).andReturn(mMockIDevice);
        replayMocks();
        mTestDevice.remountSystemWritable();
        verifyMocks();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity v1
     * @throws Exception
     */
    public void testRemount_veritySupportedV1() throws Exception {
        injectSystemProperty("partition.system.verified", "1");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable()).andReturn(mMockIDevice);
        replayMocks();
        mTestDevice.remountSystemWritable();
        verifyMocks();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity v2
     * @throws Exception
     */
    public void testRemount_veritySupportedV2() throws Exception {
        injectSystemProperty("partition.system.verified", "2");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable()).andReturn(mMockIDevice);
        replayMocks();
        mTestDevice.remountSystemWritable();
        verifyMocks();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity but with unknown version
     * @throws Exception
     */
    public void testRemount_veritySupportedNonNumerical() throws Exception {
        injectSystemProperty("partition.system.verified", "foo");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        EasyMock.expect(mMockStateMonitor.waitForDeviceAvailable()).andReturn(mMockIDevice);
        replayMocks();
        mTestDevice.remountSystemWritable();
        verifyMocks();
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} works for the typical "test-keys" case
     * @throws Exception
     */
    public void testGetBuildSigningKeys_test_keys() throws Exception {
        injectSystemProperty(TestDevice.BUILD_TAGS, "test-keys");
        replayMocks();
        assertEquals("test-keys", mTestDevice.getBuildSigningKeys());
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} works for the case where build tags is a
     * comma separated list
     * @throws Exception
     */
    public void testGetBuildSigningKeys_test_keys_commas() throws Exception {
        injectSystemProperty(TestDevice.BUILD_TAGS, "test-keys,foo,bar,yadda");
        replayMocks();
        assertEquals("test-keys", mTestDevice.getBuildSigningKeys());
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} returns null for non-matching case
     * @throws Exception
     */
    public void testGetBuildSigningKeys_not_matched() throws Exception {
        injectSystemProperty(TestDevice.BUILD_TAGS, "huh,foo,bar,yadda");
        replayMocks();
        assertNull(mTestDevice.getBuildSigningKeys());
    }
}
