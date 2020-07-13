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
package com.intellij.android.designer;

import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.designer.DesignerEditor;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.inspection.DesignerBackgroundEditorHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditor extends DesignerEditor {
  private BackgroundEditorHighlighter myHighlighter;

  public AndroidDesignerEditor(Project project, VirtualFile file) {
    super(project, file);
  }

  @Override
  @Nullable
  protected Module findModule(Project project, VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      module = AndroidPsiUtils.getModuleSafely(project, file);
    }
    if (module == null) {
      throw new IllegalArgumentException("No module for file " + file + " in project " + project);
    }
    return module;
  }

  @Override
  @NotNull
  protected DesignerEditorPanel createDesignerPanel(Project project, Module module, VirtualFile file) {
    return new AndroidDesignerEditorPanel(this, project, module, file);
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (myHighlighter == null) {
      myHighlighter = new DesignerBackgroundEditorHighlighter(getDesignerPanel());
    }
    return myHighlighter;
  }
}