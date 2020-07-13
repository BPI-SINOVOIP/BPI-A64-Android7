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
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.util.RunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link DeviceStateMonitorTest}.
 */
public class DeviceStateMonitorTest extends TestCase {

    private static final String SERIAL_NUMBER = "1";
    private IDevice mMockDevice;
    private DeviceStateMonitor mMonitor;
    private IDeviceManager mMockMgr;

    @Override
    protected void setUp() {
        mMockMgr = EasyMock.createMock(IDeviceManager.class);
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn(SERIAL_NUMBER).anyTimes();
        EasyMock.replay(mMockDevice);
        mMonitor = new DeviceStateMonitor(mMockMgr, mMockDevice, true);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device is already online
     */
    public void testWaitForDeviceOnline_alreadyOnline() {
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline());
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device becomes online
     */
    public void testWaitForDeviceOnline() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        new Thread() {
            @Override
            public void run() {
                RunUtil.getDefault().sleep(100);
                mMonitor.setState(TestDeviceState.ONLINE);
            }
        }.start();
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline());
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device does not becomes online
     * within allowed time
     */
    public void testWaitForDeviceOnline_timeout() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        new Thread() {
            @Override
            public void run() {
                RunUtil.getDefault().sleep(500);
                mMonitor.setState(TestDeviceState.ONLINE);
            }
        }.start();
        assertNull(mMonitor.waitForDeviceOnline(100));
    }

    /**
     * Normal case test for {@link DeviceStateMonitor#waitForDeviceOnline()}
     */
    public void testWaitForDeviceAvailable() {
        // TODO: implement this when IDevice.executeShellCommand can be mocked more easily
        //assertEquals(mMockDevice, mMonitor.waitForDeviceAvailable());
    }

    /**
     * Test {@link DeviceStateMonitor#isAdbTcp()} with a USB serial number.
     */
    public void testIsAdbTcp_usb() {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("2345asdf");
        EasyMock.expect(mockDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.replay(mockDevice);
        DeviceStateMonitor monitor = new DeviceStateMonitor(mMockMgr, mockDevice, true);
        assertFalse(monitor.isAdbTcp());
    }

    /**
     * Test {@link DeviceStateMonitor#isAdbTcp()} with a TCP serial number.
     */
    public void testIsAdbTcp_tcp() {
        IDevice mockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("192.168.1.1:5555");
        EasyMock.expect(mockDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.replay(mockDevice);
        DeviceStateMonitor monitor = new DeviceStateMonitor(mMockMgr, mockDevice, true);
        assertTrue(monitor.isAdbTcp());
    }

}
