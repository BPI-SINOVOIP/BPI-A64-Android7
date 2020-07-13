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

import com.android.ddmlib.IDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link WaitDeviceRecovery}.
 */
public class WaitDeviceRecoveryTest extends TestCase {

    private IRunUtil mMockRunUtil;
    private WaitDeviceRecovery mRecovery;
    private IDeviceStateMonitor mMockMonitor;
    private IDevice mMockDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mRecovery = new WaitDeviceRecovery() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
        };
        mMockMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        EasyMock.expect(mMockMonitor.getSerialNumber()).andStubReturn("serial");
        mMockDevice = EasyMock.createMock(IDevice.class);
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor)} when devices comes back
     * online on its own accord.
     */
    public void testRecoverDevice_success() throws DeviceNotAvailableException {
        // expect initial sleep
        mMockRunUtil.sleep(EasyMock.anyLong());
        mMockMonitor.waitForDeviceBootloaderStateUpdate();
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.NOT_AVAILABLE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(mMockDevice);
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockDevice);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(mMockDevice);
        replayMocks();
        mRecovery.recoverDevice(mMockMonitor, false);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor)} when device is not
     * available.
     */
    public void testRecoverDevice_unavailable()  {
        // expect initial sleep
        mMockRunUtil.sleep(EasyMock.anyLong());
        mMockMonitor.waitForDeviceBootloaderStateUpdate();
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.NOT_AVAILABLE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong())).andReturn(null);
        replayMocks();
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor)} when device is not
     * responsive.
     */
    public void testRecoverDevice_unresponsive() throws Exception {
        // expect initial sleep
        mMockRunUtil.sleep(EasyMock.anyLong());
        mMockMonitor.waitForDeviceBootloaderStateUpdate();
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong()))
                .andReturn(mMockDevice).anyTimes();
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong()))
                .andReturn(null).anyTimes();
        mMockDevice.reboot((String)EasyMock.isNull());
        replayMocks();
        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor)} when device is in
     * fastboot.
     */
    public void testRecoverDevice_fastboot() throws DeviceNotAvailableException {
        // expect initial sleep
        mMockRunUtil.sleep(EasyMock.anyLong());
        mMockMonitor.waitForDeviceBootloaderStateUpdate();
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.FASTBOOT);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        // expect reboot
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("reboot"))).
                andReturn(result);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong()))
                .andReturn(mMockDevice);
        EasyMock.expect(mMockMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(true);
        EasyMock.expect(mMockMonitor.waitForDeviceAvailable(EasyMock.anyLong())).andReturn(
                mMockDevice);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong()))
                .andReturn(mMockDevice);
        replayMocks();
        mRecovery.recoverDevice(mMockMonitor, false);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * already in bootloader
     */
    public void testRecoverDeviceBootloader_fastboot() throws DeviceNotAvailableException {
        mMockRunUtil.sleep(EasyMock.anyLong());
        // expect reboot
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("reboot-bootloader"))).
                andReturn(new CommandResult(CommandStatus.SUCCESS));
        EasyMock.expect(mMockMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE).times(2);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("getvar"),
                EasyMock.eq("product"))).
                andReturn(new CommandResult(CommandStatus.SUCCESS));
        replayMocks();
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * unavailable but comes back to bootloader on its own
     */
    public void testRecoverDeviceBootloader_unavailable() throws DeviceNotAvailableException {
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.NOT_AVAILABLE);
        // expect reboot
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("reboot-bootloader"))).
                andReturn(new CommandResult(CommandStatus.SUCCESS));
        EasyMock.expect(mMockMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE).times(2);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                EasyMock.eq("-s"), EasyMock.eq("serial"), EasyMock.eq("getvar"),
                EasyMock.eq("product"))).
                andReturn(new CommandResult(CommandStatus.SUCCESS));
        replayMocks();
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * online when bootloader is expected
     */
    public void testRecoverDeviceBootloader_online() throws Exception {
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong()))
                .andReturn(mMockDevice);
        mMockDevice.reboot("bootloader");
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        replayMocks();
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * initially unavailable, then comes online when bootloader is expected
     */
    public void testRecoverDeviceBootloader_unavailable_online() throws Exception {
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.NOT_AVAILABLE);
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.FALSE);
        EasyMock.expect(mMockMonitor.getDeviceState()).andReturn(TestDeviceState.ONLINE);
        EasyMock.expect(mMockMonitor.waitForDeviceOnline(EasyMock.anyLong()))
                .andReturn(mMockDevice);
        mMockDevice.reboot("bootloader");
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        replayMocks();
        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verifyMocks();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * unavailable
     */
    public void testRecoverDeviceBootloader_unavailable_failure() throws Exception {
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expect(mMockMonitor.waitForDeviceBootloader(EasyMock.anyLong())).andStubReturn(
                Boolean.FALSE);
        EasyMock.expect(mMockMonitor.getDeviceState()).andStubReturn(TestDeviceState.NOT_AVAILABLE);
        replayMocks();
        try {
            mRecovery.recoverDeviceBootloader(mMockMonitor);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verifyMocks();
    }

    /**
     * Verify all the mock objects
     */
    private void verifyMocks() {
        EasyMock.verify(mMockRunUtil, mMockMonitor, mMockDevice);
    }

    /**
     * Switch all the mock objects to replay mode
     */
    private void replayMocks() {
        EasyMock.replay(mMockRunUtil, mMockMonitor, mMockDevice);
    }

}
