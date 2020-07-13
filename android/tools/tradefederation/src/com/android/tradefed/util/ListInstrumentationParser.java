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

package com.android.tradefed.util;

import com.android.ddmlib.MultiLineReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A {@link IShellOutputReceiver} that parses the output of a 'pm list instrumentation' query
 */
public class ListInstrumentationParser extends MultiLineReceiver {

    // Each line of output from `pm list instrumentation` has the following format:
    // instrumentation:com.example.test/android.test.InstrumentationTestRunner (target=com.example)
    private static final Pattern LIST_INSTR_PATTERN =
            Pattern.compile("instrumentation:(.+)/(.+) \\(target=(.+)\\)");

    private List<InstrumentationTarget> mInstrumentationTargets = new ArrayList<>();

    public static class InstrumentationTarget {
        public final String packageName;
        public final String runnerName;
        public final String targetName;

        public InstrumentationTarget(String packageName, String runnerName, String targetName) {
            this.packageName = packageName;
            this.runnerName = runnerName;
            this.targetName = targetName;
        }
    }

    public List<InstrumentationTarget> getInstrumentationTargets() {
        return mInstrumentationTargets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNewLines(String[] lines) {
        for (String line : lines) {
            Matcher m = LIST_INSTR_PATTERN.matcher(line);
            if (m.find()) {
                mInstrumentationTargets.add(
                        new InstrumentationTarget(m.group(1), m.group(2), m.group(3)));
            }
        }
    }
}
