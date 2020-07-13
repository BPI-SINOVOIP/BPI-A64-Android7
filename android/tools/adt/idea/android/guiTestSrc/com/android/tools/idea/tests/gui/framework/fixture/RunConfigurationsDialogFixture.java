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

import com.android.tools.idea.tests.gui.framework.matcher.ClassNameMatcher;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.driver.BasicJListCellReader;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.reflect.core.Reflection.method;

/**
 * Controls the Run Configurations dialog
 */
public class RunConfigurationsDialogFixture extends ComponentFixture<RunConfigurationsDialogFixture, JDialog> {

  public RunConfigurationsDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(RunConfigurationsDialogFixture.class, robot, target);
  }

  @NotNull
  public static RunConfigurationsDialogFixture find(@NotNull Robot robot) {
    JDialog frame = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Run/Debug Configurations".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new RunConfigurationsDialogFixture(robot, frame);
  }

  @NotNull
  private JButton findButtonByText(@NotNull String text) {
    return robot().finder().find(target(), JButtonMatcher.withText(text).andShowing());
  }

  public void save() {
    robot().click(findButtonByText("OK"));
  }

  public JTreeFixture getTreeFixture() {
    Tree tree = robot().finder().findByType(target(), Tree.class, true);
    JTreeFixture fixture = new JTreeFixture(robot(), tree);
    fixture.replaceCellReader(new RunConfigurationsJTreeCellReader());
    return fixture;
  }

  public void select(String path) {
    getTreeFixture().selectPath(path);
  }

  public void useGradleAwareMake() {
    JBList stepsList = robot().finder().findByType(target(), JBList.class);
    JListFixture stepsFixture = new JListFixture(robot(), stepsList);
    // Find ActionToolbarImpl and ActionButtons inside.
    if (!stepsFixture.contents()[0].equals("Make")) {
      throw new IllegalStateException("There should be only only one make step, 'Make'.");
    }

    stepsFixture.clickItem(0);
    JPanel beforeRunStepsPanel = robot().finder().find(
      target(),
      ClassNameMatcher.forClass("com.intellij.execution.impl.BeforeRunStepsPanel", JPanel.class, true));

    ActionButtonFixture.findByText("Remove", robot(), beforeRunStepsPanel).click();
    ActionButtonFixture.findByText("Add", robot(), beforeRunStepsPanel).click();

    JBList popupList = robot().finder().find(
      target(),
      ClassNameMatcher.forClass("com.intellij.ui.popup.list.ListPopupImpl$MyList", JBList.class, true));

    click(popupList, "Gradle-aware Make");

    Dialog gradleMakeDialog = robot().finder().find(DialogMatcher.withTitle("Select Gradle Task").andShowing());
    robot().click(robot().finder().find(gradleMakeDialog, JButtonMatcher.withText("OK")));
  }

  private void click(final JBList popupList, String text) {
    JListFixture popupFixture = new JListFixture(robot(), popupList);
    popupFixture.replaceCellReader(new MakeStepsCellReader());
    final int index = popupFixture.item(text).index();

    // For some reason calling popupFixture.click(...) doesn't work, but this does:
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        popupList.setSelectedIndex(index);
      }
    });
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }

  /** {@link JTreeCellReader} that works with this particular tree. */
  private static class RunConfigurationsJTreeCellReader implements JTreeCellReader {
    @Nullable
    @Override
    public String valueAt(@NotNull JTree tree, Object modelValue) {
      Object userObject = method("getUserObject").withReturnType(Object.class).in(modelValue).invoke();
      if (userObject == null) {
        return null;
      }

      if (userObject instanceof ConfigurationType) {
        return ((ConfigurationType)userObject).getDisplayName();
      }

      return userObject.toString();
    }
  }

  private static class MakeStepsCellReader extends BasicJListCellReader {
    @Nullable
    @Override
    public String valueAt(@NotNull JList list, int index) {
      Object element = list.getModel().getElementAt(index);
      if (element instanceof ActionItem) {
        return ((ActionItem)element).getText();
      }
      return super.valueAt(list, index);
    }
  }
}
