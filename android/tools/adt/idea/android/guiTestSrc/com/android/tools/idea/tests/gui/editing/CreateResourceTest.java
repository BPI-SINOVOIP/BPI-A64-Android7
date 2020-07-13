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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import org.junit.Test;

import java.io.IOException;

/** Tests creating new resources */
public class CreateResourceTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testLibraryPrefix() throws IOException {
    // Tests creating a new resource in a library project with a predefined library prefix,
    // and makes sure the prefix is correct, including checking that we don't end up with
    // double prefixes as described in issue http://b.android.com/77421.

    IdeFrameFixture ideFrame = importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = ideFrame.getEditor();
    editor.open("lib/src/main/java/com/android/tools/test/mylibrary/LibraryActivity.java");
    editor.select(editor.findOffset("R.layout.^activity_library"),
                  editor.findOffset("R.layout.activity_library^);"));
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.enterText("x"); // text now says setContentView(R.layout.x), a missing layout

    // Wait for symbol to be marked as unrecognized
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);

    editor.invokeIntentionAction("Create layout resource file");

    CreateResourceFileDialogFixture dialog = CreateResourceFileDialogFixture.find(myRobot);
    // Should automatically prepend library prefix lib1:
    dialog.requireName("lib1_x.xml");
    dialog.clickCancel();

    // Now make sure we don't enter it if we already use that prefix
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.enterText("lib1_y");

    // Wait for symbol to be marked as unrecognized
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 1);

    editor.invokeIntentionAction("Create layout resource file");

    dialog = CreateResourceFileDialogFixture.find(myRobot);
    // Should automatically prepend library prefix lib1:
    dialog.requireName("lib1_y.xml");
    dialog.clickCancel();
  }
}
