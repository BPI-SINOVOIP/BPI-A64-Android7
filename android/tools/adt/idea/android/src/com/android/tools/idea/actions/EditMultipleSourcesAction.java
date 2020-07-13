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
package com.android.tools.idea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.components.JBList;

import javax.swing.*;

public class EditMultipleSourcesAction extends AnAction {
  public EditMultipleSourcesAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.actionText("EditSource"));
    presentation.setIcon(AllIcons.Actions.EditSource);
    presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
    // TODO shortcuts
    // setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Navigatable[] navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
    if (navigatables != null && navigatables.length > 0) {
      e.getPresentation().setEnabled(true);
      if (navigatables.length > 1) {
        e.getPresentation().setText(ActionsBundle.actionText("EditSource") + "...");
      }
      else {
        e.getPresentation().setText(ActionsBundle.actionText("EditSource"));
      }
    }
    else {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setText(ActionsBundle.actionText("EditSource"));
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;

    final Navigatable[] files = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
    assert files != null && files.length > 0;

    if (files.length > 1) {
      DefaultListModel listModel = new DefaultListModel();
      for (int i = 0; i < files.length; ++i) {
        assert files[i] instanceof PsiFileAndLineNavigation;
        //noinspection unchecked
        listModel.add(i, ((PsiFileAndLineNavigation)files[i]).getPsiFile());
      }
      final JBList list = new JBList(listModel);
      int width = WindowManager.getInstance().getFrame(project).getSize().width;
      list.setCellRenderer(new GotoFileCellRenderer(width));

      JBPopup popup =
        JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Target File").setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            Object selectedValue = list.getSelectedValue();
            PsiFileAndLineNavigation navigationWrapper = null;
            for (Navigatable file : files) {
              if (selectedValue == ((PsiFileAndLineNavigation)file).getPsiFile()) {
                navigationWrapper = (PsiFileAndLineNavigation)file;
                break;
              }
            }
            assert navigationWrapper != null;
            if (navigationWrapper.canNavigate()) {
              navigationWrapper.navigate(true);
            }
          }
        }).createPopup();

      if (e.getInputEvent().getSource() instanceof ActionButton) {
        popup.showUnderneathOf((ActionButton)e.getInputEvent().getSource());
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
    else {
      assert files[0] instanceof PsiFileAndLineNavigation;
      PsiFileAndLineNavigation file = (PsiFileAndLineNavigation)files[0];
      if (file.canNavigate()) {
        file.navigate(true);
      }
    }
  }
}
