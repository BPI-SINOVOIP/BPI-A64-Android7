/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Factory to create the different kind of devices that can be monitored by Tf
 */
public class ManagedTestDeviceFactory implements IManagedTestDeviceFactory {

    private boolean mFastbootEnabled;
    private IDeviceManager mDeviceManager;
    private IDeviceMonitor mAllocationMonitor;

    public ManagedTestDeviceFactory(boolean fastbootEnabled, IDeviceManager deviceManager,
            IDeviceMonitor allocationMonitor) {
        mFastbootEnabled = fastbootEnabled;
        mDeviceManager = deviceManager;
        mAllocationMonitor = allocationMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IManagedTestDevice createDevice(IDevice idevice) {
        // TODO: Based on idevice chose what device to implement
        TestDevice testDevice = new TestDevice(idevice,
                new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                mAllocationMonitor);
        testDevice.setFastbootEnabled(mFastbootEnabled);

        if (idevice instanceof FastbootDevice) {
            testDevice.setDeviceState(TestDeviceState.FASTBOOT);
        } else if (idevice instanceof StubDevice) {
            testDevice.setDeviceState(TestDeviceState.ONLINE);
        }
        return testDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean enable) {
        mFastbootEnabled = enable;
    }
}
