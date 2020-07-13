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

import android.content.Context;
import android.support.v7.preference.Preference;
import android.view.View;
import android.widget.Switch;
import android.support.v7.preference.PreferenceViewHolder;
import com.android.settings.R;

public class BackgroundCleanupAppPreference extends Preference {
    private boolean mChecked;
    private String mPackageName;

    public BackgroundCleanupAppPreference(Context context, String packageName) {
        super(context, null);

        mPackageName = packageName;
        setLayoutResource(R.layout.background_cleanup_app_item);
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean getChecked() {
        return mChecked;
    }

    public void setChecked(boolean checked) {
        mChecked = checked;
        notifyChanged();
    }

    public void doSwitch() {
        setChecked(!mChecked);
    }

	@Override
	public void onBindViewHolder(PreferenceViewHolder arg0) {
		// TODO Auto-generated method stub
		super.onBindViewHolder(arg0);
		
		((Switch) arg0.findViewById(R.id.switchWidget)).setChecked(mChecked);
		 	
	}
	
   /* @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        ((Switch) view.findViewById(R.id.switchWidget)).setChecked(mChecked);
    }
	*/
}
