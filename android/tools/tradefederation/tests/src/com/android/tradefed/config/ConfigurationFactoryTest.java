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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationFactory.ConfigId;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ConfigurationFactory}
 */
public class ConfigurationFactoryTest extends TestCase {

    private ConfigurationFactory mFactory;

    /** the test config name that is built into this jar */
    private static final String TEST_CONFIG = "test-config";
    private static final String GLOBAL_TEST_CONFIG = "global-config";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFactory = new ConfigurationFactory() {
            @Override
            String getConfigPrefix() {
                return "testconfigs/";
            }
        };
    }

    /**
     * Sanity test to ensure all config names on classpath are loadable
     */
    public void disabled__testLoadAllConfigs() throws ConfigurationException {
        new ConfigurationFactory().loadAllConfigs(false);
    }

    /**
     * Sanity test to ensure all configs on classpath can be fully loaded and parsed
     */
    public void testLoadAndPrintAllConfigs() throws ConfigurationException {
        try {
            new ConfigurationFactory().loadAndPrintAllConfigs();
        } catch (ConfigurationException e) {
            // TODO: temporarily suppress this error, until all configs are cleaned up b/14027179
            CLog.e("Suppressing failed test testLoadAndPrintAllConfigs");
            CLog.e(e);
        }
    }

    /**
     * Test that a config xml defined in this test jar can be read as a built-in
     */
    public void testGetConfiguration_extension() throws ConfigurationException {
        assertConfigValid(TEST_CONFIG);
    }

    private Map<String, String> buildMap(String... args) {
        if ((args.length % 2) != 0) {
            throw new IllegalArgumentException(String.format(
                "Expected an even number of args; got %d", args.length));
        }

        final Map<String, String> map = new HashMap<String, String>(args.length / 2);
        for (int i = 0; i < args.length; i += 2) {
            map.put(args[i], args[i + 1]);
        }

        return map;
    }

    /**
     * Make sure that ConfigId behaves in the right way to serve as a hash key
     */
    public void testConfigId_equals() {
        final ConfigId config1a = new ConfigId("one");
        final ConfigId config1b = new ConfigId("one");
        final ConfigId config2 = new ConfigId("two");
        final ConfigId config3a = new ConfigId("one", buildMap("target", "foo"));
        final ConfigId config3b = new ConfigId("one", buildMap("target", "foo"));
        final ConfigId config4 = new ConfigId("two", buildMap("target", "bar"));

        assertEquals(config1a, config1b);
        assertEquals(config3a, config3b);

        // Check for false equivalences, and don't depend on #equals being commutative
        assertFalse(config1a.equals(config2));
        assertFalse(config1a.equals(config3a));
        assertFalse(config1a.equals(config4));

        assertFalse(config2.equals(config1a));
        assertFalse(config2.equals(config3a));
        assertFalse(config2.equals(config4));

        assertFalse(config3a.equals(config1a));
        assertFalse(config3a.equals(config2));
        assertFalse(config3a.equals(config4));

        assertFalse(config4.equals(config1a));
        assertFalse(config4.equals(config2));
        assertFalse(config4.equals(config3a));
    }

    /**
     * Make sure that ConfigId behaves in the right way to serve as a hash key
     */
    public void testConfigId_hashKey() {
        final Map<ConfigId, String> map = new HashMap<>();
        final ConfigId config1a = new ConfigId("one");
        final ConfigId config1b = new ConfigId("one");
        final ConfigId config2 = new ConfigId("two");
        final ConfigId config3a = new ConfigId("one", buildMap("target", "foo"));
        final ConfigId config3b = new ConfigId("one", buildMap("target", "foo"));
        final ConfigId config4 = new ConfigId("two", buildMap("target", "bar"));

        // Make sure that keys config1a and config1b behave identically
        map.put(config1a, "1a");
        assertEquals("1a", map.get(config1a));
        assertEquals("1a", map.get(config1b));

        map.put(config1b, "1b");
        assertEquals("1b", map.get(config1a));
        assertEquals("1b", map.get(config1b));

        assertFalse(map.containsKey(config2));
        assertFalse(map.containsKey(config3a));
        assertFalse(map.containsKey(config4));

        // Make sure that keys config3a and config3b behave identically
        map.put(config3a, "3a");
        assertEquals("3a", map.get(config3a));
        assertEquals("3a", map.get(config3b));

        map.put(config3b, "3b");
        assertEquals("3b", map.get(config3a));
        assertEquals("3b", map.get(config3b));

        assertEquals(2, map.size());
        assertFalse(map.containsKey(config2));
        assertFalse(map.containsKey(config4));

        // It's unlikely for these to fail if the above tests all passed, but just fill everything
        // out for completeness
        map.put(config2, "2");
        map.put(config4, "4");

        assertEquals(4, map.size());
        assertEquals("1b", map.get(config1a));
        assertEquals("1b", map.get(config1b));
        assertEquals("2", map.get(config2));
        assertEquals("3b", map.get(config3a));
        assertEquals("3b", map.get(config3b));
        assertEquals("4", map.get(config4));
    }

    /**
     * Test specifying a config xml by file path
     */
    public void testGetConfiguration_xmlpath() throws ConfigurationException, IOException {
        // extract the test-config.xml into a tmp file
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/testconfigs/%s.xml", TEST_CONFIG));
        File tmpFile = FileUtil.createTempFile(TEST_CONFIG, ".xml");
        try {
            FileUtil.writeToFile(configStream, tmpFile);
            assertConfigValid(tmpFile.getAbsolutePath());
            // check reading it again - should grab the cached version
            assertConfigValid(tmpFile.getAbsolutePath());
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Test that a config xml defined in this test jar can be read as a built-in
     */
    public void testGetGlobalConfiguration_extension() throws ConfigurationException {
        assertGlobalConfigValid(GLOBAL_TEST_CONFIG);
    }

    /**
     * Test specifying a config xml by file path
     */
    public void testGetGlobalConfiguration_xmlpath() throws ConfigurationException, IOException {
        // extract the test-config.xml into a tmp file
        InputStream configStream = getClass().getResourceAsStream(
                String.format("/testconfigs/%s.xml", GLOBAL_TEST_CONFIG));
        File tmpFile = FileUtil.createTempFile(GLOBAL_TEST_CONFIG, ".xml");
        try {
            FileUtil.writeToFile(configStream, tmpFile);
            assertGlobalConfigValid(tmpFile.getAbsolutePath());
            // check reading it again - should grab the cached version
            assertGlobalConfigValid(tmpFile.getAbsolutePath());
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Checks all config attributes are non-null
     */
    private void assertConfigValid(String name) throws ConfigurationException {
        IConfiguration config = mFactory.createConfigurationFromArgs(new String[] {name});
        assertNotNull(config);
    }

    /**
     * Checks all config attributes are non-null
     */
    private void assertGlobalConfigValid(String name) throws ConfigurationException {
        List<String> unprocessed = new ArrayList<String>();
        IGlobalConfiguration config =
                mFactory.createGlobalConfigurationFromArgs(new String[] {name}, unprocessed);
        assertNotNull(config);
        assertNotNull(config.getDeviceMonitors());
        assertNotNull(config.getWtfHandler());
        assertTrue(unprocessed.isEmpty());
    }

    /**
     * Test calling {@link ConfigurationFactory#getConfiguration(String)} with a name that does not
     * exist.
     */
    public void testCreateConfigurationFromArgs_missing()  {
        try {
            mFactory.createConfigurationFromArgs(new String[] {"non existent"});
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test calling {@link ConfigurationFactory#getConfiguration(String)} with config that has
     * unset mandatory options.
     * <p/>
     * Expect this to succeed, since mandatory option validation no longer happens at configuration
     * instantiation time.
     */
    public void testCreateConfigurationFromArgs_mandatory() throws ConfigurationException {
        assertNotNull(mFactory.createConfigurationFromArgs(new String[] {"mandatory-config"}));
    }

    /**
     * Test passing empty arg list to
     * {@link ConfigurationFactory#createConfigurationFromArgs(String[])}.
     */
    public void testCreateConfigurationFromArgs_empty() {
        try {
            mFactory.createConfigurationFromArgs(new String[] {});
            fail("did not throw ConfigurationException");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationFactory#createConfigurationFromArgs(String[])} using TEST_CONFIG
     */
    public void testCreateConfigurationFromArgs() throws ConfigurationException {
        // pick an arbitrary option to test to ensure it gets populated
        IConfiguration config = mFactory.createConfigurationFromArgs(new String[] {TEST_CONFIG,
                "--log-level", LogLevel.VERBOSE.getStringValue()});
        ILeveledLogOutput logger = config.getLogOutput();
        assertEquals(LogLevel.VERBOSE, logger.getLogLevel());
    }

    /**
     * Test {@link ConfigurationFactory#createConfigurationFromArgs(String[])} when extra positional
     * arguments are supplied
     */
    public void testCreateConfigurationFromArgs_unprocessedArgs() throws ConfigurationException {
        try {
            mFactory.createConfigurationFromArgs(new String[] {TEST_CONFIG, "--log-level",
                    LogLevel.VERBOSE.getStringValue(), "blah"});
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
     * Test {@link ConfigurationFactory#printHelp( PrintStream))}
     */
    public void testPrintHelp() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        new ConfigurationFactory().printHelp(mockPrintStream);
        // verify all the instrument config names are present
        final String usageString = outputStream.toString();
        assertTrue(usageString.contains("instrument"));
    }

    /**
     * Test {@link ConfigurationFactory#printHelpForConfig(String[], boolean, PrintStream))} when
     * config referenced by args exists
     */
    public void testPrintHelpForConfig_configExists() {
        String[] args = new String[] {TEST_CONFIG};
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream mockPrintStream = new PrintStream(outputStream);
        mFactory.printHelpForConfig(args, true, mockPrintStream);

        // verify the default configs name used is present
        final String usageString = outputStream.toString();
        assertTrue(usageString.contains(TEST_CONFIG));
        // TODO: add stricter verification
    }

    /**
     * Test loading a config that includes another config.
     */
    public void testCreateConfigurationFromArgs_includeConfig() throws Exception {
        IConfiguration config = mFactory.createConfigurationFromArgs(
                new String[]{"include-config"});
        assertTrue(config.getTests().get(0) instanceof StubOptionTest);
        assertTrue(config.getTests().get(1) instanceof StubOptionTest);
        StubOptionTest fromTestConfig = (StubOptionTest) config.getTests().get(0);
        StubOptionTest fromIncludeConfig = (StubOptionTest) config.getTests().get(1);
        assertEquals("valueFromTestConfig", fromTestConfig.mOption);
        assertEquals("valueFromIncludeConfig", fromIncludeConfig.mOption);
    }

    /**
     * Test loading a config that uses the "default" attribute of a template-include tag to include
     * another config.
     */
    public void testCreateConfigurationFromArgs_defaultTemplateInclude_default() throws Exception {
        // The default behavior is to include test-config directly.  Nesting is such that innermost
        // elements come first.
        IConfiguration config = mFactory.createConfigurationFromArgs(
                new String[]{"template-include-config-with-default"});
        assertEquals(2, config.getTests().size());
        assertTrue(config.getTests().get(0) instanceof StubOptionTest);
        assertTrue(config.getTests().get(1) instanceof StubOptionTest);
        StubOptionTest innerConfig = (StubOptionTest) config.getTests().get(0);
        StubOptionTest outerConfig = (StubOptionTest) config.getTests().get(1);
        assertEquals("valueFromTestConfig", innerConfig.mOption);
        assertEquals("valueFromTemplateIncludeWithDefaultConfig", outerConfig.mOption);
    }

    /**
     * Test using {@code <include>} to load a config that uses the "default" attribute of a
     * template-include tag to include a third config.
     */
    public void testCreateConfigurationFromArgs_includeTemplateIncludeWithDefault() throws Exception {
        // The default behavior is to include test-config directly.  Nesting is such that innermost
        // elements come first.
        IConfiguration config = mFactory.createConfigurationFromArgs(
                new String[]{"include-template-config-with-default"});
        assertEquals(3, config.getTests().size());
        assertTrue(config.getTests().get(0) instanceof StubOptionTest);
        assertTrue(config.getTests().get(1) instanceof StubOptionTest);
        assertTrue(config.getTests().get(2) instanceof StubOptionTest);
        StubOptionTest innerConfig = (StubOptionTest) config.getTests().get(0);
        StubOptionTest middleConfig = (StubOptionTest) config.getTests().get(1);
        StubOptionTest outerConfig = (StubOptionTest) config.getTests().get(2);
        assertEquals("valueFromTestConfig", innerConfig.mOption);
        assertEquals("valueFromTemplateIncludeWithDefaultConfig", middleConfig.mOption);
        assertEquals("valueFromIncludeTemplateConfigWithDefault", outerConfig.mOption);
    }

    /**
     * Test loading a config that uses the "default" attribute of a template-include tag to include
     * another config.  In this case, we override the default attribute on the commandline.
     */
    public void testCreateConfigurationFromArgs_defaultTemplateInclude_alternate() throws Exception {
        IConfiguration config = mFactory.createConfigurationFromArgs(
                new String[]{"template-include-config-with-default", "--template:map", "target",
                "include-config"});
        assertEquals(3, config.getTests().size());
        assertTrue(config.getTests().get(0) instanceof StubOptionTest);
        assertTrue(config.getTests().get(1) instanceof StubOptionTest);
        assertTrue(config.getTests().get(2) instanceof StubOptionTest);

        StubOptionTest innerConfig = (StubOptionTest) config.getTests().get(0);
        StubOptionTest middleConfig = (StubOptionTest) config.getTests().get(1);
        StubOptionTest outerConfig = (StubOptionTest) config.getTests().get(2);

        assertEquals("valueFromTestConfig", innerConfig.mOption);
        assertEquals("valueFromIncludeConfig", middleConfig.mOption);
        assertEquals("valueFromTemplateIncludeWithDefaultConfig", outerConfig.mOption);
    }

    /**
     * Test loading a config that uses template-include to include another config.
     */
    public void testCreateConfigurationFromArgs_templateInclude() throws Exception {
        IConfiguration config = mFactory.createConfigurationFromArgs(
                new String[]{"template-include-config", "--template:map", "target",
                "test-config"});
        assertTrue(config.getTests().get(0) instanceof StubOptionTest);
        assertTrue(config.getTests().get(1) instanceof StubOptionTest);
        StubOptionTest fromTestConfig = (StubOptionTest) config.getTests().get(0);
        StubOptionTest fromTemplateIncludeConfig = (StubOptionTest) config.getTests().get(1);
        assertEquals("valueFromTestConfig", fromTestConfig.mOption);
        assertEquals("valueFromTemplateIncludeConfig", fromTemplateIncludeConfig.mOption);
    }

    /**
     * Make sure that we throw a useful error when template-include usage is underspecified.
     */
    public void testCreateConfigurationFromArgs_templateInclude_unspecified() throws Exception {
        final String configName = "template-include-config";
        try {
            mFactory.createConfigurationFromArgs(new String[]{configName});
            fail ("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // Make sure that we get the expected error message
            final String msg = e.getMessage();
            assertNotNull(msg);

            assertTrue(String.format("Error message does not mention the name of the broken " +
                    "config.  msg was: %s", msg), msg.contains(configName));

            // Error message should help people to resolve the problem
            assertTrue(String.format("Error message should help user to resolve the " +
                    "template-include.  msg was: %s", msg),
                    msg.contains(String.format("--template:map %s", "target")));
            assertTrue(String.format("Error message should mention the ability to specify a " +
                    "default resolution.  msg was: %s", msg),
                    msg.contains(String.format("'default'", configName)));
        }
    }

    /**
     * Make sure that we throw a useful error when template-include mentions a target configuration
     * that doesn't exist.
     */
    public void testCreateConfigurationFromArgs_templateInclude_missing() throws Exception {
        final String configName = "template-include-config";
        final String includeName = "no-exist";

        try {
            mFactory.createConfigurationFromArgs(
                    new String[]{configName, "--template:map", "target", includeName});
            fail ("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // Make sure that we get the expected error message
            final String msg = e.getMessage();
            assertNotNull(msg);

            assertTrue(String.format("Error message does not mention the name of the broken " +
                    "config.  msg was: %s", msg), msg.contains(configName));
            assertTrue(String.format("Error message does not mention the name of the missing " +
                    "include target.  msg was: %s", msg), msg.contains(includeName));
        }
    }

    /**
     * A limitation of the current implementation is that template args are only passed to the
     * outermost configuration.  This unit test codifies the expectation that an inner
     * {@code <template-include>} tag that doesn't have a default resolution set will fail.
     */
    public void testCreateConfigurationFromArgs_templateInclude_dependent() throws Exception {
        final String configName = "depend-template-include-config";
        final String depTargetName = "template-include-config";
        final String targetName = "test-config";
        final String expError = String.format(
                "Failed to parse config xml '%s'. Reason: " +
                ConfigurationXmlParser.ConfigHandler.INNER_TEMPLATE_INCLUDE_ERROR,
                configName, configName, depTargetName);

        try {
            mFactory.createConfigurationFromArgs(new String[]{configName,
                    "--template:map", "dep-target", depTargetName,
                    "--template:map", "target", targetName});
            fail ("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // Make sure that we get the expected error message
            assertEquals(expError, e.getMessage());
        }
    }

    /**
     * A limitation of the current implementation is that template args are only passed to the
     * outermost configuration.  This unit test codifies the expectation that an inner
     * {@code <template-include>} tag that doesn't have a default resolution set will fail.
     */
    public void testCreateConfigurationFromArgs_include_dependent() throws Exception {
        final String configName = "include-template-config";
        final String targetName = "test-config";
        final String failedTargetName = "template-include-config";
        final String expError = String.format(
                "Failed to parse config xml '%s'. Reason: " +
                ConfigurationXmlParser.ConfigHandler.INNER_TEMPLATE_INCLUDE_ERROR,
                configName, configName, failedTargetName);

        try {
            mFactory.createConfigurationFromArgs(new String[]{configName,
                    "--template:map", "target", targetName});
            fail ("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // Make sure that we get the expected error message
            assertEquals(expError, e.getMessage());
        }
    }

    /**
     * Test loading a config that tries to include itself
     */
    public void testCreateConfigurationFromArgs_recursiveInclude() throws Exception {
        try {
            mFactory.createConfigurationFromArgs(new String[] {"recursive-config"});
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    /**
    * Test loading a config that tries to include a non-bundled config
    */
    public void testCreateConfigurationFromArgs_nonBundledInclude() throws Exception {
       try {
           mFactory.createConfigurationFromArgs(new String[] {"non-bundled-config"});
           fail("ConfigurationException not thrown");
       } catch (ConfigurationException e) {
           // expected
       }
    }

    /**
     * Test reloading a config after it has been updated.
     */
    public void testCreateConfigurationFromArgs_localConfigReload()
            throws ConfigurationException, IOException {

        File localConfigFile = FileUtil.createTempFile("local-config", ".xml");
        try {
            // Copy the config to the local filesystem
            InputStream source = getClass().getResourceAsStream("/testconfigs/local-config.xml");
            FileUtil.writeToFile(source, localConfigFile);

            // Depending on the system, file modification times might not have greater than 1 second
            // resolution. Backdate the original contents so that when we write to the file later,
            // it shows up as a new change.
            localConfigFile.setLastModified(System.currentTimeMillis() - 5000);

            // Load the configuration from the local file
            IConfiguration config = mFactory.createConfigurationFromArgs(
                    new String[] { localConfigFile.getAbsolutePath() });
            if (!(config.getTests().get(0) instanceof StubOptionTest)) {
                fail(String.format("Expected a StubOptionTest, but got %s",
                        config.getTests().get(0).getClass().getName()));
                return;
            }
            StubOptionTest test = (StubOptionTest)config.getTests().get(0);
            assertEquals("valueFromOriginalConfig", test.mOption);

            // Change the contents of the local file
            source = getClass().getResourceAsStream("/testconfigs/local-config-update.xml");
            FileUtil.writeToFile(source, localConfigFile);

            // Get the configuration again and verify that it picked up the update
            config = mFactory.createConfigurationFromArgs(
                    new String[] { localConfigFile.getAbsolutePath() });
            if (!(config.getTests().get(0) instanceof StubOptionTest)) {
                fail(String.format("Expected a StubOptionTest, but got %s",
                        config.getTests().get(0).getClass().getName()));
                return;
            }
            test = (StubOptionTest)config.getTests().get(0);
            assertEquals("valueFromUpdatedConfig", test.mOption);
        } finally {
            localConfigFile.delete();
        }
    }

    /**
     * Test loading a config that has a circular include
     */
    public void testCreateConfigurationFromArgs_circularInclude() throws Exception {
        try {
            mFactory.createConfigurationFromArgs(new String[] {"circular-config"});
            fail("ConfigurationException not thrown");
        } catch (ConfigurationException e) {
            // expected
        }
    }
}
