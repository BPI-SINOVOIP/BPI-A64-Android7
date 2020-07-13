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
package com.android.tools.idea.gradle.stubs.gradle;

import com.google.common.collect.Lists;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class GradleProjectStub implements GradleProject {
  @NotNull private final String myName;
  @NotNull private final String myPath;
  @NotNull private final GradleScriptStub myScript;
  @NotNull private final List<GradleTaskStub> myTasks;

  public GradleProjectStub(@NotNull String name, @NotNull String path, @Nullable File projectFile, @NotNull String... tasks) {
    myName = name;
    myPath = path;
    myScript = new GradleScriptStub(projectFile);
    myTasks = Lists.newArrayList();
    for (String taskName : tasks) {
      GradleTaskStub task = new GradleTaskStub(taskName, this);
      myTasks.add(task);
    }
  }

  @Override
  public DomainObjectSet<? extends GradleTask> getTasks() {
    return ImmutableDomainObjectSet.of(myTasks);
  }

  @Override
  @Nullable
  public GradleProject getParent() {
    return null;
  }

  @Override
  public DomainObjectSet<? extends GradleProject> getChildren() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public String getPath() {
    return myPath;
  }

  @Override
  public GradleProject findByPath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String getDescription() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GradleScriptStub getBuildScript() {
    return myScript;
  }

  @Override
  public File getBuildDirectory() {
    return null;
  }
}
