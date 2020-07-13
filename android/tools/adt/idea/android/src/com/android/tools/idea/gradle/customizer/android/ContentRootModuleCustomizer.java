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

import com.android.builder.model.*;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

/**
 * Sets the content roots of an IDEA module imported from an {@link AndroidProject}.
 */
public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaAndroidProject>
  implements BuildVariantModuleCustomizer<IdeaAndroidProject> {
  // TODO This is a temporary solution. The real fix is in the Android Gradle plug-in we need to take exploded-aar/${library}/${version}/res
  // folder somewhere else out of "exploded-aar" so the IDE can index it, but we need to exclude everything else in "exploded-aar"
  // (e.g. jar files) to avoid unnecessary indexing.
  private static final String[] EXCLUDED_INTERMEDIATE_FOLDER_NAMES = {"assets", "bundles", "classes", "coverage-instrumented-classes",
    "dependency-cache", "dex-cache", "dex", "incremental", "jacoco", "javaResources", "libs", "lint", "manifests", "ndk", "pre-dexed",
    "proguard", "res", "rs", "symbols"};

  @NotNull public static final List<String> EXCLUDED_OUTPUT_FOLDER_NAMES = Lists.newArrayList(FD_OUTPUTS);

  static {
    for (String name : EXCLUDED_INTERMEDIATE_FOLDER_NAMES) {
      EXCLUDED_OUTPUT_FOLDER_NAMES.add(join(FD_INTERMEDIATES, name));
    }
  }

  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel model,
                                                                @NotNull IdeaAndroidProject androidModel) {

    List<ContentEntry> contentEntries = Lists.newArrayList(model.addContentEntry(androidModel.getRootDir()));
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(model.addContentEntry(FilePaths.pathToIdeaUrl(buildFolderPath)));
    }
    return contentEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull ModifiableRootModel ideaModuleModel,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull IdeaAndroidProject androidModel,
                                     @NotNull List<RootSourceFolder> orphans) {
    Variant selectedVariant = androidModel.getSelectedVariant();

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(androidModel, contentEntries, mainArtifact, false, orphans);

    BaseArtifact testArtifact = androidModel.findSelectedTestArtifact(androidModel.getSelectedVariant());
    if (testArtifact != null) {
      addSourceFolders(androidModel, contentEntries, testArtifact, true, orphans);
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidModel.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(androidModel, contentEntries, flavor, orphans);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidModel.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(androidModel, contentEntries, buildTypeContainer.getSourceProvider(), false, orphans);

      Collection<SourceProvider> testSourceProviders =
        androidModel.getSourceProvidersForSelectedTestArtifact(buildTypeContainer.getExtraSourceProviders());


      for (SourceProvider testSourceProvider : testSourceProviders) {
        addSourceFolder(androidModel, contentEntries, testSourceProvider, true, orphans);
      }
    }

    ProductFlavorContainer defaultConfig = androidModel.getAndroidProject().getDefaultConfig();
    addSourceFolder(androidModel, contentEntries, defaultConfig, orphans);

    addExcludedOutputFolders(contentEntries, androidModel);
  }

  private void addSourceFolders(@NotNull IdeaAndroidProject androidModel,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull BaseArtifact artifact,
                                boolean isTest,
                                @NotNull List<RootSourceFolder> orphans) {
    addGeneratedSourceFolders(androidModel, contentEntries, artifact, isTest, orphans);

    SourceProvider variantSourceProvider = artifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, variantSourceProvider, isTest, orphans);
    }

    SourceProvider multiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, multiFlavorSourceProvider, isTest, orphans);
    }
  }

  private void addGeneratedSourceFolders(@NotNull IdeaAndroidProject androidModel,
                                         @NotNull Collection<ContentEntry> contentEntries,
                                         @NotNull BaseArtifact artifact,
                                         boolean isTest,
                                         @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);

    if (artifact instanceof AndroidArtifact || modelVersionIsAtLeast(androidModel, "1.2")) {
      // getGeneratedSourceFolders used to be in AndroidArtifact only.
      Collection<File> generatedSourceFolders = artifact.getGeneratedSourceFolders();

      //noinspection ConstantConditions - this returned null in 1.2
      if (generatedSourceFolders != null) {
        addSourceFolders(androidModel, contentEntries, generatedSourceFolders, sourceType, true, orphans);
      }
    }

    if (artifact instanceof AndroidArtifact) {
      sourceType = getResourceSourceType(isTest);
      addSourceFolders(androidModel, contentEntries, ((AndroidArtifact)artifact).getGeneratedResourceFolders(), sourceType, true,
                       orphans);
    }
  }

  private static boolean modelVersionIsAtLeast(@NotNull IdeaAndroidProject androidModel, @NotNull String revision) {
    String original = androidModel.getAndroidProject().getModelVersion();
    FullRevision modelVersion;
    try {
      modelVersion = FullRevision.parseRevision(original);
    } catch (NumberFormatException e) {
      Logger.getInstance(IdeaAndroidProject.class).warn("Failed to parse '" + original + "'", e);
      return false;
    }
    return modelVersion.compareTo(FullRevision.parseRevision(revision), FullRevision.PreviewComparison.IGNORE) >= 0;
  }

  private void addSourceFolder(@NotNull IdeaAndroidProject androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull ProductFlavorContainer flavor,
                               @NotNull List<RootSourceFolder> orphans) {
    addSourceFolder(androidModel, contentEntries, flavor.getSourceProvider(), false, orphans);

    Collection<SourceProvider> testSourceProviders =
      androidModel.getSourceProvidersForSelectedTestArtifact(flavor.getExtraSourceProviders());

    for (SourceProvider sourceProvider : testSourceProviders) {
      addSourceFolder(androidModel, contentEntries, sourceProvider, true, orphans);
    }
  }

  private void addSourceFolder(@NotNull IdeaAndroidProject androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull SourceProvider sourceProvider,
                               boolean isTest,
                               @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getResDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getResourcesDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAssetsDirectories(), sourceType, false, orphans);

    sourceType = getSourceType(isTest);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAidlDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getJavaDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getCDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getCppDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getRenderscriptDirectories(), sourceType, false, orphans);
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? TEST_RESOURCE : RESOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? TEST_SOURCE : SOURCE;
  }

  private void addSourceFolders(@NotNull IdeaAndroidProject androidModel,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> folderPaths,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated,
                                @NotNull List<RootSourceFolder> orphans) {
    for (File folderPath : folderPaths) {
      if (generated && !isGeneratedAtCorrectLocation(folderPath, androidModel.getAndroidProject())) {
        androidModel.registerExtraGeneratedSourceFolder(folderPath);
      }
      addSourceFolder(contentEntries, folderPath, type, generated, orphans);
    }
  }

  private static boolean isGeneratedAtCorrectLocation(@NotNull File folderPath, @NotNull AndroidProject project) {
    File generatedFolderPath = new File(project.getBuildFolder(), FD_GENERATED);
    return isAncestor(generatedFolderPath, folderPath, false);
  }

  private void addExcludedOutputFolders(@NotNull Collection<ContentEntry> contentEntries, @NotNull IdeaAndroidProject androidModel) {
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    ContentEntry parentContentEntry = findParentContentEntry(buildFolderPath, contentEntries);
    if (parentContentEntry == null) {
      return;
    }

    // Explicitly exclude the output folders created by the Android Gradle plug-in
    for (String folderName : EXCLUDED_OUTPUT_FOLDER_NAMES) {
      File excludedFolderPath = new File(buildFolderPath, folderName);
      addExcludedFolder(parentContentEntry, excludedFolderPath);
    }

    // Iterate through the build folder's children, excluding any folders that are not "generated" and haven't been already excluded.
    File[] children = notNullize(buildFolderPath.listFiles());
    for (File child : children) {
      if (androidModel.shouldManuallyExclude(child)) {
        addExcludedFolder(parentContentEntry, child);
      }
    }
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @Override
  @NotNull
  public Class<IdeaAndroidProject> getSupportedModelType() {
    return IdeaAndroidProject.class;
  }
}
