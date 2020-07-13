/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public abstract class XDebuggerTreeInplaceEditor extends TreeInplaceEditor {
  private final XDebuggerTreeNode myNode;
  protected final XDebuggerExpressionComboBox myExpressionEditor;
  protected XDebuggerTree myTree;

  public XDebuggerTreeInplaceEditor(final XDebuggerTreeNode node, @NonNls final String historyId) {
    myNode = node;
    myTree = myNode.getTree();
    myExpressionEditor = new XDebuggerExpressionComboBox(myTree.getProject(), myTree.getEditorsProvider(), historyId, myTree.getSourcePosition());
  }

  @Override
  protected void onHidden() {
    final ComboPopup popup = myExpressionEditor.getComboBox().getPopup();
    if (popup != null && popup.isVisible()) {
      popup.hide();
    }
  }

  protected JComponent getPreferredFocusedComponent() {
    return myExpressionEditor.getPreferredFocusedComponent();
  }

  public XDebuggerTreeNode getNode() {
    return myNode;
  }

  public Editor getEditor() {
    return myExpressionEditor.getEditor();
  }

  public JComponent getEditorComponent() {
    return myExpressionEditor.getEditorComponent();
  }

  protected TreePath getNodePath() {
    return myNode.getPath();
  }

  protected JTree getTree() {
    return myNode.getTree();
  }

  protected Project getProject() {
    return myNode.getTree().getProject();
  }
}
