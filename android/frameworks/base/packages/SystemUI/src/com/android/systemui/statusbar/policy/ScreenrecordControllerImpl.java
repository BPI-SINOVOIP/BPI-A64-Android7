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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.screenrecord.GlobalScreenrecord;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller to manage changes of location related states and update the views accordingly.
 */
public class ScreenrecordControllerImpl extends BroadcastReceiver implements ScreenrecordController {
    private static final int DELAY = 500;
    private Context mContext;
    private Handler mHandler;
    private boolean mRecording;

    private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();

    public ScreenrecordControllerImpl(Context context) {
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(GlobalScreenrecord.SCREENRECORD_STATE_CHANGED_ACTION);
        context.registerReceiverAsUser(this, UserHandle.ALL, filter, null, null);

        mHandler = new Handler();

        updateState(false);
    }

    private void updateState(boolean isRecording) {
        mRecording = isRecording;
        for (Callback cb : mCallbacks) {
            cb.onStateChange(isRecording);
        }
    }

    @Override
    public void addCallback(Callback cb) {
        mCallbacks.add(cb);
        cb.onStateChange(isRecording());
    }

    @Override
    public void removeCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    @Override
    public boolean isRecording() {
        return mRecording;
    }

    @Override
    public void autoRecord() {
        mHandler.postDelayed(mScreenrecordRunnable, DELAY);
    }

    private final Runnable mScreenrecordRunnable = new Runnable() {
        @Override
        public void run() {
            takeScreenrecord();
        }
    };

   final Object mScreenrecordLock = new Object();
   // Assume this is called from the Handler thread.
   private void takeScreenrecord() {
       synchronized (mScreenrecordLock) {
           ComponentName cn = new ComponentName("com.android.systemui",
                   "com.android.systemui.screenrecord.TakeScreenrecordService");
           Intent intent = new Intent();
           intent.setComponent(cn);
           ServiceConnection conn = new ServiceConnection() {
               @Override
               public void onServiceConnected(ComponentName name, IBinder service) {
                   synchronized (mScreenrecordLock) {
                       Messenger messenger = new Messenger(service);
                       Message msg = Message.obtain(null, 1);
                       try {
                           messenger.send(msg);
                       } catch (RemoteException e) {
                       }
                   }
               }
               @Override
               public void onServiceDisconnected(ComponentName name) {}
           };
           mContext.bindServiceAsUser(intent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT);
       }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (GlobalScreenrecord.SCREENRECORD_STATE_CHANGED_ACTION.equals(action)) {
            boolean isRecording = intent.getBooleanExtra(GlobalScreenrecord.EXTRA_SCREENRECORD_RECORDING, false);
            updateState(isRecording);
        }
    }
}
