/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.idea.appengine.run;

import com.google.gct.idea.appengine.gradle.facet.AppEngineConfigurationProperties;
import com.google.gct.idea.appengine.gradle.facet.AppEngineGradleFacet;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** GUI for configuring App Engine Run Configurations */
public class AppEngineRunConfigurationSettingsEditor extends SettingsEditor<AppEngineRunConfiguration> {
  private JTextField myServerPortField;
  private JPanel mainPanel;
  private JComboBox myModuleComboBox;
  private JTextField myVmArgsField;
  private TextFieldWithBrowseButton myWarPathField;
  private TextFieldWithBrowseButton myAppEngineSdkField;
  private JTextField myServerAddressField;
  private JCheckBox mySynchronizeWithBuildGradleCheckBox;
  private final Project myProject;
  private final ConfigurationModuleSelector moduleSelector;

  public AppEngineRunConfigurationSettingsEditor(Project project) {
    myProject = project;
    moduleSelector = new ConfigurationModuleSelector(project, myModuleComboBox);
    myModuleComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSync();
      }
    });
    mySynchronizeWithBuildGradleCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSync();
      }
    });
    myAppEngineSdkField.addBrowseFolderListener("Select App Engine Sdk Root", null, null,
                                                FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myWarPathField.addBrowseFolderListener("Select Exploded War Root", null, myProject,
                                           FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  protected void updateSync() {
    boolean isSync = mySynchronizeWithBuildGradleCheckBox.isSelected();
    if (isSync) {
      syncWithBuildFile();
    }
    myWarPathField.setEditable(!isSync);
    myServerAddressField.setEditable(!isSync);
    myServerPortField.setEditable(!isSync);
    myAppEngineSdkField.setEditable(!isSync);
  }

  // Syncs a run configuration with information from build.gradle via the App Engine Gradle facet
  // This is also a near-duplicate of the sync in AppEngineRunConfiguration, but this is required
  // here to update the UI correctly when "sync" is checked and turned on, if we didn't reflect the
  // configuration in the UI, we wouldn't need this.
  protected void syncWithBuildFile() {

    AppEngineGradleFacet facet = AppEngineGradleFacet.getInstance(moduleSelector.getModule());
    if (facet != null) {
      AppEngineConfigurationProperties model = facet.getConfiguration().getState();
      if (model != null) {
        myServerPortField.setText(model.HTTP_PORT.toString());
        myServerAddressField.setText(model.HTTP_ADDRESS);
        myAppEngineSdkField.setText(model.APPENGINE_SDKROOT);
        myWarPathField.setText(model.WAR_DIR);
      }
    }
  }

  @Override
  protected void resetEditorFrom(AppEngineRunConfiguration configuration) {
    if (configuration.getSdkPath() != null) {
      myAppEngineSdkField.setText(configuration.getSdkPath());
    }
    if (configuration.getWarPath() != null) {
      myWarPathField.setText(configuration.getWarPath());
    }
    myServerPortField.setText(configuration.getServerPort());
    myServerAddressField.setText(configuration.getServerAddress());
    myVmArgsField.setText(configuration.getVmArgs());
    moduleSelector.reset(configuration);
    mySynchronizeWithBuildGradleCheckBox.setSelected(configuration.getSyncWithGradle());
    updateSync();
  }

  @Override
  protected void applyEditorTo(AppEngineRunConfiguration configuration) throws ConfigurationException {
    moduleSelector.applyTo(configuration);
    configuration.setSdkPath(myAppEngineSdkField.getText());
    configuration.setServerAddress(myServerAddressField.getText());
    configuration.setServerPort(myServerPortField.getText());
    configuration.setVmArgs(myVmArgsField.getText());
    configuration.setWarPath(myWarPathField.getText());
    configuration.setSyncWithGradle(mySynchronizeWithBuildGradleCheckBox.isSelected());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return mainPanel;
  }

  @Override
  protected void disposeEditor() {
    // nothing here
  }
}
