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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.JavaArtifact;
import com.android.sdklib.IAndroidTarget;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.pathsEqual;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * Implementation of {@link JUnitPatcher} that removes android.jar from the class path. It's only applicable to
 * JUnit run configurations if the selected test artifact is "unit tests". In this case, the mockable android.jar is already in the
 * dependencies (taken from the model).
 */
public class AndroidJunitPatcher extends JUnitPatcher {
  @Override
  public void patchJavaParameters(@Nullable Module module, @NotNull JavaParameters javaParameters) {
    if (module == null) {
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return;
    }

    IdeaAndroidProject androidModel = androidFacet.getAndroidModel();
    if (androidModel == null) {
      return;
    }

    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();
    if (testArtifact == null) {
      return;
    }

    // Modify the class path only if we're dealing with the unit test artifact.
    if (!AndroidProject.ARTIFACT_UNIT_TEST.equals(testArtifact.getName()) || !(testArtifact instanceof JavaArtifact)) {
      return;
    }

    PathsList classPath = javaParameters.getClassPath();

    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      return;
    }

    String originalClassPath = classPath.getPathsString();
    try {
      handlePlatformJar(classPath, platform, (JavaArtifact)testArtifact);
      handleJavaResources(module, androidModel, classPath);
    }
    catch (Exception e) {
      throw new RuntimeException(String.format("Error patching the JUnit class path. Original class path:%n%s", originalClassPath),
                                 e);
    }
  }

  // Removes real android.jar from the classpath and puts the mockable one at the end.
  private static void handlePlatformJar(@NotNull PathsList classPath,
                                        @NotNull AndroidPlatform platform,
                                        @NotNull JavaArtifact artifact) {
    String androidJarPath = platform.getTarget().getPath(IAndroidTarget.ANDROID_JAR);
    for (String entry : classPath.getPathList()) {
      if (pathsEqual(androidJarPath, entry)) {
        classPath.remove(entry);
      }
    }

    // Move the mockable android jar to the end. This is to make sure "empty" classes from android.jar don't end up shadowing real
    // classes needed by the testing code (e.g. XML/JSON related). Since mockable jars were introduced in 1.1, they were put in the model
    // as dependencies, which means a module which depends on Android libraries with different  will end up with more than one mockable jar in the
    // classpath.
    List<String> mockableJars = ContainerUtil.newSmartList();
    for (String path : classPath.getPathList()) {
      if (new File(toSystemDependentName(path)).getName().startsWith("mockable-")) {
        // PathsList stores strings - use the one that's actually stored there.
        mockableJars.add(path);
      }
    }

    // Remove all mockable android.jars.
    for (String mockableJar : mockableJars) {
      classPath.remove(mockableJar);
    }

    File mockableJar = getMockableJarFromModel(artifact);

    if (mockableJar != null) {
      classPath.addTail(mockableJar.getPath());
    }
    else {
      // We're dealing with an old plugin, that puts the mockable jar in the dependencies. Just put the matching android.jar at the end of
      // the classpath.
      for (String mockableJarPath : mockableJars) {
        if (mockableJarPath.endsWith("-" + platform.getApiLevel() + ".jar")) {
          classPath.addTail(mockableJarPath);
          return;
        }
      }
    }
  }

  @Nullable
  private static File getMockableJarFromModel(@NotNull JavaArtifact model) {
    try {
      return model.getMockablePlatformJar();
    }
    catch (UnsupportedMethodException e) {
      // Older model.
      return null;
    }
  }

  /**
   * Puts folders with merged java resources for the selected variant of every module on the classpath.
   *
   * <p>The problem we're solving here is that CompilerModuleExtension supports only one directory for "compiler output". When IJ compiles
   * Java projects, it copies resources to the output classes dir. This is something our Gradle plugin doesn't do, so we need to add the
   * resource directories to the classpath here.
   *
   * <p>We need to do this for every project dependency as well, since we're using classes and resources directories of these directly.
   *
   * @see <a href="http://b.android.com/172409">Bug 172409</a>
   * @see com.android.tools.idea.gradle.customizer.android.CompilerOutputModuleCustomizer#customizeModule(Project, ModifiableRootModel, IdeaAndroidProject)
   */
  private static void handleJavaResources(@NotNull Module module,
                                          @NotNull IdeaAndroidProject androidModel,
                                          @NotNull PathsList classPath) {
    final CompilerManager compilerManager = CompilerManager.getInstance(module.getProject());
    CompileScope scope = compilerManager.createModulesCompileScope(new Module[]{module}, true, true);

    for (Module affectedModule : scope.getAffectedModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(affectedModule);
      if (facet != null) {
        IdeaAndroidProject affectedAndroidModel = facet.getAndroidModel();
        if (affectedAndroidModel != null) {
          try {
            classPath.add(affectedAndroidModel.getMainArtifact().getJavaResourcesFolder());
          }
          catch (UnsupportedMethodException e) {
            // Java resources were not present in older versions of the gradle plugin.
          }
        }
      }
    }

    // The only test resources we want to use, are the ones from the module where the test is.
    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      try {
        classPath.add(testArtifact.getJavaResourcesFolder());
      }
      catch (UnsupportedMethodException e) {
        // Java resources were not present in older versions of the gradle plugin.
      }
    }
  }
}
