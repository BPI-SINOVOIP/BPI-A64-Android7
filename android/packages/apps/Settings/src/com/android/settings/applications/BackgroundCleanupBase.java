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
package com.android.settings.applications;

import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public abstract class BackgroundCleanupBase extends SettingsPreferenceFragment
        implements OnItemSelectedListener {
    private static final int NUM_LIMIT_MAX = 4;
    protected int mLimit;

    private ViewGroup mSpinnerHeader;
    private Spinner mFilterSpinner;
    private ArrayAdapter<String> mFilterAdapter;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.background_cleanup);
        mLimit = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.BACKGROUND_SERVICES_LIMIT_COUNT, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSpinnerHeader = (ViewGroup) setPinnedHeaderView(R.layout.apps_filter_spinner);
        mFilterSpinner = (Spinner) mSpinnerHeader.findViewById(R.id.filter_spinner);
        mFilterAdapter = new ArrayAdapter<String>(getActivity(), R.layout.filter_spinner_item);
        mFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (int i = 0; i < NUM_LIMIT_MAX + 1; i++) {
            mFilterAdapter.add(getString(R.string.background_cleanup_limit, i));
        }
        mFilterSpinner.setAdapter(mFilterAdapter);
        mFilterSpinner.setSelection(mLimit);
        mFilterSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mLimit = position;
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.BACKGROUND_SERVICES_LIMIT_COUNT, mLimit);
        refreshUi();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Select something.
        //mFilterSpinner.setSelection(0);
    }

    public abstract void refreshUi();
}
