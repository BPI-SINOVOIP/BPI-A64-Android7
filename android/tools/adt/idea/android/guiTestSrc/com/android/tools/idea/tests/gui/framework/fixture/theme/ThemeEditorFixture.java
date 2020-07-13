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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.AttributesPanel;
import com.android.tools.idea.editors.theme.ThemeEditor;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.ThemeEditorTable;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.google.common.collect.ImmutableList;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ThemeEditorFixture extends ComponentFixture<ThemeEditorFixture, ThemeEditorComponent> {
  private final JComboBoxFixture myThemesComboBox;
  private final JComboBoxFixture myModulesComboBox;

  public ThemeEditorFixture(@NotNull Robot robot, @NotNull ThemeEditorComponent themeEditorComponent) {
    super(ThemeEditorFixture.class, robot, themeEditorComponent);
    myThemesComboBox = new JComboBoxFixture(robot(), robot().finder().findByName(this.target().getSecondComponent(), AttributesPanel.THEME_SELECTOR_NAME, JComboBox.class));
    myModulesComboBox = new JComboBoxFixture(robot(), robot().finder().findByName(this.target().getSecondComponent(), AttributesPanel.MODULE_SELECTOR_NAME, JComboBox.class));
  }

  @NotNull
  public JComboBoxFixture getThemesComboBox() {
    return myThemesComboBox;
  }

  public JComboBoxFixture getModulesComboBox() {
    return myModulesComboBox;
  }

  @NotNull
  public ThemeEditorTableFixture getPropertiesTable() {
    return ThemeEditorTableFixture.find(robot());
  }

  @NotNull
  public AndroidThemePreviewPanelFixture getThemePreviewPanel() {
    return new AndroidThemePreviewPanelFixture(robot(), robot().finder()
      .findByType(this.target().getFirstComponent(), AndroidThemePreviewPanel.class));
  }

  @NotNull
  public List<String> getThemesList() {
    return ImmutableList.copyOf(myThemesComboBox.contents());
  }

  @NotNull
  public List<String> getModulesList() {
    return ImmutableList.copyOf(myModulesComboBox.contents());
  }

  public void waitForThemeSelection(@NotNull final String themeName) {
    pause(new Condition("Waiting for " + themeName + " to be selected") {
      @Override
      public boolean test() {
        return themeName.equals(myThemesComboBox.selectedItem());
      }
    }, GuiTests.SHORT_TIMEOUT);
  }

  @NotNull
  public JButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilFound(robot(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        return tooltip.equals(button.getToolTipText());
      }
    });
  }

  @Nullable
  private Configuration getConfiguration() {
    return getThemePreviewPanel().target().getConfiguration();
  }

  @NotNull
  public ThemeEditorFixture requireDevice(@NotNull String id) {
    Configuration configuration = getConfiguration();
    assertNotNull(configuration);
    Device device = configuration.getDevice();
    assertNotNull(device);
    assertEquals(id, device.getId());
    return this;
  }

  @NotNull
  public ThemeEditorFixture requireApi(int apiLevel) {
    Configuration configuration = getConfiguration();
    assertNotNull(configuration);
    IAndroidTarget androidTarget = configuration.getTarget();
    assertNotNull(androidTarget);
    assertEquals(apiLevel, androidTarget.getVersion().getApiLevel());
    return this;
  }
}

