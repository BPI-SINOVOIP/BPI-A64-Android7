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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.assertNotNull;

public class IdeSettingsDialogFixture extends IdeaDialogFixture<SettingsDialog> {
  @NotNull
  public static IdeSettingsDialogFixture find(@NotNull Robot robot) {
    return new IdeSettingsDialogFixture(robot, find(robot, SettingsDialog.class, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String expectedTitle = SystemInfo.isMac ? "Preferences" : "Settings";
        return expectedTitle.equals(dialog.getTitle()) && dialog.isShowing();
      }
    }));
  }

  private IdeSettingsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<SettingsDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public List<String> getProjectSettingsNames() {
    return getSettingsNames(ProjectConfigurablesGroup.class);
  }

  private List<String> getSettingsNames(@NotNull Class<? extends ConfigurableGroup> groupType) {
    List<String> names = Lists.newArrayList();
    JPanel optionsEditor = field("myEditor").ofType(JPanel.class).in(getDialogWrapper()).get();
    assertNotNull(optionsEditor);

    List<JComponent> trees = findComponentsOfType(optionsEditor, "com.intellij.openapi.options.newEditor.SettingsTreeView");
    assertThat(trees).hasSize(1);
    JComponent tree = trees.get(0);

    CachingSimpleNode root = field("myRoot").ofType(CachingSimpleNode.class).in(tree).get();
    assertNotNull(root);

    ConfigurableGroup[] groups = field("myGroups").ofType(ConfigurableGroup[].class).in(root).get();
    assertNotNull(groups);
    ConfigurableGroup group = null;
    for (ConfigurableGroup current : groups) {
      if (groupType.isInstance(current)) {
        group = current;
        break;
      }
    }
    assertNotNull(group);
    for (Configurable configurable : group.getConfigurables()) {
      names.add(configurable.getDisplayName());
    }
    return names;
  }

  @NotNull
  private static List<JComponent> findComponentsOfType(@NotNull JComponent parent, @NotNull String typeName) {
    List<JComponent> result = Lists.newArrayList();
    findComponentsOfType(typeName, result, parent);
    return result;
  }

  private static void findComponentsOfType(@NotNull String typeName, @NotNull List<JComponent> result, @Nullable JComponent parent) {
    if (parent == null) {
      return;
    }
    if (parent.getClass().getName().equals(typeName)) {
      result.add(parent);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType(typeName, result, (JComponent)c);
      }
    }
  }
}
