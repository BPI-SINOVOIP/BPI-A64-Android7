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

import com.android.tools.idea.profiling.capture.CaptureType;
import com.android.tools.idea.profiling.capture.CaptureTypeService;
import com.android.tools.idea.profiling.capture.FileCaptureType;
import com.android.tools.idea.profiling.view.CapturesToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.SimpleTree;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.event.KeyEvent;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.swing.timing.Pause.pause;

public class CapturesToolWindowFixture extends ToolWindowFixture {
  @NotNull private JTreeFixture myTreeFixture;

  protected CapturesToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super(CapturesToolWindowFactory.TOOL_WINDOW_ID, project, robot);
    show();

    myTreeFixture = new JTreeFixture(robot, getTree());
  }

  public void openFile(@NotNull final String fileName) throws IOException {
    String pathName = null;
    CaptureType[] captureTypes = CaptureTypeService.getInstance().getCaptureTypes();
    for (CaptureType captureType : captureTypes) {
      if (captureType instanceof FileCaptureType) {
        FileCaptureType fileCaptureType = (FileCaptureType)captureType;
        if (fileCaptureType.isValidCapture(fileName)) {
          pathName = fileCaptureType.getName();
          break;
        }
      }
    }

    if (pathName != null) {
      final String finalPathName = pathName;
      pause(new Condition("Wait for file to be recognized") {
        @Override
        public boolean test() {
          try {
            String fileToSelect = finalPathName + "/" + fileName;
            myTreeFixture.selectPath(fileToSelect);
            // TODO: Use mouse clicks instead of the keyboard when the fixture responds correctly to double clicks,
            //       as this will better model how users interact with the feature in practice.
            myTreeFixture.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_ENTER));
            return true;
          }
          catch (LocationUnavailableException e) {
            return false;
          }
        }
      }, SHORT_TIMEOUT);
    }
  }

  private void show() {
    activate();
    waitUntilIsVisible();
  }

  @NotNull
  private JTree getTree() {
    JComponent component = myToolWindow.getContentManager().getComponent();
    return myRobot.finder().findByType(component, JTree.class);
  }
}
