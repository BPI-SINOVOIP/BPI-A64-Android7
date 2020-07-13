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

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.device.IManagedTestDevice.DeviceEventResponse;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Unit tests for {@link DeviceManager}.
 */
public class DeviceManagerTest extends TestCase {

    private static final String DEVICE_SERIAL = "serial";

    private IAndroidDebugBridge mMockAdbBridge;
    private IDevice mMockIDevice;
    private IDeviceStateMonitor mMockStateMonitor;
    private IRunUtil mMockRunUtil;
    private IManagedTestDevice mMockTestDevice;
    private IManagedTestDeviceFactory mMockDeviceFactory;
    private IGlobalConfiguration mMockGlobalConfig;

    /**
     * a reference to the DeviceManager's IDeviceChangeListener. Used for triggering device
     * connection events
     */
    private IDeviceChangeListener mDeviceListener;

    static class MockProcess extends Process {

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroy() {
            // ignore
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int exitValue() {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getErrorStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InputStream getInputStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int waitFor() throws InterruptedException {
            return 0;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockAdbBridge = EasyMock.createNiceMock(IAndroidDebugBridge.class);
        mMockAdbBridge.addDeviceChangeListener((IDeviceChangeListener) EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new IAndroidDebugBridge() {
            @Override
            public void addDeviceChangeListener(final IDeviceChangeListener listener) {
                mDeviceListener = listener;
            }

            @Override
            public IDevice[] getDevices() {
                return null;
            }

            @Override
            public void removeDeviceChangeListener(IDeviceChangeListener listener) {
            }

            @Override
            public void init(boolean clientSupport, String adbOsLocation) {
            }

            @Override
            public void terminate() {
            }

            @Override
            public void disconnectBridge() {
            }
        });
        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);

        mMockTestDevice = EasyMock.createMock(IManagedTestDevice.class);
        mMockDeviceFactory = new IManagedTestDeviceFactory() {
            @Override
            public IManagedTestDevice createDevice(IDevice idevice) {
                mMockTestDevice.setIDevice(idevice);
                return mMockTestDevice;
            }

            @Override
            public void setFastbootEnabled(boolean enable) {
                // ignore
            }
        };
        mMockGlobalConfig = EasyMock.createNiceMock(IGlobalConfiguration.class);

        EasyMock.expect(mMockIDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockStateMonitor.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.FALSE);
        final Capture<IDevice> capturedIDevice = new Capture<>();
        mMockTestDevice.setIDevice(EasyMock.capture(capturedIDevice));
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(mMockTestDevice.getIDevice()).andStubAnswer(new IAnswer<IDevice>() {
            @Override
            public IDevice answer() throws Throwable {
                return capturedIDevice.getValue();
            }
        });
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubAnswer(new IAnswer<String>() {
            @Override
            public String answer() throws Throwable {
                return capturedIDevice.getValue().getSerialNumber();
            }
        });
        EasyMock.expect(mMockTestDevice.getMonitor()).andStubReturn(mMockStateMonitor);
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), (String) EasyMock.anyObject(),
                        (String) EasyMock.anyObject())).andStubReturn(new CommandResult());
        EasyMock.expect(
                mMockRunUtil.runTimedCmdSilently(EasyMock.anyLong(), (String) EasyMock.anyObject(),
                        (String) EasyMock.anyObject())).andStubReturn(new CommandResult());

        EasyMock.expect(mMockGlobalConfig.getDeviceRequirements()).andStubReturn(
                DeviceManager.ANY_DEVICE_OPTIONS);
    }

    private DeviceManager createDeviceManager(List<IDeviceMonitor> deviceMonitors,
            IDevice... devices) {
        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.init(null, deviceMonitors, mMockDeviceFactory);
        for (IDevice device : devices) {
            mDeviceListener.deviceConnected(device);
        }
        return mgr;
    }

    private DeviceManager createDeviceManagerNoInit() {

        DeviceManager mgr = new DeviceManager() {
            @Override
            IAndroidDebugBridge createAdbBridge() {
                return mMockAdbBridge;
            }

            @Override
            void startFastbootMonitor() {
            }

            @Override
            void startDeviceRecoverer() {
            }

            @Override
            IDeviceStateMonitor createStateMonitor(IDevice device) {
                return mMockStateMonitor;
            }

            @Override
            IGlobalConfiguration getGlobalConfig() {
                return mMockGlobalConfig;
            }

            @Override
            IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
        };
        mgr.setSynchronousMode(true);
        mgr.setMaxEmulators(0);
        mgr.setMaxNullDevices(0);
        mgr.setMaxTcpDevices(0);
        return mgr;
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     */
    public void testAllocateDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.allocateDevice());
        EasyMock.verify(mMockStateMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when device is
     * returned.
     */
    public void testAllocateDevice_match() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addSerial(DEVICE_SERIAL);
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(options));
        EasyMock.verify(mMockStateMonitor);
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when stub emulator
     * is requested
     */
    public void testAllocateDevice_stubEmulator() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setStubEmulatorRequested(true);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.TRUE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.setMaxEmulators(1);
        mgr.init(null, null, mMockDeviceFactory);
        assertNotNull(mgr.allocateDevice(options));
    }

    /**
     * Test freeing an emulator
     */
    public void testFreeDevice_emulator() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setStubEmulatorRequested(true);
        EasyMock.expect(mMockStateMonitor.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.TRUE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        mMockTestDevice.stopLogcat();
        EasyMock.expect(mMockTestDevice.getEmulatorProcess()).andStubReturn(new MockProcess());
        EasyMock.expect(mMockTestDevice.waitForDeviceNotAvailable(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        mMockTestDevice.stopEmulatorOutput();
        replayMocks();
        DeviceManager manager = createDeviceManagerNoInit();
        manager.setMaxEmulators(1);
        manager.init(null, null, mMockDeviceFactory);
        IManagedTestDevice emulator = (IManagedTestDevice) manager.allocateDevice(options);
        assertNotNull(emulator);
        // a freed 'unavailable' emulator should be returned to the available
        // queue.
        manager.freeDevice(emulator, FreeDeviceState.UNAVAILABLE);
        // ensure device can be allocated again
        assertNotNull(manager.allocateDevice(options));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(long, DeviceSelectionOptions))} when a null device
     * is requested.
     */
    public void testAllocateDevice_nullDevice() throws DeviceNotAvailableException {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.FALSE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.setMaxNullDevices(1);
        mgr.init(null, null, mMockDeviceFactory);
        ITestDevice device = mgr.allocateDevice(options);
        assertNotNull(device);
        assertTrue(device.getIDevice() instanceof NullDevice);
    }

    /**
     * Test that DeviceManager will add devices on fastboot to available queue on startup, and that
     * they can be allocated.
     */
    public void testAllocateDevice_fastboot() throws DeviceNotAvailableException {
        EasyMock.reset(mMockRunUtil);
        // mock 'fastboot help' call
        EasyMock.expect(
                mMockRunUtil.runTimedCmdSilently(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                        EasyMock.eq("help"))).andReturn(new CommandResult(CommandStatus.SUCCESS));

        // mock 'fastboot devices' call to return one device
        CommandResult fastbootResult = new CommandResult(CommandStatus.SUCCESS);
        fastbootResult.setStdout("serial        fastboot\n");
        EasyMock.expect(
                mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("fastboot"),
                        EasyMock.eq("devices"))).andReturn(fastbootResult);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        assertNotNull(manager.allocateDevice());
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is unknown
     */
    public void testForceAllocateDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        assertNotNull(manager.forceAllocateDevice("unknownserial"));
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is available
     */
    public void testForceAllocateDevice_available() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /**
     * Test {@link DeviceManager#forceAllocateDevice(String)} when device is already allocated
     */
    public void testForceAllocateDevice_alreadyAllocated() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.allocateDevice());
        assertNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /**
     * Test method for {@link DeviceManager#freeDevice(ITestDevice)}.
     */
    public void testFreeDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        mMockTestDevice.stopLogcat();
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNotNull(manager.allocateDevice());
        manager.freeDevice(mMockTestDevice, FreeDeviceState.AVAILABLE);
    }

    /**
     * Verified that {@link DeviceManager#freeDevice(ITestDevice)} ignores a call with a device that
     * has not been allocated.
     */
    public void testFreeDevice_noop() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        IManagedTestDevice testDevice = EasyMock.createNiceMock(IManagedTestDevice.class);
        EasyMock.expect(testDevice.getSerialNumber()).andReturn("dontexist");
        IDevice mockIDevice = EasyMock.createNiceMock(IDevice.class);
        EasyMock.expect(testDevice.getIDevice()).andReturn(mockIDevice);
        EasyMock.expect(mockIDevice.isEmulator()).andReturn(Boolean.FALSE);

        replayMocks(testDevice, mockIDevice);
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        manager.freeDevice(testDevice, FreeDeviceState.AVAILABLE);
    }

    /**
     * Verified that {@link DeviceManager} calls {@link IManagedTestDevice#setIDevice(IDevice)} when
     * DDMS allocates a new IDevice on connection.
     */
    public void testSetIDevice() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED)).andReturn(
                new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        IDevice newMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newMockDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newMockDevice.getState()).andReturn(DeviceState.ONLINE);
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        mMockTestDevice.setDeviceState(TestDeviceState.ONLINE);
        replayMocks(newMockDevice);
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        ITestDevice device = manager.allocateDevice();
        assertNotNull(device);
        // now trigger a device disconnect + reconnection
        mDeviceListener.deviceDisconnected(mMockIDevice);
        mDeviceListener.deviceConnected(newMockDevice);
        assertEquals(newMockDevice, device.getIDevice());
        // TODO: figure out why verification fails
        // verifyMocks(newMockDevice);
    }

    /**
     * Test {@link DeviceManager#allocateDevice()} when {@link DeviceManager#init()} has not been
     * called.
     */
    public void testAllocateDevice_noInit() throws DeviceNotAvailableException {
        try {
            createDeviceManagerNoInit().allocateDevice();
            fail("IllegalStateException not thrown when manager has not been initialized");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test {@link DeviceManager#init(IDeviceSelectionOptions)} with a global exclusion filter
     */
    public void testInit_excludeDevice() throws DeviceNotAvailableException {
        EasyMock.expect(mMockIDevice.getState()).andReturn(DeviceState.ONLINE).times(2);
        replayMocks();
        DeviceManager manager = createDeviceManagerNoInit();
        DeviceSelectionOptions excludeFilter = new DeviceSelectionOptions();
        excludeFilter.addExcludeSerial(mMockIDevice.getSerialNumber());
        manager.init(excludeFilter, null);
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNull(manager.allocateDevice());
    }

    /**
     * Test {@link DeviceManager#init(IDeviceSelectionOptions)} with a global inclusion filter
     */
    public void testInit_includeDevice() throws DeviceNotAvailableException {
        IDevice excludedDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(excludedDevice.getSerialNumber()).andStubReturn("excluded");
        EasyMock.expect(excludedDevice.getState()).andStubReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockIDevice.getState()).andStubReturn(DeviceState.ONLINE);
        EasyMock.expect(excludedDevice.isEmulator()).andStubReturn(Boolean.FALSE);
        setCheckAvailableDeviceExpectations();
        replayMocks(excludedDevice);
        DeviceManager manager = createDeviceManagerNoInit();
        DeviceSelectionOptions includeFilter = new DeviceSelectionOptions();
        includeFilter.addSerial(mMockIDevice.getSerialNumber());
        manager.init(includeFilter, null);
        mDeviceListener.deviceConnected(excludedDevice);
        // ensure excludedDevice cannot be allocated
        assertNull(manager.allocateDevice());
        EasyMock.verify();
    }

    /**
     * Verified that a disconnected device state gets updated
     */
    public void testSetState_disconnected() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        mDeviceListener.deviceDisconnected(mMockIDevice);
        EasyMock.verify();
    }

    /**
     * Verified that a offline device state gets updated
     */
    public void testSetState_offline() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        mMockTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        IDevice newDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(newDevice.getSerialNumber()).andReturn(DEVICE_SERIAL).anyTimes();
        EasyMock.expect(newDevice.getState()).andReturn(DeviceState.OFFLINE);
        EasyMock.replay(newDevice);
        mDeviceListener.deviceChanged(newDevice, IDevice.CHANGE_STATE);
    }

    // TODO: add test for fastboot state changes

    /**
     * Test normal success case for {@link DeviceManager#connectToTcpDevice(String)}
     */
    public void testConnectToTcpDevice() throws Exception {
        final String ipAndPort = "ip:5555";
        setConnectToTcpDeviceExpectations(ipAndPort);
        mMockTestDevice.waitForDeviceOnline();
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        IManagedTestDevice device = (IManagedTestDevice) manager.connectToTcpDevice(ipAndPort);
        assertNotNull(device);
        verifyMocks();
    }

    /**
     * Test a {@link DeviceManager#connectToTcpDevice(String)} call where device is already
     * allocated
     */
    public void testConnectToTcpDevice_alreadyAllocated() throws Exception {
        final String ipAndPort = "ip:5555";
        setConnectToTcpDeviceExpectations(ipAndPort);
        mMockTestDevice.waitForDeviceOnline();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        IManagedTestDevice device = (IManagedTestDevice) manager.connectToTcpDevice(ipAndPort);
        assertNotNull(device);
        // now attempt to re-allocate
        assertNull(manager.connectToTcpDevice(ipAndPort));
        verifyMocks();
    }

    /**
     * Test {@link DeviceManager#connectToTcpDevice(String)} where device does not appear on adb
     */
    public void testConnectToTcpDevice_notOnline() throws Exception {
        final String ipAndPort = "ip:5555";
        setConnectToTcpDeviceExpectations(ipAndPort, null);
        mMockTestDevice.waitForDeviceOnline();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        mMockTestDevice.stopLogcat();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN)).andReturn(
                new DeviceEventResponse(DeviceAllocationState.Unknown, false));
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in list
        assertEquals(0, manager.getDeviceList().size());
        verifyMocks();
    }

    /**
     * Test {@link DeviceManager#connectToTcpDevice(String)} where the 'adb connect' call fails.
     */
    public void testConnectToTcpDevice_connectFailed() throws Exception {
        final String ipAndPort = "ip:5555";

        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, true));
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("failed to connect to %s", ipAndPort));
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("adb"),
                EasyMock.eq("connect"), EasyMock.eq(ipAndPort)))
                .andReturn(connectResult)
                .times(3);
        mMockRunUtil.sleep(EasyMock.anyLong());
        EasyMock.expectLastCall().times(3);

        mMockTestDevice.stopLogcat();

        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in list
        assertEquals(0, manager.getDeviceList().size());
        verifyMocks();
    }

    /**
     * Test normal success case for {@link DeviceManager#disconnectFromTcpDevice(ITestDevice)}
     */
    public void testDisconnectFromTcpDevice() throws Exception {
        final String ipAndPort = "ip:5555";
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN)).andReturn(
                new DeviceEventResponse(DeviceAllocationState.Unknown, true));
        setConnectToTcpDeviceExpectations(ipAndPort);
        EasyMock.expect(mMockTestDevice.switchToAdbUsb()).andReturn(Boolean.TRUE);
        mMockTestDevice.waitForDeviceOnline();
        mMockTestDevice.stopLogcat();
        replayMocks();
        DeviceManager manager = createDeviceManager(null);
        assertNotNull(manager.connectToTcpDevice(ipAndPort));
        manager.disconnectFromTcpDevice(mMockTestDevice);
        // verify device is not in allocated or available list
        assertEquals(0, manager.getDeviceList().size());
        verifyMocks();
    }

    /**
     * Test normal success case for {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)}.
     */
    public void testReconnectDeviceToTcp() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.switchToAdbTcp()).andReturn(ipAndPort);
        setConnectToTcpDeviceExpectations(ipAndPort);
        mMockTestDevice.waitForDeviceOnline();
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        assertNotNull(manager.reconnectDeviceToTcp(mMockTestDevice));
        // TODO: figure out why verification fails on setIDevice
        // verifyMocks();
    }

    /**
     * Test {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)} when tcp connected device does
     * not come online.
     */
    public void testReconnectDeviceToTcp_notOnline() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        EasyMock.expect(mMockTestDevice.switchToAdbTcp()).andReturn(ipAndPort);
        setConnectToTcpDeviceExpectations(ipAndPort);
        mMockTestDevice.waitForDeviceOnline();
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        // expect recover to be attempted on usb device
        mMockTestDevice.recoverDevice();
        mMockTestDevice.stopLogcat();
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN)).andReturn(
                new DeviceEventResponse(DeviceAllocationState.Unknown, true));
        replayMocks();
        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice());
        assertNull(manager.reconnectDeviceToTcp(mMockTestDevice));
        // verify only usb device is in list
        assertEquals(1, manager.getDeviceList().size());
        // TODO: figure out why verification fails on setIDevice
        // verifyMocks();
    }

    /**
     * Basic test for {@link DeviceManager#sortDeviceList(List))}
     */
    public void testSortDeviceList() {
        DeviceDescriptor availDevice1 = createDeviceDesc("aaa", DeviceAllocationState.Available);
        DeviceDescriptor availDevice2 = createDeviceDesc("bbb", DeviceAllocationState.Available);
        DeviceDescriptor allocatedDevice = createDeviceDesc("ccc", DeviceAllocationState.Allocated);
        List<DeviceDescriptor> deviceList = ArrayUtil.list(availDevice1, availDevice2,
                allocatedDevice);
        List<DeviceDescriptor> sortedList = DeviceManager.sortDeviceList(deviceList);
        assertEquals(allocatedDevice, sortedList.get(0));
        assertEquals(availDevice1, sortedList.get(1));
        assertEquals(availDevice2, sortedList.get(2));
    }

    /**
     * Helper method to create a {@link DeviceDescriptor} using only serial and state.
     */
    private DeviceDescriptor createDeviceDesc(String serial, DeviceAllocationState state) {
        return new DeviceDescriptor(serial, false, state, null, null, null, null, null);
    }

    private void setConnectToTcpDeviceExpectations(final String ipAndPort)
            throws DeviceNotAvailableException {
        setConnectToTcpDeviceExpectations(ipAndPort, mMockIDevice);
    }

    /**
     * Set EasyMock expectations for a successful {@link DeviceManager#connectToTcpDevice(String)}
     * call.
     *
     * @param ipAndPort the ip and port of the device
     * @throws DeviceNotAvailableException
     */
    private void setConnectToTcpDeviceExpectations(final String ipAndPort, IDevice result)
            throws DeviceNotAvailableException {
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        mMockTestDevice.setRecovery((IDeviceRecovery) EasyMock.anyObject());
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("connected to %s", ipAndPort));
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(), EasyMock.eq("adb"),
                EasyMock.eq("connect"), EasyMock.eq(ipAndPort)))
                .andReturn(connectResult);
    }

    /**
     * Sets all member mock objects into replay mode.
     *
     * @param additionalMocks extra local mock objects to set to replay mode
     */
    private void replayMocks(Object... additionalMocks) {
        EasyMock.replay(mMockStateMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge,
                mMockRunUtil, mMockGlobalConfig);
        for (Object mock : additionalMocks) {
            EasyMock.replay(mock);
        }
    }

    /**
     * Verify all member mock objects.
     *
     * @param additionalMocks extra local mock objects to set to verify
     */
    private void verifyMocks(Object... additionalMocks) {
        EasyMock.verify(mMockStateMonitor, mMockTestDevice, mMockIDevice, mMockAdbBridge,
                mMockRunUtil);
        for (Object mock : additionalMocks) {
            EasyMock.verify(mock);
        }
    }

    /**
     * Configure EasyMock expectations for a {@link DeviceManager#checkAndAddAvailableDevice()} call
     * for an online device
     */
    private void setCheckAvailableDeviceExpectations() {
        setCheckAvailableDeviceExpectations(mMockIDevice);
    }

    private void setCheckAvailableDeviceExpectations(IDevice iDevice) {
        EasyMock.expect(mMockIDevice.isEmulator()).andStubReturn(Boolean.FALSE);
        EasyMock.expect(iDevice.getState()).andReturn(DeviceState.ONLINE);
        EasyMock.expect(mMockStateMonitor.waitForDeviceShell(EasyMock.anyLong())).andReturn(
                Boolean.TRUE);
        mMockTestDevice.setIDevice(iDevice);
        mMockTestDevice.setDeviceState(TestDeviceState.ONLINE);
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Checking_Availability,
                        true));
        EasyMock.expect(mMockTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_PASSED))
                .andReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
    }
}
