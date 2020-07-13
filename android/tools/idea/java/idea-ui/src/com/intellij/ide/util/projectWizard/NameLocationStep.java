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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 29, 2003
 */
public class NameLocationStep extends ModuleWizardStep {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.NameLocationStep");
  private final JPanel myPanel;
  private final NamePathComponent myNamePathComponent;
  private final WizardContext myWizardContext;
  private final JavaModuleBuilder myBuilder;
  private final ModulesProvider myModulesProvider;
  private final Icon myIcon;
  private final String myHelpId;
  private boolean myModuleFileDirectoryChangedByUser = false;
  private final JTextField myTfModuleFilePath;
  private boolean myFirstTimeInitializationDone = false;

  public NameLocationStep(WizardContext wizardContext, JavaModuleBuilder builder,
                          ModulesProvider modulesProvider,
                          Icon icon,
                          @NonNls String helpId) {
    myWizardContext = wizardContext;
    myBuilder = builder;
    myModulesProvider = modulesProvider;
    myIcon = icon;
    myHelpId = helpId;
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final String text = IdeBundle.message("prompt.please.specify.module.name.and.content.root");
    final JLabel textLabel = new JLabel(text);
    textLabel.setUI(new MultiLineLabelUI());
    myPanel.add(textLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 8, 10), 0, 0));

    myNamePathComponent = new NamePathComponent(IdeBundle.message("label.module.name"), IdeBundle.message("label.module.content.root"), 'M', 'r',
                                                IdeBundle.message("title.select.module.content.root"), "");
    //if (ModuleType.J2EE_APPLICATION.equals(moduleType)) {
    //  myNamePathComponent.setPathComponentVisible(false);
    //}

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));

    final JLabel label = new JLabel(IdeBundle.message("label.module.file.will.be.saved.in"));
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL, new Insets(30, 10, 0, 10), 0, 0));

    myTfModuleFilePath = new JTextField();
    myTfModuleFilePath.setEditable(false);
    final Insets borderInsets = myTfModuleFilePath.getBorder().getBorderInsets(myTfModuleFilePath);
    myTfModuleFilePath.setBorder(BorderFactory.createEmptyBorder(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right));
    final FieldPanel fieldPanel = createFieldPanel(myTfModuleFilePath, null, null);
    myPanel.add(fieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 10, 10, 0), 0, 0));

    JButton browseButton = new JButton(IdeBundle.message("button.change.directory"));
    browseButton.addActionListener(new BrowseModuleFileDirectoryListener(myTfModuleFilePath));
    myPanel.add(browseButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 10, 10), 0, 0));

    final DocumentListener documentListener = new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateModuleFilePathField();
      }
    };
    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(documentListener);
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(documentListener);
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        myWizardContext.requestWizardButtonsUpdate();
      }
    });
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String suggestModuleName(ModuleType moduleType) {
    return "untitled";
  }

  public void updateStep() {
    super.updateStep();

    // initial UI settings
    if (!myFirstTimeInitializationDone) {
      if (myWizardContext.isCreatingNewProject()) {
        setSyncEnabled(false);
      }
      else { // project already exists
        final VirtualFile baseDir = myWizardContext.getProject().getBaseDir();
        final String projectFileDirectory = baseDir != null ? VfsUtil.virtualToIoFile(baseDir).getPath() : null;
        if (projectFileDirectory != null) {
          final String name = ProjectWizardUtil.findNonExistingFileName(projectFileDirectory, suggestModuleName(myBuilder.getModuleType()), "");
          setModuleName(name);
          setContentEntryPath(projectFileDirectory + File.separatorChar + name);
        }
      }
    }

    if (myWizardContext.isCreatingNewProject()) { // creating new project
      // in this mode we depend on the settings of the "project name" step
      if (!isPathChangedByUser()) {
        setContentEntryPath(myWizardContext.getProjectFileDirectory().replace('/', File.separatorChar));
      }
      if (!isNameChangedByUser()) {
        setModuleName(myWizardContext.getProjectName());
      }
    }

    if (!myFirstTimeInitializationDone) {
      myNamePathComponent.getNameComponent().selectAll();
    }
    myFirstTimeInitializationDone = true;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public String getModuleName() {
    return myNamePathComponent.getNameValue();
  }

  public void setModuleName(String name) {
    myNamePathComponent.setNameValue(name);
  }

  public String getContentEntryPath() {
    final String path = myNamePathComponent.getPath();
    return path.length() == 0? null : path;
  }

  public void setContentEntryPath(String path) {
    myNamePathComponent.setPath(path);
  }

  public String getModuleFilePath() {
    return getModuleFileDirectory() + "/" + myNamePathComponent.getNameValue() + ModuleFileType.DOT_DEFAULT_EXTENSION;
  }

  public boolean isSyncEnabled() {
    return myNamePathComponent.isSyncEnabled();
  }

  public void setSyncEnabled(boolean isSyncEnabled) {
    myNamePathComponent.setSyncEnabled(isSyncEnabled);
  }

  public String getModuleFileDirectory() {
    return myTfModuleFilePath.getText().trim().replace(File.separatorChar, '/');
  }

  public boolean validate() {
    final String moduleName = getModuleName();
    if (moduleName.length() == 0) {
      Messages.showErrorDialog(myNamePathComponent.getNameComponent(), IdeBundle.message("prompt.please.specify.module.name"),
                               IdeBundle.message("title.module.name.not.specified"));
      return false;
    }
    if (isAlreadyExists(moduleName)) {
      Messages.showErrorDialog(IdeBundle.message("error.module.with.name.already.exists", moduleName), IdeBundle.message("title.module.already.exists"));
      return false;
    }
    final String moduleLocation = getModuleFileDirectory();
    if (moduleLocation.length() == 0) {
      Messages.showErrorDialog(myNamePathComponent.getPathComponent(), IdeBundle.message("error.please.specify.module.file.location"),
                               IdeBundle.message("title.module.file.location.not.specified"));
      return false;
    }

    final String contentEntryPath = getContentEntryPath();
    if (contentEntryPath != null) {
      // the check makes sence only for non-null module root
      Module[] modules = myModulesProvider.getModules();
      for (final Module module : modules) {
        ModuleRootModel rootModel = myModulesProvider.getRootModel(module);
        LOG.assertTrue(rootModel != null);
        final VirtualFile[] moduleContentRoots = rootModel.getContentRoots();
        final String moduleContentRoot = contentEntryPath.replace(File.separatorChar, '/');
        for (final VirtualFile root : moduleContentRoots) {
          if (moduleContentRoot.equals(root.getPath())) {
            Messages.showErrorDialog(myNamePathComponent.getPathComponent(),
                                     IdeBundle.message("error.content.root.already.defined.for.module", contentEntryPath, module.getName()),
                                     IdeBundle.message("title.module.content.root.already.exists"));
            return false;
          }
        }
      }
      if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), contentEntryPath, myNamePathComponent.isPathChangedByUser())) {
        return false;
      }
    }
    final String moduleFileDirectory = getModuleFileDirectory();
    return ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory, myModuleFileDirectoryChangedByUser);
  }

  public void updateDataModel() {
    myBuilder.setName(getModuleName());
    myBuilder.setModuleFilePath(getModuleFilePath());
    myBuilder.setContentEntryPath(getContentEntryPath());
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  private boolean isAlreadyExists(String moduleName) {
    final Module[] modules = myModulesProvider.getModules();
    for (Module module : modules) {
      if (moduleName.equals(module.getName())) {
        return true;
      }
    }
    return false;
  }

  private void updateModuleFilePathField() {
    if (!myModuleFileDirectoryChangedByUser) {
      final String dir = myNamePathComponent.getPath().replace('/', File.separatorChar);
      myTfModuleFilePath.setText(dir);
    }
  }

  public boolean isNameChangedByUser() {
    return myNamePathComponent.isNameChangedByUser();
  }

  public boolean isPathChangedByUser() {
    return myNamePathComponent.isPathChangedByUser();
  }

  private class BrowseModuleFileDirectoryListener extends BrowseFilesListener {
    private BrowseModuleFileDirectoryListener(final JTextField textField) {
      super(textField, IdeBundle.message("title.select.module.file.location"),
            IdeBundle.message("description.select.module.file.location"), SINGLE_DIRECTORY_DESCRIPTOR);
    }

    public void actionPerformed(ActionEvent e) {
      final String pathBefore = myTfModuleFilePath.getText().trim();
      super.actionPerformed(e);
      final String path = myTfModuleFilePath.getText().trim();
      if (!path.equals(pathBefore)) {
        myModuleFileDirectoryChangedByUser = true;
      }
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
