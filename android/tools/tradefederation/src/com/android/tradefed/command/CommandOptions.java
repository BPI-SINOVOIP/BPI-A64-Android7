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
package com.android.tradefed.command;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.config.OptionUpdateRule;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Implementation of {@link ICommandOptions}.
 */
public class CommandOptions implements ICommandOptions {

    @Option(name = "help", description =
        "display the help text for the most important/critical options.",
        importance = Importance.ALWAYS)
    private boolean mHelpMode = false;

    @Option(name = "help-all", description = "display the full help text for all options.",
            importance = Importance.ALWAYS)
    private boolean mFullHelpMode = false;

    @Option(name = "json-help", description = "display the full help in json format.")
    private boolean mJsonHelpMode = false;

    @Option(name = "dry-run",
            description = "build but don't actually run the command.  Intended as a quick check " +
                    "to ensure that a command is runnable.",
            importance = Importance.ALWAYS)
    private boolean mDryRunMode = false;

    @Option(name = "noisy-dry-run",
            description = "build but don't actually run the command.  This version prints the " +
                    "command to the console.  Intended for cmdfile debugging.",
            importance = Importance.ALWAYS)
    private boolean mNoisyDryRunMode = false;

    @Option(name = "min-loop-time", description =
            "the minimum invocation time in ms when in loop mode.")
    private Long mMinLoopTime = 10L * 60L * 1000L;

    @Option(name = "max-random-loop-time", description =
            "the maximum time to wait between invocation attempts when in loop mode. " +
            "when set, the actual value will be a random number between min-loop-time and this " +
            "number.",
            updateRule = OptionUpdateRule.LEAST)
    private Long mMaxRandomLoopTime = null;

    @Option(name = "loop", description = "keep running continuously.",
            importance = Importance.ALWAYS)
    private boolean mLoopMode = false;

    @Option(name = "all-devices", description =
            "fork this command to run on all connected devices.")
    private boolean mAllDevices = false;

    @Option(name = "bugreport-on-invocation-ended", description =
            "take a bugreport when the test invocation has ended")
    private boolean mTakeBugreportOnInvocationEnded = false;

    /**
     * Set the help mode for the config.
     * <p/>
     * Exposed for testing.
     */
    void setHelpMode(boolean helpMode) {
        mHelpMode = helpMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHelpMode() {
        return mHelpMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFullHelpMode() {
        return mFullHelpMode;
    }

    /**
     * Set the json help mode for the config.
     * <p/>
     * Exposed for testing.
     */
    void setJsonHelpMode(boolean jsonHelpMode) {
        mJsonHelpMode = jsonHelpMode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isJsonHelpMode() {
        return mJsonHelpMode;
    }

    /**
     * Set the dry run mode for the config.
     * <p/>
     * Exposed for testing.
     */
    void setDryRunMode(boolean dryRunMode) {
        mDryRunMode = dryRunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDryRunMode() {
        return mDryRunMode || mNoisyDryRunMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNoisyDryRunMode() {
        return mNoisyDryRunMode;
    }

    /**
     * Set the loop mode for the config.
     */
    @Override
    public void setLoopMode(boolean loopMode) {
        mLoopMode = loopMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLoopMode() {
        return mLoopMode;
    }

    /**
     * Set the min loop time for the config.
     * <p/>
     * Exposed for testing.
     */
    void setMinLoopTime(long loopTime) {
        mMinLoopTime = loopTime;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public long getMinLoopTime() {
        return mMinLoopTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLoopTime() {
        if (mMaxRandomLoopTime != null) {
            long randomizedValue = mMaxRandomLoopTime - mMinLoopTime;
            if (randomizedValue > 0) {
                return mMinLoopTime + Math.round(randomizedValue * Math.random());
            } else {
                CLog.e("max loop time %d is less than min loop time %d", mMaxRandomLoopTime,
                        mMinLoopTime);
            }
        }
        return mMinLoopTime;
    }


    @Override
    public ICommandOptions clone() {
        CommandOptions clone = new CommandOptions();
        try {
            OptionCopier.copyOptions(this, clone);
        } catch (ConfigurationException e) {
            CLog.e("failed to clone command options: %s", e.getMessage());
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean runOnAllDevices() {
        return mAllDevices;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean takeBugreportOnInvocationEnded() {
        return mTakeBugreportOnInvocationEnded;
    }
}
