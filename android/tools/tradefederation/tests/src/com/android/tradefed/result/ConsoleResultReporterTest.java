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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConsoleResultReporter}
 */
public class ConsoleResultReporterTest extends TestCase {
    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    /**
     * Test the results printed for an empty invocation
     */
    public void testGetInvocationSummary_empty() {
        ConsoleResultReporter reporter = new ConsoleResultReporter();
        reporter.invocationStarted(null);
        reporter.invocationEnded(0);
        assertEquals("No test results\n", reporter.getInvocationSummary());
    }

    /**
     * Test that run metrics are sorted by key
     */
    public void testGetInvocationSummary_test_run_metrics() {
        ConsoleResultReporter reporter = new ConsoleResultReporter();
        reporter.invocationStarted(null);
        reporter.testRunStarted("Test Run", 0);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key2", "value2");
        metrics.put("key1", "value1");
        reporter.testRunEnded(0, metrics);
        reporter.invocationEnded(0);
        assertEquals(
                "Test results:\n" +
                "Test Run:\n" +
                "  key1: value1\n" +
                "  key2: value2\n",
                reporter.getInvocationSummary());
    }

    /**
     * Test that test metrics are sorted by key
     */
    public void testGetInvocationSummary_test_metrics() {
        ConsoleResultReporter reporter = new ConsoleResultReporter();
        reporter.invocationStarted(null);
        reporter.testRunStarted("Test Run", 1);
        TestIdentifier testId = new TestIdentifier("class", "method");
        reporter.testStarted(testId);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key2", "value2");
        metrics.put("key1", "value1");
        reporter.testEnded(testId, metrics);
        reporter.testRunEnded(0, EMPTY_MAP);
        reporter.invocationEnded(0);
        assertEquals(
                "Test results:\n" +
                "Test Run: 1 Test, 1 Passed, 0 Failed, 0 Ignored\n" +
                "  class#method: PASSED (0ms)\n" +
                "    key1: value1\n" +
                "    key2: value2\n",
                reporter.getInvocationSummary());
    }

    /**
     * Test that logs are printed, favoring url.
     */
    public void testGetInvocationSummary_logs() {
        ConsoleResultReporter reporter = new ConsoleResultReporter();
        reporter.invocationStarted(null);
        reporter.testLogSaved(null, null, null, new LogFile("/path/to/log1", "http://log1"));
        reporter.testLogSaved(null, null, null, new LogFile("/path/to/log2", null));
        reporter.invocationEnded(0);
        assertEquals(
                "Test results:\n" +
                "Log Files:\n" +
                "  http://log1\n" +
                "  /path/to/log2\n",
                reporter.getInvocationSummary());
    }

    /**
     * Inclusive test to test that all test runs are printed, and metrics and logs are printed with
     * it.
     */
    public void testGetInvocationSummary_all() {
        ConsoleResultReporter reporter = new ConsoleResultReporter();
        reporter.invocationStarted(null);

        reporter.testRunStarted("Test Run 1", 3);

        TestIdentifier run1test1Id = new TestIdentifier("class1", "method1");
        reporter.testStarted(run1test1Id);
        reporter.testFailed(run1test1Id, "trace");
        Map<String, String> run1Test1Metrics = new HashMap<>();
        run1Test1Metrics.put("run1_test1_key1", "run1_test1_value1");
        run1Test1Metrics.put("run1_test1_key2", "run1_test1_value2");
        reporter.testEnded(run1test1Id, run1Test1Metrics);

        TestIdentifier run1test2Id = new TestIdentifier("class1", "method2");
        reporter.testStarted(run1test2Id);
        Map<String, String> run1Test2Metrics = new HashMap<>();
        run1Test2Metrics.put("run1_test2_key1", "run1_test2_value1");
        run1Test2Metrics.put("run1_test2_key2", "run1_test2_value2");
        reporter.testEnded(run1test2Id, run1Test2Metrics);

        TestIdentifier run1test3Id = new TestIdentifier("class1", "method3");
        reporter.testStarted(run1test3Id);
        reporter.testAssumptionFailure(run1test3Id, "trace");
        Map<String, String> run1Test3Metrics = new HashMap<>();
        run1Test3Metrics.put("run1_test3_key1", "run1_test3_value1");
        run1Test3Metrics.put("run1_test3_key2", "run1_test3_value2");
        reporter.testEnded(run1test3Id, run1Test3Metrics);

        TestIdentifier run1test4Id = new TestIdentifier("class1", "method4");
        reporter.testStarted(run1test4Id);
        reporter.testIgnored(run1test4Id);
        reporter.testEnded(run1test4Id, EMPTY_MAP);

        Map<String, String> run1Metrics = new HashMap<>();
        run1Metrics.put("run1_key1", "run1_value2");
        run1Metrics.put("run1_key2", "run1_value1");
        reporter.testRunEnded(0, run1Metrics);

        reporter.testRunStarted("Test Run 2", 4);
        TestIdentifier run2test1Id = new TestIdentifier("class2", "method1");
        reporter.testStarted(run2test1Id);
        reporter.testFailed(run2test1Id, "trace");
        reporter.testEnded(run2test1Id, EMPTY_MAP);

        TestIdentifier run2test2Id = new TestIdentifier("class2", "method2");
        reporter.testStarted(run2test2Id);
        reporter.testEnded(run2test2Id, EMPTY_MAP);

        TestIdentifier run2test3Id = new TestIdentifier("class2", "method3");
        reporter.testStarted(run2test3Id);
        reporter.testAssumptionFailure(run2test3Id, "trace");
        reporter.testEnded(run2test3Id, EMPTY_MAP);

        TestIdentifier run2test4Id = new TestIdentifier("class2", "method4");
        reporter.testStarted(run2test4Id);
        reporter.testIgnored(run2test4Id);
        reporter.testEnded(run2test4Id, EMPTY_MAP);
        reporter.testRunEnded(0, EMPTY_MAP);

        reporter.testRunStarted("Test Run 3", 0);
        Map<String, String> run3Metrics = new HashMap<>();
        run3Metrics.put("run3_key1", "run3_value1");
        run3Metrics.put("run3_key2", "run3_value2");
        reporter.testRunEnded(0, run3Metrics);

        reporter.testRunStarted("Test Run 4", 0);
        reporter.testRunEnded(0, EMPTY_MAP);

        reporter.testLogSaved(null, null, null, new LogFile("/path/to/log1", "http://log1"));
        reporter.testLogSaved(null, null, null, new LogFile("/path/to/log2", null));
        reporter.invocationEnded(0);
        assertEquals(
                "Test results:\n" +
                "Test Run 1: 4 Tests, 1 Passed, 1 Failed, 1 Ignored\n" +
                "  class1#method1: FAILURE (0ms)\n" +
                "  stack=\n" +
                "    trace\n" +
                "    run1_test1_key1: run1_test1_value1\n" +
                "    run1_test1_key2: run1_test1_value2\n" +
                "  class1#method2: PASSED (0ms)\n" +
                "    run1_test2_key1: run1_test2_value1\n" +
                "    run1_test2_key2: run1_test2_value2\n" +
                "  class1#method3: ASSUMPTION_FAILURE (0ms)\n" +
                "  stack=\n" +
                "    trace\n" +
                "    run1_test3_key1: run1_test3_value1\n" +
                "    run1_test3_key2: run1_test3_value2\n" +
                "  class1#method4: IGNORED (0ms)\n" +
                "  run1_key1: run1_value2\n" +
                "  run1_key2: run1_value1\n" +
                "\n" +
                "Test Run 2: 4 Tests, 1 Passed, 1 Failed, 1 Ignored\n" +
                "  class2#method1: FAILURE (0ms)\n" +
                "  stack=\n" +
                "    trace\n" +
                "  class2#method2: PASSED (0ms)\n" +
                "  class2#method3: ASSUMPTION_FAILURE (0ms)\n" +
                "  stack=\n" +
                "    trace\n" +
                "  class2#method4: IGNORED (0ms)\n" +
                "\n" +
                "Test Run 3:\n" +
                "  run3_key1: run3_value1\n" +
                "  run3_key2: run3_value2\n" +
                "\n" +
                "Test Run 4: No results\n" +
                "\n" +
                "Log Files:\n" +
                "  http://log1\n" +
                "  /path/to/log2\n",
                reporter.getInvocationSummary());
    }
}
