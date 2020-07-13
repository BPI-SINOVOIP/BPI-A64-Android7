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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;

import java.io.*;
import java.util.Properties;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link LocalProperties}.
 */
public class LocalPropertiesTest extends IdeaTestCase {
  private LocalProperties myLocalProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLocalProperties = new LocalProperties(myProject);
  }

  // See https://code.google.com/p/android/issues/detail?id=82184
  public void testGetAndroidSdkPathWithSeparatorDifferentThanPlatformOne() throws IOException {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.doSetAndroidSdkPath(path);
      myLocalProperties.save();

      File actual = myLocalProperties.getAndroidSdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  public void testGetAndroidNdkPathWithSeparatorDifferentThanPlatformOne() throws IOException {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.doSetAndroidNdkPath(path);
      myLocalProperties.save();

      File actual = myLocalProperties.getAndroidNdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  public void testCreateFileOnSave() throws Exception {
    myLocalProperties.save();
    File localPropertiesFile = new File(myProject.getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
    assertTrue(localPropertiesFile.isFile());
  }

  public void testSetAndroidSdkPathWithFile() throws Exception {
    File androidSdkPath = new File(toSystemDependentName("/home/sdk2"));
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath.getPath()), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidSdkPathWithString() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidSdkPathWithSdk() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");

    Sdk sdk = createMock(Sdk.class);
    expect(sdk.getHomePath()).andReturn(androidSdkPath);

    replay(sdk);

    myLocalProperties.setAndroidSdkPath(sdk);

    verify(sdk);

    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidNdkPathWithString() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(androidNdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidNdkPathWithFile() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(androidNdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testUnicodeLoad() throws Exception {
    File localPropertiesFile = new File(myProject.getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
    File tempDir = Files.createTempDir();
    File sdk = new File(tempDir, "\u00C6\u0424");
    sdk.mkdirs();

    Properties outProperties = new Properties();
    outProperties.setProperty(SdkConstants.SDK_DIR_PROPERTY, sdk.getPath());

    // First write properties using the default encoding (which will \\u escape all non-iso-8859 chars)
    PropertiesUtil.savePropertiesToFile(outProperties, localPropertiesFile, null);

    // Read back platform default version of string; confirm that it gets converted properly
    LocalProperties properties1 = new LocalProperties(myProject);
    File sdkPath1 = properties1.getAndroidSdkPath();
    assertNotNull(sdkPath1);
    assertTrue(sdkPath1.exists());
    assertTrue(FileUtil.filesEqual(sdk, sdkPath1));

    // Next write properties using the UTF-8 encoding. Chars will no longer be escaped.
    // Confirm that we read these in properly too.
    Writer writer = new OutputStreamWriter(new FileOutputStream(localPropertiesFile), Charsets.UTF_8);
    outProperties.store(writer, null);

    // Read back platform default version of string; confirm that it gets converted properly
    LocalProperties properties2 = new LocalProperties(myProject);
    File sdkPath2 = properties2.getAndroidSdkPath();
    assertNotNull(sdkPath2);
    assertTrue(sdkPath2.exists());
    assertTrue(FileUtil.filesEqual(sdk, sdkPath2));

    sdk.delete();
    tempDir.delete();
  }
}
