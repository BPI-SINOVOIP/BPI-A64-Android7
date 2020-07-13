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
package com.android.tools.idea.templates;

/**
 * Utility Methods for custom Freemarker commands
 */
public class FmUtil {

  /**
   * Strip off the end portion of the name. The user might be typing
   * the activity name such that only a portion has been entered so far (e.g.
   * "MainActivi") and we want to chop off that portion too such that we don't
   * offer a layout name partially containing the activity suffix (e.g. "main_activi").
   */
  public static String stripSuffix(String name, String suffix, boolean recursiveStrip) {
    if (name.length() < 2) {
      return name;
    }

    int suffixStart = name.lastIndexOf(suffix.charAt(0));
    if (suffixStart != -1 && name.regionMatches(suffixStart, suffix, 0,
                                                name.length() - suffixStart)) {
      name = name.substring(0, suffixStart);
    }
    // Recursively continue to strip the suffix (catch the FooActivityActivity case)
    if (recursiveStrip && name.endsWith(suffix)) {
      return stripSuffix(name, suffix, recursiveStrip);
    }

    return name;
  }
}
