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
package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import java.lang.System;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.Assert;

/**
 * An utility used to control WiFi/bluetooth attenuator through its ip network connection
 * Designed for Mini-circuit USB/ETHERNET programmable attenuator
 */
public class AttenuatorUtil {

    private Socket mAtt = null;
    private String mIpAddress = "";

    // Create a new attenuator object by providing an ip address
    public AttenuatorUtil(String ip) {
        mIpAddress = ip;
    }

    private void connect () throws IOException {
        mAtt = null;
        try {
            mAtt = new Socket(mIpAddress, 23);
        } catch (UnknownHostException e) {
            Assert.fail("Unknown host");
        }
    }

    private void disconnect() throws IOException {
        mAtt.close();
        mAtt = null;
    }

    private String sendCommand(String command) {
    //Sending a raw command like this temp = a1.sendCommand("SETATT=70");
        PrintWriter out = null;
        BufferedReader in = null;
        String buffer = "";
        try {
            connect();
            if (mAtt == null) {
                Assert.fail("Please connect to the attenuator first");
            }
            out = new PrintWriter(mAtt.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(mAtt.getInputStream()));
            out.println(command);
            /* Example output from the command
             *   ATT?
             *    0.0
             *   SETATT=10
             *   1
             *   ATT?
             *   10.0
             */
            buffer = in.readLine();
            buffer = in.readLine();
            out.close();
            in.close();
            disconnect();
        } catch (IOException e) {
            CLog.e(e);
            Assert.fail();
        }
        return buffer;
    }

    /* Set attenuator to a value between 0-99,
     * return true if it set correctly,
     * return false if it failed
     * @value: The value you want to set the attenuator
     */
    public boolean setValue(int value) {
        Assert.assertTrue("Use value between 0-95", value>= 0 && value <= 95);
        String temp1 = sendCommand(String.format("SETATT=%d", value));
        String temp2 = sendCommand("ATT?");
        try {
            float x = Float.parseFloat(temp2);
            int y = Integer.parseInt(temp1);
            if (value != (int)x || y != 1) {
                CLog.e("Failed to set value set to %d, %s, %s", value, temp1, temp2);
                return false;
            } else {
                CLog.d("Attenuator %s set to %d", mIpAddress, value);
            }
        } catch (NumberFormatException e) {
            CLog.e("Error format, set value to %d, '%s' ,'%s'", value, temp1, temp2);
            return false;
        }
        return true;
    }

    // Get current attenuator value
    public int getValue() {
        String temp = sendCommand("ATT?");
        float x = 0;
        try {
            x = Float.parseFloat(temp);
        } catch (NumberFormatException e) {
            Assert.fail(String.format("Failed to get value '%s'", temp));
        }
        return (int)x;
    }

    /**
     * Set attenuator to a new value progressively with multiple setting steps from current value
     * @startValue: The starting value for attenuator
     * @endValue: The final value for attenuator
     * @steps: The steps we takes to reach the final value
     * @waitTime: The wait time in MS between each iteration
     */
    public boolean progressivelySetAttValue(int startValue, int endValue, int steps, int waitTime) {
        int stepSize = (endValue - startValue) / steps;
        if (stepSize >= 0) {
            for (int i = startValue; i < endValue; i+= stepSize) {
                if (!setValue(i)) {
                    return false;
                }
                if (waitTime > 0) {
                    RunUtil.getDefault().sleep(waitTime);
                }
            }
        } else {
            for (int i = startValue; i > endValue; i+= stepSize) {
                if (!setValue(i)) {
                    return false;
                }
                if (waitTime > 0) {
                    RunUtil.getDefault().sleep(waitTime);
                }
            }
        }
        if (!setValue(endValue)) {
            return false;
        }
        return true;
    }
}
