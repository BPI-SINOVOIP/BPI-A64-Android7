/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.util.List;

import android.hardware.display.DisplayManager;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.SystemProperties;
import android.provider.Settings;

public class ScrapEdgePreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener{
    
    private SeekBar mHeightSeekBar, mWidthSeekBar;
    private TextView mHeightTextView, mWidthTextView;
	private Context mContext;
	private int hValue, wValue;
	private int edgeSize, hEdgeSize, wEdgeSize;

    private static final int SEEK_BAR_RANGE = 10;
	private DisplayManager mDisplayManager;

    public ScrapEdgePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
		mContext = context;
		mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        setDialogLayoutResource(R.layout.preference_dialog_scrapedge);
		//setDialogIcon(R.drawable.ic_settings_display);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

		mHeightSeekBar = (SeekBar) view.findViewById(R.id.height_seekbar);
		mWidthSeekBar = (SeekBar) view.findViewById(R.id.width_seekbar);
        mHeightSeekBar.setMax(SEEK_BAR_RANGE);
		mWidthSeekBar.setMax(SEEK_BAR_RANGE);
		mHeightTextView = (TextView)view.findViewById(R.id.height_percent_txt);
		mWidthTextView = (TextView)view.findViewById(R.id.width_percent_txt);
		//set default value
		hValue = hEdgeSize = 90;
		wValue = wEdgeSize = 90;
		try{
			edgeSize = Settings.System.getInt(mContext.getContentResolver(),Settings.System.HDMI_PERSENT);
			hValue = hEdgeSize = edgeSize&0xff;
			wValue = wEdgeSize = (edgeSize&0xff00)>>8;
		}catch(android.provider.Settings.SettingNotFoundException ex){ 
		
		}
		
        mHeightSeekBar.setProgress(hValue- 90);
		mWidthSeekBar.setProgress(wValue- 90);
		mHeightTextView.setText(hValue+"%");
		mWidthTextView.setText(wValue+"%");
		
		mHeightSeekBar.setEnabled(true);
        mHeightSeekBar.setOnSeekBarChangeListener(this);
		mWidthSeekBar.setEnabled(true);
        mWidthSeekBar.setOnSeekBarChangeListener(this);
    }
	
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
		if(seekBar == mHeightSeekBar){
			hEdgeSize = 90 + progress;
			edgeSize = hEdgeSize + (wEdgeSize<<8);
			Settings.System.putInt(mContext.getContentResolver(),Settings.System.HDMI_PERSENT,edgeSize);
			mHeightTextView.setText(hEdgeSize+"%");
		}else if(seekBar == mWidthSeekBar){
			wEdgeSize = 90 + progress;
			edgeSize = hEdgeSize + (wEdgeSize<<8);
			Settings.System.putInt(mContext.getContentResolver(),Settings.System.HDMI_PERSENT,edgeSize);
			mWidthTextView.setText(wEdgeSize+"%");
		}

    }
	
	public void onStartTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        // NA
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
			Settings.System.putInt(mContext.getContentResolver(),Settings.System.HDMI_PERSENT,edgeSize);
        } else {
			Settings.System.putInt(mContext.getContentResolver(),Settings.System.HDMI_PERSENT,hValue + (wValue<<8));
        }
    }
	
}
