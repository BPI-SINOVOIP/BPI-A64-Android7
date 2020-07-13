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

package android.dram.cts;

import android.content.Context;
import android.cts.util.CtsAndroidTestCase;
import android.graphics.Point;
import android.util.Log;
import android.view.WindowManager;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

/**
 * check how many screens the memcpy function can copy in a sec.
 * Note that this does not represent the total memory bandwidth available in the system
 * as typically CPU cannot use the whole bandwidth.
 * Smaller buffers can fit into L1 or L2 cache, which can show big boost.
 */
public class BandwidthTest extends CtsAndroidTestCase {
    private static final String TAG = BandwidthTest.class.getSimpleName();
    private static final String REPORT_LOG_NAME = "CtsDramTestCases";
    // data length is odd to prevent rare cases that all data are rejected.
    private static final int MEMCPY_REPETITION = 11;
    private static final int MEMSET_REPETITION = 31;
    private static final int REPEAT_IN_EACH_CALL = 100;
    private static final int KB = 1024;
    private static final int MB = 1024 * 1024;
    private static final int MEMSET_CHAR = 0xa5;
    // reject data outside +/- this value * median
    private static final double OUTLIER_THRESHOLD = 0.1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // warm-up
        MemoryNative.runMemcpy(2 * MB, 100);
    }

    public void testMemcpyK004() {
        doRunMemcpy(4 * KB);
    }

    public void testMemcpyK008() {
        doRunMemcpy(8 * KB);
    }

    public void testMemcpyK016() {
        doRunMemcpy(16 * KB);
    }

    public void testMemcpyK032() {
        doRunMemcpy(32 * KB);
    }

    public void testMemcpyK064() {
        doRunMemcpy(64 * KB);
    }

    public void testMemcpyK128() {
        doRunMemcpy(128 * KB);
    }

    public void testMemcpyK256() {
        doRunMemcpy(256 * KB);
    }

    public void testMemcpyK512() {
        doRunMemcpy(512 * KB);
    }

    public void testMemcpyM001() {
        doRunMemcpy(1 * MB);
    }

    public void testMemcpyM002() {
        doRunMemcpy(2 * MB);
    }

    public void testMemcpyM004() {
        doRunMemcpy(4 * MB);
    }

    public void testMemcpyM008() {
        doRunMemcpy(8 * MB);
    }

    public void testMemcpyM016() {
        doRunMemcpy(16 * MB);
    }

    public void testMemsetK004() {
        doRunMemset(4 * KB);
    }

    public void testMemsetK008() {
        doRunMemset(8 * KB);
    }

    public void testMemsetK016() {
        doRunMemset(16 * KB);
    }

    public void testMemsetK032() {
        doRunMemset(32 * KB);
    }

    public void testMemsetK064() {
        doRunMemset(64 * KB);
    }

    public void testMemsetK128() {
        doRunMemset(128 * KB);
    }

    public void testMemsetK256() {
        doRunMemset(256 * KB);
    }

    public void testMemsetK512() {
        doRunMemset(512 * KB);
    }

    public void testMemsetM001() {
        doRunMemset(1 * MB);
    }

    public void testMemsetM002() {
        doRunMemset(2 * MB);
    }

    public void testMemsetM004() {
        doRunMemset(4 * MB);
    }

    public void testMemsetM008() {
        doRunMemset(8 * MB);
    }

    public void testMemsetM016() {
        doRunMemset(16 * MB);
    }

    private void doRunMemcpy(int bufferSize) {
        double[] result = new double[MEMCPY_REPETITION];
        int repeatInEachCall = REPEAT_IN_EACH_CALL;
        if (bufferSize < (1 * MB)) {
            // too small buffer size finishes too early to give accurate result.
            repeatInEachCall *= (1 * MB / bufferSize);
        }
        for (int i = 0; i < MEMCPY_REPETITION; i++) {
            result[i] = MemoryNative.runMemcpy(bufferSize, repeatInEachCall);
        }
        String streamName = "run_memcpy";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("buffer_size", bufferSize, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValues("memcpy_time", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        double[] mbps = Stat.calcRatePerSecArray(
                (double)bufferSize * repeatInEachCall / 1024.0 / 1024.0, result);
        report.addValues("memcpy_throughput", mbps, ResultType.HIGHER_BETTER, ResultUnit.MBPS);
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(mbps, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);
        Log.i(TAG, " x " + size.x + " y " + size.y);
        double pixels = size.x * size.y;
        // now this represents how many times the whole screen can be copied in a sec.
        double screensPerSecAverage = stat.mAverage / pixels * 1024.0 * 1024.0 / 4.0;
        report.addValue("memcpy_in_fps", screensPerSecAverage, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        report.setSummary("memcpy_throughput_average", stat.mAverage, ResultType.HIGHER_BETTER,
                ResultUnit.MBPS);
        report.submit(getInstrumentation());
    }

    private void doRunMemset(int bufferSize) {
        double[] result = new double[MEMSET_REPETITION];
        int repeatInEachCall = REPEAT_IN_EACH_CALL;
        if (bufferSize < (1 * MB)) {
            // too small buffer size finishes too early to give accurate result.
            repeatInEachCall *= (1 * MB / bufferSize);
        }
        for (int i = 0; i < MEMSET_REPETITION; i++) {
            result[i] = MemoryNative.runMemset(bufferSize, repeatInEachCall, MEMSET_CHAR);
        }
        String streamName = "run_memset";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("buffer_size", bufferSize, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValues("memset_time", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        double[] mbps = Stat.calcRatePerSecArray(
                (double)bufferSize * repeatInEachCall / 1024.0 / 1024.0, result);
        report.addValues("memset_throughput", mbps, ResultType.HIGHER_BETTER, ResultUnit.MBPS);
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(mbps, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);
        Log.i(TAG, " x " + size.x + " y " + size.y);
        double pixels = size.x * size.y;
        // now this represents how many times the whole screen can be copied in a sec.
        double screensPerSecAverage = stat.mAverage / pixels * 1024.0 * 1024.0 / 4.0;
        report.addValue("memset_in_fps", screensPerSecAverage, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        report.setSummary("memset_throughput_average", stat.mAverage, ResultType.HIGHER_BETTER,
                ResultUnit.MBPS);
        report.submit(getInstrumentation());
    }
}
