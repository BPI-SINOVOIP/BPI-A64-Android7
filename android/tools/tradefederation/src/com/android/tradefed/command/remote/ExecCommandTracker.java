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
package com.android.tradefed.command.remote;

import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.StubTestInvocationListener;

import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

class ExecCommandTracker extends StubTestInvocationListener implements
        IScheduledInvocationListener {

    private CommandResult.Status mStatus = CommandResult.Status.EXECUTING;
    private String mErrorDetails = null;
    private FreeDeviceState mState = null;
    Map<String, String> mRunMetrics = new HashMap<String, String>();

    @Override
    public void invocationFailed(Throwable cause) {
        // TODO: replace with StreamUtil.getStackTrace
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream bytePrintStream = new PrintStream(outputStream);
        cause.printStackTrace(bytePrintStream);
        mErrorDetails = outputStream.toString();
    }

    @Override
    public void invocationComplete(ITestDevice device, FreeDeviceState deviceState) {
        mState = deviceState;
        if (mErrorDetails != null) {
            mStatus = CommandResult.Status.INVOCATION_ERROR;
        } else {
            mStatus = CommandResult.Status.INVOCATION_SUCCESS;
        }
    }

    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        mRunMetrics.putAll(runMetrics);
    }

    /**
     * Returns the current state as a {@link CommandResult}.
     * @return
     */
    CommandResult getCommandResult() {
        return new CommandResult(mStatus, mErrorDetails, mState,
                new ImmutableMap.Builder<String, String>()
                    .putAll(mRunMetrics).build());
    }
}
