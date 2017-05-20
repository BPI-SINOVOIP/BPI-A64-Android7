/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;

public class CompatibilityBuildHelperTest extends TestCase {

    private static final String ROOT_PROPERTY = "TESTS_ROOT";
    private static final String BUILD_NUMBER = "2";
    private static final String SUITE_NAME = "TESTS";
    private static final String SUITE_FULL_NAME = "Compatibility Tests";
    private static final String SUITE_VERSION = "1";
    private static final String SUITE_PLAN = "cts";
    private static final String DYNAMIC_CONFIG_URL = "";
    private static final String ROOT_DIR_NAME = "root";
    private static final String BASE_DIR_NAME = "android-tests";
    private static final String TESTCASES = "testcases";
    private static final String COMMAND_LINE_ARGS = "cts -m CtsModuleTestCases";
    private static final long START_TIME = 123456L;

    private File mRoot = null;
    private File mBase = null;
    private File mTests = null;
    private IBuildInfo mBuild;
    private CompatibilityBuildHelper mHelper;

    @Override
    public void setUp() throws Exception {
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider();
        mBuild = provider.getBuild();
        mHelper = new CompatibilityBuildHelper(mBuild);
    }

    @Override
    public void tearDown() throws Exception {
        setProperty(null);
        FileUtil.recursiveDelete(mRoot);
        mRoot = null;
        mBase = null;
        mTests = null;
    }

    private void createDirStructure() {
        mBase = new File(mRoot, BASE_DIR_NAME);
        mBase.mkdirs();
        mTests = new File(mBase, TESTCASES);
        mTests.mkdirs();
    }

    public void testSuiteInfoLoad() throws Exception {
        setProperty(mRoot.getAbsolutePath());
        mHelper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
        assertEquals("Incorrect suite build number", BUILD_NUMBER, mHelper.getSuiteBuild());
        assertEquals("Incorrect suite name", SUITE_NAME, mHelper.getSuiteName());
        assertEquals("Incorrect suite full name", SUITE_FULL_NAME, mHelper.getSuiteFullName());
        assertEquals("Incorrect suite version", SUITE_VERSION, mHelper.getSuiteVersion());
    }

    public void testProperty() throws Exception {
        setProperty(null);
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider();
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(provider.getBuild());
        try {
            // Should fail with root unset
            helper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
            fail("Expected fail for unset root property");
        } catch (IllegalArgumentException e) {
            /* expected */
        }
        setProperty(mRoot.getAbsolutePath());
        // Shouldn't fail with root set
        helper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
    }

    public void testValidation() throws Exception {
        setProperty(mRoot.getAbsolutePath());
        mHelper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
        try {
            mHelper.getDir();
            fail("Build helper validation succeeded on an invalid installation");
        } catch (FileNotFoundException e) {
            // Expected
        }
        createDirStructure();
        try {
            mHelper.getTestsDir();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            fail("Build helper validation failed on a valid installation");
        }
    }

    public void testDirs() throws Exception {
        setProperty(mRoot.getAbsolutePath());
        mHelper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
        createDirStructure();
        assertNotNull(mRoot);
        assertNotNull(mBuild);
        assertNotNull(mHelper.getRootDir());
        assertEquals("Incorrect root dir", mRoot.getAbsolutePath(),
                mHelper.getRootDir().getAbsolutePath());
        assertEquals("Incorrect base dir", mBase.getAbsolutePath(),
                mHelper.getDir().getAbsolutePath());
        assertEquals("Incorrect logs dir", new File(mBase, "logs").getAbsolutePath(),
                mHelper.getLogsDir().getAbsolutePath());
        assertEquals("Incorrect tests dir", mTests.getAbsolutePath(),
                mHelper.getTestsDir().getAbsolutePath());
        assertEquals("Incorrect results dir", new File(mBase, "results").getAbsolutePath(),
                mHelper.getResultsDir().getAbsolutePath());
    }

    public void testGetCommandLineArgs() {
        assertNull(mHelper.getCommandLineArgs());
        mBuild.addBuildAttribute("command_line_args", COMMAND_LINE_ARGS);
        assertEquals(COMMAND_LINE_ARGS, mHelper.getCommandLineArgs());

        mBuild.addBuildAttribute("command_line_args", "cts --retry 0");
        mHelper.setRetryCommandLineArgs(COMMAND_LINE_ARGS);
        assertEquals(COMMAND_LINE_ARGS, mHelper.getCommandLineArgs());
    }

    public void testSetModuleIds() {
        mHelper.setModuleIds(new String[] {"module1", "module2"});

        assertEquals("module1,module2",
            mBuild.getBuildAttributes().get(CompatibilityBuildHelper.MODULE_IDS));
    }

    /**
     * Sets the *_ROOT property of the build's installation location.
     *
     * @param value the value to set, or null to clear the property.
     */
    public static void setProperty(String value) {
        if (value == null) {
            System.clearProperty(ROOT_PROPERTY);
        } else {
            System.setProperty(ROOT_PROPERTY, value);
        }
    }
}
