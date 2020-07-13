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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtilRt.getRelativePath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Adds the Android facet to modules imported from {@link AndroidProject}s.
 */
public class AndroidFacetModuleCustomizer implements ModuleCustomizer<IdeaAndroidProject> {

  // It is safe to use "/" instead of File.separator. JpsAndroidModule uses it.
  private static final String SEPARATOR = "/";

  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull ModifiableRootModel moduleModel,
                              @Nullable IdeaAndroidProject androidModel) {
    Module module = moduleModel.getModule();
    if (androidModel == null) {
      removeAllFacetsOfType(module, AndroidFacet.ID);
    }
    else {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        configureFacet(facet, androidModel);
      }
      else {
        // Module does not have Android facet. Create one and add it.
        FacetManager facetManager = FacetManager.getInstance(module);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
          model.addFacet(facet);
          configureFacet(facet, androidModel);
        } finally {
          model.commit();
        }
      }
    }
  }

  private static void configureFacet(@NotNull AndroidFacet facet, @NotNull IdeaAndroidProject androidModel) {
    JpsAndroidModuleProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;

    AndroidProject androidProject = androidModel.getAndroidProject();
    facetState.LIBRARY_PROJECT = androidProject.isLibrary();

    SourceProvider sourceProvider = androidModel.getDefaultSourceProvider();

    syncSelectedVariantAndTestArtifact(facetState, androidModel);

    // This code needs to be modified soon. Read the TODO in getRelativePath
    File moduleDirPath = androidModel.getRootDirPath();
    File manifestFile = sourceProvider.getManifestFile();
    facetState.MANIFEST_FILE_RELATIVE_PATH = relativePath(moduleDirPath, manifestFile);

    Collection<File> resDirs = sourceProvider.getResDirectories();
    facetState.RES_FOLDER_RELATIVE_PATH = relativePath(moduleDirPath, resDirs);

    Collection<File> assetsDirs = sourceProvider.getAssetsDirectories();
    facetState.ASSETS_FOLDER_RELATIVE_PATH = relativePath(moduleDirPath, assetsDirs);

    facet.setAndroidModel(androidModel);
    facet.syncSelectedVariantAndTestArtifact();
  }

  private static void syncSelectedVariantAndTestArtifact(@NotNull JpsAndroidModuleProperties facetState,
                                                         @NotNull IdeaAndroidProject androidModel) {
    String variantStoredInFacet = facetState.SELECTED_BUILD_VARIANT;
    if (!isNullOrEmpty(variantStoredInFacet) && androidModel.getVariantNames().contains(variantStoredInFacet)) {
      androidModel.setSelectedVariantName(variantStoredInFacet);
    }

    String testArtifactStoredInFacet = facetState.SELECTED_TEST_ARTIFACT;
    if (!isNullOrEmpty(testArtifactStoredInFacet)) {
      androidModel.setSelectedTestArtifactName(testArtifactStoredInFacet);
    }
  }

  // We are only getting the relative path of the first file in the collection, because JpsAndroidModuleProperties only accepts one path.
  // TODO(alruiz): Change JpsAndroidModuleProperties (and callers) to use multiple paths.
  @NotNull
  private static String relativePath(@NotNull File basePath, @NotNull Collection<File> dirs) {
    return relativePath(basePath, getFirstItem(dirs));
  }

  @NotNull
  private static String relativePath(@NotNull File basePath, @Nullable File file) {
    String relativePath = null;
    if (file != null) {
      relativePath = getRelativePath(basePath, file);
    }
    if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
      return SEPARATOR + toSystemIndependentName(relativePath);
    }
    return "";
  }
}
