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
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.util.ConditionPriorityBlockingQueue;
import com.android.tradefed.util.ConditionPriorityBlockingQueue.IMatcher;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Assert;

import java.io.PrintWriter;
import java.util.List;

/**
 * A {@link IDeviceManager} that simulates the resource allocation of {@link DeviceManager}
 * for a configurable set of devices.
 */
public class MockDeviceManager implements IDeviceManager {

    // acts as an available device queue
    ConditionPriorityBlockingQueue<ITestDevice> mAvailableDeviceQueue =
        new ConditionPriorityBlockingQueue<ITestDevice>();

    private int mTotalDevices;
    private DeviceMonitorMultiplexer mDvcMon = new DeviceMonitorMultiplexer();

    public MockDeviceManager(int numDevices) {
        setNumDevices(numDevices);
    }

    public void setNumDevices(int numDevices) {
        mAvailableDeviceQueue.clear();
        mTotalDevices = numDevices;
        for (int i = 0; i < numDevices; i++) {
            ITestDevice mockDevice = EasyMock.createNiceMock(ITestDevice.class);
            EasyMock.expect(mockDevice.getSerialNumber()).andReturn("serial" + i).anyTimes();
            IDevice mockIDevice = EasyMock.createNiceMock(IDevice.class);
            EasyMock.expect(mockIDevice.getSerialNumber()).andReturn("serial" + i).anyTimes();
            EasyMock.expect(mockDevice.getIDevice()).andReturn(mockIDevice).anyTimes();
            EasyMock.expect(mockDevice.getDeviceState()).andReturn(
                    TestDeviceState.ONLINE).anyTimes();
            EasyMock.replay(mockDevice, mockIDevice);
            mAvailableDeviceQueue.add(mockDevice);
        }
    }

    private static class TestDeviceMatcher implements IMatcher<ITestDevice> {
        private IDeviceSelection mDeviceOptions;

        /**
         * @param deviceSelectionOptions
         */
        public TestDeviceMatcher(IDeviceSelection deviceSelectionOptions) {
            mDeviceOptions = deviceSelectionOptions;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(ITestDevice element) {
            return mDeviceOptions.matches(element.getIDevice());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFastbootListener(IFastbootListener listener) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice allocateDevice() {
        try {
            return mAvailableDeviceQueue.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void freeDevice(ITestDevice device, FreeDeviceState state) {
        if (!state.equals(FreeDeviceState.UNAVAILABLE)) {
            mAvailableDeviceQueue.add(device);
            mDvcMon.notifyDeviceStateChange(device.getSerialNumber(), DeviceAllocationState.Allocated,
                    DeviceAllocationState.Available);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice forceAllocateDevice(String serial) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeFastbootListener(IFastbootListener listener) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminate() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice allocateDevice(IDeviceSelection options) {
        ITestDevice d = mAvailableDeviceQueue.poll(new TestDeviceMatcher(options));
        if (d!= null) {
            mDvcMon.notifyDeviceStateChange(d.getSerialNumber(), DeviceAllocationState.Available,
                    DeviceAllocationState.Allocated);
        }
        return d;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminateHard() {
        // ignore
    }

    @Override
    public void init() {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(IDeviceSelection globalDeviceFilter,
            List<IDeviceMonitor> globalDeviceMonitors) {
        // ignore
    }

    /**
     * Verifies that all devices were returned to queue.
     * @throws AssertionError
     */
    public void assertDevicesFreed() throws AssertionError {
        Assert.assertEquals("allocated device was not returned to queue", mTotalDevices,
                mAvailableDeviceQueue.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice reconnectDeviceToTcp(ITestDevice usbDevice)
            throws DeviceNotAvailableException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice connectToTcpDevice(String ipAndPort) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disconnectFromTcpDevice(ITestDevice tcpDevice) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launchEmulator(ITestDevice device, long bootTimeout, IRunUtil runUtil,
            List<String> emulatorArgs) throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void killEmulator(ITestDevice device) throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void displayDevicesInfo(PrintWriter stream) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DeviceDescriptor> listAllDevices() {
        return null;
    }

    @Override
    public boolean isNullDevice(String serial) {
        return false;
    }

    @Override
    public boolean isEmulator(String serial) {
        return false;
    }

    @Override
    public void addDeviceMonitor(IDeviceMonitor mon) {
        mDvcMon.addMonitor(mon);
    }

    @Override
    public void removeDeviceMonitor(IDeviceMonitor mon) {
        mDvcMon.removeMonitor(mon);
    }
}
