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
package com.android.phone.vvm.omtp;

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.protocol.VisualVoicemailProtocol;
import com.android.phone.vvm.omtp.protocol.VisualVoicemailProtocolFactory;
import com.android.phone.vvm.omtp.sms.StatusMessage;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;
import java.util.Arrays;
import java.util.Set;

/**
 * Manages carrier dependent visual voicemail configuration values. The primary source is the value
 * retrieved from CarrierConfigManager. If CarrierConfigManager does not provide the config
 * (KEY_VVM_TYPE_STRING is empty, or "hidden" configs), then the value hardcoded in telephony will
 * be used (in res/xml/vvm_config.xml)
 *
 * Hidden configs are new configs that are planned for future APIs, or miscellaneous settings that
 * may clutter CarrierConfigManager too much.
 *
 * The current hidden configs are: {@link #getSslPort()} {@link #getDisabledCapabilities()}
 */
public class OmtpVvmCarrierConfigHelper {

    private static final String TAG = "OmtpVvmCarrierCfgHlpr";

    static final String KEY_VVM_TYPE_STRING = CarrierConfigManager.KEY_VVM_TYPE_STRING;
    static final String KEY_VVM_DESTINATION_NUMBER_STRING =
            CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING;
    static final String KEY_VVM_PORT_NUMBER_INT =
            CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT;
    static final String KEY_CARRIER_VVM_PACKAGE_NAME_STRING =
            CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING;
    static final String KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY =
            "carrier_vvm_package_name_string_array";
    static final String KEY_VVM_PREFETCH_BOOL =
            CarrierConfigManager.KEY_VVM_PREFETCH_BOOL;
    static final String KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL =
            CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL;

    /**
     * @see #getSslPort()
     */
    static final String KEY_VVM_SSL_PORT_NUMBER_INT =
            "vvm_ssl_port_number_int";

    /**
     * @see #isLegacyModeEnabled()
     */
    static final String KEY_VVM_LEGACY_MODE_ENABLED_BOOL =
            "vvm_legacy_mode_enabled_bool";

    /**
     * Ban a capability reported by the server from being used. The array of string should be a
     * subset of the capabilities returned IMAP CAPABILITY command.
     *
     * @see #getDisabledCapabilities()
     */
    static final String KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY =
            "vvm_disabled_capabilities_string_array";
    static final String KEY_VVM_CLIENT_PREFIX_STRING =
            "vvm_client_prefix_string";

    private final Context mContext;
    private final int mSubId;
    private final PersistableBundle mCarrierConfig;
    private final String mVvmType;
    private final VisualVoicemailProtocol mProtocol;
    private final PersistableBundle mTelephonyConfig;

    private PhoneAccountHandle mPhoneAccountHandle;

    public OmtpVvmCarrierConfigHelper(Context context, int subId) {
        mContext = context;
        mSubId = subId;
        mCarrierConfig = getCarrierConfig();

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyConfig = new TelephonyVvmConfigManager(context.getResources())
                .getConfig(telephonyManager.getSimOperator(subId));

        mVvmType = getVvmType();
        mProtocol = VisualVoicemailProtocolFactory.create(mContext.getResources(), mVvmType);
    }

    public OmtpVvmCarrierConfigHelper(Context context, PhoneAccountHandle handle) {
        this(context, PhoneAccountHandleConverter.toSubId(handle));
        mPhoneAccountHandle = handle;
    }

    @VisibleForTesting
    OmtpVvmCarrierConfigHelper(Context context, PersistableBundle carrierConfig,
            PersistableBundle telephonyConfig) {
        mContext = context;
        mSubId = 0;
        mCarrierConfig = carrierConfig;
        mTelephonyConfig = telephonyConfig;
        mVvmType = getVvmType();
        mProtocol = VisualVoicemailProtocolFactory.create(mContext.getResources(), mVvmType);
    }

    public Context getContext() {
        return mContext;
    }

    public int getSubId() {
        return mSubId;
    }

    @Nullable
    public PhoneAccountHandle getPhoneAccountHandle() {
        if (mPhoneAccountHandle == null) {
            mPhoneAccountHandle = PhoneAccountHandleConverter.fromSubId(mSubId);
            if (mPhoneAccountHandle == null) {
                VvmLog.e(TAG, "null phone account for subId " + mSubId);
            }
        }
        return mPhoneAccountHandle;
    }

    /**
     * return whether the carrier's visual voicemail is supported, with KEY_VVM_TYPE_STRING set as a
     * known protocol.
     */
    public boolean isValid() {
        return mProtocol != null;
    }

    @Nullable
    public String getVvmType() {
        return (String) getValue(KEY_VVM_TYPE_STRING);
    }

    @Nullable
    public VisualVoicemailProtocol getProtocol() {
        return mProtocol;
    }

    /**
     * @returns arbitrary String stored in the config file. Used for protocol specific values.
     */
    @Nullable
    public String getString(String key) {
        return (String) getValue(key);
    }

    @Nullable
    public Set<String> getCarrierVvmPackageNames() {
        Set<String> names = getCarrierVvmPackageNames(mCarrierConfig);
        if (names != null) {
            return names;
        }
        return getCarrierVvmPackageNames(mTelephonyConfig);
    }

    private static Set<String> getCarrierVvmPackageNames(@Nullable PersistableBundle bundle) {
        if (bundle == null) {
            return null;
        }
        Set<String> names = new ArraySet<>();
        if (bundle.containsKey(KEY_CARRIER_VVM_PACKAGE_NAME_STRING)) {
            names.add(bundle.getString(KEY_CARRIER_VVM_PACKAGE_NAME_STRING));
        }
        if (bundle.containsKey(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY)) {
            names.addAll(Arrays.asList(
                    bundle.getStringArray(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY)));
        }
        if (names.isEmpty()) {
            return null;
        }
        return names;
    }

    /**
     * For checking upon sim insertion whether visual voicemail should be enabled. This method does
     * so by checking if the carrier's voicemail app is installed.
     */
    public boolean isEnabledByDefault() {
        if (!isValid()) {
            return false;
        }

        Set<String> carrierPackages = getCarrierVvmPackageNames();
        if (carrierPackages == null) {
            return true;
        }
        for (String packageName : carrierPackages) {
            try {
                mContext.getPackageManager().getPackageInfo(packageName, 0);
                return false;
            } catch (NameNotFoundException e) {
                // Do nothing.
            }
        }
        return true;
    }

    public boolean isCellularDataRequired() {
        return (boolean) getValue(KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL, false);
    }

    public boolean isPrefetchEnabled() {
        return (boolean) getValue(KEY_VVM_PREFETCH_BOOL, true);
    }


    public int getApplicationPort() {
        return (int) getValue(KEY_VVM_PORT_NUMBER_INT, 0);
    }

    @Nullable
    public String getDestinationNumber() {
        return (String) getValue(KEY_VVM_DESTINATION_NUMBER_STRING);
    }

    /**
     * Hidden config.
     *
     * @return Port to start a SSL IMAP connection directly.
     *
     * TODO: make config public and add to CarrierConfigManager
     */
    public int getSslPort() {
        return (int) getValue(KEY_VVM_SSL_PORT_NUMBER_INT, 0);
    }

    /**
     * Hidden Config.
     *
     * <p>Sometimes the server states it supports a certain feature but we found they have bug on
     * the server side. For example, in b/28717550 the server reported AUTH=DIGEST-MD5 capability
     * but using it to login will cause subsequent response to be erroneous.
     *
     * @return A set of capabilities that is reported by the IMAP CAPABILITY command, but determined
     * to have issues and should not be used.
     */
    @Nullable
    public Set<String> getDisabledCapabilities() {
        Set<String> disabledCapabilities = getDisabledCapabilities(mCarrierConfig);
        if (disabledCapabilities != null) {
            return disabledCapabilities;
        }
        return getDisabledCapabilities(mTelephonyConfig);
    }

    @Nullable
    private static Set<String> getDisabledCapabilities(@Nullable PersistableBundle bundle) {
        if (bundle == null) {
            return null;
        }
        if (!bundle.containsKey(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY)) {
            return null;
        }
        ArraySet<String> result = new ArraySet<String>();
        result.addAll(
                Arrays.asList(bundle.getStringArray(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY)));
        return result;
    }

    public String getClientPrefix() {
        String prefix = (String) getValue(KEY_VVM_CLIENT_PREFIX_STRING);
        if (prefix != null) {
            return prefix;
        }
        return "//VVM";
    }

    /**
     * Should legacy mode be used when the OMTP VVM client is disabled?
     *
     * <p>Legacy mode is a mode that on the carrier side visual voicemail is still activated, but on
     * the client side all network operations are disabled. SMSs are still monitored so a new
     * message SYNC SMS will be translated to show a message waiting indicator, like traditional
     * voicemails.
     *
     * <p>This is for carriers that does not support VVM deactivation so voicemail can continue to
     * function without the data cost.
     */
    public boolean isLegacyModeEnabled() {
        return (boolean) getValue(KEY_VVM_LEGACY_MODE_ENABLED_BOOL, false);
    }

    public void startActivation() {
        PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
        if (phoneAccountHandle == null) {
            // This should never happen
            // Error logged in getPhoneAccountHandle().
            return;
        }

        if (mVvmType == null || mVvmType.isEmpty()) {
            // The VVM type is invalid; we should never have gotten here in the first place since
            // this is loaded initially in the constructor, and callers should check isValid()
            // before trying to start activation anyways.
            VvmLog.e(TAG, "startActivation : vvmType is null or empty for account " +
                    phoneAccountHandle);
            return;
        }

        activateSmsFilter();

        if (mProtocol != null) {
            ActivationTask.start(mContext, mSubId, null);
        }
    }

    public void activateSmsFilter() {
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        telephonyManager.enableVisualVoicemailSmsFilter(mSubId,
                new VisualVoicemailSmsFilterSettings.Builder().setClientPrefix(getClientPrefix())
                        .build());
    }

    public void startDeactivation() {
        if (!isLegacyModeEnabled()) {
            // SMS should still be filtered in legacy mode
            mContext.getSystemService(TelephonyManager.class)
                    .disableVisualVoicemailSmsFilter(mSubId);
        }
        if (mProtocol != null) {
            mProtocol.startDeactivation(this);
        }
    }

    public boolean supportsProvisioning() {
        if (mProtocol != null) {
            return mProtocol.supportsProvisioning();
        }
        return false;
    }

    public void startProvisioning(ActivationTask task, PhoneAccountHandle phone,
        VoicemailStatus.Editor status, StatusMessage message, Bundle data) {
        if (mProtocol != null) {
            mProtocol.startProvisioning(task, phone, this, status, message, data);
        }
    }

    public void requestStatus(@Nullable PendingIntent sentIntent) {
        if (mProtocol != null) {
            mProtocol.requestStatus(this, sentIntent);
        }
    }

    public void handleEvent(VoicemailStatus.Editor status, OmtpEvents event) {
        VvmLog.i(TAG, "OmtpEvent:" + event);
        if (mProtocol != null) {
            mProtocol.handleEvent(mContext, this, status, event);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("OmtpVvmCarrierConfigHelper [");
        builder.append("subId: ").append(getSubId())
                .append(", carrierConfig: ").append(mCarrierConfig != null)
                .append(", telephonyConfig: ").append(mTelephonyConfig != null)
                .append(", type: ").append(getVvmType())
                .append(", destinationNumber: ").append(getDestinationNumber())
                .append(", applicationPort: ").append(getApplicationPort())
                .append(", sslPort: ").append(getSslPort())
                .append(", isEnabledByDefault: ").append(isEnabledByDefault())
                .append(", isCellularDataRequired: ").append(isCellularDataRequired())
                .append(", isPrefetchEnabled: ").append(isPrefetchEnabled())
                .append(", isLegacyModeEnabled: ").append(isLegacyModeEnabled())
                .append("]");
        return builder.toString();
    }

    @Nullable
    private PersistableBundle getCarrierConfig() {
        if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
            VvmLog
                    .w(TAG, "Invalid subscriptionId or subscriptionId not provided in intent.");
            return null;
        }

        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager == null) {
            VvmLog.w(TAG, "No carrier config service found.");
            return null;
        }

        PersistableBundle config = carrierConfigManager.getConfigForSubId(mSubId);

        if (TextUtils.isEmpty(config.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING))) {
            return null;
        }
        return config;
    }

    @Nullable
    private Object getValue(String key) {
        return getValue(key, null);
    }

    @Nullable
    private Object getValue(String key, Object defaultValue) {
        Object result;
        if (mCarrierConfig != null) {
            result = mCarrierConfig.get(key);
            if (result != null) {
                return result;
            }
        }
        if (mTelephonyConfig != null) {
            result = mTelephonyConfig.get(key);
            if (result != null) {
                return result;
            }
        }
        return defaultValue;
    }

}