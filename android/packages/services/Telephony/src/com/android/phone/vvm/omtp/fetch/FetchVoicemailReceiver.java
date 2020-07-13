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
 * limitations under the License
 */
package com.android.phone.vvm.omtp.fetch;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.internal.telephony.Phone;
import com.android.phone.PhoneUtils;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;
import com.android.phone.vvm.omtp.imap.ImapHelper;
import com.android.phone.vvm.omtp.imap.ImapHelper.InitializingException;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.sync.VvmNetworkRequestCallback;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FetchVoicemailReceiver extends BroadcastReceiver {

    private static final String TAG = "FetchVoicemailReceiver";

    final static String[] PROJECTION = new String[]{
            Voicemails.SOURCE_DATA,      // 0
            Voicemails.PHONE_ACCOUNT_ID, // 1
            Voicemails.PHONE_ACCOUNT_COMPONENT_NAME, // 2
    };

    public static final int SOURCE_DATA = 0;
    public static final int PHONE_ACCOUNT_ID = 1;
    public static final int PHONE_ACCOUNT_COMPONENT_NAME = 2;

    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;

    // Number of retries
    private static final int NETWORK_RETRY_COUNT = 3;

    private ContentResolver mContentResolver;
    private Uri mUri;
    private NetworkRequest mNetworkRequest;
    private VvmNetworkRequestCallback mNetworkCallback;
    private Context mContext;
    private String mUid;
    private ConnectivityManager mConnectivityManager;
    private PhoneAccountHandle mPhoneAccount;
    private int mRetryCount = NETWORK_RETRY_COUNT;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (VoicemailContract.ACTION_FETCH_VOICEMAIL.equals(intent.getAction())) {
            VvmLog.i(TAG, "ACTION_FETCH_VOICEMAIL received");
            mContext = context;
            mContentResolver = context.getContentResolver();
            mUri = intent.getData();

            if (mUri == null) {
                VvmLog.w(TAG,
                        VoicemailContract.ACTION_FETCH_VOICEMAIL + " intent sent with no data");
                return;
            }

            if (!context.getPackageName().equals(
                    mUri.getQueryParameter(VoicemailContract.PARAM_KEY_SOURCE_PACKAGE))) {
                // Ignore if the fetch request is for a voicemail not from this package.
                VvmLog.e(TAG,
                        "ACTION_FETCH_VOICEMAIL from foreign pacakge " + context.getPackageName());
                return;
            }

            Cursor cursor = mContentResolver.query(mUri, PROJECTION, null, null, null);
            if (cursor == null) {
                VvmLog.i(TAG, "ACTION_FETCH_VOICEMAIL query returned null");
                return;
            }
            try {
                if (cursor.moveToFirst()) {
                    mUid = cursor.getString(SOURCE_DATA);
                    String accountId = cursor.getString(PHONE_ACCOUNT_ID);
                    if (TextUtils.isEmpty(accountId)) {
                        TelephonyManager telephonyManager = (TelephonyManager)
                                context.getSystemService(Context.TELEPHONY_SERVICE);
                        accountId = telephonyManager.getSimSerialNumber();

                        if (TextUtils.isEmpty(accountId)) {
                            VvmLog.e(TAG, "Account null and no default sim found.");
                            return;
                        }
                    }

                    mPhoneAccount = new PhoneAccountHandle(
                            ComponentName.unflattenFromString(
                                    cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME)),
                            cursor.getString(PHONE_ACCOUNT_ID));
                    if (!OmtpVvmSourceManager.getInstance(context)
                            .isVvmSourceRegistered(mPhoneAccount)) {
                        mPhoneAccount = getAccountFromMarshmallowAccount(context, mPhoneAccount);
                        if (mPhoneAccount == null) {
                            VvmLog.w(TAG, "Account not registered - cannot retrieve message.");
                            return;
                        }
                        VvmLog.i(TAG, "Fetching voicemail with Marshmallow PhoneAccountHandle");
                    }

                    int subId = PhoneUtils.getSubIdForPhoneAccountHandle(mPhoneAccount);
                    OmtpVvmCarrierConfigHelper carrierConfigHelper =
                            new OmtpVvmCarrierConfigHelper(context, subId);
                    VvmLog.i(TAG, "Requesting network to fetch voicemail");
                    mNetworkCallback = new fetchVoicemailNetworkRequestCallback(context,
                            mPhoneAccount);
                    mNetworkCallback.requestNetwork();
                }
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * In ag/930496 the format of PhoneAccountHandle has changed between Marshmallow and Nougat.
     * This method attempts to search the account from the old database in registered sources using
     * the old format. There's a chance of M phone account collisions on multi-SIM devices, but
     * visual voicemail is not supported on M multi-SIM.
     */
    @Nullable
    private static PhoneAccountHandle getAccountFromMarshmallowAccount(Context context,
            PhoneAccountHandle oldAccount) {
        for (PhoneAccountHandle handle : OmtpVvmSourceManager.getInstance(context)
                .getOmtpVvmSources()) {
            Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(handle);
            if (phone == null) {
                continue;
            }
            // getIccSerialNumber() is used for ID before N, and getFullIccSerialNumber() after.
            if (phone.getIccSerialNumber().equals(oldAccount.getId())) {
                return handle;
            }
        }
        return null;
    }

    private class fetchVoicemailNetworkRequestCallback extends VvmNetworkRequestCallback {

        public fetchVoicemailNetworkRequestCallback(Context context,
                PhoneAccountHandle phoneAccount) {
            super(context, phoneAccount, VoicemailStatus.edit(context, phoneAccount));
        }

        @Override
        public void onAvailable(final Network network) {
            super.onAvailable(network);
            fetchVoicemail(network, getVoicemailStatusEditor());
        }
    }

    private void fetchVoicemail(final Network network, final VoicemailStatus.Editor status) {
        Executor executor = Executors.newCachedThreadPool();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mRetryCount > 0) {
                        VvmLog.i(TAG, "fetching voicemail, retry count=" + mRetryCount);
                        try (ImapHelper imapHelper = new ImapHelper(mContext, mPhoneAccount,
                            network, status)) {
                            boolean success = imapHelper.fetchVoicemailPayload(
                                    new VoicemailFetchedCallback(mContext, mUri, mPhoneAccount),
                                    mUid);
                            if (!success && mRetryCount > 0) {
                                VvmLog.i(TAG, "fetch voicemail failed, retrying");
                                mRetryCount--;
                            } else {
                                return;
                            }
                        } catch (InitializingException e) {
                          VvmLog.w(TAG, "Can't retrieve Imap credentials ", e);
                            return;
                        }
                    }
                } finally {
                    if (mNetworkCallback != null) {
                        mNetworkCallback.releaseNetwork();
                    }
                }
            }
        });
    }
}
