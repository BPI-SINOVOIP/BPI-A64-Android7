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

import junit.framework.TestCase;

/**
 * Unit tests for {@link OptionUpdateRule}
 */
public class OptionUpdateRuleTest extends TestCase {
    private static final String mOptionName = "option-name";
    private static final Object mCurrent = "5 current value";
    private static final Object mUpdate = "5 update value";
    private static final Object mSmallUpdate = "0 update value";
    private static final Object mBigUpdate = "9 update value";

    public void testFirst_simple() throws Exception {
        assertTrue(OptionUpdateRule.FIRST.shouldUpdate(mOptionName, null, mUpdate));
        assertFalse(OptionUpdateRule.FIRST.shouldUpdate(mOptionName, mCurrent, mUpdate));
    }

    public void testLast_simple() throws Exception {
        assertTrue(OptionUpdateRule.LAST.shouldUpdate(mOptionName, null, mUpdate));
        assertTrue(OptionUpdateRule.LAST.shouldUpdate(mOptionName, mCurrent, mUpdate));
    }

    public void testGreatest_simple() throws Exception {
        assertTrue(OptionUpdateRule.GREATEST.shouldUpdate(mOptionName, null, mSmallUpdate));
        assertFalse(OptionUpdateRule.GREATEST.shouldUpdate(mOptionName, mCurrent, mSmallUpdate));
        assertTrue(OptionUpdateRule.GREATEST.shouldUpdate(mOptionName, mCurrent, mBigUpdate));
    }

    public void testLeast_simple() throws Exception {
        assertTrue(OptionUpdateRule.LEAST.shouldUpdate(mOptionName, null, mBigUpdate));
        assertTrue(OptionUpdateRule.LEAST.shouldUpdate(mOptionName, mCurrent, mSmallUpdate));
        assertFalse(OptionUpdateRule.LEAST.shouldUpdate(mOptionName, mCurrent, mBigUpdate));
    }

    public void testImmutable_simple() throws Exception {
        assertTrue(OptionUpdateRule.IMMUTABLE.shouldUpdate(mOptionName, null, mUpdate));
        try {
            OptionUpdateRule.IMMUTABLE.shouldUpdate(mOptionName, mCurrent, mUpdate);
            fail("ConfigurationException not thrown when updating an IMMUTABLE option");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    public void testInvalidComparison() throws Exception {
        try {
            // Strings aren't comparable with integers
            OptionUpdateRule.GREATEST.shouldUpdate(mOptionName, 13, mUpdate);
            fail("ConfigurationException not thrown for invalid comparison.");
        } catch (ConfigurationException e) {
            // Expected.  Moreover, the exception should be actionable, so make sure we mention the
            // specific mismatching types.
            final String msg = e.getMessage();
            assertTrue(msg.contains("Integer"));
            assertTrue(msg.contains("String"));
        }
    }

    public void testNotComparable() throws Exception {
        try {
            OptionUpdateRule.LEAST.shouldUpdate(mOptionName, new Exception("hi"), mUpdate);
        } catch (ConfigurationException e) {
            // expected
        }
    }
}

