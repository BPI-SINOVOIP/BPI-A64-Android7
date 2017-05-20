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

package com.android.notification.functional;

import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.IntentService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.notification.functional.R;

import java.lang.InterruptedException;
import java.util.List;
import java.util.Map;

public class NotificationHelper {

    private static final String LOG_TAG = NotificationHelper.class.getSimpleName();
    private static final int LONG_TIMEOUT = 2000;
    private static final int SHORT_TIMEOUT = 200;
    private static final String KEY_QUICK_REPLY_TEXT = "quick_reply";
    private static final UiSelector LIST_VIEW = new UiSelector().className(ListView.class);
    private static final UiSelector LIST_ITEM_VALUE = new UiSelector().className(TextView.class);

    private UiDevice mDevice;
    private Instrumentation mInst;
    private NotificationManager mNotificationManager = null;
    private Context mContext = null;

    public NotificationHelper(UiDevice device, Instrumentation inst, NotificationManager nm) {
        this.mDevice = device;
        mInst = inst;
        mNotificationManager = nm;
        mContext = inst.getContext();
    }

    public void sleepAndWakeUpDevice() throws RemoteException, InterruptedException {
        mDevice.sleep();
        Thread.sleep(LONG_TIMEOUT);
        mDevice.wakeUp();
    }

    public static void launchSettingsPage(Context ctx, String pageName) throws Exception {
        Intent intent = new Intent(pageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        Thread.sleep(LONG_TIMEOUT * 2);
    }

    /**
     * Sets the screen lock pin
     * @param pin 4 digits
     * @return false if a pin is already set or pin value is not 4 digits
     * @throws UiObjectNotFoundException
     */
    public boolean setScreenLockPin(int pin) throws Exception {
        if (pin >= 0 && pin <= 9999) {
            navigateToScreenLock();
            if (new UiObject(new UiSelector().text("Confirm your PIN")).exists()) {
                UiObject pinField = new UiObject(
                        new UiSelector().className(EditText.class.getName()));
                pinField.setText(String.format("%04d", pin));
                mDevice.pressEnter();
            }
            new UiObject(new UiSelector().text("PIN")).click();
            clickText("No thanks");
            UiObject pinField = new UiObject(new UiSelector().className(EditText.class.getName()));
            pinField.setText(String.format("%04d", pin));
            mDevice.pressEnter();
            pinField.setText(String.format("%04d", pin));
            mDevice.pressEnter();
            clickText("Hide sensitive notification content");
            clickText("DONE");
            return true;
        }
        return false;
    }

    public boolean removeScreenLock(int pin, String mode) throws Exception {
        navigateToScreenLock();
        if (new UiObject(new UiSelector().text("Confirm your PIN")).exists()) {
            UiObject pinField = new UiObject(new UiSelector().className(EditText.class.getName()));
            pinField.setText(String.format("%04d", pin));
            mDevice.pressEnter();
            clickText(mode);
            clickText("YES, REMOVE");
        } else {
            clickText(mode);
        }
        return true;
    }

    public void unlockScreenByPin(int pin) throws Exception {
        String command = String.format(" %s %s %s", "input", "text", Integer.toString(pin));
        executeAdbCommand(command);
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.pressEnter();
    }

    public void enableNotificationViaAdb(boolean isShow) {
        String command = String.format(" %s %s %s %s %s", "settings", "put", "secure",
                "lock_screen_show_notifications",
                isShow ? "1" : "0");
        executeAdbCommand(command);
    }

    public void executeAdbCommand(String command) {
        Log.i(LOG_TAG, String.format("executing - %s", command));
        mInst.getUiAutomation().executeShellCommand(command);
        mDevice.waitForIdle();
    }

    private void navigateToScreenLock() throws Exception {
        launchSettingsPage(mInst.getContext(), Settings.ACTION_SECURITY_SETTINGS);
        new UiObject(new UiSelector().text("Screen lock")).click();
    }

    private void clickText(String text) throws UiObjectNotFoundException {
        mDevice.wait(Until.findObject(By.text(text)), LONG_TIMEOUT).click();
    }

    public void sendNotification(int id, int visibility, String title) throws Exception {
        Log.v(LOG_TAG, "Sending out notification...");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        CharSequence subtitle = String.valueOf(System.currentTimeMillis());
        Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.stat_notify_email)
                .setWhen(System.currentTimeMillis()).setContentTitle(title).setContentText(subtitle)
                .setContentIntent(pendingIntent).setVisibility(visibility)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        mNotificationManager.notify(id, notification);
        Thread.sleep(LONG_TIMEOUT);
    }

    public void sendNotifications(Map<Integer, String> lists) throws Exception {
        Log.v(LOG_TAG, "Sending out notification...");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        CharSequence subtitle = String.valueOf(System.currentTimeMillis());
        for (Map.Entry<Integer, String> l : lists.entrySet()) {
            Notification notification = new Notification.Builder(mContext)
                    .setSmallIcon(R.drawable.stat_notify_email)
                    .setWhen(System.currentTimeMillis()).setContentTitle(l.getValue())
                    .setContentText(subtitle)
                    .build();
            mNotificationManager.notify(l.getKey(), notification);
        }
        Thread.sleep(LONG_TIMEOUT);
    }

    public void sendBundlingNotifications(List<Integer> lists, String groupKey) throws Exception {
        Notification childNotification = new Notification.Builder(mContext)
                .setContentTitle(lists.get(1).toString())
                .setSmallIcon(R.drawable.stat_notify_email)
                .setGroup(groupKey)
                .build();
        mNotificationManager.notify(lists.get(1),
                childNotification);
        childNotification = new Notification.Builder(mContext)
                .setContentText(lists.get(2).toString())
                .setSmallIcon(R.drawable.stat_notify_email)
                .setGroup(groupKey)
                .build();
        mNotificationManager.notify(lists.get(2),
                childNotification);
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle(lists.get(0).toString())
                .setSubText(groupKey)
                .setSmallIcon(R.drawable.stat_notify_email)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .build();
        mNotificationManager.notify(lists.get(0),
                notification);
    }

    static SpannableStringBuilder BOLD(CharSequence str) {
        final SpannableStringBuilder ssb = new SpannableStringBuilder(str);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ssb.length(), 0);
        return ssb;
    }

    public boolean checkNotificationExistence(int id, boolean exists) throws Exception {
        boolean isFound = false;
        for (int tries = 3; tries-- > 0;) {
            isFound = false;
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id) {
                    isFound = true;
                    break;
                }
            }
            if (isFound == exists) {
                break;
            }
            Thread.sleep(SHORT_TIMEOUT);
        }
        Log.i(LOG_TAG, "checkNotificationExistence..." + isFound);
        return isFound == exists;
    }

    public StatusBarNotification getStatusBarNotification(int id) {
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        StatusBarNotification n = null;
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() == id) {
                n = sbn;
                break;
            }
        }
        return n;
    }

    public void swipeUp() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight(),
                mDevice.getDisplayWidth() / 2, 0, 30);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void swipeDown() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, 0, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2 + 50, 20);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void unlockScreen() throws Exception {
        KeyguardManager myKM = (KeyguardManager) mContext
                .getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            // it is locked
            swipeUp();
        }
    }

    public void showInstalledAppDetails(Context context, String packageName) throws Exception {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = Uri.fromParts("package", packageName, null);
        intent.setData(uri);
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$AppNotificationSettingsActivity");
        intent.putExtra("app_package", mContext.getPackageName());
        intent.putExtra("app_uid", mContext.getApplicationInfo().uid);
        context.startActivity(intent);
        Thread.sleep(LONG_TIMEOUT * 2);
    }

    /**
     * This is the main list view containing the items that settings are possible for
     */
    public static class SettingsListView {
        public static boolean selectSettingsFor(String name) throws UiObjectNotFoundException {
            UiScrollable settingsList = new UiScrollable(
                    new UiSelector().resourceId("android:id/content"));
            UiObject appSettings = settingsList.getChildByText(LIST_ITEM_VALUE, name);
            if (appSettings != null) {
                return appSettings.click();
            }
            return false;
        }

        public boolean checkSettingsExists(String name) {
            try {
                UiScrollable settingsList = new UiScrollable(LIST_VIEW);
                UiObject appSettings = settingsList.getChildByText(LIST_ITEM_VALUE, name);
                return appSettings.exists();
            } catch (UiObjectNotFoundException e) {
                return false;
            }
        }
    }

    public void sendNotificationsWithInLineReply(int notificationId, boolean isHeadsUp) {
        Notification.Action action = new Notification.Action.Builder(
                R.drawable.stat_notify_email, "Reply", ToastService.getPendingIntent(mContext,
                        "inline reply test"))
                                .addRemoteInput(new RemoteInput.Builder(KEY_QUICK_REPLY_TEXT)
                                        .setLabel("Quick reply").build())
                                .build();
        Notification.Builder n = new Notification.Builder(mContext)
                .setContentTitle(Integer.toString(notificationId))
                .setContentText("INLINE REPLY TEST")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.stat_notify_email)
                .addAction(action);
        if (isHeadsUp) {
            n.setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_VIBRATE);
        }
        mNotificationManager.notify(notificationId, n.build());
    }

    public static class ToastService extends IntentService {
        private static final String TAG = "ToastService";
        private static final String ACTION_TOAST = "toast";
        private Handler handler;

        public ToastService() {
            super(TAG);
        }

        public ToastService(String name) {
            super(name);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            handler = new Handler();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            if (intent.hasExtra("text")) {
                final String text = intent.getStringExtra("text");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ToastService.this, text, Toast.LENGTH_LONG).show();
                        Log.v(TAG, "toast " + text);
                    }
                });
            }
        }

        public static PendingIntent getPendingIntent(Context context, String text) {
            Intent toastIntent = new Intent(context, ToastService.class);
            toastIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            toastIntent.setAction(ACTION_TOAST + ":" + text); // one per toast message
            toastIntent.putExtra("text", text);
            PendingIntent pi = PendingIntent.getService(
                    context, 58, toastIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            return pi;
        }
    }
}
