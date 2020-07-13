/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.IRunUtil;
import com.google.common.util.concurrent.SettableFuture;

import junit.framework.TestCase;

import org.easymock.EasyMock;

public class DeviceBatteryLevelCheckerTest extends TestCase {
    private DeviceBatteryLevelChecker mChecker = null;
    ITestDevice mFakeTestDevice = null;
    IDevice mFakeDevice = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mChecker = new DeviceBatteryLevelChecker() {
            @Override
            IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }
        };
        mFakeTestDevice = EasyMock.createStrictMock(ITestDevice.class);
        mFakeDevice = EasyMock.createStrictMock(IDevice.class);

        mChecker.setDevice(mFakeTestDevice);

        EasyMock.expect(mFakeTestDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mFakeTestDevice.getIDevice()).andStubReturn(mFakeDevice);
    }

    public void testNull() throws Exception {
        expectBattLevel(null);
        replayDevices();

        mChecker.run(null);
        // expect this to return immediately without throwing an exception.  Should log a warning.
        verifyDevices();
    }

    public void testNormal() throws Exception {
        expectBattLevel(45);
        replayDevices();

        mChecker.run(null);
        verifyDevices();
    }

    public void testLow() throws Exception {
        expectBattLevel(5);
        expectBattLevel(20);
        expectBattLevel(50);
        expectBattLevel(90);
        replayDevices();

        mChecker.run(null);
        verifyDevices();
    }

    private void expectBattLevel(Integer level) throws Exception {
        SettableFuture<Integer> f = SettableFuture.create();
        f.set(level);
        EasyMock.expect(mFakeDevice.getBattery()).andReturn(f);
    }

    private void replayDevices() {
        EasyMock.replay(mFakeTestDevice, mFakeDevice);
    }

    private void verifyDevices() {
        EasyMock.verify(mFakeTestDevice, mFakeDevice);
    }
}

