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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.testFramework.CompositeException;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Tests for {@link SdkSync}.
 */
public class SdkSyncTest extends IdeaTestCase {
  private LocalProperties myLocalProperties;
  private File myAndroidSdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidTestCaseHelper.removeExistingAndroidSdks();
    myLocalProperties = new LocalProperties(myProject);
    myAndroidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();

    assertNull(IdeSdks.getAndroidSdkPath());
  }

  @Override
  protected CompositeException checkForSettingsDamage() throws Exception {
    // For this test we don't care about checking for settings damage.
    return new CompositeException();
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndNoProjectSdk() throws Exception {
    IdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);

    SdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndInvalidProjectSdk() throws Exception {
    IdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);

    myLocalProperties.setAndroidSdkPath(new File("randomPath"));
    myLocalProperties.save();

    SdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithNoIdeSdkAndValidProjectSdk() throws Exception {
    myLocalProperties.setAndroidSdkPath(myAndroidSdkPath);
    myLocalProperties.save();

    SdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenUserSelectsValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return myAndroidSdkPath;
      }
    };
    SdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, task);

    assertProjectSdkSet();
    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenUserDoesNotSelectValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return null;
      }
    };
    try {
      SdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, task);
      fail("Expecting ExternalSystemException");
    } catch (ExternalSystemException e) {
      // expected
    }

    assertNull(IdeSdks.getAndroidSdkPath());
    myLocalProperties = new LocalProperties(myProject);
    assertNull(myLocalProperties.getAndroidSdkPath());
  }

  private void assertDefaultSdkSet() {
    File actual = IdeSdks.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void assertProjectSdkSet() throws Exception {
    myLocalProperties = new LocalProperties(myProject);
    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }
}
