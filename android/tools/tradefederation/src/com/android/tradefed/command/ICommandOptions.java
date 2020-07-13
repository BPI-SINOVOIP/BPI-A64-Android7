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

/**
 *  Container for execution options for commands.
 */
public interface ICommandOptions {

    /**
     * Returns <code>true</code> if abbreviated help mode has been requested
     */
    public boolean isHelpMode();

    /**
     * Returns <code>true</code> if full detailed help mode has been requested
     */
    public boolean isFullHelpMode();

    /**
     * Returns <code>true</code> if full json help mode has been requested
     */
    public boolean isJsonHelpMode();

    /**
     * Return <code>true</code> if we should <emph>skip</emph> adding this command to the queue.
     */
    public boolean isDryRunMode();

    /**
     * Return <code>true</code> if we should print the command out to the console before we
     * <emph>skip</emph> adding it to the queue.
     */
    public boolean isNoisyDryRunMode();

    /**
     * Return the loop mode for the config.
     */
    public boolean isLoopMode();

    /**
     * Get the min loop time for the config.
     *
     * @deprecated use {@link #getLoopTime()} instead
     */
    @Deprecated
    public long getMinLoopTime();

    /**
     * Get the time to wait before re-scheduling this command.
     * @return time in ms
     */
    public long getLoopTime();

    /**
     * Sets the loop mode for the command
     *
     * @param loopMode
     */
    public void setLoopMode(boolean loopMode);

    /**
     * Creates a copy of the {@link ICommandOptions} object.
     * @return
     */
    public ICommandOptions clone();

    /**
     * Return true if command should run on all devices.
     */
    public boolean runOnAllDevices();

    /**
     * Return true if a bugreport should be taken when the test invocation has ended.
     */
    public boolean takeBugreportOnInvocationEnded();

}
