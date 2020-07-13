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
package org.jetbrains.android.inspections.lint;

import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.utils.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.checks.ManifestDetector.MOCK_LOCATION_PERMISSION;

/**
 * Quickfix for the {@link com.android.tools.lint.checks.ManifestDetector#MOCK_LOCATION} error, which deletes a mock
 * location permission from a non-debug manifest and adds it to a debug specific one (which is created if possible)
 */
class MoveToDebugManifestQuickFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
    if (attribute != null) {
      XmlTag parent = attribute.getParent();
      if (parent != null && parent.getName().equals(TAG_USES_PERMISSION)) {
        Module module = getModule(parent);

        assert MOCK_LOCATION_PERMISSION.equals(parent.getAttributeValue(ATTR_NAME, ANDROID_URI));
        parent.delete();

        if (module != null) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null) {
            VirtualFile mainManifest = facet.getMainIdeaSourceProvider().getManifestFile();
            IdeaAndroidProject androidModel = facet.getAndroidModel();
            if (androidModel != null && mainManifest != null
                && mainManifest.getParent() != null && mainManifest.getParent().getParent() != null) {
              final VirtualFile src = mainManifest.getParent().getParent();
              for (BuildTypeContainer container : androidModel.getAndroidProject().getBuildTypes()) {
                BuildType buildType = container.getBuildType();
                if (buildType.isDebuggable()) {
                  addManifest(module, src, buildType.getName());
                  return;
                }
              }
              Messages.showErrorDialog(module.getProject(), "Did not find a debug build type", "Move Permission");
            }
          }
        }
      }
    }
  }

  private void addManifest(@NotNull final Module module, @NotNull final VirtualFile src, @NotNull final String buildTypeName) {
    final Project project = module.getProject();
    final VirtualFile manifest = src.findFileByRelativePath(buildTypeName + '/' + ANDROID_MANIFEST_XML);
    Pair<String, VirtualFile> result =
      ApplicationManager.getApplication().runWriteAction(new Computable<Pair<String, VirtualFile>>() {
        @Override
        public Pair<String, VirtualFile> compute() {
          if (manifest == null) {
            try {
              VirtualFile newParentFolder = src.findChild(buildTypeName);
              if (newParentFolder == null) {
                newParentFolder = src.createChildDirectory(this, buildTypeName);
                if (newParentFolder == null) {
                  String message = String.format("Could not create folder %1$s in %2$s", buildTypeName, src.getPath());
                  return Pair.of(message, null);
                }
              }

              VirtualFile newFile = newParentFolder.createChildData(this, ANDROID_MANIFEST_XML);
              // TODO: \r\n on Windows?
              String text = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                            "    <uses-permission android:name=\"android.permission.ACCESS_MOCK_LOCATION\" />\n" +
                            "</manifest>\n";
              VfsUtil.saveText(newFile, text);
              return Pair.of(null, newFile);
            }
            catch (IOException e) {
              String message = String.format("Failed to create file: %1$s", e.getMessage());
              return Pair.of(message, null);
            }
          }
          else {
            Document document = FileDocumentManager.getInstance().getDocument(manifest);
            if (document != null) {
              String text = document.getText();
              int index = text.lastIndexOf("</manifest>");
              if (index != -1) {
                document.insertString(index, "    <uses-permission android:name=\"android.permission.ACCESS_MOCK_LOCATION\" />\n");
                return Pair.of(null, manifest);
              }
            }

            return Pair.of("Could not add to " + VfsUtilCore.virtualToIoFile(manifest), null);
          }
        }
      });

    String error = result.getFirst();
    VirtualFile newFile = result.getSecond();
    if (error != null) {
      Messages.showErrorDialog(project, error, "Move Permission");
    }
    else {
      TemplateUtils.openEditor(project, newFile);
      TemplateUtils.selectEditor(project, newFile);
    }
  }

  @Nullable
  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Move to debug-specific manifest";
  }
}
