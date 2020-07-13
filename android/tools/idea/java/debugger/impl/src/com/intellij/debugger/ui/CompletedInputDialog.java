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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.awt.*;

/**
 * User: lex
 * Date: Sep 16, 2003
 * Time: 6:43:46 PM
 */
public class CompletedInputDialog extends DialogWrapper {
  JPanel myPanel;
  DebuggerExpressionComboBox myCombo;
  PsiElement myContext;
  Project myProject;
  private JLabel myLabel;

  public CompletedInputDialog(String title, String okText, Project project) {
    super(project, false);
    setTitle(title);
    setOKButtonText(okText);
    myProject = project;
    setModal(false);
    DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    myContext = PositionUtil.getContextElement(debuggerContext);
    this.init();
  }

  public JComponent getPreferredFocusedComponent() {
   return myCombo.getPreferredFocusedComponent();
  }

  protected JComponent createCenterPanel() {
    myPanel = new JPanel(new GridBagLayout());
    myLabel = new JLabel(DebuggerBundle.message("label.complete.input.dialog.expression"));
    myPanel.add(myLabel, new GridBagConstraints(0, 0, GridBagConstraints.REMAINDER, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
            new Insets(0, 1, 0, 1), 0, 0));

    myCombo = new DebuggerExpressionComboBox(myProject, myContext, "evaluation", DefaultCodeFragmentFactory.getInstance());
    myCombo.selectAll();

    myPanel.add(myCombo, new GridBagConstraints(0, 1, GridBagConstraints.REMAINDER, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
            new Insets(0, 1, 0, 1), 0, 0));
    myPanel.setPreferredSize(new Dimension(200, 50));
    return myPanel;
  }

  public TextWithImports getExpressionText() {
    return myCombo.getText();
  }

  public DebuggerExpressionComboBox getCombo() {
    return myCombo;
  }

  public void setExpressionLabel(String text) {
    myLabel.setText(text);
  }

  public void dispose() {
    myCombo.dispose();
    super.dispose();
  }
}
