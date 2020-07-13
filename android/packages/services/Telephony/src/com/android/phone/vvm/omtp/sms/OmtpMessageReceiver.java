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
package com.android.phone.vvm.omtp.sms;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.Voicemail;
import com.android.phone.settings.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.omtp.ActivationTask;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;
import com.android.phone.vvm.omtp.protocol.VisualVoicemailProtocol;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncService;
import com.android.phone.vvm.omtp.sync.SyncOneTask;
import com.android.phone.vvm.omtp.sync.SyncTask;
import com.android.phone.vvm.omtp.sync.VoicemailsQueryHelper;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Receive SMS messages and send for processing by the OMTP visual voicemail source.
 */
public class OmtpMessageReceiver extends BroadcastReceiver {

    private static final String TAG = "OmtpMessageReceiver";

    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        int subId = intent.getExtras().getInt(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID);
        PhoneAccountHandle phone = PhoneAccountHandleConverter.fromSubId(subId);

        if (phone == null) {
            // This should never happen
            VvmLog.i(TAG, "Received message for null phone account on subId " + subId);
            return;
        }

        if (!UserManager.get(context).isUserUnlocked()) {
            VvmLog.i(TAG, "Received message on locked device");
            // LegacyModeSmsHandler can handle new message notifications without storage access
            LegacyModeSmsHandler.handle(context, intent, phone);
            // A full sync will happen after the device is unlocked, so nothing else need to be
            // done.
            return;
        }

        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(mContext, subId);
        if (!VisualVoicemailSettingsUtil.isEnabled(mContext, phone)) {
            if (helper.isLegacyModeEnabled()) {
                LegacyModeSmsHandler.handle(context, intent, phone);
            } else {
                VvmLog.i(TAG, "Received vvm message for disabled vvm source.");
            }
            return;
        }

        String eventType = intent.getExtras()
                .getString(VoicemailContract.EXTRA_VOICEMAIL_SMS_PREFIX);
        Bundle data = intent.getExtras().getBundle(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS);

        if (eventType == null || data == null) {
            VvmLog.e(TAG, "Unparsable VVM SMS received, ignoring");
            return;
        }

        if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
            SyncMessage message = new SyncMessage(data);

            VvmLog.v(TAG, "Received SYNC sms for " + subId +
                    " with event " + message.getSyncTriggerEvent());
            processSync(phone, message);
        } else if (eventType.equals(OmtpConstants.STATUS_SMS_PREFIX)) {
            VvmLog.v(TAG, "Received Status sms for " + subId);
            // If the STATUS SMS is initiated by ActivationTask the TaskSchedulerService will reject
            // the follow request. Providing the data will also prevent ActivationTask from
            // requesting another STATUS SMS. The following task will only run if the carrier
            // spontaneous send a STATUS SMS, in that case, the VVM service should be reactivated.
            ActivationTask.start(context, subId, data);
        } else {
            VvmLog.w(TAG, "Unknown prefix: " + eventType);
            VisualVoicemailProtocol protocol = helper.getProtocol();
            if (protocol == null) {
                return;
            }
            Bundle statusData = helper.getProtocol()
                    .translateStatusSmsBundle(helper, eventType, data);
            if (statusData != null) {
                VvmLog.i(TAG, "Protocol recognized the SMS as STATUS, activating");
                ActivationTask.start(context, subId, data);
            }
        }
    }

    /**
     * A sync message has two purposes: to signal a new voicemail message, and to indicate the
     * voicemails on the server have changed remotely (usually through the TUI). Save the new
     * message to the voicemail provider if it is the former case and perform a full sync in the
     * latter case.
     *
     * @param message The sync message to extract data from.
     */
    private void processSync(PhoneAccountHandle phone, SyncMessage message) {
        Intent serviceIntent = null;
        switch (message.getSyncTriggerEvent()) {
            case OmtpConstants.NEW_MESSAGE:
                if (!OmtpConstants.VOICE.equals(message.getContentType())) {
                    VvmLog.i(TAG, "Non-voice message of type '" + message.getContentType()
                        + "' received, ignoring");
                    return;
                }

                Voicemail.Builder builder = Voicemail.createForInsertion(
                        message.getTimestampMillis(), message.getSender())
                        .setPhoneAccount(phone)
                        .setSourceData(message.getId())
                        .setDuration(message.getLength())
                        .setSourcePackage(mContext.getPackageName());
                Voicemail voicemail = builder.build();

                VoicemailsQueryHelper queryHelper = new VoicemailsQueryHelper(mContext);
                if (queryHelper.isVoicemailUnique(voicemail)) {
                    Uri uri = VoicemailContract.Voicemails.insert(mContext, voicemail);
                    voicemail = builder.setId(ContentUris.parseId(uri)).setUri(uri).build();
                    SyncOneTask.start(mContext, phone, voicemail);
                }
                break;
            case OmtpConstants.MAILBOX_UPDATE:
                SyncTask.start(mContext, phone, OmtpVvmSyncService.SYNC_DOWNLOAD_ONLY);
                break;
            case OmtpConstants.GREETINGS_UPDATE:
                // Not implemented in V1
                break;
            default:
                VvmLog.e(TAG,
                        "Unrecognized sync trigger event: " + message.getSyncTriggerEvent());
                break;
        }
    }
}
