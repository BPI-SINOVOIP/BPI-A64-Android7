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
package org.jetbrains.android.run;

import com.android.tools.idea.templates.AndroidGradleTestCase;

public class AndroidRunConfigurationTest extends AndroidGradleTestCase {
  public void testActivity() throws Exception {
    loadProject("projects/runConfig/activity");
    assertFalse(AndroidRunConfiguration.isWatchFaceApp(myAndroidFacet));
  }

  public void testActivityAlias() throws Exception {
    loadProject("projects/runConfig/alias");
    assertFalse(AndroidRunConfiguration.isWatchFaceApp(myAndroidFacet));
  }

  public void testWatchFaceService() throws Exception {
    loadProject("projects/runConfig/watchface");
    assertTrue(AndroidRunConfiguration.isWatchFaceApp(myAndroidFacet));
  }
}
