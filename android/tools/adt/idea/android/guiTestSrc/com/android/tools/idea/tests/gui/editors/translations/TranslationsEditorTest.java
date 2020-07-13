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
package com.android.tools.idea.tests.gui.editors.translations;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.TranslationsEditorFixture;
import com.intellij.ui.components.JBLoadingPanel;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.FontFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.*;

public class TranslationsEditorTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testBasics() throws IOException {
    IdeFrameFixture ideFrame = importSimpleApplication();

    // open editor on a strings file
    String stringsXmlPath = "app/src/main/res/values/strings.xml";
    EditorFixture editor = ideFrame.getEditor();
    editor.open(stringsXmlPath, EDITOR);

    // make sure the notification is visible, and click on Open Editor to open the translations editor
    EditorNotificationPanelFixture notificationPanel =
      ideFrame.requireEditorNotification("Edit translations for all locales in the translations editor.");
    notificationPanel.performAction("Open editor");

    // Wait for the translations editor table to show up, and the table to be initialized
    waitUntilFound(myRobot, new GenericTypeMatcher<JTable>(JTable.class) {
      @Override
      protected boolean isMatching(@NotNull JTable table) {
        return table.getModel() != null && table.getModel().getColumnCount() > 0;
      }
    });

    // Now obtain the fixture for the displayed translations editor
    TranslationsEditorFixture txEditor = editor.getTranslationsEditor();
    assertNotNull(txEditor);

    List<String> locales = txEditor.locales();
    Object[] expectedLocales = {"English (en)", "English (en) in United Kingdom (GB)", "Tamil (ta)", "Chinese (zh) in China (CN)"};
    assertThat(locales).containsSequence(expectedLocales);

    List<String> keys = txEditor.keys();
    assertThat(keys).containsSequence("action_settings", "app_name", "cancel", "hello_world");

    JTableCellFixture cancel = txEditor.cell(TableCell.row(2).column(6)); // cancel in zh-rCN
    assertEquals("取消", cancel.value());

    // validate that the font used can actually display these characters
    // Note: this test will always pass on Mac, but will fail on Windows & Linux if the appropriate fonts were not set by the renderer
    // See FontUtil.getFontAbleToDisplay()
    final FontFixture font = cancel.font();
    //noinspection ConstantConditions
    assertTrue("Font " + font.target().getName() + " cannot display Chinese characters.", execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        return font.target().canDisplay('消');
      }
    }));
  }
}
