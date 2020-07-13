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
package com.android.tools.idea.gradle.dependency;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.dependency.LibraryDependency.PathType.BINARY;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * An IDEA module's dependency on an artifact (e.g. a jar file or another IDEA module.)
 */
public abstract class Dependency {
  /**
   * The Android Gradle plug-in only supports "compile" and "test" scopes. This list is sorted by width of the scope, being "compile" a
   * wider scope than "test."
   */
  static final List<DependencyScope> SUPPORTED_SCOPES = Lists.newArrayList(DependencyScope.COMPILE, DependencyScope.TEST);

  // Without this '@SuppressWarnings' IDEA shows a warning because the field 'myScope' is not set directly in the constructor, and therefore
  // IDEA thinks it can be null, contradicting '@NotNull'. In reality, the field is set in the constructor by calling 'setScope'. To avoid
  // this warning we can either use '@SuppressWarnings' or duplicate, in the constructor, what 'setScope' is doing.
  @SuppressWarnings("NullableProblems")
  @NotNull private DependencyScope myScope;

  /**
   * Creates a new {@link Dependency} with {@link DependencyScope#COMPILE} scope.
   */
  Dependency() {
    this(DependencyScope.COMPILE);
  }

  /**
   * Creates a new {@link Dependency}.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  Dependency(@NotNull DependencyScope scope) throws IllegalArgumentException {
    setScope(scope);
  }

  @NotNull
  public final DependencyScope getScope() {
    return myScope;
  }

  /**
   * Sets the scope of this dependency.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  void setScope(@NotNull DependencyScope scope) throws IllegalArgumentException {
    if (!SUPPORTED_SCOPES.contains(scope)) {
      String msg = String.format("'%1$s' is not a supported scope. Supported scopes are %2$s.", scope, SUPPORTED_SCOPES);
      throw new IllegalArgumentException(msg);
    }
    myScope = scope;
  }

  @NotNull
  public static DependencySet extractFrom(@NotNull IdeaAndroidProject androidProject) {
    DependencySet dependencies = new DependencySet();

    BaseArtifact testArtifact = androidProject.findSelectedTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      populate(dependencies, testArtifact, DependencyScope.TEST);
    }
    AndroidArtifact mainArtifact = androidProject.getMainArtifact();
    populate(dependencies, mainArtifact, DependencyScope.COMPILE);

    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull BaseArtifact artifact,
                               @NotNull DependencyScope scope) {
    addJavaLibraries(dependencies, artifact.getDependencies().getJavaLibraries(), scope);

    Set<File> unique = Sets.newHashSet();
    for (AndroidLibrary lib : artifact.getDependencies().getLibraries()) {
      ModuleDependency mainDependency = null;
      String gradleProjectPath = lib.getProject();
      if (isNotEmpty(gradleProjectPath)) {
        mainDependency = new ModuleDependency(gradleProjectPath, scope);
        dependencies.add(mainDependency);
      }
      if (mainDependency == null) {
        addLibrary(lib, dependencies, scope, unique);
      }
      else {
        // add the aar as dependency in case there is a module dependency that cannot be satisfied (e.g. the module is outside of the
        // project.) If we cannot set the module dependency, we set a library dependency instead.
        LibraryDependency backup = createLibraryDependency(lib, scope);
        mainDependency.setBackupDependency(backup);
      }
    }

    for (String gradleProjectPath : artifact.getDependencies().getProjects()) {
      if (gradleProjectPath != null && !gradleProjectPath.isEmpty()) {
        ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
        dependencies.add(dependency);
      }
    }
  }

  @NotNull
  private static String getLibraryName(@NotNull AndroidLibrary library) {
    MavenCoordinates coordinates = library.getResolvedCoordinates();
    if (coordinates != null) {
      return coordinates.getArtifactId() + "-" + coordinates.getVersion();
    }
    File bundle = library.getBundle();
    return FileUtil.getNameWithoutExtension(bundle);
  }

  /**
   * Add a library, along with any recursive library dependencies
   */
  private static void addLibrary(@NotNull AndroidLibrary library,
                                 @NotNull DependencySet dependencies,
                                 @NotNull DependencyScope scope,
                                 @NotNull Set<File> unique) {
    // We're using the library location as a unique handle rather than the AndroidLibrary instance itself, in case
    // the model just blindly manufactures library instances as it's following dependencies
    File folder = library.getFolder();
    if (unique.contains(folder)) {
      return;
    }
    unique.add(folder);

    LibraryDependency dependency = createLibraryDependency(library, scope);
    dependencies.add(dependency);

    for (AndroidLibrary dependentLibrary : library.getLibraryDependencies()) {
      addLibrary(dependentLibrary, dependencies, scope, unique);
    }
  }

  @NotNull
  private static LibraryDependency createLibraryDependency(@NotNull AndroidLibrary library, @NotNull DependencyScope scope) {
    LibraryDependency dependency = new LibraryDependency(getLibraryName(library), scope);
    dependency.addPath(BINARY, library.getJarFile());
    dependency.addPath(BINARY, library.getResFolder());

    for (File localJar : library.getLocalJars()) {
      dependency.addPath(BINARY, localJar);
    }
    return dependency;
  }

  private static void addJavaLibraries(@NotNull DependencySet dependencies,
                                       @NotNull Collection<JavaLibrary> libraries,
                                       @NotNull DependencyScope scope) {
    for (JavaLibrary library : libraries) {
      File jar = library.getJarFile();
      dependencies.add(new LibraryDependency(jar, scope));
    }
  }
}
