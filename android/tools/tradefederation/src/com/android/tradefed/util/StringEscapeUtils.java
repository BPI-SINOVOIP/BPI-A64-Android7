/*
 * Copyright (C) 2011 The Android Open Source Project
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


/**
 * Utility class for escaping strings for specific formats.
 * Include methods to escape strings that are being passed to the Android Shell.
 */
public class StringEscapeUtils {

    /**
     * Escapes a {@link String} for use in an Android shell command.
     *
     * @param str the {@link String} to escape
     * @return the Android shell escaped {@link String}
     */
    public static String escapeShell(String str) {
        if (str == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < str.length(); ++i) {
            char ch = str.charAt(i);
            // TODO: add other characters as needed.
            switch (ch) {
                case '$':
                    out.append("\\$");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    out.append(ch);
                    break;
            }
        }
        return out.toString();
    }
}
