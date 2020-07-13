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
package com.android.tools.idea.uibuilder.structure;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlStructurePanel extends JPanel implements LightToolWindowContent {
  private final NlComponentTree myTree;
  private final NlPropertiesPanel myPropertiesPanel;

  public NlStructurePanel(@NonNull DesignSurface designSurface) {
    myTree = new NlComponentTree(designSurface);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myPropertiesPanel = new NlPropertiesPanel(designSurface);
    Splitter splitter = new Splitter(true, 0.4f);
    splitter.setFirstComponent(pane);
    splitter.setSecondComponent(myPropertiesPanel);
    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myTree.setDesignSurface(designSurface);
    myPropertiesPanel.setDesignSurface(designSurface);
  }

  public JComponent getPanel() {
    return this;
  }

  @Override
  public void dispose() {
    myTree.dispose();
  }
}
