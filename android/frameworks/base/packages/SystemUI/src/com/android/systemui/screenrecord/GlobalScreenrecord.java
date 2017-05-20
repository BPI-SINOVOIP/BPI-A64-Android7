/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.screenrecord;

import static android.system.OsConstants.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.android.systemui.R;

/**
 * 
 */
public class GlobalScreenrecord {
    private static final String TAG = "GlobalScreenrecord";
    public static final String SCREENRECORD_STATE_CHANGED_ACTION = "screenrecord_state_changed_action";
    public static final String EXTRA_SCREENRECORD_RECORDING = "extra_screenrecord_recording";

    final String CMD_FIND_SCREENRECORD_PID = "ps | grep screenrecord";
    final String CMD_EXIT = "exit\n";
    final Object mScreenrecordLock = new Object();
    private boolean mRecording = false;

    private ScreenrecordNotification mScreenrecordNotification;

    private Context mContext;

    private Notification.Builder  mBuilder;
    
    /**
     * @param context everything needs a context :(
     */
    public GlobalScreenrecord(Context context) {
        mContext = context;
        mRecording = false;
    }

    /**
     * Auto to take a screenrecord or stop.
     */
    public void autoScreenrecord() {
        if (mRecording) stopScreenrecord();
        else takeScreenrecord();
    }

    /**
     * Takes a screenrecord.
     */
    private void takeScreenrecord() {
        synchronized (mScreenrecordLock) {
            if (mRecording) return;
            new Thread() {
                @Override
                public void run() {
                    mRecording = true;
                    String cmd = "screenrecord";
                    String path = Environment.getExternalStorageDirectory() + "/screen_" + System.currentTimeMillis() + ".mp4";
                    //String videoSize = Settings.System.getString(mContext.getContentResolver(), Settings.System.SCREENRECORD_VIDEO_SIZE);
                    //if (videoSize != null && videoSize.length() > 0) cmd += " --size " + videoSize;
                    //String timeLimit = Settings.System.getString(mContext.getContentResolver(), Settings.System.SCREENRECORD_VIDEO_TIME_LIMIT);
                    //if (timeLimit != null && timeLimit.length() > 0) cmd += " --time-limit " + timeLimit;
                    //String audioSource = Settings.System.getString(mContext.getContentResolver(), Settings.System.SCREENRECORD_AUDIO_SOURCE);
                    //if (audioSource != null && audioSource.length() > 0) cmd += " --audio " + audioSource;
                    cmd += " " + path;
                    Log.d(TAG, "screenrecord: " + cmd);
                    try {
                        Process process = Runtime.getRuntime().exec(cmd);
                        DataOutputStream dos = new DataOutputStream(process.getOutputStream());

                        dos.writeBytes(CMD_EXIT);
                        dos.flush();
                        process.waitFor();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    } finally {
                        mRecording = false;
                        onStop();
                        Log.d(TAG, "Screenrecord stop");
                    }
                }
            }.start();
            onStart();
        }
    }

    private void onStart() {
        Intent intent = new Intent(SCREENRECORD_STATE_CHANGED_ACTION);
        intent.putExtra(EXTRA_SCREENRECORD_RECORDING, true);
        mContext.sendBroadcast(intent);
        
        //发送通知
        if (mScreenrecordNotification == null){
            mScreenrecordNotification = new ScreenrecordNotification(mContext);
        }
        mScreenrecordNotification.notifyNotificaiton();
    }

    private void onStop() {
        Intent intent = new Intent(SCREENRECORD_STATE_CHANGED_ACTION);
        intent.putExtra(EXTRA_SCREENRECORD_RECORDING, false);
        mContext.sendBroadcast(intent);
        //取消通知
        mScreenrecordNotification.cancelNotificaiton();
    }

    /**
     * Stop screenrecord.
     */
    private void stopScreenrecord() {
        if (mRecording) {
            try {
				// ps ?  ps|grep screenrecord
                //Process p = Runtime.getRuntime().exec(CMD_FIND_SCREENRECORD_PID);
                Process p = Runtime.getRuntime().exec("ps");
                DataInputStream dis = new DataInputStream(p.getInputStream());
                String line = null;
                String[] strs = null;
                int pid = 0;
                while ((line = dis.readLine()) != null) {
					strs = line.split("[\\s]+");
                    if (strs.length >= 9 && "screenrecord".equals(strs[8])) {
                        pid = Integer.parseInt(strs[1]);
                        if (pid > 0) {
                            try {
                                libcore.io.Libcore.os.kill(pid, SIGINT);
                            } catch (android.system.ErrnoException e) {
                                Log.e(TAG, "Failed to destroy process " + pid + e);
                            }
                        }
                    }
                }
                p.waitFor();
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    class ScreenrecordNotification {
        final String NOTIFICATION_TITLE = "screenrecord";
        final int NOTIFICATION_ID = 10086;

        private Context mContext;
        private NotificationManager mNManager;
        private Notification mNotification;
        private long mStartTime;

        Handler srHandler = new Handler();
        Runnable srRunnable = new Runnable(){
            @Override
            public void run(){
            	//改变时间发送通知
                
            	//mNotification.setLatestEventInfo(mContext, "Recording", getFormatDuration(), null);
            	
            	mBuilder.setContentText(getFormatDuration()+"");
            	
            	//Log.w("send", "......"+getFormatDuration());
             	 
                mNManager.notify(NOTIFICATION_TITLE, NOTIFICATION_ID, mBuilder.build());
                
                srHandler.postDelayed(srRunnable, 1000);
            }
        };

        public ScreenrecordNotification(Context context){
            mContext = context;
            mNManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            //新建通知
            //mNotification = new Notification(R.drawable.ic_qs_screen_record,"Recording",System.currentTimeMillis());
            
               
             
             mBuilder=new Builder(mContext) 
             .setContentTitle("Recording")    
             .setContentText("00:00:00")        
             .setSmallIcon(R.drawable.ic_qs_screen_record);
             
             mNotification=mBuilder.build();
             
         	
             mNotification.flags |= Notification.FLAG_NO_CLEAR;
        }

        public void notifyNotificaiton(){
        	//初始时间
            mStartTime = System.currentTimeMillis();
            
            //mNotification.setLatestEventInfo(mContext, "Recording", "00:00:00",null);
            
            mNManager.notify(NOTIFICATION_TITLE, NOTIFICATION_ID, mNotification);
            
            srHandler.postDelayed(srRunnable, 1000);
        }

        public void cancelNotificaiton(){
            srHandler.removeCallbacks(srRunnable);
            mNManager.cancel(NOTIFICATION_TITLE, NOTIFICATION_ID);
        }
        
        

        private String getFormatDuration(){
            long duration = (System.currentTimeMillis() - mStartTime) / 1000;
            long h = duration / 3600;
            long m = duration / 60 % 60;
            long s = duration % 60;
            String fs = String.format("%02d:%02d:%02d", h, m, s);
            return fs;
        }
    }
}
