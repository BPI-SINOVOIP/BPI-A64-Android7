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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.repository.License;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;

/**
 * A review step for reviewing the changes about to be made and accepting the required licences.
 */
public class LicenseAgreementStep extends DynamicWizardStepWithDescription {
  private JTextPane myLicenseTextField;
  private Tree myChangeTree;
  private JRadioButton myDeclineRadioButton;
  private JRadioButton myAcceptRadioButton;

  private DefaultTreeModel myTreeModel = new DefaultTreeModel(null);
  private Map<String, Boolean> myAcceptances = Maps.newHashMap();
  private Set<String> myVisibleLicenses = Sets.newHashSet();
  private String myCurrentLicense;
  private Set<License> myLicenses = Sets.newHashSet();

  private final File mySdkRoot;

  public LicenseAgreementStep(@NotNull Disposable disposable) {
    super(disposable);
    Splitter splitter = new Splitter(false, .30f);
    splitter.setHonorComponentsMinimumSize(true);

    myChangeTree = new Tree();
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myChangeTree));

    myLicenseTextField = new JTextPane();
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myLicenseTextField));

    myDeclineRadioButton = new JBRadioButton("Decline");
    myAcceptRadioButton = new JBRadioButton("Accept");

    ButtonGroup optionsGroup = new ButtonGroup();
    optionsGroup.add(myDeclineRadioButton);
    optionsGroup.add(myAcceptRadioButton);

    JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    optionsPanel.add(myDeclineRadioButton);
    optionsPanel.add(myAcceptRadioButton);

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(splitter, BorderLayout.CENTER);
    mainPanel.add(optionsPanel, BorderLayout.SOUTH);

    setBodyComponent(mainPanel);

    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (data != null) {
      mySdkRoot = data.getLocalSdk().getLocation();
    }
    else {
      mySdkRoot = null;
    }
  }

  @Override
  public void init() {
    super.init();
    myChangeTree.setModel(myTreeModel);
    myChangeTree.setShowsRootHandles(false);
    myLicenseTextField.setEditable(false);

    // Initialize radio buttons
    ButtonGroup group = new ButtonGroup();
    group.add(myDeclineRadioButton);
    group.add(myAcceptRadioButton);

    myDeclineRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAcceptances.put(myCurrentLicense, Boolean.FALSE);
        invokeUpdate(null);
        myChangeTree.repaint();
      }
    });

    myAcceptRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAcceptances.put(myCurrentLicense, Boolean.TRUE);
        invokeUpdate(null);
        myChangeTree.repaint();
      }
    });

    myChangeTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode)myChangeTree.getLastSelectedPathComponent();
        if (selected != null && selected.isRoot()) {
          return;
        }
        if (selected != null && !selected.isLeaf()) {
          License license = (License)selected.getUserObject();
          myLicenseTextField.setText(license.getLicense());
          myCurrentLicense = license.getLicenseRef();
        }
        else if (selected != null && !selected.isRoot()) {
          Change change = (Change)selected.getUserObject();
          myLicenseTextField.setText(change.license.getLicense());
          myCurrentLicense = change.license.getLicenseRef();
        }
        if (myAcceptances.get(myCurrentLicense)) {
          myAcceptRadioButton.setSelected(true);
        }
        else {
          myDeclineRadioButton.setSelected(true);
        }
        myLicenseTextField.setCaretPosition(0);
      }
    });

    myChangeTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

        if (row == 0) {
          append("Licenses", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (!leaf) {
          License license = (License)node.getUserObject();

          if (license.getLicenseRef() != null) {
            appendLicenseText(license, license.getLicenseRef());
          }
        }
        else {
          Change change = (Change)node.getUserObject();
          if (change == null) {
            return;
          }
          appendLicenseText(change.license, change.toString());
          setIcon(change.getIcon());
        }
      }

      private void appendLicenseText(@Nullable License license, String text) {
        boolean notAccepted = license != null && !myAcceptances.get(license.getLicenseRef());
        if (notAccepted) {
          append("*", SimpleTextAttributes.ERROR_ATTRIBUTES);
          append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);

        }
      }
    });

    setChanges(createChangesList());
  }

  @Override
  public boolean isStepVisible() {
    return myState.listSize(INSTALL_REQUESTS_KEY) > 0 && !myLicenses.isEmpty();
  }

  @Override
  public boolean validate() {
    for (String licenseRef : myVisibleLicenses) {
      if (!myAcceptances.get(licenseRef)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "License Agreement";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "License Agreement";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Read and agree to the licenses for the components which will be installed";
  }

  private void expandTree() {
    for (int i = 0; i < myChangeTree.getRowCount(); ++i) {
      myChangeTree.expandRow(i);
    }
  }

  private List<Change> createChangesList() {
    List<Change> toReturn = Lists.newArrayList();
    List requestedPackageNames = myState.get(INSTALL_REQUESTS_KEY);
    if (requestedPackageNames != null) {
      for (Object o : requestedPackageNames) {
        IPkgDesc desc = (IPkgDesc)o;
        License license = desc.getLicense();
        if (license == null) { // Android SDK license
          if (desc.getAndroidVersion() != null && desc.getAndroidVersion().isPreview()) {
            license = AndroidSdkLicenseTemporaryData.HARDCODED_ANDROID_PREVIEW_SDK_LICENSE;
          }
          else {
            license = AndroidSdkLicenseTemporaryData.HARDCODED_ANDROID_SDK_LICENSE;
          }
        }
        myLicenses.add(license);
        if (!license.checkAccepted(mySdkRoot)) {
          toReturn.add(new Change(ChangeType.INSTALL, (IPkgDesc)o, license));
        }
      }
    }
    return toReturn;
  }

  public void setChanges(List<Change> changes) {
    Map<String, DefaultMutableTreeNode> licenseNodeMap = Maps.newHashMap();
    myVisibleLicenses.clear();

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultMutableTreeNode firstChild = null;
    for (Change change : changes) {
      String licenseRef = change.license.getLicenseRef();
      myVisibleLicenses.add(licenseRef);
      if (!licenseNodeMap.containsKey(licenseRef)) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(change.license);
        if (firstChild == null) {
          firstChild = n;
        }
        licenseNodeMap.put(licenseRef, n);
        myAcceptances.put(licenseRef, Boolean.FALSE);
        root.add(n);
      }
      licenseNodeMap.get(licenseRef).add(new DefaultMutableTreeNode(change));
    }
    myTreeModel = new DefaultTreeModel(root);
    myChangeTree.setModel(myTreeModel);
    expandTree();
    if (firstChild != null) {
      myChangeTree.setSelectionPath(new TreePath(firstChild.getPath()));
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangeTree;
  }

  public void performFinishingActions() {
    for (License license : myLicenses) {
      license.setAccepted(mySdkRoot);
    }
  }

  protected enum ChangeType {
    INSTALL,
    UPDATE,
    REMOVE
  }

  protected static class Change {
    public ChangeType type;
    public IPkgDesc packageDescription;
    public License license;

    public Change(@NotNull ChangeType type, @NotNull IPkgDesc packageDescription, @NotNull License license) {
      this.type = type;
      this.packageDescription = packageDescription;
      this.license = license;
    }

    @Override
    public String toString() {
      if (packageDescription.getListDescription() != null) {
        return packageDescription.getListDescription();
      } else {
        return "INCORRECT PACKAGE DESCRIPTION";
      }
    }

    public Icon getIcon() {
      switch (type) {
        case INSTALL:
          return AllIcons.Actions.Download;
        case UPDATE:
          return AllIcons.Actions.Refresh;
        case REMOVE:
          return AllIcons.Actions.Cancel;
        default:
          return null;
      }
    }
  }
}
