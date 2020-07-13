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

package com.android.loganalysis.util.config;

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
        assertEquals(mUpdate, OptionUpdateRule.FIRST.update(mOptionName, null, mUpdate));
        assertEquals(mCurrent, OptionUpdateRule.FIRST.update(mOptionName, mCurrent, mUpdate));
    }

    public void testLast_simple() throws Exception {
        assertEquals(mUpdate, OptionUpdateRule.LAST.update(mOptionName, null, mUpdate));
        assertEquals(mUpdate, OptionUpdateRule.LAST.update(mOptionName, mCurrent, mUpdate));
    }

    public void testGreatest_simple() throws Exception {
        assertEquals(mSmallUpdate,
                OptionUpdateRule.GREATEST.update(mOptionName, null, mSmallUpdate));
        assertEquals(mCurrent,
                OptionUpdateRule.GREATEST.update(mOptionName, mCurrent, mSmallUpdate));
        assertEquals(mBigUpdate,
                OptionUpdateRule.GREATEST.update(mOptionName, mCurrent, mBigUpdate));
    }

    public void testLeast_simple() throws Exception {
        assertEquals(mBigUpdate,
                OptionUpdateRule.LEAST.update(mOptionName, null, mBigUpdate));
        assertEquals(mSmallUpdate,
                OptionUpdateRule.LEAST.update(mOptionName, mCurrent, mSmallUpdate));
        assertEquals(mCurrent,
                OptionUpdateRule.LEAST.update(mOptionName, mCurrent, mBigUpdate));
    }

    public void testImmutable_simple() throws Exception {
        assertEquals(mUpdate, OptionUpdateRule.IMMUTABLE.update(mOptionName, null, mUpdate));
        try {
            OptionUpdateRule.IMMUTABLE.update(mOptionName, mCurrent, mUpdate);
            fail("ConfigurationException not thrown when updating an IMMUTABLE option");
        } catch (ConfigurationException e) {
            // expected
        }
    }

    public void testInvalidComparison() throws Exception {
        try {
            // Strings aren't comparable with integers
            OptionUpdateRule.GREATEST.update(mOptionName, 13, mUpdate);
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
            OptionUpdateRule.LEAST.update(mOptionName, new Exception("hi"), mUpdate);
        } catch (ConfigurationException e) {
            // expected
        }
    }
}

