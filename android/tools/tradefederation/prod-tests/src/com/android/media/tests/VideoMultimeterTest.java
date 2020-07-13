/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.media.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A harness that test video playback and reports result.
 */
public class VideoMultimeterTest implements IDeviceTest, IRemoteTest {

    static final String RUN_KEY = "video_multimeter";

    @Option(name = "multimeter-util-path", description = "path for multimeter control util",
            importance = Importance.ALWAYS)
    String mMeterUtilPath = "/tmp/util.sh";

    static final String START_VIDEO_PLAYER = "am start"
            + " -a android.intent.action.VIEW -t video/mp4 -d \"file://%s\""
            + " -n \"com.google.android.apps.photos/.viewintents.ViewIntentHandlerActivity\"";
    static final String KILL_VIDEO_PLAYER = "am force-stop com.google.android.apps.photos";
    static final String ROTATE_LANDSCAPE = "content insert --uri content://settings/system"
            + " --bind name:s:user_rotation --bind value:i:1";

    static final String VIDEO_DIR = "/sdcard/DCIM/Camera/";

    static final String CALI_VIDEO_DEVICE_PATH = VIDEO_DIR + "video_cali.mp4";

    // FIXIT: move video path and info to options for flexibility
    static final String TEST_VIDEO_1_DEVICE_PATH = VIDEO_DIR + "video.mp4";
    static final String TEST_VIDEO_1_PREFIX = "24fps_";
    static final float TEST_VIDEO_1_FPS = 24;
    static final long TEST_VIDEO_1_DURATION = 11 * 60; // in second

    static final String TEST_VIDEO_2_DEVICE_PATH = VIDEO_DIR + "video2.mp4";
    static final String TEST_VIDEO_2_PREFIX = "60fps_";
    static final float TEST_VIDEO_2_FPS = 60;
    static final long TEST_VIDEO_2_DURATION = 5 * 60; // in second

    // Max number of trailing frames to trim
    static final int TRAILING_FRAMES_MAX = 3;
    // Min threshold for duration of trailing frames
    static final long FRAME_DURATION_THRESHOLD_US = 500 * 1000; // 0.5s

    static final String CMD_GET_FRAMERATE_STATE = "GETF";
    static final String CMD_START_CALIBRATION = "STAC";
    static final String CMD_SET_CALIBRATION_VALS = "SETCAL";
    static final String CMD_STOP_CALIBRATION = "STOC";
    static final String CMD_START_MEASUREMENT = "STAM";
    static final String CMD_STOP_MEASUREMENT = "STOM";
    static final String CMD_GET_NUM_FRAMES = "GETN";
    static final String CMD_GET_ALL_DATA = "GETD";

    static final long DEVICE_SYNC_TIME_MS = 30 * 1000;
    static final long CALIBRATION_TIMEOUT_MS = 30 * 1000;
    static final long COMMAND_TIMEOUT_MS = 5 * 1000;
    static final long GETDATA_TIMEOUT_MS = 10 * 60 * 1000;

    ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    private void rotateScreen() throws DeviceNotAvailableException {
        // rotate to landscape mode, except for manta
        if (!getDevice().getProductType().contains("manta")) {
            getDevice().executeShellCommand(ROTATE_LANDSCAPE);
        }
    }

    protected boolean setupTestEnv() throws DeviceNotAvailableException {
        return setupTestEnv(null);
    }

    /**
     * Perform calibration process for video multimeter
     *
     * @return boolean whether calibration succeeds
     * @throws DeviceNotAvailableException
     */
    protected boolean doCalibration() throws DeviceNotAvailableException {
        // play calibration video
        getDevice().executeShellCommand(String.format(
                START_VIDEO_PLAYER, CALI_VIDEO_DEVICE_PATH));
        getRunUtil().sleep(3 * 1000);
        rotateScreen();
        getRunUtil().sleep(1 * 1000);
        CommandResult cr = getRunUtil().runTimedCmd(
                COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_START_CALIBRATION);
        CLog.i("Starting calibration: " + cr.getStdout());
        // check whether multimeter is calibrated
        boolean isCalibrated = false;
        long calibrationStartTime = System.currentTimeMillis();
        while (!isCalibrated &&
                System.currentTimeMillis() - calibrationStartTime <= CALIBRATION_TIMEOUT_MS) {
            getRunUtil().sleep(1 * 1000);
            cr = getRunUtil().runTimedCmd(2 * 1000, mMeterUtilPath, CMD_GET_FRAMERATE_STATE);
            if (cr.getStdout().contains("calib0")) {
                isCalibrated = true;
            }
        }
        if (!isCalibrated) {
            // stop calibration if time out
            cr = getRunUtil().runTimedCmd(
                        COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_STOP_CALIBRATION);
            CLog.e("Calibration timed out.");
        } else {
            CLog.i("Calibration succeeds.");
        }
        getDevice().executeShellCommand(KILL_VIDEO_PLAYER);
        return isCalibrated;
    }

    protected boolean setupTestEnv(String caliValues) throws DeviceNotAvailableException {
        getRunUtil().sleep(DEVICE_SYNC_TIME_MS);
        CommandResult cr = getRunUtil().runTimedCmd(
                COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_STOP_MEASUREMENT);

        getDevice().setDate(new Date());
        CLog.i("syncing device time to host time");
        getRunUtil().sleep(3 * 1000);

        // TODO: need a better way to clear old data
        // start and stop to clear old data
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_START_MEASUREMENT);
        getRunUtil().sleep(3 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_STOP_MEASUREMENT);
        getRunUtil().sleep(3 * 1000);
        CLog.i("Stopping measurement: " + cr.getStdout());
        getDevice().unlockDevice();
        getRunUtil().sleep(3 * 1000);

        if (caliValues == null) {
            return doCalibration();
        } else {
            CLog.i("Setting calibration values: " + caliValues);
            cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath,
                    CMD_SET_CALIBRATION_VALS + " " +  caliValues);
            if (cr.getStdout().contains("OK")) {
                CLog.i("Calibration values are set to: " + caliValues);
                return true;
            } else {
                CLog.e("Failed to set calibration values: " + cr.getStdout());
                return false;
            }
        }
    }

    private void doMeasurement(String testVideoPath, long durationSecond)
            throws DeviceNotAvailableException {
        CommandResult cr;
        getDevice().clearErrorDialogs();
        getDevice().unlockDevice();

        // play test video
        getDevice().executeShellCommand(String.format(START_VIDEO_PLAYER, testVideoPath));
        getRunUtil().sleep(3 * 1000);

        rotateScreen();
        getRunUtil().sleep(1 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_START_MEASUREMENT);
        CLog.i("Starting measurement: " + cr.getStdout());

        // end measurement
        getRunUtil().sleep(durationSecond * 1000);

        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_STOP_MEASUREMENT);
        CLog.i("Stopping measurement: " + cr.getStdout());
        if (cr == null || !cr.getStdout().contains("OK")) {
            cr = getRunUtil().runTimedCmd(
                    COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_STOP_MEASUREMENT);
            CLog.i("Retry - Stopping measurement: " + cr.getStdout());
        }

        getDevice().executeShellCommand(KILL_VIDEO_PLAYER);
        getDevice().clearErrorDialogs();
    }

    private Map<String, String> getResult(ITestInvocationListener listener,
            Map<String, String> metrics, String keyprefix, float fps, boolean lipsync) {
        CommandResult cr;

        // get number of results
        getRunUtil().sleep(5 * 1000);
        cr = getRunUtil().runTimedCmd(COMMAND_TIMEOUT_MS, mMeterUtilPath, CMD_GET_NUM_FRAMES);
        String frameNum = cr.getStdout();
        CLog.i("Number of results: " + frameNum);

        // get all results and write to output file
        cr = getRunUtil().runTimedCmd(GETDATA_TIMEOUT_MS, mMeterUtilPath, CMD_GET_ALL_DATA);
        String allData = cr.getStdout();
        listener.testLog(keyprefix, LogDataType.TEXT, new SnapshotInputStreamSource(
                new ByteArrayInputStream(allData.getBytes())));

        // parse results
        return parseResult(metrics, frameNum, allData, keyprefix, fps, lipsync);
    }

    protected void runMultimeterTest(ITestInvocationListener listener,
            Map<String,String> metrics) throws DeviceNotAvailableException {
        doMeasurement(TEST_VIDEO_1_DEVICE_PATH, TEST_VIDEO_1_DURATION);
        metrics = getResult(listener, metrics, TEST_VIDEO_1_PREFIX, TEST_VIDEO_1_FPS, true);

        doMeasurement(TEST_VIDEO_2_DEVICE_PATH, TEST_VIDEO_2_DURATION);
        metrics = getResult(listener, metrics, TEST_VIDEO_2_PREFIX, TEST_VIDEO_2_FPS, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass()
                .getCanonicalName(), RUN_KEY);

        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();

        if (setupTestEnv()) {
            runMultimeterTest(listener, metrics);
        }

        long durationMs = System.currentTimeMillis() - testStartTime;
        listener.testEnded(testId, metrics);
        listener.testRunEnded(durationMs, metrics);
    }

    /**
     * Parse Multimeter result.
     *
     * @param result
     * @return a {@link HashMap} that contains metrics keys and results
     */
    private Map<String, String> parseResult(Map<String, String> metrics,
            String numFrames, String result, String keyprefix, float fps,
            boolean lipsync) {
        final int MISSING_FRAME_CEILING = 5; //5+ frames missing count the same
        final double[] MISSING_FRAME_WEIGHT = {0.0, 1.0, 2.5, 5.0, 6.25, 8.0};

        CLog.i("== Video Multimeter Result '%s' ==", keyprefix);
        Pattern p = Pattern.compile("OK\\s+(\\d+)$");
        Matcher m = p.matcher(numFrames.trim());
        String frameCapturedStr = "0";
        long frameCaptured = 0;
        if (m.matches()) {
            frameCapturedStr = m.group(1);
            metrics.put(keyprefix + "frame_captured", frameCapturedStr);
            CLog.i("Captured frames: " + frameCapturedStr);
            frameCaptured = Long.parseLong(frameCapturedStr);
            if (frameCaptured == 0) {
                // no frame captured
                CLog.w("No frame captured for " + keyprefix);
                return metrics;
            }
        } else {
            CLog.i("Cannot parse result for " + keyprefix);
            return metrics;
        }

        // Get total captured frames and calculate smoothness and freezing score
        // format: "OK (time); (frame duration); (marker color); (total dropped frames)"
        p = Pattern.compile("OK\\s+\\d+;\\s*(-?\\d+);\\s*[a-z]+;\\s*(\\d+)");
        String[] lines = result.split(System.getProperty("line.separator"));
        String totalDropFrame = "-1";
        String lastDropFrame = "0";
        long frameCount = 0;
        long consecutiveDropFrame = 0;
        double freezingPenalty = 0.0;
        long frameDuration = 0;
        double offByOne = 0;
        double offByMultiple = 0;
        double expectedFrameDurationInUs = 1000000.0 / fps;
        for (int i = 0; i < lines.length; i++) {
            m = p.matcher(lines[i].trim());
            if (m.matches()) {
                frameDuration = Long.parseLong(m.group(1));
                // frameDuration = -1 indicates dropped frame
                if (frameDuration > 0) {
                    frameCount++;
                }
                totalDropFrame = m.group(2);
                // trim the last few data points if needed
                if (frameCount >= frameCaptured - TRAILING_FRAMES_MAX - 1 &&
                        frameDuration > FRAME_DURATION_THRESHOLD_US) {
                    metrics.put(keyprefix + "frame_captured", String.valueOf(frameCount));
                    break;
                }
                if (lastDropFrame.equals(totalDropFrame)) {
                    if (consecutiveDropFrame > 0) {
                      freezingPenalty += MISSING_FRAME_WEIGHT[(int) (Math.min(consecutiveDropFrame,
                              MISSING_FRAME_CEILING))] * consecutiveDropFrame;
                      consecutiveDropFrame = 0;
                    }
                } else {
                    consecutiveDropFrame++;
                }
                lastDropFrame = totalDropFrame;

                if (frameDuration < expectedFrameDurationInUs * 0.5) {
                    offByOne++;
                } else if (frameDuration > expectedFrameDurationInUs * 1.5) {
                    if (frameDuration < expectedFrameDurationInUs * 2.5) {
                        offByOne++;
                    } else {
                        offByMultiple++;
                    }
                }
            }
        }
        if (totalDropFrame.equals("-1")) {
            // no matching result found
            CLog.w("No result found for " + keyprefix);
            return metrics;
        } else {
            metrics.put(keyprefix + "frame_drop", totalDropFrame);
            CLog.i("Dropped frames: " + totalDropFrame);
        }
        double smoothnessScore = 100.0 - (offByOne / frameCaptured) * 100.0 -
                (offByMultiple / frameCaptured) * 300.0;
        metrics.put(keyprefix + "smoothness", String.valueOf(smoothnessScore));
        CLog.i("Off by one frame: " + offByOne);
        CLog.i("Off by multiple frames: " + offByMultiple);
        CLog.i("Smoothness score: " + smoothnessScore);

        double freezingScore = 100.0 - 100.0 * freezingPenalty / frameCaptured;
        metrics.put(keyprefix + "freezing", String.valueOf(freezingScore));
        CLog.i("Freezing score: " + freezingScore);

        // parse lipsync results (the audio and video synchronization offset)
        // format: "OK (time); (frame duration); (marker color); (total dropped frames); (lipsync)"
        p = Pattern.compile("OK\\s+\\d+;\\s*\\d+;\\s*[a-z]+;\\s*\\d+;\\s*(-?\\d+)");
        if (lipsync) {
            ArrayList<Integer> lipsyncVals = new ArrayList<Integer>();
            StringBuilder lipsyncValsStr = new StringBuilder("[");
            long lipsyncSum = 0;
            for (int i = 0; i < lines.length; i++) {
                m = p.matcher(lines[i].trim());
                if (m.matches()) {
                    int lipSyncVal = Integer.parseInt(m.group(1));
                    lipsyncVals.add(lipSyncVal);
                    lipsyncValsStr.append(lipSyncVal);
                    lipsyncValsStr.append(", ");
                    lipsyncSum += lipSyncVal;
                }
            }
            if (lipsyncVals.size() > 0) {
                lipsyncValsStr.append("]");
                CLog.i("Lipsync values: " + lipsyncValsStr);
                Collections.sort(lipsyncVals);
                int lipsyncCount = lipsyncVals.size();
                int minLipsync = lipsyncVals.get(0);
                int maxLipsync = lipsyncVals.get(lipsyncCount - 1);
                metrics.put(keyprefix + "lipsync_count", String.valueOf(lipsyncCount));
                CLog.i("Lipsync Count: " + lipsyncCount);
                metrics.put(keyprefix + "lipsync_min", String.valueOf(lipsyncVals.get(0)));
                CLog.i("Lipsync Min: " + minLipsync);
                metrics.put(keyprefix + "lipsync_max", String.valueOf(maxLipsync));
                CLog.i("Lipsync Max: " + maxLipsync);
                double meanLipSync = (double) lipsyncSum / lipsyncCount;
                metrics.put(keyprefix + "lipsync_mean", String.valueOf(meanLipSync));
                CLog.i("Lipsync Mean: " + meanLipSync);
            } else {
                CLog.w("Lipsync value not found in result.");
            }
        }
        CLog.i("== End ==", keyprefix);
        return metrics;
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
