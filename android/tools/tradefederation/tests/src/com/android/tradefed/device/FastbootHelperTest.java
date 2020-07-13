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

package com.android.tradefed.device;

import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Collection;

/**
 * Unit tests for {@link FastBootHelper}.
 */
public class FastbootHelperTest extends TestCase {

    private IRunUtil mMockRunUtil;
    private FastbootHelper mFastbootHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mFastbootHelper = new FastbootHelper(mMockRunUtil);
    }

    /**
     * Verify the 'fastboot devices' output parsing
     */
    public void testParseDevicesOnFastboot() {
        Collection<String> deviceSerials = mFastbootHelper.parseDevices(
                "04035EEB0B01F01C        fastboot\n" +
                "HT99PP800024    fastboot\n" +
                "????????????    fastboot");
        assertEquals(2, deviceSerials.size());
        assertTrue(deviceSerials.contains("04035EEB0B01F01C"));
        assertTrue(deviceSerials.contains("HT99PP800024"));
    }

    /**
     * Verify the 'fastboot devices' output parsing when empty
     */
    public void testParseDevicesOnFastboot_empty() {
        Collection<String> deviceSerials = mFastbootHelper.parseDevices("");
        assertEquals(0, deviceSerials.size());
    }

}
