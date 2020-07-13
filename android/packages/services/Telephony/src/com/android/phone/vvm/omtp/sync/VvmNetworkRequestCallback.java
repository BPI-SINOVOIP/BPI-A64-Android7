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
package com.android.phone.vvm.omtp.sync;

import android.annotation.CallSuper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import com.android.phone.PhoneUtils;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.OmtpEvents;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;

/**
 * Base class for network request call backs for visual voicemail syncing with the Imap server. This
 * handles retries and network requests.
 */
public abstract class VvmNetworkRequestCallback extends ConnectivityManager.NetworkCallback {

    private static final String TAG = "VvmNetworkRequest";

    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;

    public static final String NETWORK_REQUEST_FAILED_TIMEOUT = "timeout";
    public static final String NETWORK_REQUEST_FAILED_LOST = "lost";

    protected Context mContext;
    protected PhoneAccountHandle mPhoneAccount;
    protected NetworkRequest mNetworkRequest;
    private ConnectivityManager mConnectivityManager;
    private final OmtpVvmCarrierConfigHelper mCarrierConfigHelper;
    private final int mSubId;
    private final VoicemailStatus.Editor mStatus;
    private boolean mRequestSent = false;
    private boolean mResultReceived = false;

    public VvmNetworkRequestCallback(Context context, PhoneAccountHandle phoneAccount,
        VoicemailStatus.Editor status) {
        mContext = context;
        mPhoneAccount = phoneAccount;
        mSubId = PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccount);
        mStatus = status;
        mCarrierConfigHelper = new OmtpVvmCarrierConfigHelper(context, mSubId);
        mNetworkRequest = createNetworkRequest();
    }

    public VvmNetworkRequestCallback(OmtpVvmCarrierConfigHelper config,
        PhoneAccountHandle phoneAccount, VoicemailStatus.Editor status) {
        mContext = config.getContext();
        mPhoneAccount = phoneAccount;
        mSubId = config.getSubId();
        mStatus = status;
        mCarrierConfigHelper = config;
        mNetworkRequest = createNetworkRequest();
    }

    public VoicemailStatus.Editor getVoicemailStatusEditor() {
        return mStatus;
    }

    /**
     * @return NetworkRequest for a proper transport type. Use only cellular network if the carrier
     * requires it. Otherwise use whatever available.
     */
    private NetworkRequest createNetworkRequest() {

        NetworkRequest.Builder builder = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        if (mCarrierConfigHelper.isCellularDataRequired()) {
            VvmLog.d(TAG, "Transport type: CELLULAR");
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .setNetworkSpecifier(Integer.toString(mSubId));
        } else {
            VvmLog.d(TAG, "Transport type: ANY");
        }
        return builder.build();
    }

    public NetworkRequest getNetworkRequest() {
        return mNetworkRequest;
    }

    @Override
    @CallSuper
    public void onLost(Network network) {
        VvmLog.d(TAG, "onLost");
        mResultReceived = true;
        onFailed(NETWORK_REQUEST_FAILED_LOST);
    }

    @Override
    @CallSuper
    public void onAvailable(Network network) {
        super.onAvailable(network);
        mResultReceived = true;
    }

    @Override
    @CallSuper
    public void onUnavailable() {
        mResultReceived = true;
        onFailed(NETWORK_REQUEST_FAILED_TIMEOUT);
    }

    public void requestNetwork() {
        if (mRequestSent == true) {
            VvmLog.e(TAG, "requestNetwork() called twice");
            return;
        }
        mRequestSent = true;
        getConnectivityManager().requestNetwork(getNetworkRequest(), this);
        /**
         * Somehow requestNetwork() with timeout doesn't work, and it's a hidden method.
         * Implement our own timeout mechanism instead.
         */
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mResultReceived == false) {
                    onFailed(NETWORK_REQUEST_FAILED_TIMEOUT);
                }
            }
        }, NETWORK_REQUEST_TIMEOUT_MILLIS);
    }

    public void releaseNetwork() {
        VvmLog.d(TAG, "releaseNetwork");
        getConnectivityManager().unregisterNetworkCallback(this);
    }

    public ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    @CallSuper
    public void onFailed(String reason) {
        VvmLog.d(TAG, "onFailed: " + reason);
        if (mCarrierConfigHelper.isCellularDataRequired()) {
            mCarrierConfigHelper
                .handleEvent(mStatus, OmtpEvents.DATA_NO_CONNECTION_CELLULAR_REQUIRED);
        } else {
            mCarrierConfigHelper.handleEvent(mStatus, OmtpEvents.DATA_NO_CONNECTION);
        }
        releaseNetwork();
    }
}
