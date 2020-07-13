/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.folding;

import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.AndroidTestCase;

public class ResourceFoldingBuilderTest extends AndroidTestCase {

  public void testJavaStrings() throws Throwable { performTest(".java"); }
  public void testJavaStrings2() throws Throwable { performTest(".java"); }
  public void testJavaDimens() throws Throwable { performTest(".java"); }
  public void testXmlString() throws Throwable { performTest(".xml"); }
  public void testPlurals() throws Throwable { performTest(".java"); }

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public ResourceFoldingBuilderTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  private void performTest(String extension) throws Throwable {
    myFixture.copyFileToProject(getTestDataPath() + "/folding/values.xml", "res/values/values.xml");
    myFixture.testFoldingWithCollapseStatus(getTestDataPath() + "/folding/" + getTestName(true) + extension);
  }
}
