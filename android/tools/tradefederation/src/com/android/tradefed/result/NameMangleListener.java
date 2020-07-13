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

package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;

import junit.framework.TestFailure;

import java.util.Map;

/**
 * A proxy listener to translate test method, class, and package names as results are reported.
 */
public abstract class NameMangleListener implements ITestInvocationListener {
    private final ITestInvocationListener mListener;

    public NameMangleListener(ITestInvocationListener listener) {
        if (listener == null) throw new NullPointerException();
        mListener = listener;
    }

    /**
     * This method is run on all {@link TestIdentifier}s that are passed to the
     * {@link #testStarted(TestIdentifier)},
     * {@link #testFailed(TestFailure, TestIdentifier, String)}, and
     * {@link #testEnded(TestIdentifier, Map)} callbacks.  The method should return a
     * possibly-different {@link TestIdentifier} that will be passed to the downstream
     * {@link ITestInvocationListener} that was specified during construction.
     * <p />
     * The implementation should be careful to not modify the original {@link TestIdentifier}.
     * <p />
     * The default implementation passes the incoming identifier through unmodified.
     */
    protected TestIdentifier mangleTestId(TestIdentifier test) {
        return test;
    }

    /**
     * This method is run on all test run names that are passed to the
     * {@link #testRunStarted(String, int)} callback.  The method should return a possibly-different
     * test run name that will be passed to the downstream {@link ITestInvocationListener} that was
     * specified during construction.
     * <p />
     * The implementation should be careful to not modify the original run name.
     * <p />
     * The default implementation passes the incoming test run name through unmodified.
     */
    protected String mangleTestRunName(String name) {
        return name;
    }

    /**
     * This method is run on all {@link IBuildInfo}s that are passed to the
     * {@link #invocationStarted(IBuildInfo)} callback.  The method should return a
     * possibly-different {@link IBuildInfo} that will be passed to the downstream
     * {@link ITestInvocationListener} that was specified during construction.
     * <p />
     * The implementation should be careful to not modify the original {@link IBuildInfo}.
     * <p />
     * The default implementation passes the incoming IBuildInfo through unmodified.
     */
    protected IBuildInfo mangleBuildInfo(IBuildInfo buildInfo) {
        return buildInfo;
    }


    // ITestRunListener methods
    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        final TestIdentifier mangledTestId = mangleTestId(test);
        mListener.testEnded(mangledTestId, testMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        final TestIdentifier mangledTestId = mangleTestId(test);
        mListener.testFailed(mangledTestId, trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        final TestIdentifier mangledTestId = mangleTestId(test);
        mListener.testAssumptionFailure(mangledTestId, trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        final TestIdentifier mangledTestId = mangleTestId(test);
        mListener.testIgnored(mangledTestId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        mListener.testRunEnded(elapsedTime, runMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        mListener.testRunFailed(errorMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String runName, int testCount) {
        final String mangledName = mangleTestRunName(runName);
        mListener.testRunStarted(mangledName, testCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        mListener.testRunStopped(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        final TestIdentifier mangledTestId = mangleTestId(test);
        mListener.testStarted(mangledTestId);
    }


    // ITestInvocationListener methods
    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        final IBuildInfo mangledBuildInfo = mangleBuildInfo(buildInfo);
        mListener.invocationStarted(mangledBuildInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        mListener.testLog(dataName, dataType, dataStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        mListener.invocationEnded(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        mListener.invocationFailed(cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        return mListener.getSummary();
    }
}

