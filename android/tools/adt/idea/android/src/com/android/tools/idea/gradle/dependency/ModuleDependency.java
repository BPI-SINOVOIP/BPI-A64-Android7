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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An IDEA module's dependency on another IDEA module.
 */
public class ModuleDependency extends Dependency {
  @NotNull private final String myGradlePath;

  @Nullable private LibraryDependency myBackupDependency;

  /**
   * Creates a new {@link ModuleDependency}.
   *
   * @param gradlePath the Gradle path of the project that maps to the IDEA module to depend on.
   * @param scope      the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public ModuleDependency(@NotNull String gradlePath, @NotNull DependencyScope scope) {
    super(scope);
    myGradlePath = gradlePath;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  /**
   * @return the backup library that can be used as dependency in case it is not possible to use the module dependency (e.g. the module is
   * outside the project and we don't have the path of the module folder.)
   */
  @Nullable
  public LibraryDependency getBackupDependency() {
    return myBackupDependency;
  }

  @VisibleForTesting
  public void setBackupDependency(@Nullable LibraryDependency backupDependency) {
    myBackupDependency = backupDependency;
    updateBackupDependencyScope();
  }

  /**
   * Sets the scope of this dependency. It also updates the scope of this dependency's backup dependency if it is not {@code null}.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @Override
  void setScope(@NotNull DependencyScope scope) throws IllegalArgumentException {
    super.setScope(scope);
    updateBackupDependencyScope();
  }

  private void updateBackupDependencyScope() {
    if (myBackupDependency != null) {
      myBackupDependency.setScope(getScope());
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "gradlePath=" + myGradlePath +
           ", scope=" + getScope() +
           ", backUpDependency=" + myBackupDependency +
           "]";
  }
}
