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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.DependencySetupErrors;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacetConfiguration;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static java.util.Collections.singletonList;

public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaJavaProject> {
  private static final DependencyScope DEFAULT_DEPENDENCY_SCOPE = COMPILE;

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel moduleModel, @NotNull IdeaJavaProject javaProject) {
    List<String> unresolved = Lists.newArrayList();
    for (JavaModuleDependency dependency : javaProject.getJavaModuleDependencies()) {
      updateDependency(moduleModel, dependency);
    }

    for (JarLibraryDependency dependency : javaProject.getJarLibraryDependencies()) {
      if (dependency.isResolved()) {
        updateDependency(moduleModel, dependency);
      }
      else {
        unresolved.add(dependency.getName());
      }
    }

    Module module = moduleModel.getModule();

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(moduleModel.getProject());
    messages.reportUnresolvedDependencies(unresolved, module);

    JavaGradleFacet facet = setAndGetJavaGradleFacet(module);
    File buildFolderPath = javaProject.getBuildFolderPath();
    if (!isGradleProjectModule(module)) {
      JavaModel javaModel = new JavaModel(unresolved, buildFolderPath);
      facet.setJavaModel(javaModel);
    }
    JavaGradleFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaProject.isBuildable();
  }

  private void updateDependency(@NotNull ModifiableRootModel moduleModel, @NotNull JavaModuleDependency dependency) {
    DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());

    String moduleName = dependency.getModuleName();
    ModuleManager moduleManager = ModuleManager.getInstance(moduleModel.getProject());
    Module found = null;
    for (Module module : moduleManager.getModules()) {
      if (moduleName.equals(module.getName())) {
        found = module;
      }
    }
    if (found != null) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(found);
      if (androidFacet == null) {
        ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(found);
        orderEntry.setExported(true);
      } else {
        // If it depends on an android module, we should skip that.
        setupErrors.addInvalidModuleDependency(moduleModel.getModule(), found.getName(), "Java modules cannot depend on Android modules");
      }
      return;
    }
    setupErrors.addMissingModule(moduleName, moduleModel.getModule().getName(), null);
  }

  private void updateDependency(@NotNull ModifiableRootModel moduleModel, @NotNull JarLibraryDependency dependency) {
    DependencyScope scope = parseScope(dependency.getScope());
    File binaryPath = dependency.getBinaryPath();
    if (binaryPath == null) {
      DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());
      setupErrors.addMissingBinaryPath(moduleModel.getModule().getName());
      return;
    }
    String path = binaryPath.getPath();

    // Gradle API doesn't provide library name at the moment.
    String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(path);
    setUpLibraryDependency(moduleModel, name, scope, singletonList(path), asPaths(dependency.getSourcePath()),
                           asPaths(dependency.getJavadocPath()));
  }

  @NotNull
  private static List<String> asPaths(@Nullable File file) {
    return file == null ? Collections.<String>emptyList() : singletonList(file.getPath());
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable String scope) {
    if (scope == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scope.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return DEFAULT_DEPENDENCY_SCOPE;
  }

  @NotNull
  private static JavaGradleFacet setAndGetJavaGradleFacet(Module module) {
    JavaGradleFacet facet = JavaGradleFacet.getInstance(module);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null);
      model.addFacet(facet);
    }
    finally {
      model.commit();
    }
    return facet;
  }
}
