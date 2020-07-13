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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getManager;

public class OpenGradleSettingsHyperlink extends NotificationHyperlink {
  public OpenGradleSettingsHyperlink() {
    super("openGradleSettings", "Gradle settings");
  }

  @Override
  protected void execute(@NotNull Project project) {
    ExternalSystemManager<?,?,?,?,?> manager = getManager(GRADLE_SYSTEM_ID);
    assert manager instanceof GradleManager;
    GradleManager gradleManager = (GradleManager)manager;
    Configurable configurable = gradleManager.getConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }
}
