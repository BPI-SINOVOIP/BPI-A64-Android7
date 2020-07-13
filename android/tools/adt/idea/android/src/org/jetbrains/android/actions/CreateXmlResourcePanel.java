/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.android.builder.model.SourceProvider;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateXmlResourcePanel {
  private JPanel myPanel;
  private JTextField myNameField;
  private ModulesComboBox myModuleCombo;
  private JBLabel myModuleLabel;
  private JPanel myDirectoriesPanel;
  private JBLabel myDirectoriesLabel;
  private JTextField myValueField;
  private JBLabel myValueLabel;
  private JBLabel myNameLabel;
  private JComboBox myFileNameCombo;
  private JBLabel mySourceSetLabel;
  private JComboBox mySourceSetCombo;
  private JBLabel myFileNameLabel;

  private final Module myModule;
  private final ResourceType myResourceType;

  private Map<String, JCheckBox> myCheckBoxes = Collections.emptyMap();
  private String[] myDirNames = ArrayUtil.EMPTY_STRING_ARRAY;

  private final CheckBoxList myDirectoriesList;
  private VirtualFile myResourceDir;
  private ResourceFolderType myFolderType;

  public CreateXmlResourcePanel(@NotNull Module module,
                                @NotNull ResourceType resourceType,
                                @Nullable String predefinedName,
                                @Nullable String predefinedValue,
                                boolean chooseName,
                                @Nullable VirtualFile defaultFile) {
    this(module, resourceType, defaultFile, ResourceFolderType.VALUES);

    if (chooseName) {
      predefinedName = ResourceHelper.prependResourcePrefix(module, predefinedName);
    }

    if (!StringUtil.isEmpty(predefinedName)) {
      if (chooseName) {
        setChangeNameVisible(true);
      }
      myNameField.setText(predefinedName);
    }
    else {
      setChangeNameVisible(true);
    }

    if (!StringUtil.isEmpty(predefinedValue)) {
      myValueField.setText(predefinedValue);
    }
    else {
      setChangeValueVisible(true);
    }
  }

  public CreateXmlResourcePanel(@NotNull Module module, @NotNull ResourceType resourceType, @Nullable VirtualFile defaultFile,
                                @NotNull ResourceFolderType folderType) {
    setChangeNameVisible(false);
    setChangeValueVisible(false);

    myResourceType = resourceType;
    myFolderType = folderType;

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    assert modulesSet.size() > 0;

    if (modulesSet.size() == 1) {
      myModule = module;
      setChangeModuleVisible(false);
    }
    else {
      myModule = null;
      myModuleCombo.setModules(modulesSet);
      myModuleCombo.setSelectedModule(module);
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    CreateResourceActionBase.updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo,
                                                  modulesSet.size() == 1 ? AndroidFacet.getInstance(modulesSet.iterator().next()) : null,
                                                  myResourceDir != null ? PsiManager.getInstance(module.getProject()).findDirectory(myResourceDir) : null);

    if (defaultFile == null) {
      final String defaultFileName = AndroidResourceUtil.getDefaultResourceFileName(resourceType);

      if (defaultFileName != null) {
        myFileNameCombo.getEditor().setItem(defaultFileName);
      }
    }
    myDirectoriesList = new CheckBoxList();
    myDirectoriesLabel.setLabelFor(myDirectoriesList);
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myDirectoriesList);

    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doAddNewDirectory();
      }
    });

    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doDeleteDirectory();
      }
    });

    final AnActionButton selectAll = new AnActionButton("Select All", null, PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doSelectAllDirs();
      }
    };
    decorator.addExtraAction(selectAll);

    final AnActionButton unselectAll = new AnActionButton("Unselect All", null, PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doUnselectAllDirs();
      }
    };
    decorator.addExtraAction(unselectAll);

    myDirectoriesPanel.add(decorator.createPanel());

    updateDirectories(true);

    addModuleComboActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDirectories(true);
      }
    });

    if (defaultFile != null) {
      resetFromFile(defaultFile, module.getProject());
    }
  }

  public void addModuleComboActionListener(@NotNull ActionListener actionListener) {
    myModuleCombo.addActionListener(actionListener);
  }

  private void resetFromFile(@NotNull VirtualFile file, @NotNull Project project) {
    final Module moduleForFile = ModuleUtilCore.findModuleForFile(file, project);
    if (moduleForFile == null) {
      return;
    }

    final VirtualFile parent = file.getParent();
    if (parent == null) {
      return;
    }

    if (myModule == null) {
      final Module prev = myModuleCombo.getSelectedModule();
      myModuleCombo.setSelectedItem(moduleForFile);

      if (!moduleForFile.equals(myModuleCombo.getSelectedItem())) {
        myModuleCombo.setSelectedModule(prev);
        return;
      }
    }
    else if (!myModule.equals(moduleForFile)) {
      return;
    }

    final JCheckBox checkBox = myCheckBoxes.get(parent.getName());
    if (checkBox == null) {
      return;
    }

    for (JCheckBox checkBox1 : myCheckBoxes.values()) {
      checkBox1.setSelected(false);
    }
    checkBox.setSelected(true);
    myFileNameCombo.getEditor().setItem(file.getName());
  }

  private void doDeleteDirectory() {
    if (myResourceDir == null) {
      return;
    }

    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }

    final String selectedDirName = myDirNames[selectedIndex];
    final VirtualFile selectedDir = myResourceDir.findChild(selectedDirName);
    if (selectedDir == null) {
      return;
    }

    final VirtualFileDeleteProvider provider = new VirtualFileDeleteProvider();
    provider.deleteElement(new DataContext() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (CommonDataKeys.VIRTUAL_FILE_ARRAY.getName().equals(dataId)) {
          return new VirtualFile[]{selectedDir};
        }
        else {
          return null;
        }
      }
    });
    updateDirectories(false);
  }

  private void doSelectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(true);
    }
    myDirectoriesList.repaint();
  }

  private void doUnselectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(false);
    }
    myDirectoriesList.repaint();
  }

  private void doAddNewDirectory() {
    if (myResourceDir == null) {
      return;
    }
    final Module module = getModule();
    if (module == null) {
      return;
    }
    final Project project = module.getProject();
    final PsiDirectory psiResDir = PsiManager.getInstance(project).findDirectory(myResourceDir);

    if (psiResDir != null) {
      final PsiElement[] createdElements = new CreateResourceDirectoryAction(myFolderType).invokeDialog(project, psiResDir);

      if (createdElements.length > 0) {
        updateDirectories(false);
      }
    }
  }

  private void updateDirectories(boolean updateFileCombo) {
    final Module module = getModule();
    List<VirtualFile> directories = Collections.emptyList();

    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null) {
        myResourceDir = facet.getPrimaryResourceDir();

        if (myResourceDir != null) {
          directories = AndroidResourceUtil.getResourceSubdirs(myFolderType.getName(), new VirtualFile[]{myResourceDir});
        }
      }
    }

    Collections.sort(directories, new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile f1, VirtualFile f2) {
        return f1.getName().compareTo(f2.getName());
      }
    });

    final Map<String, JCheckBox> oldCheckBoxes = myCheckBoxes;
    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    final String selectedDirName = selectedIndex >= 0 ? myDirNames[selectedIndex] : null;

    final List<JCheckBox> checkBoxList = new ArrayList<JCheckBox>();
    myCheckBoxes = new HashMap<String, JCheckBox>();
    myDirNames = new String[directories.size()];

    int newSelectedIndex = -1;

    int i = 0;

    for (VirtualFile dir : directories) {
      final String dirName = dir.getName();
      final JCheckBox oldCheckBox = oldCheckBoxes.get(dirName);
      final boolean selected = oldCheckBox != null && oldCheckBox.isSelected();
      final JCheckBox checkBox = new JCheckBox(dirName, selected);
      checkBoxList.add(checkBox);
      myCheckBoxes.put(dirName, checkBox);
      myDirNames[i] = dirName;

      if (dirName.equals(selectedDirName)) {
        newSelectedIndex = i;
      }
      i++;
    }

    String defaultFolderName = myFolderType.getName();
    JCheckBox noQualifierCheckBox = myCheckBoxes.get(defaultFolderName);
    if (noQualifierCheckBox == null) {
      noQualifierCheckBox = new JCheckBox(defaultFolderName);

      checkBoxList.add(0, noQualifierCheckBox);
      myCheckBoxes.put(defaultFolderName, noQualifierCheckBox);

      String[] newDirNames = new String[myDirNames.length + 1];
      newDirNames[0] = defaultFolderName;
      System.arraycopy(myDirNames, 0, newDirNames, 1, myDirNames.length);
      myDirNames = newDirNames;
    }
    noQualifierCheckBox.setSelected(true);

    myDirectoriesList.setModel(new CollectionListModel<JCheckBox>(checkBoxList));

    if (newSelectedIndex >= 0) {
      myDirectoriesList.setSelectedIndex(newSelectedIndex);
    }

    if (updateFileCombo) {
      final Object oldItem = myFileNameCombo.getEditor().getItem();
      final Set<String> fileNameSet = new HashSet<String>();

      for (VirtualFile dir : directories) {
        for (VirtualFile file : dir.getChildren()) {
          fileNameSet.add(file.getName());
        }
      }
      final List<String> fileNames = new ArrayList<String>(fileNameSet);
      Collections.sort(fileNames);
      myFileNameCombo.setModel(new DefaultComboBoxModel(fileNames.toArray()));
      myFileNameCombo.getEditor().setItem(oldItem);
    }
  }

  /**
   * @see CreateXmlResourceDialog#doValidate()
   */
  public ValidationInfo doValidate() {
    final String resourceName = getResourceName();
    final Module selectedModule = getModule();
    final List<String> directoryNames = getDirNames();
    final String fileName = getFileName();

    if (myNameField.isVisible() && resourceName.isEmpty()) {
      return new ValidationInfo("specify resource name", myNameField);
    }
    else if (myNameField.isVisible() && !AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
      return new ValidationInfo(resourceName + " is not correct resource name", myNameField);
    }
    else if (fileName.isEmpty()) {
      return new ValidationInfo("specify file name", myFileNameCombo);
    }
    else if (selectedModule == null) {
      return new ValidationInfo("specify module", myModuleCombo);
    }
    else if (directoryNames.isEmpty()) {
      return new ValidationInfo("choose directories", myDirectoriesList);
    }
    else if (resourceName.equals(ResourceHelper.prependResourcePrefix(myModule, null))) {
      return new ValidationInfo("specify more than resource prefix", myNameField);
    }

    return CreateXmlResourceDialog.checkIfResourceAlreadyExists(selectedModule, resourceName, myResourceType, directoryNames, fileName);
  }

  /**
   * @see CreateXmlResourceDialog#getPreferredFocusedComponent()
   */
  public JComponent getPreferredFocusedComponent() {
    String name = myNameField.getText();
    if (name.isEmpty() || name.equals(ResourceHelper.prependResourcePrefix(myModule, null))) {
      return myNameField;
    }
    else if (myValueField.isVisible()) {
      return myValueField;
    }
    else if (myModuleCombo.isVisible()) {
      return myModuleCombo;
    }
    else {
      return myFileNameCombo;
    }
  }

  @NotNull
  public String getResourceName() {
    return myNameField.getText().trim();
  }

  @NotNull
  public List<String> getDirNames() {
    final List<String> selectedDirs = new ArrayList<String>();

    for (Map.Entry<String, JCheckBox> entry : myCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected()) {
        selectedDirs.add(entry.getKey());
      }
    }
    return selectedDirs;
  }

  @NotNull
  public String getFileName() {
    return ((String)myFileNameCombo.getEditor().getItem()).trim();
  }

  @NotNull
  public String getName() {
    return myNameField.getText().trim();
  }

  @NotNull
  public String getValue() {
    return myValueField.getText().trim();
  }

  @Nullable
  public SourceProvider getSourceProvider() {
    return CreateResourceActionBase.getSourceProvider(mySourceSetCombo);
  }

  @Nullable
  public Module getModule() {
    return myModule != null ? myModule : myModuleCombo.getSelectedModule();
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void setChangeFileNameVisible(boolean isVisible) {
    myFileNameLabel.setVisible(isVisible);
    myFileNameCombo.setVisible(isVisible);
  }

  private void setChangeValueVisible(boolean isVisible) {
    myValueField.setVisible(isVisible);
    myValueLabel.setVisible(isVisible);
  }

  private void setChangeNameVisible(boolean isVisible) {
    myNameField.setVisible(isVisible);
    myNameLabel.setVisible(isVisible);
  }

  private void setChangeModuleVisible(boolean isVisible) {
    myModuleLabel.setVisible(isVisible);
    myModuleCombo.setVisible(isVisible);
  }
}
