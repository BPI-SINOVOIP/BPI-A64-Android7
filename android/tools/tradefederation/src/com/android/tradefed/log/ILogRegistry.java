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

package com.android.tradefed.log;

import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;

import java.util.Collection;

/**
 * An interface for a {@link ILogOutput} singleton logger that multiplexes and manages different
 * loggers.
 */
public interface ILogRegistry extends ILogOutput {

    /**
     * Set the log level display for the global log
     *
     * @param logLevel the {@link LogLevel} to use
     */
    public void setGlobalLogDisplayLevel(LogLevel logLevel);

    /**
     * Set the log tags to display for the global log
     */
    public void setGlobalLogTagDisplay(Collection<String> logTagsDisplay);

    /**
     * Returns current log level display for the global log
     *
     * @return logLevel the {@link LogLevel} to use
     */
    public LogLevel getGlobalLogDisplayLevel();

    /**
     * Registers the logger as the instance to use for the current thread.
     */
    public void registerLogger(ILeveledLogOutput log);

    /**
     * Unregisters the current logger in effect for the current thread.
     */
    public void unregisterLogger();

    /**
     * Dumps the entire contents of a {@link ILeveledLogOutput} logger to the global log.
     * <p/>
     * This is useful in scenarios where you know the logger's output won't be saved permanently,
     * yet you want the contents to be saved somewhere and not lost.
     *
     * @param log
     */
    public void dumpToGlobalLog(ILeveledLogOutput log);

    /**
     * Closes and removes all logs being managed by this LogRegistry.
     */
    public void closeAndRemoveAllLogs();

    /**
     * Saves global logger contents to a tmp file.
     */
    public void saveGlobalLog();

    /**
     * Diagnosis method to dump all logs to files.
     */
    public void dumpLogs();

}
