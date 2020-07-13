/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.activity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.Build;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;

import com.googlecode.android_scripting.AndroidProxy;
import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.Constants;
import com.googlecode.android_scripting.ForegroundService;
import com.googlecode.android_scripting.NotificationIdFactory;
import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.ScriptLauncher;
import com.googlecode.android_scripting.ScriptProcess;
import com.googlecode.android_scripting.interpreter.InterpreterConfiguration;
import com.googlecode.android_scripting.interpreter.InterpreterProcess;
import com.googlecode.android_scripting.interpreter.shell.ShellInterpreter;

import org.connectbot.ConsoleActivity;
import org.connectbot.service.TerminalManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A service that allows scripts and the RPC server to run in the background.
 *
 */
public class ScriptingLayerService extends ForegroundService {
  private static final int NOTIFICATION_ID = NotificationIdFactory.create();

  private final IBinder mBinder;
  private final Map<Integer, InterpreterProcess> mProcessMap;
  private final String LOG_TAG = "sl4a";
  private volatile int mModCount = 0;
  private NotificationManager mNotificationManager;
  private Notification mNotification;
  private PendingIntent mNotificationPendingIntent;
  private InterpreterConfiguration mInterpreterConfiguration;

  private volatile WeakReference<InterpreterProcess> mRecentlyKilledProcess;

  private TerminalManager mTerminalManager;

  private SharedPreferences mPreferences = null;
  private boolean mHide;

  public class LocalBinder extends Binder {
    public ScriptingLayerService getService() {
      return ScriptingLayerService.this;
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  public ScriptingLayerService() {
    super(NOTIFICATION_ID);
    mProcessMap = new ConcurrentHashMap<Integer, InterpreterProcess>();
    mBinder = new LocalBinder();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    mInterpreterConfiguration = ((BaseApplication) getApplication()).getInterpreterConfiguration();
    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mRecentlyKilledProcess = new WeakReference<InterpreterProcess>(null);
    mTerminalManager = new TerminalManager(this);
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    mHide = mPreferences.getBoolean(Constants.HIDE_NOTIFY, false);
  }

  @Override
  protected Notification createNotification() {
    Intent notificationIntent = new Intent(this, ScriptingLayerService.class);
    notificationIntent.setAction(Constants.ACTION_SHOW_RUNNING_SCRIPTS);
    mNotificationPendingIntent = PendingIntent.getService(this, 0, notificationIntent, 0);

    Notification.Builder builder = new Notification.Builder(this);
    builder.setSmallIcon(R.drawable.sl4a_notification_logo)
           .setTicker(null)
           .setWhen(System.currentTimeMillis())
           .setContentTitle("SL4A Service")
           .setContentText("Tap to view running scripts")
           .setContentIntent(mNotificationPendingIntent);
    mNotification = builder.build();
    mNotification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    return mNotification;
  }

  private void updateNotification(String tickerText) {
    if (tickerText.equals(mNotification.tickerText)) {
      // Consequent notifications with the same ticker-text are displayed without any ticker-text.
      // This is a way around. Alternatively, we can display process name and port.
      tickerText = tickerText + " ";
    }
    String msg;
    if (mProcessMap.size() <= 1) {
      msg = "Tap to view " + Integer.toString(mProcessMap.size()) + " running script";
    } else {
      msg = "Tap to view " + Integer.toString(mProcessMap.size()) + " running scripts";
    }
    Notification.Builder builder = new Notification.Builder(this);
    builder.setContentTitle("SL4A Service")
           .setContentText(msg)
           .setContentIntent(mNotificationPendingIntent)
           .setSmallIcon(mNotification.icon, mProcessMap.size())
           .setWhen(mNotification.when)
           .setTicker(tickerText);

    mNotification = builder.build();
    mNotificationManager.notify(NOTIFICATION_ID, mNotification);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    String errmsg = null;
    if (intent == null) {
      return START_REDELIVER_INTENT;
    }
    if (intent.getAction().equals(Constants.ACTION_KILL_ALL)) {
      killAll();
      stopSelf(startId);
      return START_REDELIVER_INTENT;
    }

    if (intent.getAction().equals(Constants.ACTION_KILL_PROCESS)) {
      killProcess(intent);
      if (mProcessMap.isEmpty()) {
        stopSelf(startId);
      }
      return START_REDELIVER_INTENT;
    }

    if (intent.getAction().equals(Constants.ACTION_SHOW_RUNNING_SCRIPTS)) {
      showRunningScripts();
      return START_REDELIVER_INTENT;
    }

    //TODO: b/26538940 We need to go back to a strict policy and fix the problems
    StrictMode.ThreadPolicy sl4aPolicy = new StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .build();

    StrictMode.setThreadPolicy(sl4aPolicy);

    AndroidProxy proxy = null;
    InterpreterProcess interpreterProcess = null;
    if (intent.getAction().equals(Constants.ACTION_LAUNCH_SERVER)) {
      proxy = launchServer(intent, false);
      // TODO(damonkohler): This is just to make things easier. Really, we shouldn't need to start
      // an interpreter when all we want is a server.
      interpreterProcess = new InterpreterProcess(new ShellInterpreter(), proxy);
      interpreterProcess.setName("Server");
    } else {
      proxy = launchServer(intent, true);
      if (intent.getAction().equals(Constants.ACTION_LAUNCH_FOREGROUND_SCRIPT)) {
        launchTerminal(proxy.getAddress());
        try {
          interpreterProcess = launchScript(intent, proxy);
        } catch (RuntimeException e) {
          errmsg =
              "Unable to run " + intent.getStringExtra(Constants.EXTRA_SCRIPT_PATH) + "\n"
                  + e.getMessage();
          interpreterProcess = null;
        }
      } else if (intent.getAction().equals(Constants.ACTION_LAUNCH_BACKGROUND_SCRIPT)) {
        interpreterProcess = launchScript(intent, proxy);
      } else if (intent.getAction().equals(Constants.ACTION_LAUNCH_INTERPRETER)) {
        launchTerminal(proxy.getAddress());
        interpreterProcess = launchInterpreter(intent, proxy);
      }
    }
    if (errmsg != null) {
      updateNotification(errmsg);
    } else if (interpreterProcess == null) {
      updateNotification("Action not implemented: " + intent.getAction());
    } else {
      addProcess(interpreterProcess);
    }
    return START_REDELIVER_INTENT;
  }

  private boolean tryPort(AndroidProxy androidProxy, boolean usePublicIp, int usePort) {
    if (usePublicIp) {
      return (androidProxy.startPublic(usePort) != null);
    } else {
      return (androidProxy.startLocal(usePort) != null);
    }
  }

  private AndroidProxy launchServer(Intent intent, boolean requiresHandshake) {
    AndroidProxy androidProxy = new AndroidProxy(this, intent, requiresHandshake);
    boolean usePublicIp = intent.getBooleanExtra(Constants.EXTRA_USE_EXTERNAL_IP, false);
    int usePort = intent.getIntExtra(Constants.EXTRA_USE_SERVICE_PORT, 0);
    // If port is in use, fall back to default behaviour
    if (!tryPort(androidProxy, usePublicIp, usePort)) {
      if (usePort != 0) {
        tryPort(androidProxy, usePublicIp, 0);
      }
    }
    return androidProxy;
  }

  private ScriptProcess launchScript(Intent intent, AndroidProxy proxy) {
    final int port = proxy.getAddress().getPort();
    File script = new File(intent.getStringExtra(Constants.EXTRA_SCRIPT_PATH));
    return ScriptLauncher.launchScript(script, mInterpreterConfiguration, proxy, new Runnable() {
      @Override
      public void run() {
        // TODO(damonkohler): This action actually kills the script rather than notifying the
        // service that script exited on its own. We should distinguish between these two cases.
        Intent intent = new Intent(ScriptingLayerService.this, ScriptingLayerService.class);
        intent.setAction(Constants.ACTION_KILL_PROCESS);
        intent.putExtra(Constants.EXTRA_PROXY_PORT, port);
        startService(intent);
      }
    });
  }

  private InterpreterProcess launchInterpreter(Intent intent, AndroidProxy proxy) {
    InterpreterConfiguration config =
        ((BaseApplication) getApplication()).getInterpreterConfiguration();
    final int port = proxy.getAddress().getPort();
    return ScriptLauncher.launchInterpreter(proxy, intent, config, new Runnable() {
      @Override
      public void run() {
        // TODO(damonkohler): This action actually kills the script rather than notifying the
        // service that script exited on its own. We should distinguish between these two cases.
        Intent intent = new Intent(ScriptingLayerService.this, ScriptingLayerService.class);
        intent.setAction(Constants.ACTION_KILL_PROCESS);
        intent.putExtra(Constants.EXTRA_PROXY_PORT, port);
        startService(intent);
      }
    });
  }

  private void launchTerminal(InetSocketAddress address) {
    Intent i = new Intent(this, ConsoleActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    i.putExtra(Constants.EXTRA_PROXY_PORT, address.getPort());
    startActivity(i);
  }

  private void showRunningScripts() {
    Intent i = new Intent(this, ScriptProcessMonitor.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(i);
  }

  private void addProcess(InterpreterProcess process) {
    mProcessMap.put(process.getPort(), process);
    mModCount++;
    if (!mHide) {
      updateNotification(process.getName() + " started.");
    }
  }

  private InterpreterProcess removeProcess(int port) {
    InterpreterProcess process = mProcessMap.remove(port);
    if (process == null) {
      return null;
    }
    mModCount++;
    if (!mHide) {
      updateNotification(process.getName() + " exited.");
    }
    return process;
  }

  private void killProcess(Intent intent) {
    int processId = intent.getIntExtra(Constants.EXTRA_PROXY_PORT, 0);
    InterpreterProcess process = removeProcess(processId);
    if (process != null) {
      process.kill();
      mRecentlyKilledProcess = new WeakReference<InterpreterProcess>(process);
    }
  }

  public int getModCount() {
    return mModCount;
  }

  private void killAll() {
    for (InterpreterProcess process : getScriptProcessesList()) {
      process = removeProcess(process.getPort());
      if (process != null) {
        process.kill();
      }
    }
  }

  public List<InterpreterProcess> getScriptProcessesList() {
    ArrayList<InterpreterProcess> result = new ArrayList<InterpreterProcess>();
    result.addAll(mProcessMap.values());
    return result;
  }

  public InterpreterProcess getProcess(int port) {
    InterpreterProcess p = mProcessMap.get(port);
    if (p == null) {
      return mRecentlyKilledProcess.get();
    }
    return p;
  }

  public TerminalManager getTerminalManager() {
    return mTerminalManager;
  }
}
