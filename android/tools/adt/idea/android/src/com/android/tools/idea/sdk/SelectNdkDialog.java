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

package com.android.tools.idea.sdk;

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.SdkPaths.ValidationResult;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SelectNdkDialog extends DialogWrapper {
  private JPanel myPanel;
  private JBLabel myInvalidNdkPathLabel;
  private JRadioButton myRemoveInvalidNdkRadioButton;
  private JRadioButton mySelectValidNdkRadioButton;
  private TextFieldWithBrowseButton myNdkTextFieldWithButton;
  private JRadioButton myDownloadNdkRadioButton;
  private JBLabel myHeaderText;

  private String myNdkPath = "";

  /**
   * Displays NDK selection dialog.
   *
   * @param invalidNdkPath path to the invalid Android Ndk. If {@code null}, no NDK path is set but one is required.
   */
  public SelectNdkDialog(@Nullable String invalidNdkPath, boolean showRemove) {
    super(false);
    init();

    setTitle("Select Android NDK");

    myInvalidNdkPathLabel.setText(invalidNdkPath);

    configureNdkTextField();

    myDownloadNdkRadioButton.setSelected(true);
    myNdkTextFieldWithButton.setEnabled(false);

    if (showRemove) {
      myRemoveInvalidNdkRadioButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          myNdkTextFieldWithButton.setEnabled(!myRemoveInvalidNdkRadioButton.isSelected());
        }
      });
    }
    else {
      myRemoveInvalidNdkRadioButton.setVisible(false);
    }

    if (invalidNdkPath == null) {
      myHeaderText.setText("The project's local.properties doesn't contain an NDK path.");
    }
    else {
      myHeaderText.setText("The project's local.properties file contains an invalid NDK path:");
    }

    mySelectValidNdkRadioButton.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myNdkTextFieldWithButton.setEnabled(mySelectValidNdkRadioButton.isSelected());
      }
    });
  }

  private void configureNdkTextField() {
    myNdkTextFieldWithButton.setTextFieldPreferredWidth(50);

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        for (VirtualFile virtualFile : files) {
          File file = virtualToIoFile(virtualFile);
          ValidationResult validationResult = validateAndroidNdk(file, false);
          if (!validationResult.success) {
            String msg = validationResult.message;
            if (isEmpty(msg)) {
              msg = "Please choose a valid Android NDK directory.";
            }
            throw new IllegalArgumentException(msg);
          }
        }
      }
    };

    if (SystemInfo.isMac) {
      descriptor.withShowHiddenFiles(true);
    }
    descriptor.setTitle("Choose Android NDK Location");

    myNdkTextFieldWithButton.addBrowseFolderListener(null, new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(
      "Select Android NDK Home", null, myNdkTextFieldWithButton, null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT));
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    // To hide the cancel button.
    Action[] actions = super.createActions();
    List<Action> filteredActions = Lists.newArrayList();
    Action cancelAction = getCancelAction();
    for (Action action : actions) {
      if(!action.equals(cancelAction)) {
        filteredActions.add(action);
      }
    }
    return filteredActions.toArray(new Action[filteredActions.size()]);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (!mySelectValidNdkRadioButton.isSelected()) {
      return null;
    }
    String ndkPath = myNdkTextFieldWithButton.getText().trim();
    String ndkError = validateAndroidNdkPath(ndkPath);
    if (ndkError != null) {
      return new ValidationInfo(ndkError, myNdkTextFieldWithButton.getTextField());
    }
    return null;
  }

  @Nullable
  private static String validateAndroidNdkPath(@Nullable String path) {
    if (isEmpty(path)) {
      return "Android NDK path not specified.";
    }

    ValidationResult validationResult = validateAndroidNdk(new File(toSystemDependentName(path)), false);
    if (!validationResult.success) {
      // Show error message in new line. Long lines trigger incorrect layout rendering.
      // See https://code.google.com/p/android/issues/detail?id=78291
      return String.format("Invalid Android NDK path:<br>%1$s", validationResult.message);
    } else {
      return null;
    }
  }

  @Override
  protected void doOKAction() {
    if (myDownloadNdkRadioButton.isSelected()) {
      List<IPkgDesc> requested = ImmutableList.of(PkgDesc.Builder.newNdk(FullRevision.NOT_SPECIFIED).create());
      SdkQuickfixWizard wizard = new SdkQuickfixWizard(null, null, requested);
      wizard.init();
      if (wizard.showAndGet()) {
        File ndk = IdeSdks.getAndroidNdkPath();
        if (ndk != null) {
          myNdkPath = ndk.getPath();
        }
      }
    }
    if (mySelectValidNdkRadioButton.isSelected()) {
      myNdkPath = myNdkTextFieldWithButton.getText();
    }
    super.doOKAction();
  }

  @NotNull
  public String getAndroidNdkPath() {
    return myNdkPath;
  }
}
