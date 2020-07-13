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
package com.android.tools.idea.configurations;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ThemeSelectionDialog extends DialogWrapper {
  @NotNull private final ThemeSelectionPanel myPanel;

  public ThemeSelectionDialog(@NotNull Configuration configuration) {
    super(configuration.getModule().getProject());
    myPanel = new ThemeSelectionPanel(this, configuration);
    setTitle("Select Theme");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel contentPanel = myPanel.getContentPanel();
    contentPanel.setPreferredSize(new Dimension(800, 500));
    return contentPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidThemeDialog";
  }

  @Nullable
  public String getTheme() {
    return myPanel.getTheme();
  }

  public void checkValidation() {
    initValidation();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    String theme = myPanel.getTheme();
    if (theme == null) {
      return new ValidationInfo("Select a theme");
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
