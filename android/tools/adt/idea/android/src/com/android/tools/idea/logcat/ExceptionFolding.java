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
package com.android.tools.idea.logcat;

import com.intellij.execution.ConsoleFolding;
import org.jetbrains.android.logcat.AndroidLogcatReceiver;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** {@link AndroidLogcatReceiver} expands stack traces that were elided "(...N more)". This folds just those additional lines. */
public class ExceptionFolding extends ConsoleFolding {
  private static final String STACK_TRACE_LINE_PREFIX = AndroidLogcatReceiver.EXPANDED_STACK_TRACE_LINE_PREFIX + "at ";

  @Override
  public boolean shouldFoldLine(String line) {
    return line.startsWith(STACK_TRACE_LINE_PREFIX);
  }

  @Nullable
  @Override
  public String getPlaceholderText(List<String> lines) {
    return " <" + lines.size() + " more...>";
  }
}
