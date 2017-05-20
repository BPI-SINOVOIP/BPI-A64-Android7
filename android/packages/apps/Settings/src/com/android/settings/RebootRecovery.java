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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.internal.logging.MetricsLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirm and execute a reboot to recovery mode.
 . Multiple confirmations are required: first, a general "are you sure you want to do this?"
 * prompt, followed by a keyguard pattern trace if the user has defined one, followed by a final
 * strongly-worded "THIS WILL REBOOT DEVICE" prompt.  
 *
 * This is the initial screen.
 */
public class RebootRecovery extends OptionsMenuFragment {
    private static final String TAG = "RebootRecovery";
    // Arbitrary to avoid conficts
    private static final int KEYGUARD_REQUEST = 55;

    private View mContentView;
    private Button mInitiateButton;

    /**
     * Keyguard validation is run using the standard {@link ConfirmLockPattern}
     * component as a subactivity
     * @param request the request code to be returned once confirmation finishes
     * @return true if confirmation launched
     */
    private boolean runKeyguardConfirmation(int request) {
        Resources res = getActivity().getResources();
        return new ChooseLockSettingsHelper(getActivity(), this).launchConfirmationActivity(
                request, res.getText(R.string.reboot_recovery_title));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != KEYGUARD_REQUEST) {
            return;
        }

        // If the user entered a valid keyguard trace, present the final
        // confirmation prompt; otherwise, go back to the initial state.
        if (resultCode == Activity.RESULT_OK) {
            showFinalConfirmation();
        } else {
            establishInitialState();
        }
    }

    private void showFinalConfirmation() {
        ((SettingsActivity) getActivity()).startPreferencePanel(RebootRecoveryConfirm.class.getName(),
                null, R.string.reboot_recovery_confirm_title, null, null, 0);
    }

    /**
     * If the user clicks to begin the reboot sequence, we next require a
     * keyguard confirmation if the user has currently enabled one.  If there
     * is no keyguard available, we simply go to the final confirmation prompt.
     */
    private final Button.OnClickListener mInitiateListener = new Button.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                showFinalConfirmation();
            }
        }
    };

    /**
     * In its initial state, the activity presents a button for the user to
     * click in order to initiate a confirmation sequence.  This method is
     * called from various other points in the code to reset the activity to
     * this base state.
     */
    private void establishInitialState() {
        mInitiateButton = (Button) mContentView.findViewById(R.id.initiate_reboot_recovery);
        mInitiateButton.setOnClickListener(mInitiateListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.reboot_recovery, null);

        establishInitialState();
        return mContentView;
    }

    @Override
    protected int getMetricsCategory() {
        return REBOOT_RECOVERY;
    }
}
