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

package com.example.android.support.design.widget;

import com.example.android.support.design.R;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


/**
 * This demonstrates basic usage of hideable {@link BottomSheetBehavior}.
 */
public class BottomSheetHideable extends BottomSheetUsageBase {

    private TextView mTextSlideOffset;

    private Button mToggle;

    @Override
    protected int getLayoutId() {
        return R.layout.design_bottom_sheet_hideable;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTextSlideOffset = (TextView) findViewById(R.id.slide_offset);
        mToggle = (Button) findViewById(R.id.toggle);
        mToggle.setOnClickListener(mOnClickListener);
        mBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet,
                    @BottomSheetBehavior.State int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        mToggle.setText(R.string.bottomsheet_show);
                        mToggle.setEnabled(true);
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        mToggle.setText(R.string.bottomsheet_hide);
                        mToggle.setEnabled(true);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                mTextSlideOffset.setText(String.valueOf(slideOffset));
            }
        });
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.toggle && mBehavior != null) {
                mToggle.setEnabled(false);
                if (mBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                    mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    mBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }
        }
    };

}
