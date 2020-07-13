/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link ITestInvocationListener} that forwards invocation results to a list of other listeners.
 */
public class ResultForwarder implements ITestInvocationListener {

    private List<ITestInvocationListener> mListeners;

    /**
     * Create a {@link ResultForwarder} with deferred listener setting.  Intended only for use by
     * subclasses.
     */
    protected ResultForwarder() {
        mListeners = Collections.emptyList();
    }

    /**
     * Create a {@link ResultForwarder}.
     *
     * @param listeners the real {@link ITestInvocationListener}s to forward results to
     */
    public ResultForwarder(List<ITestInvocationListener> listeners) {
        mListeners = listeners;
    }

    /**
     * Alternate variable arg constructor for {@link ResultForwarder}.
     *
     * @param listeners the real {@link ITestInvocationListener}s to forward results to
     */
    public ResultForwarder(ITestInvocationListener... listeners) {
        mListeners = Arrays.asList(listeners);
    }

    /**
     * Set the listeners after construction.  Intended only for use by subclasses.
     *
     * @param listeners the real {@link ITestInvocationListener}s to forward results to
     */
    protected void setListeners(List<ITestInvocationListener> listeners) {
        mListeners = listeners;
    }

    /**
     * Set the listeners after construction.  Intended only for use by subclasses.
     *
     * @param listeners the real {@link ITestInvocationListener}s to forward results to
     */
    protected void setListeners(ITestInvocationListener... listeners) {
        mListeners = Arrays.asList(listeners);
    }

    /**
     * Get the list of listeners.  Intended only for use by subclasses.
     *
     * @return The list of {@link ITestInvocationListener}s.
     */
    protected List<ITestInvocationListener> getListeners() {
        return mListeners;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.invocationStarted(buildInfo);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#invocationStarted",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.invocationFailed(cause);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#invocationFailed",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        InvocationSummaryHelper.reportInvocationEnded(mListeners, elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        // should never be called
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testLog(dataName, dataType, dataStream);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testLog",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String runName, int testCount) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testRunStarted(runName, testCount);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testRunStarted",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testRunFailed(errorMessage);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testRunFailed",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testRunStopped(elapsedTime);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testRunStopped",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testRunEnded(elapsedTime, runMetrics);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testRunEnded",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testStarted(test);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testStarted",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testFailed(test, trace);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testFailed",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testEnded(test, testMetrics);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testEnded",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testAssumptionFailure(test, trace);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testAssumptionFailure",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }

    @Override
    public void testIgnored(TestIdentifier test) {
        for (ITestInvocationListener listener : mListeners) {
            try {
                listener.testIgnored(test);
            } catch (RuntimeException e) {
                CLog.e("RuntimeException while invoking %s#testIgnored",
                        listener.getClass().getName());
                CLog.e(e);
            }
        }
    }
}
