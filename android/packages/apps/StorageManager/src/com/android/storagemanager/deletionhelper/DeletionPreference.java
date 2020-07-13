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

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.CheckBoxPreference;
import android.util.AttributeSet;

import com.android.storagemanager.deletionhelper.DeletionType;
import com.android.storagemanager.R;

/**
 * Preference to handle the deletion of various data types in the Deletion Helper.
 */
public abstract class DeletionPreference extends CheckBoxPreference implements
        DeletionType.FreeableChangedListener, OnPreferenceChangeListener {
    private DeletionType.FreeableChangedListener mListener;
    private long mFreeableBytes;
    private int mFreeableItems;
    private DeletionType mDeletionService;

    public DeletionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.deletion_preference);
        setOnPreferenceChangeListener(this);
        setPersistent(false);
    }

    /**
     * Returns the number of bytes which can be cleared by the deletion service.
     * @return The number of bytes.
     */
    public long getFreeableBytes() {
        return isChecked() ? mFreeableBytes : 0;
    }

    /**
     * Register a listener to be called back on when the freeable bytes have changed.
     * @param listener The callback listener.
     */
    public void registerFreeableChangedListener(DeletionType.FreeableChangedListener listener) {
        mListener = listener;
    }

    /**
     * Registers a deletion service to update the preference's information.
     * @param deletionService A photo/video deletion service.
     */
    public void registerDeletionService(DeletionType deletionService) {
        mDeletionService = deletionService;
        if (mDeletionService != null) {
            mDeletionService.registerFreeableChangedListener(this);
        }
    }

    /**
     * Returns the deletion service powering the preference.
     * @return The deletion service.
     */
    public DeletionType getDeletionService() {
        return mDeletionService;
    }

    @Override
    public void onFreeableChanged(int numItems, long freeableBytes) {
        mFreeableItems = numItems;
        mFreeableBytes = freeableBytes;
        maybeUpdateListener();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setChecked((boolean) newValue);
        maybeUpdateListener();
        return true;
    }

    private void maybeUpdateListener() {
        if (mListener != null) {
            mListener.onFreeableChanged(mFreeableItems, getFreeableBytes());
        }
    }
}
