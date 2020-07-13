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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.NumberFormatter;

public class GradleExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;

  private JPanel myPanel;
  private JCheckBox myEnableModuleSelectionOnImportCheckBox;
  private JSpinner myModuleNumberSpinner;
  private JCheckBox mySkipSourceGenOnSyncCheckbox;

  public GradleExperimentalSettingsConfigurable() {
    mySettings = GradleExperimentalSettings.getInstance();
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.experimental";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Experimental";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    if (mySettings.SELECT_MODULES_ON_PROJECT_IMPORT != isModuleSelectionOnImportEnabled() ||
        mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC != isSkipSourceGenOnSync()) {
      return true;
    }
    Integer value = getMaxModuleCountForSourceGen();
    return value != null && mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN != value;
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = isModuleSelectionOnImportEnabled();
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = isSkipSourceGenOnSync();
    Integer value = getMaxModuleCountForSourceGen();
    if (value != null) {
      mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = value;
    }
  }

  @Nullable
  private Integer getMaxModuleCountForSourceGen() {
    Object value = myModuleNumberSpinner.getValue();
    return value instanceof Integer ? (Integer)value : null;
  }

  private boolean isModuleSelectionOnImportEnabled() {
    return myEnableModuleSelectionOnImportCheckBox.isSelected();
  }

  private boolean isSkipSourceGenOnSync() {
    return mySkipSourceGenOnSyncCheckbox.isSelected();
  }

  @Override
  public void reset() {
    myEnableModuleSelectionOnImportCheckBox.setSelected(mySettings.SELECT_MODULES_ON_PROJECT_IMPORT);
    mySkipSourceGenOnSyncCheckbox.setSelected(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    myModuleNumberSpinner.setValue(mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
  }

  @Override
  public void disposeUIResources() {
  }

  private void createUIComponents() {
    int value = GradleExperimentalSettings.getInstance().MAX_MODULE_COUNT_FOR_SOURCE_GEN;
    myModuleNumberSpinner = new JSpinner(new SpinnerNumberModel(value, 0, Integer.MAX_VALUE, 1));
    // Force the spinner to accept numbers only.
    JComponent editor = myModuleNumberSpinner.getEditor();
    if (editor instanceof JSpinner.NumberEditor) {
      JFormattedTextField textField = ((JSpinner.NumberEditor)editor).getTextField();
      JFormattedTextField.AbstractFormatter formatter = textField.getFormatter();
      if (formatter instanceof NumberFormatter) {
        ((NumberFormatter)formatter).setAllowsInvalid(false);
      }
    }
  }
}
