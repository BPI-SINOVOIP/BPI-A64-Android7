/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.android.diagnostics.error;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class IdeaITNProxy {
  public static List<Pair<String, String>> getKeyValuePairs(@Nullable String login,
                                                            @Nullable String password,
                                                            ErrorBean error,
                                                            String compilationTimestamp,
                                                            Application application,
                                                            ApplicationInfoEx appInfo,
                                                            ApplicationNamesInfo namesInfo,
                                                            UpdateSettings updateSettings) {
    List<Pair<String, String>> params = new ArrayList<Pair<String, String>>();

    params.add(Pair.create("protocol.version", "1"));

    if (login != null) {
      params.add(Pair.create("user.login", login));
      params.add(Pair.create("user.password", password));
    }

    params.add(Pair.create("os.name", SystemProperties.getOsName()));
    params.add(Pair.create("java.version", SystemProperties.getJavaVersion()));
    params.add(Pair.create("java.vm.vendor", SystemProperties.getJavaVmVendor()));

    params.add(Pair.create("app.name", namesInfo.getProductName()));
    params.add(Pair.create("app.name.full", namesInfo.getFullProductName()));
    params.add(Pair.create("app.name.version", appInfo.getVersionName()));
    params.add(Pair.create("app.eap", Boolean.toString(appInfo.isEAP())));
    params.add(Pair.create("app.internal", Boolean.toString(application.isInternal())));
    params.add(Pair.create("app.build", appInfo.getBuild().asString()));
    params.add(Pair.create("app.version.major", appInfo.getMajorVersion()));
    params.add(Pair.create("app.version.minor", appInfo.getMinorVersion()));
    params.add(Pair.create("app.build.date", format(appInfo.getBuildDate())));
    params.add(Pair.create("app.build.date.release", format(appInfo.getMajorReleaseBuildDate())));
    params.add(Pair.create("app.compilation.timestamp", compilationTimestamp));

    params.add(Pair.create("update.channel.status", updateSettings.getSelectedChannelStatus().getCode()));
    params.add(Pair.create("update.ignored.builds", StringUtil.join(updateSettings.getIgnoredBuildNumbers(), ",")));

    params.add(Pair.create("plugin.name", error.getPluginName()));
    params.add(Pair.create("plugin.version", error.getPluginVersion()));

    params.add(Pair.create("last.action", error.getLastAction()));
    params.add(Pair.create("previous.exception",
                           error.getPreviousException() == null ? null : Integer.toString(error.getPreviousException())));

    params.add(Pair.create("error.message", error.getMessage()));
    params.add(Pair.create("error.stacktrace", error.getStackTrace()));
    params.add(Pair.create("error.description", error.getDescription()));

    params.add(Pair.create("assignee.id", error.getAssigneeId() == null ? null : Integer.toString(error.getAssigneeId())));

    for (Attachment attachment : error.getAttachments()) {
      params.add(Pair.create("attachment.name", attachment.getName()));
      params.add(Pair.create("attachment.value", attachment.getEncodedBytes()));
    }

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      params.add(Pair.create("is.gradle.project." + i, Boolean.toString(Projects.requiresAndroidModel(projects[i]))));
    }

    return params;
  }

  private static String format(Calendar calendar) {
    return calendar == null ? "" : Long.toString(calendar.getTime().getTime());
  }
}
