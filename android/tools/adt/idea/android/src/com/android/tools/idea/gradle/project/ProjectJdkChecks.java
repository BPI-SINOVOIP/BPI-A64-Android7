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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.structure.gradle.AndroidProjectSettingsService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.setHasWrongJdk;
import static com.android.tools.idea.sdk.Jdks.isApplicableJdk;

final class ProjectJdkChecks {
  private ProjectJdkChecks() {
  }

  static boolean hasCorrectJdkVersion(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.getAndroidModel() != null) {
      return hasCorrectJdkVersion(module, facet.getAndroidModel());
    }
    return true;
  }

  static boolean hasCorrectJdkVersion(@NotNull Module module, @NotNull IdeaAndroidProject androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();
    String compileTarget = androidProject.getCompileTarget();

    AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
    if (version != null && version.getFeatureLevel() >= 21) {
      Sdk jdk = IdeSdks.getJdk();
      if (jdk != null && !isApplicableJdk(jdk, LanguageLevel.JDK_1_7)) {
        Project project = module.getProject();

        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        hyperlinks.add(new OpenUrlHyperlink(Jdks.DOWNLOAD_JDK_7_URL, "Download JDK 7"));

        ProjectSettingsService service = ProjectSettingsService.getInstance(project);
        if (service instanceof AndroidProjectSettingsService) {
          hyperlinks.add(new SelectJdkHyperlink((AndroidProjectSettingsService)service));
        }
        Message msg;
        String text = "compileSdkVersion " + compileTarget + " requires compiling with JDK 7";
        VirtualFile buildFile = getGradleBuildFile(module);
        String groupName = "Project Configuration";

        if (buildFile != null) {
          int lineNumber = -1;
          int column = -1;
          Document document = FileDocumentManager.getInstance().getDocument(buildFile);
          if (document != null) {
            int offset = findCompileSdkVersionValueOffset(document.getText());
            if (offset > -1) {
              lineNumber = document.getLineNumber(offset);
              if (lineNumber > -1) {
                int lineStartOffset = document.getLineStartOffset(lineNumber);
                column = offset - lineStartOffset;
              }
            }
          }

          hyperlinks.add(new OpenFileHyperlink(buildFile.getPath(), "Open build.gradle File", lineNumber, column));
          msg = new Message(project, groupName, Message.Type.ERROR, buildFile, lineNumber, column, text);
        }
        else {
          msg = new Message(groupName, Message.Type.ERROR, NonNavigatable.INSTANCE, text);
        }

        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        messages.add(msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));

        setHasWrongJdk(project, true);
        return false;
      }
    }
    return true;
  }

  // Returns the offset where the 'compileSdkVersion' value is in a build.gradle file.
  @VisibleForTesting
  static int findCompileSdkVersionValueOffset(@NotNull String buildFileContents) {
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(buildFileContents);

    int end = -1;

    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      String text = lexer.getTokenText();
      if (type == GroovyTokenTypes.mIDENT) {
        if ("compileSdkVersion".equals(text)) {
          end = lexer.getTokenEnd();
        }
        else if (end > -1) {
          return end;
        }
      }
      else if (type == TokenType.WHITE_SPACE && end > -1) {
        end++;
      }
      else if (end > -1) {
        return end;
      }
      lexer.advance();
    }

    return -1;
  }

  private static class SelectJdkHyperlink extends NotificationHyperlink {
    @NotNull private final AndroidProjectSettingsService mySettingsService;

    SelectJdkHyperlink(@NotNull AndroidProjectSettingsService settingsService) {
      super("select.jdk", "Select a JDK from the File System");
      mySettingsService = settingsService;
    }

    @Override
    protected void execute(@NotNull Project project) {
      mySettingsService.chooseJdkLocation();
    }
  }
}
