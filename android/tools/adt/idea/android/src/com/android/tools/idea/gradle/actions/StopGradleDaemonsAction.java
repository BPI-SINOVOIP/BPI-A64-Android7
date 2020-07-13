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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;

import java.io.IOException;

/**
 * Internal action that stops all running Gradle daemons.
 */
public class StopGradleDaemonsAction extends DumbAwareAction {
  private static final String TITLE = "Stop Gradle Daemons";

  public StopGradleDaemonsAction() {
    super(TITLE);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    try {
      GradleUtil.stopAllGradleDaemons(true);
    }
    catch (IOException error) {
      Messages.showErrorDialog("Failed to stop Gradle daemons. Please run 'gradle --stop' from the command line.\n\n" +
                               "Cause:\n" + error.getMessage(), TITLE
      );
    }
  }
}
