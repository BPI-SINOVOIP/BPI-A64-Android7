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
package com.android.tools.idea.model;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A common interface for Android module models.
 */
public interface AndroidModel {
  /**
   * @return the currently selected main Android artifact produced by this Android module.
   * TODO: Remove this method - it exposes Gradle-specific AndroidArtifact.
   */
  @Deprecated
  @NotNull
  AndroidArtifact getMainArtifact();

  /**
   * @return the default source provider.
   * TODO: To be build-system-agnostic, simplify source provider usage.
   * {@link org.jetbrains.android.facet.AndroidFacet#getMainSourceProvider()}
   */
  @Deprecated
  @NotNull
  SourceProvider getDefaultSourceProvider();

  /**
   * @return the currently active (non-test) source providers for this Android module in
   * overlay order (meaning that later providers override earlier providers when they redefine
   * resources).
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getCurrentSourceProviders}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getActiveSourceProviders();

  /**
   * @return the currently active test source providers for this Android module in overlay order.
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getCurrentTestSourceProviders}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getTestSourceProviders();

  /**
   * @return all of the non-test source providers, including those that are not currently active.
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getAllSourceProviders(AndroidFacet)}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getAllSourceProviders();

  /**
   * @return the current application ID.
   */
  @NotNull
  String getApplicationId();

  /**
   * @return all the application IDs of artifacts this Android module could produce.
   */
  @NotNull
  Set<String> getAllApplicationIds();

  /**
   * @return whether the manifest package is overriden.
   * TODO: Potentially dedupe with computePackageName.
   */
  @Deprecated
  boolean overridesManifestPackage();

  /**
   * @return whether the application is debuggable, or null if not specified.
   */
  Boolean isDebuggable();

  /**
   * @return the minimum supported SDK version.
   * {@link AndroidModuleInfo#getMinSdkVersion()}
   */
  @Nullable
  AndroidVersion getMinSdkVersion();

  /**
   * @return the target SDK version.
   * {@link AndroidModuleInfo#getTargetSdkVersion()}
   */
  @Nullable
  AndroidVersion getTargetSdkVersion();
}
