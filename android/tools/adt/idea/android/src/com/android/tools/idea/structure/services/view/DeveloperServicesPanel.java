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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.EditorPanel;
import com.android.tools.idea.structure.services.ServiceCategory;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServices;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SeparatorComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


/**
 * A vertical list of {@link DeveloperServicePanel}s.
 */
public final class DeveloperServicesPanel extends EditorPanel {

  private JPanel myRoot;
  private JComboBox myModuleCombo;
  private JPanel myServicesPanel;
  private JPanel myHeaderPanel;

  // Keep a copy of service panel children so we can iterate over them directly
  private final List<DeveloperServicePanel> myPanelsList = Lists.newArrayList();

  public DeveloperServicesPanel(@NotNull ComboBoxModel moduleList, @NotNull final ServiceCategory serviceCategory) {
    super(new BorderLayout());

    ListCellRendererWrapper<Module> renderer = new ListCellRendererWrapper<Module>() {
      @Override
      public void customize(JList list, Module value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    };
    myModuleCombo.setModel(moduleList);
    myModuleCombo.setRenderer(renderer);

    myServicesPanel.setBorder(new TitledBorder(serviceCategory.getDisplayName()));
    updateServicePanels(serviceCategory);
    myModuleCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        updateServicePanels(serviceCategory);
      }
    });

    add(myRoot);
  }

  @Override
  public void apply() {
    for (DeveloperServicePanel panel : myPanelsList) {
      panel.apply();
    }
  }

  @Override
  public boolean isModified() {
    for (DeveloperServicePanel panel : myPanelsList) {
      if (panel.isModified()) {
        return true;
      }
    }

    return false;
  }

  private void createUIComponents() {
    myServicesPanel = new JPanel(new VerticalFlowLayout());
  }

  private void updateServicePanels(@NotNull ServiceCategory serviceCategory) {
    for (DeveloperServicePanel developerServicePanel : myPanelsList) {
      developerServicePanel.dispose();
    }
    myPanelsList.clear();
    myServicesPanel.removeAll();

    Module module = (Module)myModuleCombo.getSelectedItem();
    if (module == null) {
      return;
    }

    for (DeveloperService service : DeveloperServices.getFor(module, serviceCategory)) {
      myPanelsList.add(new DeveloperServicePanel(service));
    }

    for (DeveloperServicePanel panel : myPanelsList) {
      if (myServicesPanel.getComponentCount() > 0) {
        myServicesPanel.add(new SeparatorComponent());
      }
      myServicesPanel.add(panel);
    }

    // For some reason, requesting a layout and paint update is required here, as otherwise
    // previous content may not be cleared and new content may not be laid out.
    myServicesPanel.validate();
    myServicesPanel.repaint();
  }
}
