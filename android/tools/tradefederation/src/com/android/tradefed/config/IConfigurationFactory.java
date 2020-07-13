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

package com.android.tradefed.config;

import java.io.PrintStream;
import java.util.List;

/**
 * Factory for creating {@link IConfiguration}s
 */
public interface IConfigurationFactory {

    /**
     * A convenience method which calls {@link createConfigurationFromArgs(String[], List<String>)}
     * with a {@code null} second argument.  Thus, it will throw {@link ConfigurationException} if
     * any unconsumed arguments remain.
     *
     * @see createConfigurationFromArgs(String[] List<String>)
     */
    public IConfiguration createConfigurationFromArgs(String[] args) throws ConfigurationException;

    /**
     * Create the {@link IConfiguration} from command line arguments.
     * <p/>
     * Expected format is "CONFIG [options]", where CONFIG is the built-in configuration name or
     * a file path to a configuration xml file.
     *
     * @param args the command line arguments
     * @param unconsumedArgs a List which will be populated with the arguments that were not
     *                       consumed by the Objects associated with the specified config. If this
     *                       is {@code null}, then the implementation will throw
     *                       {@link ConfigurationException} if any unprocessed args remain.
     *
     * @return the loaded {@link IConfiguration}. The delegate object {@link Option} fields have
     *         been populated with values in args.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public IConfiguration createConfigurationFromArgs(String[] args, List<String> unconsumedArgs)
            throws ConfigurationException;

    /**
     * Create a {@link IGlobalConfiguration} from command line arguments.
     * <p/>
     * Expected format is "CONFIG [options]", where CONFIG is the built-in configuration name or
     * a file path to a configuration xml file.
     *
     * @param args the command line arguments
     * @param nonGlobalArgs a list which will be populated with the arguments that weren't
     *                      processed as global arguments
     * @return the loaded {@link IGlobalConfiguration}. The delegate object {@link Option} fields
     *         have been populated with values in args.
     * @throws {@link ConfigurationException} if configuration could not be loaded
     */
    public IGlobalConfiguration createGlobalConfigurationFromArgs(String[] args,
            List<String> nonGlobalArgs) throws ConfigurationException;

    /**
     * Prints help output for this factory.
     * <p/>
     * Prints a generic help info, and lists all available configurations.
     *
     * @param out the {@link PrintStream} to dump output to
     */
    public void printHelp(PrintStream out);

    /**
     * Prints help output for the {@link IConfiguration} specified in command line arguments,
     * <p/>
     * If 'args' refers to a known configuration, a {@link IConfiguration} object will be created
     * from XML, and help for that {@link IConfiguration} will be outputted. Note all other 'args'
     * values will be ignored (ie the help text will describe the current values of {@link Option}s
     * as loaded from XML, and will not reflect option's values set by the command line args.
     * <p/>
     * If 'args' does not reference a known {@link IConfiguration}, the generic
     * {@link #printHelp(PrintStream)} help will be displayed.
     * <p/>
     *
     * @param args the command line arguments
     * @param importantOnly if <code>true</code>, print an abbreviated help listing only the
     * important details
     * @param out the {@link PrintStream} to dump output to
     */
    public void printHelpForConfig(String[] args, boolean importantOnly, PrintStream out);

    /**
     * Dumps the contents of the configuration to the given {@link PrintStream}
     *
     * @param configName the configuration name
     * @param out the {@link PrintStream} to dump output to
     */
    public void dumpConfig(String configName, PrintStream out);
}
