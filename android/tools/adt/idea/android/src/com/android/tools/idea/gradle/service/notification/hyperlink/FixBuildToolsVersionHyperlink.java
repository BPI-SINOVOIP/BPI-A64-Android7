/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.parser.BuildFileKey.BUILD_TOOLS_VERSION;

public class FixBuildToolsVersionHyperlink extends NotificationHyperlink {
  @NotNull private final VirtualFile myBuildFile;
  @NotNull private final String myVersion;

  public FixBuildToolsVersionHyperlink(@NotNull VirtualFile buildFile, @NotNull String version) {
    super("fix.build.tools.version", "Update Build Tools version and sync project");
    myBuildFile = buildFile;
    myVersion = version;
  }

  @Override
  protected void execute(@NotNull Project project) {
    fixBuildToolsVersionAndSync(project, myBuildFile, myVersion);
  }

  static void fixBuildToolsVersionAndSync(@NotNull Project project, @NotNull VirtualFile buildFile, @NotNull final String version) {
    final GradleBuildFile gradleBuildFile = new GradleBuildFile(buildFile, project);
    Object pluginVersion = gradleBuildFile.getValue(BUILD_TOOLS_VERSION);
    if (pluginVersion != null) {
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          gradleBuildFile.setValue(BUILD_TOOLS_VERSION, version);
        }
      });
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }
}
