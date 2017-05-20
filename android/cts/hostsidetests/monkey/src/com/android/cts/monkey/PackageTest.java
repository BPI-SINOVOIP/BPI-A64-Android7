/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.monkey;

import java.util.regex.Pattern;

public class PackageTest extends AbstractMonkeyTest {

    private static final int MAX_ERROR_LENGTH = 256;
    private static final Pattern ALLOW_MONKEY =
            Pattern.compile("^.*Allowing.*cmp=com\\.android\\.cts\\.monkey/\\.MonkeyActivity.*$",
                    Pattern.MULTILINE);

    private static final Pattern ALLOW_CHIMP =
            Pattern.compile("^.*Allowing.*cmp=com\\.android\\.cts\\.monkey2/\\.ChimpActivity.*$",
                    Pattern.MULTILINE);

    public void testSinglePackage() throws Exception {
        String out = mDevice.executeShellCommand(MONKEY_CMD + " -v -p " + PKGS[0] + " 5000");
        String error = truncateError(out);
        assertTrue("Monkey not found in: " + error, ALLOW_MONKEY.matcher(out).find());
        assertFalse("Chimp found in: " + error, ALLOW_CHIMP.matcher(out).find());

        out = mDevice.executeShellCommand(MONKEY_CMD + " -v -p " + PKGS[1] + " 5000");
        error = truncateError(out);
        assertFalse("Monkey found in: " + error, ALLOW_MONKEY.matcher(out).find());
        assertTrue("Chimp not found in: " + error, ALLOW_CHIMP.matcher(out).find());
    }

    public void testMultiplePackages() throws Exception {
        String out = mDevice.executeShellCommand(MONKEY_CMD + " -v -p " + PKGS[0]
                + " -p " + PKGS[1] + " 5000");
        String error = truncateError(out);
        assertTrue("Monkey not found in: " + error, ALLOW_MONKEY.matcher(out).find());
        assertTrue("Chimp not found in: " + error, ALLOW_CHIMP.matcher(out).find());
    }

    private static final String truncateError(String input) {
        return input.substring(0, Math.min(input.length(), MAX_ERROR_LENGTH));
    }
}
