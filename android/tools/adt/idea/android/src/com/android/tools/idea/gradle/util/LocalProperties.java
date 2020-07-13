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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Utility methods related to a Gradle project's local.properties file.
 */
public final class LocalProperties {
  @NotNull private final File myFilePath;
  @NotNull private final File myProjectDirPath;
  @NotNull private final Properties myProperties;

  /**
   * Creates a new {@link LocalProperties}. If a local.properties file does not exist, a new one will be created when the method
   * {@link #save()} is invoked.
   *
   * @param project the Android project.
   * @throws IOException if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" in the given project.
   */
  public LocalProperties(@NotNull Project project) throws IOException {
    this(getBaseDirPath(project));
  }

  /**
   * Creates a new {@link LocalProperties}. If a local.properties file does not exist, a new one will be created when the method
   * {@link #save()} is invoked.
   *
   * @param projectDirPath the path of the Android project's root directory.
   * @throws IOException if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" at the given path.
   */
  public LocalProperties(@NotNull File projectDirPath) throws IOException {
    myProjectDirPath = projectDirPath;
    myFilePath = new File(projectDirPath, FN_LOCAL_PROPERTIES);
    myProperties = getProperties(myFilePath);
  }

  /**
   * @return the path of the Android SDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getAndroidSdkPath() {
    return getPath(SDK_DIR_PROPERTY);
  }

  /**
   * @return the path of the Android NDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getAndroidNdkPath() {
    return getPath(NDK_DIR_PROPERTY);
  }

  /**
   * @return the path for the given propery name specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  private File getPath(String property) {
    String path = getProperty(property);
    if (isNotEmpty(path)) {
      if (!isAbsolute(path)) {
        String canonicalPath = toCanonicalPath(new File(myProjectDirPath, toSystemDependentName(path)).getPath());
        File file = new File(canonicalPath);
        if (!file.isDirectory()) {
          // Only accept resolved relative paths if they exist, otherwise just use the path as it was declared in local.properties.
          // When getting a path from another platform (e.g. a Windows path when opening a project on Mac), java.io.File will think that the
          // path is relative and it will prepend the path of the project to it.
          // See https://code.google.com/p/android/issues/detail?id=82184
          return new File(path);
        }
      }
      return new File(toSystemDependentName(path));
    }
    return null;
  }

  public void setAndroidSdkPath(@NotNull Sdk androidSdk) {
    String androidSdkPath = androidSdk.getHomePath();
    assert androidSdkPath != null;
    setAndroidSdkPath(androidSdkPath);
  }

  public void setAndroidSdkPath(@NotNull String androidSdkPath) {
    doSetAndroidSdkPath(toSystemDependentName(androidSdkPath));
  }

  public void setAndroidSdkPath(@NotNull File androidSdkPath) {
    doSetAndroidSdkPath(androidSdkPath.getPath());
  }

  // Sets the path as it is given. When invoked from production code, the path is assumed to be "system dependent".
  @VisibleForTesting
  void doSetAndroidSdkPath(@NotNull String path) {
    myProperties.setProperty(SDK_DIR_PROPERTY, path);
  }

  public void setAndroidNdkPath(@NotNull String androidNdkPath) {
    doSetAndroidNdkPath(toSystemDependentName(androidNdkPath));
  }

  public void setAndroidNdkPath(@Nullable File androidNdkPath) {
    String path = androidNdkPath != null ? androidNdkPath.getPath() : null;
    doSetAndroidNdkPath(path);
  }

  // Sets the path as it is given. When invoked from production code, the path is assumed to be "system dependent".
  @VisibleForTesting
  void doSetAndroidNdkPath(@Nullable String path) {
    if (isNotEmpty(path)) {
      myProperties.setProperty(NDK_DIR_PROPERTY, path);
    }
    else {
      myProperties.remove(NDK_DIR_PROPERTY);
    }
  }

  public boolean hasAndroidDirProperty() {
    String property = getProperty("android.dir");
    return !isNullOrEmpty(property);
  }

  @Nullable
  public String getProperty(@NotNull String key) {
    return myProperties.getProperty(key);
  }

  /**
   * Saves any changes to the underlying local.properties file.
   */
  public void save() throws IOException {
    savePropertiesToFile(myProperties, myFilePath, getHeaderComment());
  }

  @NotNull
  private static String getHeaderComment() {
    String[] lines = {
      "# This file is automatically generated by Android Studio.",
      "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!",
      "#",
      "# This file must *NOT* be checked into Version Control Systems,",
      "# as it contains information specific to your local configuration.",
      "",
      "# Location of the SDK. This is only used by Gradle.",
      "# For customization when using a Version Control System, please read the",
      "# header note."
    };
    return Joiner.on(SystemProperties.getLineSeparator()).join(lines);
  }

  @NotNull
  public File getFilePath() {
    return myFilePath;
  }
}
