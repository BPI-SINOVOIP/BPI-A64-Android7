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
package org.jetbrains.android.run;

import com.android.SdkConstants;
import org.jetbrains.android.AndroidTestCase;

public class SpecificActivityLocatorTest extends AndroidTestCase {
  public SpecificActivityLocatorTest() {
    super(false);
  }

  public void testValidLauncherActivity() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    locator.validate();
  }

  public void testActivityNotDeclared() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/undeclared/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/undeclared/Launcher2.java", "src/com/example/unittest/Launcher2.java");

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher2");
    try {
      locator.validate();
      fail("Validation succeeded even without activity declaration.");
    }
    catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("The activity 'Launcher2' is not declared in AndroidManifest.xml", e.getMessage());
    }
  }

  public void testNonActivity() {
    myFixture.copyFileToProject("projects/runConfig/undeclared/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    try {
      locator.validate();
      fail("Invalid activity accepted");
    } catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("com.example.unittest.Launcher is not an Activity subclass or alias", e.getMessage());
    }
  }

  public void testValidLauncherAlias() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "LauncherAlias");
    locator.validate();
  }

  public void testAliasNotDeclared() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/undeclared/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/undeclared/Launcher.java", "src/com/example/unittest/Launcher.java");
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "NotLaunchable");
    try {
      locator.validate();
      fail("Validation succeeded for activity alias that isn't launchable.");
    } catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("The intent-filter of the activity must contain android.intent.action.MAIN action", e.getMessage());
    }
  }
}
