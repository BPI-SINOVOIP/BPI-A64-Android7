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
import com.android.ddmlib.IDevice;
import org.jetbrains.android.AndroidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultActivityLocator}.
 */
public class DefaultActivityLocatorTest extends AndroidTestCase {
  public DefaultActivityLocatorTest() {
    super(false);
  }

  public void testActivity() throws Exception {
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.Launcher", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  public void testActivityAlias() throws Exception {
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("LauncherAlias", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple activities that with action MAIN and category LAUNCHER, then give
  // preference to the one that also has category DEFAULT
  public void testPreferDefaultCategoryActivity() throws Exception {
    myFixture.copyFileToProject("projects/runConfig/default/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.LauncherAlias", DefaultActivityLocator.computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple launcher activities, then we pick the leanback launcher for a TV device
  public void testLeanbackLauncher() throws Exception {
    myFixture.copyFileToProject("projects/runConfig/tv/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/tv/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    myFixture.copyFileToProject("projects/runConfig/tv/DefaultLauncher.java",
                                "src/com/example/unittest/DefaultLauncher.java");
    myFixture.copyFileToProject("projects/runConfig/tv/TvLauncher.java",
                                "src/com/example/unittest/TvLauncher.java");

    IDevice tv = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(true);
    assertEquals("com.example.unittest.TvLauncher", DefaultActivityLocator.computeDefaultActivity(myFacet, tv));

    IDevice device = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(false);
    assertEquals("com.example.unittest.DefaultLauncher", DefaultActivityLocator.computeDefaultActivity(myFacet, device));
  }
}
