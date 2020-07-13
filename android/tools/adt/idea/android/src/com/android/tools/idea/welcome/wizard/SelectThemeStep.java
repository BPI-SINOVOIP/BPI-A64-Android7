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
package com.android.tools.idea.welcome.wizard;

import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.ide.customize.CustomizeUIThemeStepPanel;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Step for FirstRunWizard for selecting a color scheme.
 */
public class SelectThemeStep extends FirstRunWizardStep {
  private final CustomizeUIThemeStepPanel themePanel;
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;

  public SelectThemeStep(@NotNull ScopedStateStore.Key<Boolean> keyCustomInstall) {
    super("Select UI Theme");
    myKeyCustomInstall = keyCustomInstall;
    themePanel = new CustomizeUIThemeStepPanel();
    setComponent(themePanel);
  }

  @Override
  public void init() {
  }

  @Override
  public boolean commitStep() {
    // This code is duplicated from LafManager.initComponent(). But our Welcome Wizard is started
    // AFTER that call so we repeat it here.
    if (UIUtil.isUnderDarcula()) {
      DarculaInstaller.install();
    }
    else {
      DarculaInstaller.uninstall();
    }

    return super.commitStep();
  }

  @Override
  public boolean isStepVisible() {
    return myState.getNotNull(myKeyCustomInstall, true);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}