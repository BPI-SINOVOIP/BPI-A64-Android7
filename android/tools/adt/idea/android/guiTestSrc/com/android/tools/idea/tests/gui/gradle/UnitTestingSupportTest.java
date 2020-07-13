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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.UnitTestTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@BelongsToTestGroups({TestGroup.UNIT_TESTING, TestGroup.PROJECT_SUPPORT})
public class UnitTestingSupportTest extends GuiTestCase {
  protected IdeFrameFixture myProjectFrame;
  private EditorFixture myEditor;

  @Test @IdeGuiTest
  public void appModule_defaultMake() throws Exception {
    doTest("Make", "app/src/test/java/com/android/tests", "UnitTest");
  }
  
  @Test @IdeGuiTest
  public void appModule_gradleAwareMake() throws Exception {
    doTest("Gradle-aware Make", "app/src/test/java/com/android/tests", "UnitTest");
  }

  @Test @IdeGuiTest
  public void libModule_defaultMake() throws Exception {
    doTest("Make", "lib/src/test/java/com/android/tests/lib", "LibUnitTest");
  }

  @Test @IdeGuiTest
  public void libModule_gradleAwareMake() throws Exception {
    doTest("Gradle-aware Make", "lib/src/test/java/com/android/tests/lib", "LibUnitTest");
  }
  
  /**
   * This covers all functionality that we expect from AS when it comes to unit tests:
   *
   * <ul>
   *   <li>Tests can be run from the editor.
   *   <li>Results are correctly reported in the Run window.
   *   <li>The classpath when running tests is correct.
   *   <li>You can fix a test and changes are picked up the next time tests are run (which means the correct gradle tasks are run).
   * </ul>
   */
  private void doTest(@NotNull String makeStepName, @NotNull String path, @NotNull String testClass) throws Exception {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("ProjectWithUnitTests");
    myProjectFrame.setJUnitDefaultBeforeRunTask(makeStepName);

    BuildVariantsToolWindowFixture buildVariants = myProjectFrame.getBuildVariantsWindow();
    buildVariants.activate();
    buildVariants.selectUnitTests();

    // Open the test file:
    myEditor = myProjectFrame.getEditor();
    myEditor.open(path + "/" + testClass + ".java");

    // Run the test case that is supposed to pass:
    myEditor.moveTo(myEditor.findOffset("passing", "Test", true));

    runTestUnderCursor();

    UnitTestTreeFixture unitTestTree = getTestTree(testClass + ".passingTest");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Run the test that is supposed to fail:
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("failing", "Test", true));

    runTestUnderCursor();

    unitTestTree = getTestTree(testClass + ".failingTest");
    assertEquals(1, unitTestTree.getFailingTestsCount());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Fix the failing test and re-run the tests.
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("(7", ",", true));
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("6");

    runTestUnderCursor();
    myProjectFrame.waitForBackgroundTasksToFinish();
    unitTestTree = getTestTree(testClass + ".failingTest");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Run the whole class, it should pass now.
    myEditor.moveTo(myEditor.findOffset("class ", testClass, true));

    runTestUnderCursor();

    unitTestTree = getTestTree(testClass);
    assertTrue(unitTestTree.isAllTestsPassed());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);

    // Break the test again to check the re-run buttons.
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("(6", ",", true));
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("8");

    // Re-run all the tests.
    unitTestTree.getContent().rerun();
    myProjectFrame.waitForBackgroundTasksToFinish();
    unitTestTree = getTestTree(testClass);
    assertEquals(1, unitTestTree.getFailingTestsCount());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);

    // Fix it again.
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("(8", ",", true));
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("6");

    // Re-run failed tests.
    unitTestTree.getContent().rerunFailed();
    myProjectFrame.waitForBackgroundTasksToFinish();
    unitTestTree = getTestTree("Rerun Failed Tests");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Rebuild the project and run tests again, they should still run and pass.
    myProjectFrame.invokeMenuPath("Build", "Rebuild Project");
    myProjectFrame.waitForBackgroundTasksToFinish();

    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("class ", testClass, true));
    runTestUnderCursor();
    unitTestTree = getTestTree(testClass);
    assertTrue(unitTestTree.isAllTestsPassed());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);
  }

  @NotNull
  private UnitTestTreeFixture getTestTree(@NotNull String tabName) {
    ContentFixture content = myProjectFrame.getRunToolWindow().findContent(tabName);
    content.waitForExecutionToFinish(GuiTests.SHORT_TIMEOUT);
    myProjectFrame.waitForBackgroundTasksToFinish();
    return content.getUnitTestTree();
  }

  private void runTestUnderCursor() {
    // This only works when there's one applicable run configurations, otherwise a popup would show up.
    myEditor.invokeAction(EditorFixture.EditorAction.RUN_FROM_CONTEXT);
    myProjectFrame.waitForBackgroundTasksToFinish();
  }
}
