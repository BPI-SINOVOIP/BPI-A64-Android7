/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * @author yole
 */
public class ChooseLocaleAction extends ComboBoxAction {
  private GuiEditor myLastEditor;
  private Presentation myPresentation;

  public ChooseLocaleAction() {
    getTemplatePresentation().setText("");
    getTemplatePresentation().setDescription(UIDesignerBundle.message("choose.locale.description"));
    getTemplatePresentation().setIcon(UIDesignerIcons.ChooseLocale);
  }

  @Override public JComponent createCustomComponent(Presentation presentation) {
    myPresentation = presentation;
    return super.createCustomComponent(presentation);
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    GuiEditor editor = myLastEditor;
    if (editor != null) {
      Locale[] locales = FormEditingUtil.collectUsedLocales(editor.getModule(), editor.getRootContainer());
      if (locales.length > 1 || (locales.length == 1 && locales [0].getDisplayName().length() > 0)) {
        Arrays.sort(locales, new Comparator<Locale>() {
          public int compare(final Locale o1, final Locale o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
          }
        });
        for(Locale locale: locales) {
          group.add(new SetLocaleAction(editor, locale, true));
        }
      }
      else {
        group.add(new SetLocaleAction(editor, new Locale(""), false));
      }
    }
    return group;
  }

  @Nullable private GuiEditor getEditor(final AnActionEvent e) {
    myLastEditor = FormEditingUtil.getActiveEditor(e.getDataContext());
    return myLastEditor;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getEditor(e) != null);
  }

  private class SetLocaleAction extends AnAction {
    private final GuiEditor myEditor;
    private final Locale myLocale;
    private final boolean myUpdateText;

    public SetLocaleAction(final GuiEditor editor, final Locale locale, final boolean updateText) {
      super(locale.getDisplayName().length() == 0
            ? UIDesignerBundle.message("choose.locale.default")
            : locale.getDisplayName());
      myUpdateText = updateText;
      myEditor = editor;
      myLocale = locale;
    }

    public void actionPerformed(AnActionEvent e) {
      myEditor.setStringDescriptorLocale(myLocale);
      if (myUpdateText) {
        myPresentation.setText(getTemplatePresentation().getText());
      }
    }
  }
}
