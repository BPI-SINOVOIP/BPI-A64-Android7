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

package com.android.media.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camera app startup test
 *
 * Runs CameraActivityTest to measure Camera startup time and reports the metrics.
 */
@OptionClass(alias = "camera-startup")
public class CameraStartupTest extends CameraTestBase {

    private static final Pattern STATS_REGEX = Pattern.compile(
        "^(?<coldStartup>[0-9.]+)\\|(?<warmStartup>[0-9.]+)\\|(?<values>[0-9 .-]+)");
    private static final String PREFIX_COLD_STARTUP = "Cold";

    public CameraStartupTest() {
        setTestPackage("com.google.android.camera");
        setTestClass("com.android.camera.latency.CameraStartupTest");
        setTestRunner("android.test.InstrumentationTestRunner");
        setRuKey("CameraAppStartup");
        setTestTimeoutMs(60 * 60 * 1000);   // 1 hour
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        runInstrumentationTest(listener, new CollectingListener(listener));
    }

    /**
     * A listener to collect the output from test run and fatal errors
     */
    private class CollectingListener extends DefaultCollectingListener {

        public CollectingListener(ITestInvocationListener listener) {
            super(listener);
        }

        @Override
        public void handleMetricsOnTestEnded(TestIdentifier test, Map<String, String> testMetrics) {
            // Test metrics accumulated will be posted at the end of test run.
            getAggregatedMetrics().putAll(parseResults(testMetrics));
        }

        public Map<String, String> parseResults(Map<String, String> testMetrics) {
            // Parse activity time stats from the instrumentation result.
            // Format : <metric_key>=<cold_startup>|<average_of_warm_startups>|<all_startups>
            // Example:
            // VideoStartupTimeMs=1098|1184.6|1098 1222 ... 788
            // VideoOnCreateTimeMs=138|103.3|138 114 ... 114
            // VideoOnResumeTimeMs=39|40.4|39 36 ... 41
            // VideoFirstPreviewFrameTimeMs=0|0.0|0 0 ... 0
            // CameraStartupTimeMs=2388|1045.4|2388 1109 ... 746
            // CameraOnCreateTimeMs=574|122.7|574 124 ... 109
            // CameraOnResumeTimeMs=610|504.6|610 543 ... 278
            // CameraFirstPreviewFrameTimeMs=0|0.0|0 0 ... 0
            //
            // Then report only the first two startup time of cold startup and average warm startup.
            Map<String, String> parsed = new HashMap<String, String>();
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                Matcher matcher = STATS_REGEX.matcher(metric.getValue());
                if (matcher.matches()) {
                    String keyName = metric.getKey();
                    parsed.put(PREFIX_COLD_STARTUP + keyName, matcher.group("coldStartup"));
                    parsed.put(keyName, matcher.group("warmStartup"));
                } else {
                    CLog.w(String.format("Stats not in correct format: %s", metric.getValue()));
                }
            }
            return parsed;
        }
    }
}
