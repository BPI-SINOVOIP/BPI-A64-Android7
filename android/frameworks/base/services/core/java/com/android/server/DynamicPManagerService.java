/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;

import android.os.Binder;
import android.os.IDynamicPManager;
import android.os.DynamicPManager;
import android.os.SystemClock;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.media.MediaPlayer;
import android.media.AudioManager;

import com.android.server.NativeDaemonConnector.Command;
import com.android.server.NativeDaemonConnector.SensitiveArg;
import com.android.server.am.ActivityManagerService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerService;

public class DynamicPManagerService extends IDynamicPManager.Stub
        implements INativeDaemonConnectorCallbacks {
    private static final String TAG = "DynamicPManagerService";
    private static final String SAYEYE_TAG = "SayeyeConnector";
    private static final boolean LOCAL_LOGD = false;

    private static final String LAUNCHER = "com.android.launcher";
    private int mScense = ScenseState.NORMAL;
    private final Context mContext;
    private volatile boolean mSystemReady = false;
    private volatile boolean mStatus = false;
    private volatile boolean mScreen = false;

    /** Maximum number of ASEC containers allowed to be mounted. */
    private static final int MAX_CONTAINERS = 250;

    private final NativeDaemonConnector mConnector;

    private final Handler mHandler;

    private final String mSync = "sync";

    private final int DEFAULT_MAX_LAYERS = 2;
    private int VIDEO_PLAYING = 1;
    private int DECODE_HW = 1;
    private int mCurLayers = 0;

    private Timer mTimer = null;
    private BoostUPerfTask mBoostUPerfTask = null;

    private AudioManager mAudioService;


    class ScenseCallBack {
        String pkg_name;
        String aty_name;
        int pid;
        int tags;

        ScenseCallBack(String pkg_name, String aty_name, int pid, int tags) {
            this.pkg_name = pkg_name;
            this.aty_name = aty_name;
            this.pid = pid;
            this.tags = tags;
        }

        void handleFinished() {
            if (LOCAL_LOGD) Slog.d(TAG, "ScenseSetting ");
            mScense = 0;
        }
    }

    class BoostUPerfTask extends TimerTask {
        ScenseCallBack scb;
        public void setScb(ScenseCallBack scb) {
            this.scb = scb;
        }
        public void run() {
            //do somthing
            if (LOCAL_LOGD)
                Log.e(TAG, "Scense Timer Task");
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.MONITOR, scb));
            return;
        }
    }
    /*
     * Internal sayeye volume state constants
     */
    class ScenseState {
        public static final int NORMAL          = 0x00000001;
        public static final int HOME            = 0x00000002;
        public static final int BOOTCOMPLETE    = 0x00000004;
        public static final int VIDEO           = 0x00000008;
        public static final int MUSIC           = 0x00000010;
        public static final int MONITOR         = 0x00000020;
        public static final int ROTATE          = 0x00000040;
        public static final int BENCHMARK       = 0x00000080;
    }

    /*
     * Internal scense response code constants
     */
    class ScenseResponseCode {
        static final int ScenseNormalResult         = 610;
        static final int ScenseHomeResult           = 611;
        static final int ScenseBootCompleteResult   = 612;
        static final int ScenseVideoResult          = 613;
        static final int ScenseMusicResult          = 614;
        static final int ScenseMonitorResult        = 615;
        static final int ScenseRotateResult         = 616;
        static final int ScenseBenchmarkResult      = 617;

    };

    private String ToString(int scense) {
        switch (scense) {
            case ScenseState.NORMAL:
                return "normal";
            case ScenseState.HOME:
                return "home";
            case ScenseState.BOOTCOMPLETE:
                return "bootcomplete";
            case ScenseState.VIDEO:
                return "video";
            case ScenseState.MUSIC:
                return "music";
            case ScenseState.MONITOR:
                return "monitor";
            case ScenseState.ROTATE:
                return "rotate";
            case ScenseState.BENCHMARK:
                return "benchmark";
            default:
                break;
        }

        return "un-defined scense";
    }

    class DynamicPManagerServiceHandler extends Handler {
        boolean mUpdatingStatus = false;

        DynamicPManagerServiceHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ScenseState.NORMAL:
                    try {
                        // This method must be run on the main (handler) thread,
                        mConnector.execute("normal", "enter");
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to normal mode!");
                    }
                    break;
                case ScenseState.HOME:
                    try {
                        // This method must be run on the main (handler) thread,
                        mConnector.execute("home", "enter");
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to home mode!");
                    }
                    break;
                case ScenseState.BENCHMARK:
                    try {
                        // This method must be run on the main (handler) thread,
                        final ScenseCallBack scb = (ScenseCallBack)msg.obj;
                        mConnector.execute("benchmark", "enter", "scb.pkg_name", "scb.aty_name", scb.pid, scb.tags);
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to benchmark mode!");
                    }
                    break;
                case ScenseState.BOOTCOMPLETE:
                    try {
                        // This method must be run on the main (handler) thread,
                        mConnector.execute("bootcomplete", "enter");
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed to run bootcomplete!");
                    }
                    mSystemReady = true;
                    Slog.i(TAG, "Scence Control Had Init");
                    break;
                case ScenseState.VIDEO:
                    try {
                        // This method must be run on the main (handler) thread,
                        if (true) {
                            mConnector.execute("video", "enter", "4k");
                        } else {
                            mConnector.execute("video", "enter", "1080p");
                        }
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to video mode!");
                    }
                    break;
                case ScenseState.MUSIC:
                    try {
                        // This method must be run on the main (handler) thread,
                        mConnector.execute("music", "enter");
			mStatus = true;
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to music mode!");
                    }
                    break;
                case ScenseState.ROTATE:
                    try {
                        // This method must be run on the main (handler) thread,
                        mConnector.execute("rotate", "enter");
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed change to rotate mode!");
                    }
                    break;
                case ScenseState.MONITOR:
                        // This method must be run on the main (handler) thread,
                        final ScenseCallBack scb = (ScenseCallBack)msg.obj;
                    break;
                default:
                    Slog.e(TAG,"Un-know Scense Code");
                    break;
            }
        }
    };

    /**
     * Callback from NativeDaemonConnector
     */

    public void onDaemonConnected() {
        /*
         * Since we'll be calling back into the NativeDaemonConnector,
         * we need to do our work in a new thread.
         */
        new Thread("DynamicPManagerService#onDaemonConnected") {
            @Override
            public void run() {
                Slog.i(TAG, "sayeye has connected");
            }
        }.start();
    }
    /**
     * Callback from NativeDaemonConnector
     */
    public boolean onCheckHoldWakeLock(int code) {
        return false;
    }

    /**
     * Callback from NativeDaemonConnector
     */
    public boolean onEvent(int code, String raw, String[] cooked) {
        if (LOCAL_LOGD) {
            StringBuilder builder = new StringBuilder();
            builder.append("onEvent::");
            builder.append(" raw= " + raw);
            if (cooked != null) {
                builder.append(" cooked= ");
                for (String str : cooked) {
                    builder.append(" " + str);
                }
            }
            Slog.d(TAG, builder.toString());
        }

        if (code == ScenseResponseCode.ScenseNormalResult) {
            mScense = ScenseState.NORMAL;
        } else if (code == ScenseResponseCode.ScenseHomeResult) {
            mScense = ScenseState.HOME;
        } else if (code == ScenseResponseCode.ScenseBootCompleteResult) {
            mScense = ScenseState.BOOTCOMPLETE;
        } else if (code == ScenseResponseCode.ScenseVideoResult) {
            mScense = ScenseState.VIDEO;
        } else if (code == ScenseResponseCode.ScenseMusicResult) {
            mScense = ScenseState.MUSIC;
        } else if (code == ScenseResponseCode.ScenseRotateResult) {
            mScense = ScenseState.ROTATE;
        } else if (code == ScenseResponseCode.ScenseBenchmarkResult) {
            mScense = ScenseState.BENCHMARK;
        } else if (code == ScenseResponseCode.ScenseMonitorResult) {
            mScense = ScenseState.MONITOR;
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL));
        }
        Slog.i(TAG, "Current Scense:" + ToString(mScense));
        return true;
    }

    public void systemReady() {
    }

    private void SetBoostUp(Intent intent) {
        if (!mSystemReady)
            return;
        String action = intent.getAction();
        String mode = null;
        //String package_name = null;
        //String activity_name = null;
        ScenseCallBack scb = new ScenseCallBack(null, null, -1, 0);
        int numDisplays = SystemProperties.getInt("sys.boot_up_perf.displays", 1);
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            mScreen = true;
            mode = DynamicPManager.BOOST_UPERF_NORMAL;
            if (mAudioService.isMusicActive() && numDisplays <= 1) {
                mode = DynamicPManager.BOOST_UPERF_BGMUSIC;
                scb.tags = 1;
                mStatus = false;
            } else if (mScense == ScenseState.BENCHMARK) {
                mode = DynamicPManager.BOOST_UPERF_NORMAL;
            } else {
                /* monitor tp 1 */
                //mode = "monitor";
                //mTimer.schedule(mBoostUPerfTask, 3*1000);
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            mScreen = false;
            if (mScense == ScenseState.BENCHMARK) {
                mode = DynamicPManager.BOOST_UPERF_EXTREME;
            } else if (mScense == ScenseState.MONITOR) {
                //mode = "monitor";
                //Slog.i(TAG, "monitor");
                //mTimer.schedule(mBoostUPerfTask, 3);
            } else {
                scb.tags = 0;
                mode = DynamicPManager.BOOST_UPERF_NORMAL;
                if (!mStatus) {
                    mHandler.removeMessages(ScenseState.MUSIC);
                }
            }
        } else if (Intent.ACTION_BOOST_UP_PERF.equals(action)) {
            mode = intent.getStringExtra("mode");
            if (mode == null) {
                mode = DynamicPManager.BOOST_UPERF_NORMAL;
            }

            scb.pid = intent.getIntExtra("pid", 0);
            scb.tags = intent.getIntExtra("index", 0);
            //package_name = intent.getStringExtra("package_name");
            //activity_name = intent.getStringExtra("activity_name");
            //scb.pkg_name = package_name;
            //scb.aty_name = activity_name;
        }
        switch (mode) {
            case DynamicPManager.BOOST_UPERF_4KLOCALVIDEO:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.VIDEO,null));
                break;
            case DynamicPManager.BOOST_UPERF_LOCALVIDEO:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.VIDEO,null));
                break;
            case DynamicPManager.BOOST_UPERF_NORMAL:
                if (mScreen && mScense == ScenseState.MUSIC) {
                    break;
                } else {
                    mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL,null));
                }
                break;
            case DynamicPManager.BOOST_UPERF_EXTREME:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.BENCHMARK, scb));
                break;
            case DynamicPManager.BOOST_UPERF_HOMENTER:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.HOME,null));
                break;
            case DynamicPManager.BOOST_UPERF_HOMEXIT:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL,null));
                break;
            case DynamicPManager.BOOST_UPERF_BGMUSIC:
                if (scb.tags == 1)
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(ScenseState.MUSIC, scb), 3000);
                else
                    mHandler.sendMessage(mHandler.obtainMessage(ScenseState.MUSIC, scb));
                break;
            case DynamicPManager.BOOST_UPERF_ROTATENTER:
                if (mScense == ScenseState.BENCHMARK) {
                    break;
                }
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.ROTATE,null));
                break;
            case DynamicPManager.BOOST_UPERF_ROTATEXIT:
                if (mScense == ScenseState.BENCHMARK) {
                    break;
                }
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL,null));
                break;
            case DynamicPManager.BOOST_UPERF_USBENTER:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL,null));
                break;
            case DynamicPManager.BOOST_UPERF_USBEXIT:
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL,null));
                break;
            default:
		Slog.i(TAG, "un-know mode" );
                break;
        }
    }

    /**
     * Boost receiver
     */
    private final BroadcastReceiver mBoostReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Slog.i(TAG, "Receiver notifyDPM");
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {

                if (mTimer == null) {
                    mTimer   = new Timer();
                }

                if (mBoostUPerfTask == null){
                    mBoostUPerfTask = new BoostUPerfTask();
                }

                mAudioService = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.BOOTCOMPLETE));
                mHandler.sendMessage(mHandler.obtainMessage(ScenseState.NORMAL));
                return;
            }

            if (Intent.ACTION_BOOST_UP_PERF.equals(action) ||
                    Intent.ACTION_SCREEN_ON.equals(action) ||
                    Intent.ACTION_SCREEN_OFF.equals(action)) {
                synchronized(mSync) {
                    SetBoostUp(intent);
                }
            }
        }

    };

    public DynamicPManagerService(Context context) {
        mContext = context;

        HandlerThread hthread = new HandlerThread(TAG);
        hthread.start();
        mHandler = new DynamicPManagerServiceHandler(hthread.getLooper());

        // Watch for changes
        IntentFilter boostFilter = new IntentFilter();
        boostFilter.addAction(Intent.ACTION_BOOST_UP_PERF);
        boostFilter.addAction(Intent.ACTION_SCREEN_ON);
        boostFilter.addAction(Intent.ACTION_SCREEN_OFF);
        boostFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(mBoostReceiver, boostFilter, null, mHandler);

        /*
         * Create the connection to vold with a maximum queue of twice the
         * amount of containers we'd ever expect to have. This keeps an
         * "asec list" from blocking a thread repeatedly.
         */
        mConnector = new NativeDaemonConnector(this, "sayeye", MAX_CONTAINERS * 2, SAYEYE_TAG, 25, null);

        Thread thread = new Thread(mConnector, SAYEYE_TAG);
        thread.start();

    }

    public void notifyDPM(Intent intent){
        synchronized(mSync) {
            SetBoostUp(intent);
        }
    }
}
