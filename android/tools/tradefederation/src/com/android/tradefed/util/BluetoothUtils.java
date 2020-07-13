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

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for calling BluetoothInstrumentation on device
 * <p>
 * Device side BluetoothInstrumentation code can be found in AOSP at:
 * <code>frameworks/base/core/tests/bluetoothtests</code>
 *
 */
public class BluetoothUtils {

    private static final String BT_INSTR_CMD = "am instrument -w -r -e command %s "
            + "com.android.bluetooth.tests/android.bluetooth.BluetoothInstrumentation";
    private static final String SUCCESS_INSTR_OUTPUT = "INSTRUMENTATION_RESULT: result=SUCCESS";
    private static final String BT_GETADDR_HEADER = "INSTRUMENTATION_RESULT: address=";
    private static final long BASE_RETRY_DELAY_MS = 60 * 1000;
    private static final int MAX_RETRIES = 3;
    private static final Pattern BONDED_MAC_HEADER =
            Pattern.compile("INSTRUMENTATION_RESULT: device-\\d{2}=(.*)$");

    /**
     * Convenience method to execute BT instrumentation command and return output
     * @param device
     * @param command a command string sent over to BT instrumentation, currently supported:
     *                 enable, disable, unpairAll, getName, getAddress, getBondedDevices; refer to
     *                 AOSP source for more details
     * @return
     * @throws DeviceNotAvailableException
     */
    public static String runBluetoothInstrumentation(ITestDevice device, String command)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(String.format(BT_INSTR_CMD, command), receiver);
        String output = receiver.getOutput();
        CLog.v("bluetooth instrumentation sub command: %s\noutput:\n", command);
        CLog.v(output);
        return output;
    }

    public static boolean runBluetoothInstrumentationWithRetry(ITestDevice device, String command)
        throws DeviceNotAvailableException {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            String output = runBluetoothInstrumentation(device, command);
            if (output.contains(SUCCESS_INSTR_OUTPUT)) {
                return true;
            }
            RunUtil.getDefault().sleep(retry * BASE_RETRY_DELAY_MS);
        }
        return false;
    }

    /**
     * Retries clearing of BT pairing with linear backoff
     * @param device
     * @throws DeviceNotAvailableException
     */
    public static boolean unpairWithRetry(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "unpairAll");
    }

    /**
     * Retrieves BT mac of the given device
     * @param device
     * @return
     * @throws DeviceNotAvailableException
     */
    public static String getBluetoothMac(ITestDevice device) throws DeviceNotAvailableException {
        String lines[] = runBluetoothInstrumentation(device, "getAddress").split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(BT_GETADDR_HEADER)) {
                return line.substring(BT_GETADDR_HEADER.length());
            }
        }
        return null;
    }

    /**
     * Enables bluetooth on the given device
     * @param device
     * @return
     * @throws DeviceNotAvailableException
     */
    public static boolean enable(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "enable");
    }

    /**
     * Disables bluetooth on the given device
     * @param device
     * @return
     * @throws DeviceNotAvailableException
     */
    public static boolean disable(ITestDevice device)
            throws DeviceNotAvailableException {
        return runBluetoothInstrumentationWithRetry(device, "disable");
    }

    /**
     * Returns bluetooth mac addresses of devices that the given device has bonded with
     * @param device
     * @return
     * @throws DeviceNotAvailableException
     */
    public static Set<String> getBondedDevices(ITestDevice device)
            throws DeviceNotAvailableException {
        String lines[] = runBluetoothInstrumentation(device, "getBondedDevices").split("\\r?\\n");
        return parseBondedDeviceInstrumentationOutput(lines);
    }

    /** Parses instrumentation output into mac addresses */
    static Set<String> parseBondedDeviceInstrumentationOutput(String[] lines) {
        Set<String> ret = new HashSet<>();
        for (String line : lines) {
            Matcher m = BONDED_MAC_HEADER.matcher(line.trim());
            if (m.find()) {
                ret.add(m.group(1));
            }
        }
        return ret;
    }
}
