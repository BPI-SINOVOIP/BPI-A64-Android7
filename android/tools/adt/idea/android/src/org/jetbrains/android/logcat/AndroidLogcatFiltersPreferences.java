/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.logcat;

import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidLogFilters",
  storages = {@Storage(
    file = StoragePathMacros.WORKSPACE_FILE)})
public class AndroidLogcatFiltersPreferences implements PersistentStateComponent<AndroidLogcatFiltersPreferences> {
  public String TAB_CUSTOM_FILTER = "";
  public String TOOL_WINDOW_CUSTOM_FILTER = "";
  public String TAB_LOG_LEVEL = "VERBOSE";
  public String TOOL_WINDOW_LOG_LEVEL = "VERBOSE";
  public String TOOL_WINDOW_CONFIGURED_FILTER = "";

  public static AndroidLogcatFiltersPreferences getInstance(Project project) {
    return ServiceManager.getService(project, AndroidLogcatFiltersPreferences.class);
  }

  @Override
  public AndroidLogcatFiltersPreferences getState() {
    return this;
  }

  @Override
  public void loadState(AndroidLogcatFiltersPreferences object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
