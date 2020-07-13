/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tradefed.command.remote;

import com.android.tradefed.device.DeviceAllocationState;

public class DeviceDescriptor {

    private final String mSerial;
    private final boolean mIsStubDevice;
    private final DeviceAllocationState mState;
    private final String mProduct;
    private final String mProductVariant;
    private final String mSdkVersion;
    private final String mBuildId;
    private final String mBatteryLevel;
    private final String mDeviceClass;

    public DeviceDescriptor(String serial, boolean isStubDevice, DeviceAllocationState state,
            String product, String productVariant, String sdkVersion, String buildId,
            String batteryLevel) {
        this(serial, isStubDevice, state, product, productVariant, sdkVersion, buildId,
                batteryLevel, "");
    }

    public DeviceDescriptor(String serial, boolean isStubDevice, DeviceAllocationState state,
            String product, String productVariant, String sdkVersion, String buildId,
            String batteryLevel, String deviceClass) {
        mSerial = serial;
        mIsStubDevice = isStubDevice;
        mState = state;
        mProduct = product;
        mProductVariant = productVariant;
        mSdkVersion = sdkVersion;
        mBuildId = buildId;
        mBatteryLevel = batteryLevel;
        mDeviceClass = deviceClass;
    }

    public String getSerial() {
        return mSerial;
    }

    public boolean isStubDevice() {
        return mIsStubDevice;
    }

    public DeviceAllocationState getState() {
        return mState;
    }

    public String getProduct() {
        return mProduct;
    }

    public String getProductVariant() {
        return mProductVariant;
    }

    public String getDeviceClass() {
        return mDeviceClass;
    }

    /*
     * This version maps to the ro.build.version.sdk property.
     */
    public String getSdkVersion() {
        return mSdkVersion;
    }

    public String getBuildId() {
        return mBuildId;
    }

    public String getBatteryLevel() {
        return mBatteryLevel;
    }

    /**
     * Provides a description with serials, product and build id
     */
    @Override
    public String toString() {
        return String.format("[%s %s:%s %s]", mSerial, mProduct, mProductVariant, mBuildId);
    }
}
