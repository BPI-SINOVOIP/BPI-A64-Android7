/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.taskswitching.control.cts;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.cts.util.CtsAndroidTestCase;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

/**
 * Device test which actually launches two apps sequentially and
 * measure time for switching.
 * Completion of launch is notified via broadcast.
 */
public class TaskSwitchingDeviceTest extends CtsAndroidTestCase {
    private static final String REPORT_LOG_NAME = "CtsUiHostTestCases";
    private static final String PKG_A = "android.taskswitching.appa";
    private static final String PKG_B = "android.taskswitching.appb";
    private static final String ACTIVITY_A = "AppAActivity";
    private static final String ACTIVITY_B = "AppBActivity";
    private static final long TASK_SWITCHING_WAIT_TIME = 5;
    private final AppBroadcastReceiver mReceiverA = new AppBroadcastReceiver();
    private final AppBroadcastReceiver mReceiverB = new AppBroadcastReceiver();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getContext().registerReceiver(mReceiverA, new IntentFilter(PKG_A));
        getContext().registerReceiver(mReceiverB, new IntentFilter(PKG_B));

        startActivity(PKG_A, ACTIVITY_A);
        assertTrue(mReceiverA.waitForBroadcast(TASK_SWITCHING_WAIT_TIME));

        startActivity(PKG_B, ACTIVITY_B);
        assertTrue(mReceiverB.waitForBroadcast(TASK_SWITCHING_WAIT_TIME));
    }

    @Override
    protected void tearDown() throws Exception {
        getContext().unregisterReceiver(mReceiverA);
        getContext().unregisterReceiver(mReceiverB);
        super.tearDown();
    }

    public void testMeasureTaskSwitching() throws Exception {
        final int NUMBER_REPEAT = 10;
        final int SWITCHING_PER_ONE_TRY = 10;

        double[] results = MeasureTime.measure(NUMBER_REPEAT, new MeasureRun() {

            @Override
            public void run(int i) throws Exception {
                for (int j = 0; j < SWITCHING_PER_ONE_TRY; j++) {
                    startActivity(PKG_A, ACTIVITY_A);
                    assertTrue(mReceiverA.waitForBroadcast(TASK_SWITCHING_WAIT_TIME));
                    startActivity(PKG_B, ACTIVITY_B);
                    assertTrue(mReceiverB.waitForBroadcast(TASK_SWITCHING_WAIT_TIME));
                }
            }
        });
        String streamName = "test_measure_task_switching";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValues("task_switching_time", results, ResultType.LOWER_BETTER, ResultUnit.MS);
        Stat.StatResult stat = Stat.getStat(results);
        report.setSummary("task_switching_time_average", stat.mAverage, ResultType.LOWER_BETTER,
                ResultUnit.MS);
        report.submit(getInstrumentation());
    }

    private void startActivity(String packageName, String activityName) {
        Context context = getContext();
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(packageName, packageName + "." + activityName));
        context.startActivity(intent);
    }

    class AppBroadcastReceiver extends BroadcastReceiver {
        private final Semaphore mSemaphore = new Semaphore(0);

        public boolean waitForBroadcast(long timeoutInSec) throws InterruptedException {
            return mSemaphore.tryAcquire(timeoutInSec, TimeUnit.SECONDS);
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            mSemaphore.release();
        }
    }
}
