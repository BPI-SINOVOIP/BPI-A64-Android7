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
package com.android.settings.applications;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.notification.NotificationBackend;
import com.android.settings.notification.NotificationBackend.AppRow;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.AppFilter;

import java.util.ArrayList;

/**
 * Connects the info provided by ApplicationsState and the NotificationBackend.
 * Also provides app filters that can use the notification data.
 */
public class AppStateNotificationBridge extends AppStateBaseBridge {

    private final NotificationBackend mNotifBackend;
    private final PackageManager mPm;
    private final Context mContext;

    public AppStateNotificationBridge(Context context, ApplicationsState appState,
            Callback callback, NotificationBackend notifBackend) {
        super(appState, callback);
        mContext = context;
        mPm = mContext.getPackageManager();
        mNotifBackend = notifBackend;
    }

    @Override
    protected void loadAllExtraInfo() {
        ArrayList<AppEntry> apps = mAppSession.getAllApps();
        final int N = apps.size();
        for (int i = 0; i < N; i++) {
            AppEntry app = apps.get(i);
            app.extraInfo = mNotifBackend.loadAppRow(mContext, mPm, app.info);
        }
    }

    @Override
    protected void updateExtraInfo(AppEntry app, String pkg, int uid) {
        app.extraInfo = mNotifBackend.loadAppRow(mContext, mPm, app.info);
    }

    public static final AppFilter FILTER_APP_NOTIFICATION_BLOCKED = new AppFilter() {
        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(AppEntry info) {
            if (info == null || info.extraInfo == null) {
                return false;
            }
            if (info.extraInfo instanceof AppRow) {
                AppRow row = (AppRow) info.extraInfo;
                return row.banned;
            }
            return false;
        }
    };

    public static final AppFilter FILTER_APP_NOTIFICATION_SILENCED = new AppFilter() {
        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(AppEntry info) {
            if (info == null || info.extraInfo == null) {
                return false;
            }
            AppRow row = (AppRow) info.extraInfo;
            return row.appImportance > NotificationListenerService.Ranking.IMPORTANCE_NONE
                    && row.appImportance < NotificationListenerService.Ranking.IMPORTANCE_DEFAULT;
        }
    };

    public static final AppFilter FILTER_APP_NOTIFICATION_PRIORITY = new AppFilter() {
        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(AppEntry info) {
            if (info == null || info.extraInfo == null) {
                return false;
            }
            return ((AppRow) info.extraInfo).appBypassDnd;
        }
    };

    public static final AppFilter FILTER_APP_NOTIFICATION_HIDE_SENSITIVE = new AppFilter() {
        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(AppEntry info) {
            if (info == null || info.extraInfo == null) {
                return false;
            }
            return ((AppRow) info.extraInfo).lockScreenSecure
                    && ((AppRow) info.extraInfo).appVisOverride == Notification.VISIBILITY_PRIVATE;
        }
    };

    public static final AppFilter FILTER_APP_NOTIFICATION_HIDE_ALL = new AppFilter() {
        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(AppEntry info) {
            if (info == null || info.extraInfo == null) {
                return false;
            }
            return ((AppRow) info.extraInfo).lockScreenSecure
                    && ((AppRow) info.extraInfo).appVisOverride == Notification.VISIBILITY_SECRET;
        }
    };
}
