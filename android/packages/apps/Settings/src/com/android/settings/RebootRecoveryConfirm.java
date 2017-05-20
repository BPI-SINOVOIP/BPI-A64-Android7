/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.internal.logging.MetricsLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Confirm and execute a reboot to recovery mode.
 * Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one.
 *
 * This is the confirmation screen.
 */
public class RebootRecoveryConfirm extends OptionsMenuFragment {

    private View mContentView;
    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and reset the network settings to its factory-default state.
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (Utils.isMonkeyRunning()) {
                return;
            }

            rebootRecovery();
        }
    };

    private void rebootRecovery() {
        File RECOVERY_DIR = new File("/cache/recovery");
        File COMMAND_FILE = new File(RECOVERY_DIR, "command");
        String SHOW_TEXT="--show_text";
        RECOVERY_DIR.mkdirs();
        // In case we need it                   COMMAND_FILE.delete();
        // In case it's not writable
        try {
           FileWriter command = new FileWriter(COMMAND_FILE);
            command.write(SHOW_TEXT);
            command.write("\n");
            command.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        pm.reboot("recovery");
    }

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        mContentView.findViewById(R.id.execute_reboot_recovery)
                .setOnClickListener(mFinalClickListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.reboot_recovery_confirm, null);
        establishFinalConfirmationState();
        return mContentView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getMetricsCategory() {
        return REBOOT_RECOVERY_CONFIRM;
    }
}
