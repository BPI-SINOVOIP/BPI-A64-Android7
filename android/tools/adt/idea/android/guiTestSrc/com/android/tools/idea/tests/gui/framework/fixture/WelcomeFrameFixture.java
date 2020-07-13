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

import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, WelcomeFrame> {
  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof WelcomeFrame && frame.isShowing()) {
        return new WelcomeFrameFixture(robot, (WelcomeFrame)frame);
      }
    }
    throw new ComponentLookupException("Unable to find 'Welcome' window");
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull WelcomeFrame target) {
    super(WelcomeFrameFixture.class, robot, target);
  }

  @NotNull
  public WelcomeFrameFixture clickNewProjectButton() {
    findActionButtonByActionId("WelcomeScreen.CreateNewProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture clickImportProjectButton() {
    findActionButtonByActionId("WelcomeScreen.ImportProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture clickOpenProjectButton() {
    findActionButtonByActionId("WelcomeScreen.OpenProject").click();
    return this;
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), target(), title);
  }
}
