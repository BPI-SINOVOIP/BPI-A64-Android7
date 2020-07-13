/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdkmanager;

import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import junit.framework.TestCase;

/**
 * Test the SDK Manager command-line parser using mocks.
 * These tests do not actually use SDK files.
 */
public class SdkCommandLineTest extends TestCase {

    private StdLogger mLog;

    /**
     * A mock version of the {@link SdkCommandLine} class that does not
     * exits and discards its stdout/stderr output.
     */
    public static class MockSdkCommandLine extends SdkCommandLine {
        private boolean mExitCalled;
        private boolean mHelpCalled;

        public MockSdkCommandLine(ILogger logger) {
            super(logger);
        }

        @Override
        public void printHelpAndExitForAction(String verb, String directObject,
                String errorFormat, Object... args) {
            mHelpCalled = true;
            super.printHelpAndExitForAction(verb, directObject, errorFormat, args);
        }

        @Override
        protected void exit() {
            mExitCalled = true;
        }

        @Override
        protected void stdout(String format, Object... args) {
            // discard
        }

        @Override
        protected void stderr(String format, Object... args) {
            // discard
        }

        public boolean wasExitCalled() {
            return mExitCalled;
        }

        public boolean wasHelpCalled() {
            return mHelpCalled;
        }
    }

    @Override
    protected void setUp() throws Exception {
        mLog = new StdLogger(StdLogger.Level.VERBOSE);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /** Test list */
    public final void testList_Avd_Verbose() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "-v", "list", "avd" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("list", c.getVerb());
        assertEquals("avd", c.getDirectObject());
        assertTrue(c.isVerbose());
    }

    public final void testList_Target() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "list", "target" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("list", c.getVerb());
        assertEquals("target", c.getDirectObject());
        assertFalse(c.isVerbose());
    }

    public final void testList_None() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "list" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("list", c.getVerb());
        assertEquals("", c.getDirectObject());
        assertFalse(c.isVerbose());
    }

    public final void testList_Invalid() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "list", "unknown" });
        assertTrue(c.wasHelpCalled());
        assertTrue(c.wasExitCalled());
        assertEquals(null, c.getVerb());
        assertEquals(null, c.getDirectObject());
        assertFalse(c.isVerbose());
    }

    public final void testList_Plural() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "list", "avds" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("list", c.getVerb());
        // we get the non-plural form
        assertEquals("avd", c.getDirectObject());
        assertFalse(c.isVerbose());

        c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "list", "targets" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("list", c.getVerb());
        // we get the non-plural form
        assertEquals("target", c.getDirectObject());
        assertFalse(c.isVerbose());
    }

    public final void testCreate_Avd() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "create", "avd", "-t", "android-100", "-n", "myProject" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("create", c.getVerb());
        assertEquals("avd", c.getDirectObject());
        assertFalse(c.getFlagSnapshot());
        assertEquals("android-100", c.getParamTargetId());
        assertEquals("myProject", c.getParamName());
        assertFalse(c.isVerbose());
    }

    public final void testCreate_Avd_Snapshot() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "create", "avd", "-t", "android-100", "-n", "myProject", "-a" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("create", c.getVerb());
        assertEquals("avd", c.getDirectObject());
        assertTrue(c.getFlagSnapshot());
        assertEquals("android-100", c.getParamTargetId());
        assertEquals("myProject", c.getParamName());
        assertFalse(c.isVerbose());
    }

    public final void testDirectSdk() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "sdk" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("sdk", c.getVerb());
        assertEquals("", c.getDirectObject());
        assertFalse(c.isVerbose());
    }

    public final void testDirectAvd() {
        MockSdkCommandLine c = new MockSdkCommandLine(mLog);
        c.parseArgs(new String[] { "avd" });
        assertFalse(c.wasHelpCalled());
        assertFalse(c.wasExitCalled());
        assertEquals("avd", c.getVerb());
        assertEquals("", c.getDirectObject());
        assertFalse(c.isVerbose());
    }

}
