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
package com.android.tools.idea.uibuilder.editor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.lint.NlBackgroundEditorHighlighter;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class NlEditor extends UserDataHolderBase implements FileEditor {
  private final AndroidFacet myFacet;
  private final VirtualFile myFile;
  private final Project myProject;
  private NlEditorPanel myEditorPanel;
  private BackgroundEditorHighlighter myBackgroundHighlighter;

  public NlEditor(AndroidFacet facet, VirtualFile file, Project project) {
    myFacet = facet;
    myFile = file;
    myProject = project;
  }

  @NonNull
  @Override
  public NlEditorPanel getComponent() {
    if (myEditorPanel == null) {
      myEditorPanel = new NlEditorPanel(this, myFacet, myFile);

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          NlPaletteManager.get(myProject).bind(myEditorPanel);
          NlStructureManager.get(myProject).bind(myEditorPanel);
        }
      });
    }
    return myEditorPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getComponent().getPreferredFocusedComponent();
  }

  @NonNull
  @Override
  public String getName() {
    return "Design";
  }

  @Override
  @NonNull
  public FileEditorState getState(@NonNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NonNull FileEditorState state) {
  }

  @Override
  public void dispose() {
    getComponent().dispose();
  }

  @Override
  public void selectNotify() {
    getComponent().activate();
  }

  @Override
  public void deselectNotify() {
    getComponent().deactivate();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void addPropertyChangeListener(@NonNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NonNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    // The designer should display components that have problems detected by inspections. Ideally, we'd just get the result
    // of all the inspections on the XML file. However, it doesn't look like there is an API to obtain this for a file
    // (there are  test APIs). So we add a single highlighter which uses lint..
    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new NlBackgroundEditorHighlighter(myEditorPanel);
    }
    return myBackgroundHighlighter;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }
}
