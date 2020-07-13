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
import com.android.tradefed.config.GlobalConfiguration;

/**
 * An alternate TradeFederation entry point that will run command specified in command
 * line arguments and then quit.
 * <p/>
 * Intended for use with a debugger and other non-interactive modes of operation.
 * <p/>
 * Expected arguments: [commands options] (config to run)
 */
public class CommandRunner {
    private ICommandScheduler mScheduler;
    private static int mErrorCode = 0;

    CommandRunner() {

    }

    /**
     * The main method to run the command.
     *
     * @param args the config name to run and its options
     */
    public void run(String[] args) {
        try {
            GlobalConfiguration.createGlobalConfiguration(args);
            mScheduler = GlobalConfiguration.getInstance().getCommandScheduler();
            mScheduler.start();
            mScheduler.addCommand(args);
        } catch (ConfigurationException e) {
            e.printStackTrace();
            mErrorCode = 1;
        } finally {
            mScheduler.shutdownOnEmpty();
        }
        try {
            mScheduler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            mErrorCode = 1;
        }
    }

    public static void main(final String[] mainArgs) {
        CommandRunner console = new CommandRunner();
        console.run(mainArgs);
        System.exit(mErrorCode);
    }
}
