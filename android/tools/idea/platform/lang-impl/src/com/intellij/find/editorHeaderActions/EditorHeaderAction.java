/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.editorHeaderActions;


import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Shortcut;

import javax.swing.*;
import java.util.List;

public abstract class EditorHeaderAction extends AnAction {
  private final EditorSearchComponent myEditorSearchComponent;

  protected void registerShortcutsForComponent(List<Shortcut> shortcuts, JComponent component) {
    registerCustomShortcutSet(
      new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
      component);
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  protected EditorHeaderAction(EditorSearchComponent editorSearchComponent) {

    myEditorSearchComponent = editorSearchComponent;
  }
}

