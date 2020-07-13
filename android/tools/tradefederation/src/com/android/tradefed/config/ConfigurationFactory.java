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

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ClassPathScanner;
import com.android.tradefed.util.ClassPathScanner.IClassPathFilter;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Factory for creating {@link IConfiguration}.
 */
public class ConfigurationFactory implements IConfigurationFactory {

    private static final String LOG_TAG = "ConfigurationFactory";
    private static IConfigurationFactory sInstance = null;
    private static final String CONFIG_SUFFIX = ".xml";
    private static final String CONFIG_PREFIX = "config/";
    private static final String CONFIG_SPLIT = "|";

    private Map<ConfigId, ConfigurationDef> mConfigDefMap;

    /**
     * A simple struct-like class that stores a configuration's name alongside the arguments for
     * any {@code <template-include>} tags it may contain.  Because the actual bits stored by the
     * configuration may vary with template arguments, they must be considered as essential a part
     * of the configuration's identity as the filename.
     */
    static class ConfigId {
        public String name = null;
        public Map<String, String> templateMap = new HashMap<>();

        /**
         * No-op constructor
         */
        public ConfigId() {}

        /**
         * Convenience constructor.  Equivalent to calling two-arg constructor with {@code null}
         * {@code templateMap}.
         */
        public ConfigId(String name) {
            this(name, null);
        }

        /**
         * Two-arg convenience constructor.  {@code templateMap} may be null.
         */
        public ConfigId(String name, Map<String, String> templateMap) {
            this.name = name;
            if (templateMap != null) {
                this.templateMap.putAll(templateMap);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return 2 * ((name == null) ? 0 : name.hashCode()) + 3 * templateMap.hashCode();
        }

        private boolean matches(Object a, Object b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.equals(b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object other) {
            if (other == null) return false;
            if (!(other instanceof ConfigId)) return false;

            final ConfigId otherConf = (ConfigId) other;
            return matches(name, otherConf.name) && matches(templateMap, otherConf.templateMap);
        }
    }

    /**
     * A {@link IClassPathFilter} for configuration XML files.
     */
    private class ConfigClasspathFilter implements IClassPathFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(String pathName) {
            // only accept entries that match the pattern, and that we don't already know about
            final ConfigId pathId = new ConfigId(pathName);
            return pathName.startsWith(CONFIG_PREFIX) && pathName.endsWith(CONFIG_SUFFIX) &&
                    !mConfigDefMap.containsKey(pathId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String transform(String pathName) {
            // strip off CONFIG_PREFIX and CONFIG_SUFFIX
            int pathStartIndex = CONFIG_PREFIX.length();
            int pathEndIndex = pathName.length() - CONFIG_SUFFIX.length();
            return pathName.substring(pathStartIndex, pathEndIndex);
        }
    }

    /**
     * A {@link Comparator} for {@link ConfigurationDef} that sorts by
     * {@link ConfigurationDef#getName()}.
     */
    private static class ConfigDefComparator implements Comparator<ConfigurationDef> {

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(ConfigurationDef d1, ConfigurationDef d2) {
            return d1.getName().compareTo(d2.getName());
        }

    }

    /**
     * Implementation of {@link IConfigDefLoader} that tracks the included configurations from one
     * root config, and throws an exception on circular includes.
     */
    class ConfigLoader implements IConfigDefLoader {

        private final boolean mIsGlobalConfig;
        private Set<String> mIncludedConfigs = new HashSet<String>();

        public ConfigLoader(boolean isGlobalConfig) {
            mIsGlobalConfig = isGlobalConfig;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ConfigurationDef getConfigurationDef(String name, Map<String, String> templateMap)
                throws ConfigurationException {

            String configName = name;
            if (!isBundledConfig(name)) {
                configName = getAbsolutePath(null, name);
            }

            final ConfigId configId = new ConfigId(name, templateMap);
            ConfigurationDef def = mConfigDefMap.get(configId);

            if (def == null || def.isStale()) {
                def = new ConfigurationDef(configName);
                loadConfiguration(configName, def, templateMap);

                mConfigDefMap.put(configId, def);
            }

            return def;
        }

        /**
         * Returns true if it is a config file found inside the classpath.
         */
        private boolean isBundledConfig(String name) {
            InputStream configStream = getClass().getResourceAsStream(
                    String.format("/%s%s%s", getConfigPrefix(), name, CONFIG_SUFFIX));
            return configStream != null;
        }

        /**
         * Get the absolute path of a local config file.
         * @param root parent path of config file
         * @param name config file
         * @return absolute path for local config file.
         * @throws ConfigurationException
         */
        private String getAbsolutePath(String root, String name) throws ConfigurationException {
            File file = new File(name);
            if (!file.isAbsolute()) {
                if (root == null) {
                    // if root directory was not specified, get the current working directory.
                    root = System.getProperty("user.dir");
                }
                file = new File(root, name);
            }
            try {
                return file.getCanonicalPath();
            } catch (IOException e) {
                throw new ConfigurationException(String.format(
                        "Failure when trying to determine local file canonical path %s", e));
            }
        }

        @Override
        /**
         * Configs that are bundled inside the tradefed.jar can only include other configs also
         * bundled inside tradefed.jar. However, local (external) configs can include both local
         * (external) and bundled configs.
         */
        public void loadIncludedConfiguration(ConfigurationDef def, String parentName, String name)
                throws ConfigurationException {

            String config_name = name;
            if (!isBundledConfig(name)) {
                try {
                    // Ensure bundled configs are not including local configs.
                    if (isBundledConfig(parentName)) {
                        throw new ConfigurationException(String.format("Invalid include; bundled " +
                    "config '%s' is trying to include local config '%s'.", parentName, name));
                    }
                    // Local configs' include should be relative to their parent's path.
                    String parentRoot = new File(parentName).getParentFile().getCanonicalPath();
                    config_name = getAbsolutePath(parentRoot, name);
                } catch  (IOException e) {
                    throw new ConfigurationException(String.format(
                            "Failure when trying to determine local file canonical path %s", e));
                }
            }
            if (mIncludedConfigs.contains(config_name)) {
                throw new ConfigurationException(String.format(
                        "Circular configuration include: config '%s' is already included",
                        config_name));
            }
            mIncludedConfigs.add(config_name);
            loadConfiguration(config_name, def, null);
        }

        /**
         * Loads a configuration.
         *
         * @param name the name of a built-in configuration to load or a file
         *            path to configuration xml to load
         * @param def the loaded {@link ConfigurationDef}
         * @param templateMap map from template-include names to their respective concrete
         *                    configuration files
         * @throws ConfigurationException if a configuration with given
         *             name/file path cannot be loaded or parsed
         */
        void loadConfiguration(String name, ConfigurationDef def, Map<String, String> templateMap)
                throws ConfigurationException {
            Log.i(LOG_TAG, String.format("Loading configuration '%s'", name));
            BufferedInputStream bufStream = getConfigStream(name);
            ConfigurationXmlParser parser = new ConfigurationXmlParser(this);
            parser.parse(def, name, bufStream, templateMap);

            // Track local config source files
            if (!isBundledConfig(name)) {
                def.registerSource(new File(name));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isGlobalConfig() {
            return mIsGlobalConfig;
        }
    }

    ConfigurationFactory() {
        mConfigDefMap = new Hashtable<ConfigId, ConfigurationDef>();
    }

    /**
     * Get the singleton {@link IConfigurationFactory} instance.
     */
    public static IConfigurationFactory getInstance() {
        if (sInstance == null) {
            sInstance = new ConfigurationFactory();
        }
        return sInstance;
    }

    /**
     * Retrieve the {@link ConfigurationDef} for the given name
     *
     * @param name the name of a built-in configuration to load or a file path to configuration xml
     *            to load
     * @return {@link ConfigurationDef}
     * @throws ConfigurationException if an error occurred loading the config
     */
    private ConfigurationDef getConfigurationDef(String name, boolean isGlobal,
            Map<String, String> templateMap) throws ConfigurationException {
        return new ConfigLoader(isGlobal).getConfigurationDef(name, templateMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IConfiguration createConfigurationFromArgs(String[] arrayArgs)
            throws ConfigurationException {
        return createConfigurationFromArgs(arrayArgs, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IConfiguration createConfigurationFromArgs(String[] arrayArgs,
            List<String> unconsumedArgs) throws ConfigurationException {
        List<String> listArgs = new ArrayList<String>(arrayArgs.length);
        IConfiguration config = internalCreateConfigurationFromArgs(arrayArgs, listArgs);
        config.setCommandLine(arrayArgs);
        final List<String> tmpUnconsumedArgs = config.setOptionsFromCommandLineArgs(listArgs);

        if (unconsumedArgs == null && tmpUnconsumedArgs.size() > 0) {
            // (unconsumedArgs == null) is taken as a signal that the caller expects all args to
            // be processed.
            throw new ConfigurationException(String.format(
                    "Invalid arguments provided. Unprocessed arguments: %s", tmpUnconsumedArgs));
        } else if (unconsumedArgs != null) {
            // Return the unprocessed args
            unconsumedArgs.addAll(tmpUnconsumedArgs);
        }

        return config;
    }

    /**
     * Creates a {@link Configuration} from the name given in arguments.
     * <p/>
     * Note will not populate configuration with values from options
     *
     * @param arrayArgs the full list of command line arguments, including the config name
     * @param optionArgsRef an empty list, that will be populated with the option arguments left
     *                      to be interpreted
     * @return An {@link IConfiguration} object representing the configuration that was loaded
     * @throws ConfigurationException
     */
    private IConfiguration internalCreateConfigurationFromArgs(String[] arrayArgs,
            List<String> optionArgsRef) throws ConfigurationException {
        if (arrayArgs.length == 0) {
            throw new ConfigurationException("Configuration to run was not specified");
        }
        final List<String> listArgs = new ArrayList<>(Arrays.asList(arrayArgs));
        // first arg is config name
        final String configName = listArgs.remove(0);

        // Steal ConfigurationXmlParser arguments from the command line
        final ConfigurationXmlParserSettings parserSettings = new ConfigurationXmlParserSettings();
        final ArgsOptionParser templateArgParser = new ArgsOptionParser(parserSettings);
        optionArgsRef.addAll(templateArgParser.parseBestEffort(listArgs));

        ConfigurationDef configDef = getConfigurationDef(configName, false,
                parserSettings.templateMap);
        return configDef.createConfiguration();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public IGlobalConfiguration createGlobalConfigurationFromArgs(String[] arrayArgs,
            List<String> remainingArgs) throws ConfigurationException {
        List<String> listArgs = new ArrayList<String>(arrayArgs.length);
        IGlobalConfiguration config =
                internalCreateGlobalConfigurationFromArgs(arrayArgs, listArgs);
        remainingArgs.addAll(config.setOptionsFromCommandLineArgs(listArgs));

        return config;
    }

    /**
     * Creates a {@link Configuration} from the name given in arguments.
     * <p/>
     * Note will not populate configuration with values from options
     *
     * @param arrayArgs the full list of command line arguments, including the config name
     * @param optionArgsRef an empty list, that will be populated with the remaining option
     *                      arguments
     * @return
     * @throws ConfigurationException
     */
    private IGlobalConfiguration internalCreateGlobalConfigurationFromArgs(String[] arrayArgs,
            List<String> optionArgsRef) throws ConfigurationException {
        if (arrayArgs.length == 0) {
            throw new ConfigurationException("Configuration to run was not specified");
        }
        optionArgsRef.addAll(Arrays.asList(arrayArgs));
        // first arg is config name
        final String configName = optionArgsRef.remove(0);
        ConfigurationDef configDef = getConfigurationDef(configName, true, null);
        return configDef.createGlobalConfiguration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printHelp(PrintStream out) {
        // print general help
        // TODO: move this statement to Console
        out.println("Use 'run command <configuration_name> --help' to get list of options for a " +
                "configuration");
        out.println("Use 'dump config <configuration_name>' to display the configuration's XML " +
                "content.");
        out.println();
        out.println("Available configurations include:");
        try {
            loadAllConfigs(true);
        } catch (ConfigurationException e) {
            // ignore, should never happen
        }
        // sort the configs by name before displaying
        SortedSet<ConfigurationDef> configDefs = new TreeSet<ConfigurationDef>(
                new ConfigDefComparator());
        configDefs.addAll(mConfigDefMap.values());
        for (ConfigurationDef def: configDefs) {
            out.printf("  %s: %s", def.getName(), def.getDescription());
            out.println();
        }
    }

    /**
     * Loads all configurations found in classpath.
     *
     * @param discardExceptions true if any ConfigurationException should be ignored. Exposed for
     * unit testing
     * @throws ConfigurationException
     */
    void loadAllConfigs(boolean discardExceptions) throws ConfigurationException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        boolean failed = false;
        ClassPathScanner cpScanner = new ClassPathScanner();
        Set<String> configNames = cpScanner.getClassPathEntries(new ConfigClasspathFilter());
        for (String configName : configNames) {
            final ConfigId configId = new ConfigId(configName);
            try {
                ConfigurationDef configDef = getConfigurationDef(configName, false, null);
                mConfigDefMap.put(configId, configDef);
            } catch (ConfigurationException e) {
                ps.printf("Failed to load %s: %s", configName, e.getMessage());
                ps.println();
                failed = true;

            }
        }
        if (failed) {
            if (discardExceptions) {
                CLog.e("Failure loading configs");
                CLog.e(baos.toString());
            } else {
                throw new ConfigurationException(baos.toString());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printHelpForConfig(String[] args, boolean importantOnly, PrintStream out) {
        try {
            IConfiguration config = internalCreateConfigurationFromArgs(args,
                    new ArrayList<String>(args.length));
            config.printCommandUsage(importantOnly, out);
        } catch (ConfigurationException e) {
            // config must not be specified. Print generic help
            printHelp(out);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpConfig(String configName, PrintStream out) {
        try {
            InputStream configStream = getConfigStream(configName);
            StreamUtil.copyStreams(configStream, out);
        } catch (ConfigurationException e) {
            Log.e(LOG_TAG, e);
        } catch (IOException e) {
            Log.e(LOG_TAG, e);
        }
    }

    /**
     * Return the path prefix of config xml files on classpath
     * <p/>
     * Exposed so unit tests can mock.
     * @return {@link String} path with trailing /
     */
    String getConfigPrefix() {
        return CONFIG_PREFIX;
    }

    /**
     * Loads an InputStream for given config name
     *
     * @param name the configuration name to load
     * @return a {@link BufferedInputStream} for reading config contents
     * @throws ConfigurationException if config could not be found
     */
    private BufferedInputStream getConfigStream(String name) throws ConfigurationException {
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/%s%s%s", getConfigPrefix(), name, CONFIG_SUFFIX));
        if (configStream == null) {
            // now try to load from file
            try {
                configStream = new FileInputStream(name);
            } catch (FileNotFoundException e) {
                throw new ConfigurationException(String.format("Could not find configuration '%s'",
                        name));
            }
        }
        // buffer input for performance - just in case config file is large
        return new BufferedInputStream(configStream);
    }

    /**
     * Utility method that checks that all configs can be loaded, parsed, and all option values
     * set.
     *
     * @throws ConfigurationException if one or more configs failed to load
     */
    void loadAndPrintAllConfigs() throws ConfigurationException {
        loadAllConfigs(false);
        boolean failed = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        for (ConfigurationDef def : mConfigDefMap.values()) {
            try {
                def.createConfiguration().printCommandUsage(false,
                        new PrintStream(StreamUtil.nullOutputStream()));
            } catch (ConfigurationException e) {
                ps.printf("Failed to print %s: %s", def.getName(), e.getMessage());
                ps.println();
                failed = true;
            }
        }
        if (failed) {
            throw new ConfigurationException(baos.toString());
        }
    }
}
