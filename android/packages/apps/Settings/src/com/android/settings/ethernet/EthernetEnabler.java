/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.ethernet;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.preference.CheckBoxPreference;

import java.util.ArrayList;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;
import com.android.settings.widget.SwitchBar;
import com.android.settings.SettingsActivity;
import android.widget.Switch;
import android.util.Slog;
import android.net.EthernetManager;
import android.provider.Settings;

public class EthernetEnabler implements SwitchBar.OnSwitchChangeListener {
	private final String TAG = "EthernetEnabler";
    private Context mContext;
    private SwitchBar mSwitchBar;
	private boolean mListeningToOnSwitchChange = false;
	private EthernetDialog mEthDialog = null;
	private EthernetManager mEthManager;
    public void setConfigDialog(EthernetDialog Dialog) {
        mEthDialog = Dialog;
    }

    public EthernetEnabler(Context context, SwitchBar switchBar,EthernetManager ethernetManager) {
        mContext = context;
        mSwitchBar = switchBar;
		mEthManager = ethernetManager;
        setupSwitchBar();
    }

    public void resume(Context context) {
        mContext = context;
        if (!mListeningToOnSwitchChange) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = true;
        }
    }

    public void pause() {
        if (mListeningToOnSwitchChange) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = false;
        }
    }

    public void setupSwitchBar() {
        if (!mListeningToOnSwitchChange) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = true;
        }
        mSwitchBar.show();
		int enable = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.ETHERNET_ON,0);//add by hclydao
		if(enable == EthernetManager.ETH_STATE_ENABLED) {
			mSwitchBar.setChecked(true);
		} else {
			mSwitchBar.setChecked(false);
		}
    }

    public void teardownSwitchBar() {
        if (mListeningToOnSwitchChange) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = false;
        }
        mSwitchBar.hide();
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
		if(isChecked) {
			//if(mEthDialog != null)
				//mEthDialog.show();
		} else {
			if(mEthManager != null)
				mEthManager.stop();	
		}

		Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.ETHERNET_ON,
				isChecked ? EthernetManager.ETH_STATE_ENABLED : EthernetManager.ETH_STATE_DISABLED);

		if(isChecked)
			if(mEthManager != null)
				mEthManager.start();
    }
}
