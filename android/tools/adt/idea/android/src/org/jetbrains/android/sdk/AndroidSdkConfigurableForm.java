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
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Map;

import static org.jetbrains.android.sdk.AndroidSdkUtils.getAndroidSdkAdditionalData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidSdkConfigurableForm {
  private JComboBox myInternalJdkComboBox;
  private JPanel myContentPanel;
  private JComboBox myBuildTargetComboBox;

  private final DefaultComboBoxModel myJdksModel = new DefaultComboBoxModel();
  private final SdkModel mySdkModel;

  private final DefaultComboBoxModel myBuildTargetsModel = new DefaultComboBoxModel();
  private String mySdkLocation;

  private boolean myFreeze = false;

  public AndroidSdkConfigurableForm(@NotNull SdkModel sdkModel, @NotNull final SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    myInternalJdkComboBox.setModel(myJdksModel);
    myInternalJdkComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Sdk) {
          setText(((Sdk)value).getName());
        }
      }
    });
    myBuildTargetComboBox.setModel(myBuildTargetsModel);

    myBuildTargetComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof IAndroidTarget) {
          setText(AndroidSdkUtils.getTargetPresentableName((IAndroidTarget)value));
        }
        else if (value == null) {
          setText("<html><font color='#" + ColorUtil.toHex(JBColor.RED)+ "'>[none]</font></html>");
        }
      }
    });

    myBuildTargetComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        if (myFreeze) {
          return;
        }
        final IAndroidTarget target = (IAndroidTarget)e.getItem();

        List<OrderRoot> roots = AndroidSdkUtils.getLibraryRootsForTarget(target, mySdkLocation, true);
        Map<OrderRootType, String[]> configuredRoots = new HashMap<OrderRootType, String[]>();

        for (OrderRootType type : OrderRootType.getAllTypes()) {
          if (!AndroidSdkType.getInstance().isRootTypeApplicable(type)) {
            continue;
          }

          final VirtualFile[] oldRoots = sdkModificator.getRoots(type);
          final String[] oldRootPaths = new String[oldRoots.length];

          for (int i = 0; i < oldRootPaths.length; i++) {
            oldRootPaths[i] = oldRoots[i].getPath();
          }

          configuredRoots.put(type, oldRootPaths);
        }

        for (OrderRoot root : roots) {
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            sdkModificator.removeRoot(root.getFile(), root.getType());
          }
          else {
            String[] configuredRootsForType = configuredRoots.get(root.getType());
            if (ArrayUtil.find(configuredRootsForType, root.getFile().getPath()) == -1) {
              sdkModificator.addRoot(root.getFile(), root.getType());
            }
          }
        }
      }
    });
  }

  @NotNull
  public JPanel getContentPanel() {
    return myContentPanel;
  }

  @Nullable
  public Sdk getSelectedSdk() {
    return (Sdk)myInternalJdkComboBox.getSelectedItem();
  }

  @Nullable
  public IAndroidTarget getSelectedBuildTarget() {
    return (IAndroidTarget)myBuildTargetComboBox.getSelectedItem();
  }

  public void init(@Nullable Sdk jdk, Sdk androidSdk, IAndroidTarget buildTarget) {
    updateJdks();

    final String jdkName = jdk != null ? jdk.getName() : null;

    if (androidSdk != null) {
      for (int i = 0; i < myJdksModel.getSize(); i++) {
        if (Comparing.strEqual(((Sdk)myJdksModel.getElementAt(i)).getName(), jdkName)) {
          myInternalJdkComboBox.setSelectedIndex(i);
          break;
        }
      }
    }

    mySdkLocation = androidSdk != null ? androidSdk.getHomePath() : null;
    AndroidSdkData androidSdkData = mySdkLocation != null ? AndroidSdkData.getSdkData(mySdkLocation) : null;

    myFreeze = true;
    updateBuildTargets(androidSdkData, buildTarget);
    myFreeze = false;
  }

  private void updateJdks() {
    myJdksModel.removeAllElements();
    for (Sdk sdk : mySdkModel.getSdks()) {
      if (Jdks.isApplicableJdk(sdk)) {
        myJdksModel.addElement(sdk);
      }
    }
  }

  private void updateBuildTargets(AndroidSdkData androidSdkData, IAndroidTarget buildTarget) {
    myBuildTargetsModel.removeAllElements();

    if (androidSdkData != null) {
      for (IAndroidTarget target : androidSdkData.getTargets()) {
        myBuildTargetsModel.addElement(target);
      }
    }

    if (buildTarget != null) {
      for (int i = 0; i < myBuildTargetsModel.getSize(); i++) {
        IAndroidTarget target = (IAndroidTarget)myBuildTargetsModel.getElementAt(i);
        if (buildTarget.hashString().equals(target.hashString())) {
          myBuildTargetComboBox.setSelectedIndex(i);
          return;
        }
      }
    }
    myBuildTargetComboBox.setSelectedItem(null);
  }

  public void addJavaSdk(Sdk sdk) {
    myJdksModel.addElement(sdk);
  }

  public void removeJavaSdk(Sdk sdk) {
    myJdksModel.removeElement(sdk);
  }

  public void updateJdks(Sdk sdk, String previousName) {
    Sdk[] sdks = mySdkModel.getSdks();
    for (Sdk currentSdk : sdks) {
      if (currentSdk != null && isAndroidSdk(currentSdk)) {
        AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(currentSdk);
        Sdk internalJava = data != null ? data.getJavaSdk() : null;
        if (internalJava != null && Comparing.equal(internalJava.getName(), previousName)) {
          data.setJavaSdk(sdk);
        }
      }
    }
    updateJdks();
  }

  public void internalJdkUpdate(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(sdk);
    if (data == null) {
      return;
    }
    final Sdk javaSdk = data.getJavaSdk();
    if (myJdksModel.getIndexOf(javaSdk) == -1) {
      myJdksModel.addElement(javaSdk);
    }
    else {
      myJdksModel.setSelectedItem(javaSdk);
    }
  }
}
