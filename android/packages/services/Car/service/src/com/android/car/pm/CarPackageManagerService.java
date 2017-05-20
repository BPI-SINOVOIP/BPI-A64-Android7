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
package com.android.car.pm;

import android.car.Car;
import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarAppBlockingPolicyService;
import android.car.content.pm.CarPackageManager;
import android.car.content.pm.ICarPackageManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.pm.CarAppMetadataReader.CarAppMetadataInfo;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

//TODO monitor app installing and refresh policy

public class CarPackageManagerService extends ICarPackageManager.Stub implements CarServiceBase {
    static final boolean DBG_POLICY_SET = true;
    static final boolean DBG_POLICY_CHECK = false;

    private final Context mContext;
    private final PackageManager mPackageManager;

    private final HandlerThread mHandlerThread;
    private final PackageHandler mHandler;

    /**
     * Hold policy set from policy service or client.
     * Key: packageName of policy service
     */
    @GuardedBy("this")
    private final HashMap<String, ClientPolicy> mClientPolicies =
            new HashMap<>();
    @GuardedBy("this")
    private HashMap<String, AppBlockingPackageInfoWrapper> mSystemWhitelists = new HashMap<>();
    @GuardedBy("this")
    private LinkedList<AppBlockingPolicyProxy> mProxies;
    @GuardedBy("this")
    private boolean mReleased = true;

    @GuardedBy("this")
    private final LinkedList<CarAppBlockingPolicy> mWaitingPolicies = new LinkedList<>();

    public CarPackageManagerService(Context context) {
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mHandlerThread = new HandlerThread(CarLog.TAG_PACKAGE);
        mHandlerThread.start();
        mHandler = new PackageHandler(mHandlerThread.getLooper());
    }

    @Override
    public void setAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        if (DBG_POLICY_SET) {
            Log.i(CarLog.TAG_PACKAGE, "policy setting from binder call, client:" + packageName);
        }
        doSetAppBlockingPolicy(packageName, policy, flags, true /*setNow*/);
    }

    private void doSetAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags,
            boolean setNow) {
        if (mContext.checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_APP_BLOCKING)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "requires permission " + Car.PERMISSION_CONTROL_APP_BLOCKING);
        }
        CarServiceUtils.assertPakcageName(mContext, packageName);
        if (policy == null) {
            throw new IllegalArgumentException("policy cannot be null");
        }
        if ((flags & CarPackageManager.FLAG_SET_POLICY_ADD) != 0 &&
                (flags & CarPackageManager.FLAG_SET_POLICY_REMOVE) != 0) {
            throw new IllegalArgumentException(
                    "Cannot set both FLAG_SET_POLICY_ADD and FLAG_SET_POLICY_REMOVE flag");
        }
        mHandler.requestUpdatingPolicy(packageName, policy, flags);
        if (setNow) {
            mHandler.requestPolicySetting();
            if ((flags & CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE) != 0) {
                synchronized (policy) {
                    try {
                        policy.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    @Override
    public boolean isActivityAllowedWhileDriving(String packageName, String className) {
        assertPackageAndClassName(packageName, className);
        synchronized (this) {
            if (DBG_POLICY_CHECK) {
                Log.i(CarLog.TAG_PACKAGE, "isActivityAllowedWhileDriving" +
                        dumpPoliciesLocked(false));
            }
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            return isActivityInWhitelistsLocked(packageName, className);
        }
    }

    @Override
    public boolean isServiceAllowedWhileDriving(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        synchronized (this) {
            if (DBG_POLICY_CHECK) {
                Log.i(CarLog.TAG_PACKAGE, "isServiceAllowedWhileDriving" +
                        dumpPoliciesLocked(false));
            }
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            info = searchFromWhitelistsLocked(packageName);
            if (info != null) {
                return true;
            }
        }
        return false;
    }

    public Looper getLooper() {
        return mHandlerThread.getLooper();
    }

    private void assertPackageAndClassName(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        if (className == null) {
            throw new IllegalArgumentException("Class name null");
        }
    }

    private AppBlockingPackageInfo searchFromBlacklistsLocked(String packageName) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = policy.blacklistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        return null;
    }

    private AppBlockingPackageInfo searchFromWhitelistsLocked(String packageName) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = policy.whitelistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        AppBlockingPackageInfoWrapper wrapper = mSystemWhitelists.get(packageName);
        return (wrapper != null) ? wrapper.info : null;
    }

    private boolean isActivityInWhitelistsLocked(String packageName, String className) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            if (isActivityInMapAndMatching(policy.whitelistsMap, packageName, className)) {
                return true;
            }
        }
        return isActivityInMapAndMatching(mSystemWhitelists, packageName, className);
    }

    private boolean isActivityInMapAndMatching(HashMap<String, AppBlockingPackageInfoWrapper> map,
            String packageName, String className) {
        AppBlockingPackageInfoWrapper wrapper = map.get(packageName);
        if (wrapper == null || !wrapper.isMatching) {
            return false;
        }
        return wrapper.info.isActivityCovered(className);
    }

    @Override
    public void init() {
        synchronized (this) {
            mReleased = false;
            mHandler.requestInit();
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            mReleased = true;
            mHandler.requestRelease();
            mSystemWhitelists.clear();
            mClientPolicies.clear();
            if (mProxies != null) {
                for (AppBlockingPolicyProxy proxy : mProxies) {
                    proxy.disconnect();
                }
                mProxies.clear();
            }
            wakeupClientsWaitingForPolicySetitngLocked();
        }
    }

    // run from HandlerThread
    private void doHandleInit() {
        startAppBlockingPolicies();
        generateSystemWhitelists();
    }

    private void wakeupClientsWaitingForPolicySetitngLocked() {
        for (CarAppBlockingPolicy waitingPolicy : mWaitingPolicies) {
            synchronized (waitingPolicy) {
                waitingPolicy.notifyAll();
            }
        }
        mWaitingPolicies.clear();
    }

    private void doSetPolicy() {
        //TODO should set policy to AMS
        //  waiting for framework API to be ready
        synchronized (this) {
            wakeupClientsWaitingForPolicySetitngLocked();
        }
    }

    private void doUpdatePolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        if (DBG_POLICY_SET) {
            Log.i(CarLog.TAG_PACKAGE, "setting policy from:" + packageName + ",policy:" + policy +
                    ",flags:0x" + Integer.toHexString(flags));
        }
        AppBlockingPackageInfoWrapper[] blacklistWrapper = verifyList(policy.blacklists);
        AppBlockingPackageInfoWrapper[] whitelistWrapper = verifyList(policy.whitelists);
        synchronized (this) {
            ClientPolicy clientPolicy = mClientPolicies.get(packageName);
            if (clientPolicy == null) {
                clientPolicy = new ClientPolicy();
                mClientPolicies.put(packageName, clientPolicy);
            }
            if ((flags & CarPackageManager.FLAG_SET_POLICY_ADD) != 0) {
                clientPolicy.addToBlacklists(blacklistWrapper);
                clientPolicy.addToWhitelists(whitelistWrapper);
            } else if ((flags & CarPackageManager.FLAG_SET_POLICY_REMOVE) != 0) {
                clientPolicy.removeBlacklists(blacklistWrapper);
                clientPolicy.removeWhitelists(whitelistWrapper);
            } else { //replace.
                clientPolicy.replaceBlacklists(blacklistWrapper);
                clientPolicy.replaceWhitelists(whitelistWrapper);
            }
            if ((flags & CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE) != 0) {
                mWaitingPolicies.add(policy);
            }
            if (DBG_POLICY_SET) {
                Log.i(CarLog.TAG_PACKAGE, "policy set:" + dumpPoliciesLocked(false));
            }
        }
    }

    private AppBlockingPackageInfoWrapper[] verifyList(AppBlockingPackageInfo[] list) {
        if (list == null) {
            return null;
        }
        LinkedList<AppBlockingPackageInfoWrapper> wrappers = new LinkedList<>();
        for (int i = 0; i < list.length; i++) {
            AppBlockingPackageInfo info = list[i];
            if (info == null) {
                continue;
            }
            boolean isMatching = isInstalledPackageMatching(info);
            wrappers.add(new AppBlockingPackageInfoWrapper(info, isMatching));
        }
        return wrappers.toArray(new AppBlockingPackageInfoWrapper[wrappers.size()]);
    }

    boolean isInstalledPackageMatching(AppBlockingPackageInfo info) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = mPackageManager.getPackageInfo(info.packageName,
                    PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            return false;
        }
        if (packageInfo == null) {
            return false;
        }
        // if it is system app and client specified the flag, do not check signature
        if ((info.flags & AppBlockingPackageInfo.FLAG_SYSTEM_APP) == 0 ||
                (!packageInfo.applicationInfo.isSystemApp() &&
                        !packageInfo.applicationInfo.isUpdatedSystemApp())) {
            Signature[] signatires = packageInfo.signatures;
            if (!isAnySignatureMatching(signatires, info.signatures)) {
                return false;
            }
        }
        int version = packageInfo.versionCode;
        if (info.minRevisionCode == 0) {
            if (info.maxRevisionCode == 0) { // all versions
                return true;
            } else { // only max version matters
                return info.maxRevisionCode > version;
            }
        } else { // min version matters
            if (info.maxRevisionCode == 0) {
                return info.minRevisionCode < version;
            } else {
                return (info.minRevisionCode < version) && (info.maxRevisionCode > version);
            }
        }
    }

    /**
     * Any signature from policy matching with package's signatures is treated as matching.
     */
    boolean isAnySignatureMatching(Signature[] fromPackage, Signature[] fromPolicy) {
        if (fromPackage == null) {
            return false;
        }
        if (fromPolicy == null) {
            return false;
        }
        ArraySet<Signature> setFromPackage = new ArraySet<Signature>();
        for (Signature sig : fromPackage) {
            setFromPackage.add(sig);
        }
        for (Signature sig : fromPolicy) {
            if (setFromPackage.contains(sig)) {
                return true;
            }
        }
        return false;
    }

    private void generateSystemWhitelists() {
        HashMap<String, AppBlockingPackageInfoWrapper> systemWhitelists = new HashMap<>();
        // trust all system apps for services and trust all activities with car app meta-data.
        List<PackageInfo> packages = mPackageManager.getInstalledPackages(0);
        for (PackageInfo info : packages) {
            if (info.applicationInfo != null && (info.applicationInfo.isSystemApp() ||
                    info.applicationInfo.isUpdatedSystemApp())) {
                CarAppMetadataInfo metadataInfo = CarAppMetadataReader.parseMetadata(mContext,
                        info.packageName);
                int flags = AppBlockingPackageInfo.FLAG_SYSTEM_APP;
                String[] activities = null;
                if (metadataInfo != null) {
                    if (metadataInfo.useAllActivities) {
                        flags |= AppBlockingPackageInfo.FLAG_WHOLE_ACTIVITY;
                    } else {
                        activities = metadataInfo.activities;
                    }
                }
                AppBlockingPackageInfo appBlockingInfo = new AppBlockingPackageInfo(
                        info.packageName, 0, 0, flags, null, activities);
                AppBlockingPackageInfoWrapper wrapper = new AppBlockingPackageInfoWrapper(
                        appBlockingInfo, true);
                systemWhitelists.put(info.packageName, wrapper);
            }
        }
        synchronized (this) {
            mSystemWhitelists.putAll(systemWhitelists);
        }
    }

    private void startAppBlockingPolicies() {
        Intent policyIntent = new Intent();
        policyIntent.setAction(CarAppBlockingPolicyService.SERVICE_INTERFACE);
        List<ResolveInfo> policyInfos = mPackageManager.queryIntentServices(policyIntent, 0);
        if (policyInfos == null) { //no need to wait for service binding and retrieval.
            mHandler.requestPolicySetting();
            return;
        }
        LinkedList<AppBlockingPolicyProxy> proxies = new LinkedList<>();
        for (ResolveInfo resolveInfo : policyInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null) {
                continue;
            }
            if (serviceInfo.isEnabled()) {
                if (mPackageManager.checkPermission(Car.PERMISSION_CONTROL_APP_BLOCKING,
                        serviceInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    continue;
                }
                Log.i(CarLog.TAG_PACKAGE, "found policy holding service:" + serviceInfo);
                AppBlockingPolicyProxy proxy = new AppBlockingPolicyProxy(this, mContext,
                        serviceInfo);
                proxy.connect();
                proxies.add(proxy);
            }
        }
        synchronized (this) {
            mProxies = proxies;
        }
    }

    public void onPolicyConnectionAndSet(AppBlockingPolicyProxy proxy,
            CarAppBlockingPolicy policy) {
        doHandlePolicyConnection(proxy, policy);
    }

    public void onPolicyConnectionFailure(AppBlockingPolicyProxy proxy) {
        doHandlePolicyConnection(proxy, null);
    }

    private void doHandlePolicyConnection(AppBlockingPolicyProxy proxy,
            CarAppBlockingPolicy policy) {
        boolean shouldSetPolicy = false;
        synchronized (this) {
            if (mProxies == null) {
                proxy.disconnect();
                return;
            }
            mProxies.remove(proxy);
            if (mProxies.size() == 0) {
                shouldSetPolicy = true;
                mProxies = null;
            }
        }
        try {
            if (policy != null) {
                if (DBG_POLICY_SET) {
                    Log.i(CarLog.TAG_PACKAGE, "policy setting from policy service:" +
                            proxy.getPackageName());
                }
                doSetAppBlockingPolicy(proxy.getPackageName(), policy, 0, false /*setNow*/);
            }
        } finally {
            proxy.disconnect();
            if (shouldSetPolicy) {
                mHandler.requestPolicySetting();
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (this) {
            writer.println("*PackageManagementService*");
            writer.print(dumpPoliciesLocked(true));
        }
    }

    private String dumpPoliciesLocked(boolean dumpAll) {
        StringBuilder sb = new StringBuilder();
        if (dumpAll) {
            sb.append("**System white list**\n");
            for (AppBlockingPackageInfoWrapper wrapper : mSystemWhitelists.values()) {
                sb.append(wrapper.toString() + "\n");
            }
        }
        sb.append("**Client Policies**\n");
        for (Entry<String, ClientPolicy> entry : mClientPolicies.entrySet()) {
            sb.append("Client:" + entry.getKey() + "\n");
            sb.append("  whitelists:\n");
            for (AppBlockingPackageInfoWrapper wrapper : entry.getValue().whitelistsMap.values()) {
                sb.append(wrapper.toString() + "\n");
            }
            sb.append("  blacklists:\n");
            for (AppBlockingPackageInfoWrapper wrapper : entry.getValue().blacklistsMap.values()) {
                sb.append(wrapper.toString() + "\n");
            }
        }
        sb.append("**Unprocessed policy services**\n");
        if (mProxies != null) {
            for (AppBlockingPolicyProxy proxy : mProxies) {
                sb.append(proxy.toString() + "\n");
            }
        }
        return sb.toString();
    }

    /**
     * Reading policy and setting policy can take time. Run it in a separate handler thread.
     */
    private class PackageHandler extends Handler {
        private final int MSG_INIT = 0;
        private final int MSG_SET_POLICY = 1;
        private final int MSG_UPDATE_POLICY = 2;

        private PackageHandler(Looper looper) {
            super(looper);
        }

        private void requestInit() {
            Message msg = obtainMessage(MSG_INIT);
            sendMessage(msg);
        }

        private void requestRelease() {
            removeMessages(MSG_INIT);
            removeMessages(MSG_SET_POLICY);
            removeMessages(MSG_UPDATE_POLICY);
        }

        private void requestPolicySetting() {
            Message msg = obtainMessage(MSG_SET_POLICY);
            sendMessage(msg);
        }

        private void requestUpdatingPolicy(String packageName, CarAppBlockingPolicy policy,
                int flags) {
            Pair<String, CarAppBlockingPolicy> pair = new Pair<>(packageName, policy);
            Message msg = obtainMessage(MSG_UPDATE_POLICY, flags, 0, pair);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT:
                    doHandleInit();
                    break;
                case MSG_SET_POLICY:
                    doSetPolicy();
                    break;
                case MSG_UPDATE_POLICY:
                    Pair<String, CarAppBlockingPolicy> pair =
                            (Pair<String, CarAppBlockingPolicy>) msg.obj;
                    doUpdatePolicy(pair.first, pair.second, msg.arg1);
                    break;
            }
        }
    }

    private static class AppBlockingPackageInfoWrapper {
        private final AppBlockingPackageInfo info;
        /**
         * Whether the current info is matching with the target package in system. Mismatch can
         * happen for version out of range or signature mismatch.
         */
        private boolean isMatching;

        private AppBlockingPackageInfoWrapper(AppBlockingPackageInfo info, boolean isMatching) {
            this.info =info;
            this.isMatching = isMatching;
        }

        @Override
        public String toString() {
            return "AppBlockingPackageInfoWrapper [info=" + info + ", isMatching=" + isMatching +
                    "]";
        }
    }

    /**
     * Client policy holder per each client. Should be accessed with CarpackageManagerService.this
     * held.
     */
    private static class ClientPolicy {
        private final HashMap<String, AppBlockingPackageInfoWrapper> whitelistsMap =
                new HashMap<>();
        private final HashMap<String, AppBlockingPackageInfoWrapper> blacklistsMap =
                new HashMap<>();

        private void replaceWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            whitelistsMap.clear();
            addToWhitelists(whitelists);
        }

        private void addToWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    whitelistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        private void removeWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    whitelistsMap.remove(wrapper.info.packageName);
                }
            }
        }

        private void replaceBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            blacklistsMap.clear();
            addToBlacklists(blacklists);
        }

        private void addToBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    blacklistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        private void removeBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    blacklistsMap.remove(wrapper.info.packageName);
                }
            }
        }
    }
}
