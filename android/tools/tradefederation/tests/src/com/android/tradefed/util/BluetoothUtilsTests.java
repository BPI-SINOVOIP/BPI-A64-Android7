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

package com.android.tradefed.util;

import junit.framework.TestCase;

import java.util.Set;

public class BluetoothUtilsTests extends TestCase {

    public void testParseBondedDeviceInstrumentationOutput() throws Exception {
        String[] lines = {
                "INSTRUMENTATION_RESULT: result=SUCCESS",
                "INSTRUMENTATION_RESULT: device-00=11:22:33:44:55:66",
                "INSTRUMENTATION_RESULT: device-01=22:33:44:55:66:77",
                "INSTRUMENTATION_RESULT: device-02=33:44:55:66:77:88",
                "INSTRUMENTATION_CODE: -1",
        };
        Set<String> ret = BluetoothUtils.parseBondedDeviceInstrumentationOutput(lines);
        assertEquals("return set has wrong number of entries", 3, ret.size());
        assertTrue("missing mac 00", ret.contains("11:22:33:44:55:66"));
        assertTrue("missing mac 01", ret.contains("22:33:44:55:66:77"));
        assertTrue("missing mac 02", ret.contains("33:44:55:66:77:88"));
    }
}
