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
package com.android.tools.idea.tests.gui.framework.fixture;

import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.android.uipreview.ColorPicker;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Color;

public class ColorPickerFixture extends JPanelFixture {
  JComboBoxFixture myFormat;

  public ColorPickerFixture(Robot robot, ColorPicker target) {
    super(robot, target);
    myFormat = new JComboBoxFixture(robot(), robot().finder().findByType(this.target(), JComboBox.class));
  }

  @NotNull
  public ColorPickerFixture setFormat(@NotNull String format) {
    myFormat.selectItem(format);
    return this;
  }

  @NotNull
  public ColorPickerFixture setColor(@NotNull Color color) {
    String format = myFormat.selectedItem();
    String labelR = "R:";
    String labelG = "G:";
    String labelB = "B:";
    int[] colorComponents = {color.getRed(), color.getGreen(), color.getBlue()};
    if ("HSB".equals(format)) {
      labelR = "H:";
      labelG = "S:";
      float[] floatHSB = Color.RGBtoHSB(colorComponents[0], colorComponents[1], colorComponents[2], null);
      colorComponents[0] = (int)(floatHSB[0] * 360);
      colorComponents[1] = (int)(floatHSB[1] * 100);
      colorComponents[2] = (int)(floatHSB[2] * 100);
    }
    else if ("ARGB".equals(format)) {
      JTextComponentFixture fieldA = new JTextComponentFixture(robot(), robot().finder().findByLabel(this.target(), "A:", JTextField.class));
      fieldA.enterText(Integer.toString(color.getAlpha()));
    }
    JTextComponentFixture fieldR = new JTextComponentFixture(robot(), robot().finder().findByLabel(this.target(), labelR, JTextField.class));
    fieldR.enterText(Integer.toString(colorComponents[0]));
    JTextComponentFixture fieldG = new JTextComponentFixture(robot(), robot().finder().findByLabel(this.target(), labelG, JTextField.class));
    fieldG.enterText(Integer.toString(colorComponents[1]));
    JTextComponentFixture fieldB = new JTextComponentFixture(robot(), robot().finder().findByLabel(this.target(), labelB, JTextField.class));
    fieldB.enterText(Integer.toString(colorComponents[2]));
    return this;
  }

  @NotNull
  public JTextComponentFixture getHexField() {
    return new JTextComponentFixture(robot(), robot().finder().findByLabel(this.target(), "#", JTextField.class));
  }
}
