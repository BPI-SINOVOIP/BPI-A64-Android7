/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.loganalysis;

import com.android.loganalysis.item.BatteryDischargeItemTest;
import com.android.loganalysis.item.GenericItemTest;
import com.android.loganalysis.item.InterruptItemTest;
import com.android.loganalysis.item.MemInfoItemTest;
import com.android.loganalysis.item.MonkeyLogItemTest;
import com.android.loganalysis.item.ProcrankItemTest;
import com.android.loganalysis.item.SmartMonkeyLogItemTest;
import com.android.loganalysis.item.SystemPropsItemTest;
import com.android.loganalysis.item.TopItemTest;
import com.android.loganalysis.item.WakelockItemTest;
import com.android.loganalysis.parser.AbstractSectionParserTest;
import com.android.loganalysis.parser.AnrParserTest;
import com.android.loganalysis.parser.BugreportParserTest;
import com.android.loganalysis.parser.CompactMemInfoParserTest;
import com.android.loganalysis.parser.InterruptParserTest;
import com.android.loganalysis.parser.JavaCrashParserTest;
import com.android.loganalysis.parser.KernelLogParserTest;
import com.android.loganalysis.parser.LogcatParserTest;
import com.android.loganalysis.parser.MemInfoParserTest;
import com.android.loganalysis.parser.MonkeyLogParserTest;
import com.android.loganalysis.parser.NativeCrashParserTest;
import com.android.loganalysis.parser.ProcessUsageParserTest;
import com.android.loganalysis.parser.ProcrankParserTest;
import com.android.loganalysis.parser.SystemPropsParserTest;
import com.android.loganalysis.parser.TopParserTest;
import com.android.loganalysis.parser.TracesParserTest;
import com.android.loganalysis.parser.WakelockParserTest;
import com.android.loganalysis.util.ArrayUtilTest;
import com.android.loganalysis.util.LogPatternUtilTest;
import com.android.loganalysis.util.LogTailUtilTest;
import com.android.loganalysis.util.RegexTrieTest;
import com.android.loganalysis.util.config.ArgsOptionParserTest;
import com.android.loganalysis.util.config.OptionSetterTest;
import com.android.loganalysis.util.config.OptionUpdateRuleTest;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A test suite for all Trade Federation unit tests.
 * <p/>
 * All tests listed here should be self-contained, and should not require any external dependencies.
 */
public class UnitTests extends TestSuite {

    public UnitTests() {
        super();

        // item
        addTestSuite(BatteryDischargeItemTest.class);
        addTestSuite(GenericItemTest.class);
        addTestSuite(InterruptItemTest.class);
        addTestSuite(MemInfoItemTest.class);
        addTestSuite(MonkeyLogItemTest.class);
        addTestSuite(ProcrankItemTest.class);
        addTestSuite(SmartMonkeyLogItemTest.class);
        addTestSuite(SystemPropsItemTest.class);
        addTestSuite(TopItemTest.class);
        addTestSuite(WakelockItemTest.class);

        // parser
        addTestSuite(AbstractSectionParserTest.class);
        addTestSuite(AnrParserTest.class);
        addTestSuite(BugreportParserTest.class);
        addTestSuite(CompactMemInfoParserTest.class);
        addTestSuite(InterruptParserTest.class);
        addTestSuite(JavaCrashParserTest.class);
        addTestSuite(KernelLogParserTest.class);
        addTestSuite(LogcatParserTest.class);
        addTestSuite(MemInfoParserTest.class);
        addTestSuite(MonkeyLogParserTest.class);
        addTestSuite(NativeCrashParserTest.class);
        addTestSuite(ProcessUsageParserTest.class);
        addTestSuite(ProcrankParserTest.class);
        addTestSuite(SystemPropsParserTest.class);
        addTestSuite(TopParserTest.class);
        addTestSuite(TracesParserTest.class);
        addTestSuite(WakelockParserTest.class);

        // util
        addTestSuite(ArrayUtilTest.class);
        addTestSuite(LogPatternUtilTest.class);
        addTestSuite(LogTailUtilTest.class);
        addTestSuite(RegexTrieTest.class);

        // util.config
        addTestSuite(ArgsOptionParserTest.class);
        addTestSuite(OptionSetterTest.class);
        addTestSuite(OptionUpdateRuleTest.class);
    }

    public static Test suite() {
        return new UnitTests();
    }
}
