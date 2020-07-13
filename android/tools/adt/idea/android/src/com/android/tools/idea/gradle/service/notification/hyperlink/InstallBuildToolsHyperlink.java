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

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InstallBuildToolsHyperlink extends NotificationHyperlink {
  @NotNull private final String myVersion;
  @Nullable private final VirtualFile myBuildFile;

  public InstallBuildToolsHyperlink(@NotNull String version, @Nullable VirtualFile buildFile) {
    super("install.build.tools", getText(version, buildFile));
    myBuildFile = buildFile;
    myVersion = version;
  }

  @NotNull
  private static String getText(@NotNull String version, @Nullable VirtualFile buildFile) {
    String msg = String.format("Install Build Tools %1$s", version);
    if (buildFile != null) {
      msg += ", update version in build file and sync project";
    }
    else {
      msg += " and sync project";
    }
    return msg;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<IPkgDesc> requested = Lists.newArrayList();
    FullRevision minBuildToolsRev = FullRevision.parseRevision(myVersion);
    requested.add(PkgDesc.Builder.newBuildTool(minBuildToolsRev).create());
    SdkQuickfixWizard wizard = new SdkQuickfixWizard(project, null, requested);
    wizard.init();
    if (wizard.showAndGet()) {
      if (myBuildFile != null) {
        FixBuildToolsVersionHyperlink.fixBuildToolsVersionAndSync(project, myBuildFile, myVersion);
      }
      else {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      }
    }
  }
}
