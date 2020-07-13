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
 * limitations under the License
 */

package com.android.phone.vvm.omtp.sync;

import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import com.android.phone.vvm.omtp.scheduling.BaseTask;
import com.android.phone.vvm.omtp.scheduling.MinimalIntervalPolicy;
import com.android.phone.vvm.omtp.scheduling.RetryPolicy;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * System initiated sync request.
 */
public class SyncTask extends BaseTask {

    // Try sync for a total of 5 times, should take around 5 minutes before finally giving up.
    private static final int RETRY_TIMES = 4;
    private static final int RETRY_INTERVAL_MILLIS = 5_000;
    private static final int MINIMAL_INTERVAL_MILLIS = 60_000;

    private static final String EXTRA_PHONE_ACCOUNT_HANDLE = "extra_phone_account_handle";
    private static final String EXTRA_SYNC_TYPE = "extra_sync_type";

    private final RetryPolicy mRetryPolicy;

    private PhoneAccountHandle mPhone;
    private String mSyncType;

    public static void start(Context context, PhoneAccountHandle phone, String syncType) {
        Intent intent = BaseTask
                .createIntent(context, SyncTask.class, PhoneAccountHandleConverter.toSubId(phone));
        intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, phone);
        intent.putExtra(EXTRA_SYNC_TYPE, syncType);
        context.startService(intent);
    }

    public SyncTask() {
        super(TASK_SYNC);
        mRetryPolicy = new RetryPolicy(RETRY_TIMES, RETRY_INTERVAL_MILLIS);
        addPolicy(mRetryPolicy);
        addPolicy(new MinimalIntervalPolicy(MINIMAL_INTERVAL_MILLIS));
    }

    public void onCreate(Context context, Intent intent, int flags, int startId) {
        super.onCreate(context, intent, flags, startId);
        mPhone = intent.getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE);
        mSyncType = intent.getStringExtra(EXTRA_SYNC_TYPE);
    }

    @Override
    public void onExecuteInBackgroundThread() {
        OmtpVvmSyncService service = new OmtpVvmSyncService(getContext());
        service.sync(this, mSyncType, mPhone, null, mRetryPolicy.getVoicemailStatusEditor());
    }

    @Override
    public Intent createRestartIntent() {
        Intent intent = super.createRestartIntent();
        intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, mPhone);
        intent.putExtra(EXTRA_SYNC_TYPE, mSyncType);
        return intent;
    }
}
