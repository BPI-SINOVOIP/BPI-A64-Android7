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
package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.ExpressionChildrenRenderer;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.awt.*;

public class ClassChildrenExpressionConfigurable implements UnnamedConfigurable{
  private final ExpressionChildrenRenderer myRenderer;

  private JPanel myPanel;
  private LabeledComponent<JPanel> myChildrenPanel;
  private LabeledComponent<JPanel> myExpandablePanel;

  private final CompletionEditor myChildrenEditor;
  private final CompletionEditor myExpandableEditor;

  public ClassChildrenExpressionConfigurable(Project project, ExpressionChildrenRenderer renderer) {
    myRenderer = renderer;

    PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), project, GlobalSearchScope.allScope(project));
    myChildrenEditor   = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "ClassChildrenExpression");
    myExpandableEditor = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "ClassChildrenExpression");

    myChildrenPanel.getComponent().setLayout(new BorderLayout());
    myChildrenPanel.getComponent().add(myChildrenEditor);

    myExpandablePanel.getComponent().setLayout(new BorderLayout());
    myExpandablePanel.getComponent().add(myExpandableEditor);    
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !myRenderer.getChildrenExpression().equals(myChildrenEditor.getText()) ||
           !myRenderer.getChildrenExpandable().equals(myExpandableEditor.getText());
  }

  public void apply() throws ConfigurationException {
    myRenderer.setChildrenExpression(myChildrenEditor.getText());
    myRenderer.setChildrenExpandable(myExpandableEditor.getText());
  }

  public void reset() {
    myChildrenEditor.setText(myRenderer.getChildrenExpression());
    myExpandableEditor.setText(myRenderer.getChildrenExpandable());
  }

  public void disposeUIResources() {
    myChildrenEditor.dispose();
    myExpandableEditor.dispose();
  }
}
