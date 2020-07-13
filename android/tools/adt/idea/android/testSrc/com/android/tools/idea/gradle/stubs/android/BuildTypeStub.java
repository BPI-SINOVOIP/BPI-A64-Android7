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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BuildTypeStub implements BuildType {
  @NotNull private final String myName;

  BuildTypeStub(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean isDebuggable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTestCoverageEnabled() {
    return false;
  }

  @Override
  public boolean isPseudoLocalesEnabled() {
    return false;
  }

  @Override
  public boolean isJniDebuggable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRenderscriptDebuggable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRenderscriptOptimLevel() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getApplicationIdSuffix() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isMinifyEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isZipAlignEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmbedMicroApp() {
    throw new UnsupportedOperationException();
  }

  @com.android.annotations.Nullable
  @Override
  public SigningConfig getSigningConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public Collection<File> getProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<File> getConsumerProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Boolean getMultiDexEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public File getMultiDexKeepFile() {
    return null;
  }

  @Override
  @Nullable
  public File getMultiDexKeepProguard() {
    return null;
  }

  @Override
  @NotNull
  public List<File> getJarJarRuleFiles() {
    return Collections.emptyList();
  }
}
