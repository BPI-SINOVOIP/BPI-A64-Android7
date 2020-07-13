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

package com.android.phone.vvm.omtp.sync;

import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.Voicemail;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.scheduling.BaseTask;
import com.android.phone.vvm.omtp.scheduling.RetryPolicy;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Task to download a single voicemail from the server. This task is initiated by a SMS notifying
 * the new voicemail arrival, and ignores the duplicated tasks constraint.
 */
public class SyncOneTask extends BaseTask {

    private static final int RETRY_TIMES = 2;
    private static final int RETRY_INTERVAL_MILLIS = 5_000;

    private static final String EXTRA_PHONE_ACCOUNT_HANDLE = "extra_phone_account_handle";
    private static final String EXTRA_SYNC_TYPE = "extra_sync_type";
    private static final String EXTRA_VOICEMAIL = "extra_voicemail";

    private PhoneAccountHandle mPhone;
    private String mSyncType;
    private Voicemail mVoicemail;

    public static void start(Context context, PhoneAccountHandle phone, Voicemail voicemail) {
        Intent intent = BaseTask
                .createIntent(context, SyncOneTask.class,
                        PhoneAccountHandleConverter.toSubId(phone));
        intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, phone);
        intent.putExtra(EXTRA_SYNC_TYPE, OmtpVvmSyncService.SYNC_DOWNLOAD_ONE_TRANSCRIPTION);
        intent.putExtra(EXTRA_VOICEMAIL, voicemail);
        context.startService(intent);
    }

    public SyncOneTask() {
        super(TASK_ALLOW_DUPLICATES);
        addPolicy(new RetryPolicy(RETRY_TIMES, RETRY_INTERVAL_MILLIS));
    }

    public void onCreate(Context context, Intent intent, int flags, int startId) {
        super.onCreate(context, intent, flags, startId);
        mPhone = intent.getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE);
        mSyncType = intent.getStringExtra(EXTRA_SYNC_TYPE);
        mVoicemail = intent.getParcelableExtra(EXTRA_VOICEMAIL);
    }

    @Override
    public void onExecuteInBackgroundThread() {
        OmtpVvmSyncService service = new OmtpVvmSyncService(getContext());
        service.sync(this, mSyncType, mPhone, mVoicemail,
                VoicemailStatus.edit(getContext(), mPhone));
    }

    @Override
    public Intent createRestartIntent() {
        Intent intent = super.createRestartIntent();
        intent.putExtra(EXTRA_PHONE_ACCOUNT_HANDLE, mPhone);
        intent.putExtra(EXTRA_SYNC_TYPE, mSyncType);
        intent.putExtra(EXTRA_VOICEMAIL, mVoicemail);
        return intent;
    }

}
