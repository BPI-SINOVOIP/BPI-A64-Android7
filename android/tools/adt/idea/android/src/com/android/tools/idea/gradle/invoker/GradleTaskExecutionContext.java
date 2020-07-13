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
package com.android.tools.idea.gradle.invoker;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.CancellationTokenSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 'Parameter object' pattern for dealing with gradle tasks execution.
 */
public class GradleTaskExecutionContext {

  @NotNull private final GradleInvoker myGradleInvoker;
  @NotNull private final Project myProject;
  @NotNull private final List<String> myGradleTasks;
  @NotNull private final List<String> myCommandLineArgs;
  @NotNull private final Map<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap;

  @NotNull private final ExternalSystemTaskId myTaskId;
  @Nullable private final ExternalSystemTaskNotificationListener myTaskNotificationListener;

  public GradleTaskExecutionContext(@NotNull GradleInvoker gradleInvoker,
                                    @NotNull Project project,
                                    @NotNull List<String> gradleTasks,
                                    @NotNull List<String> commandLineArgs,
                                    @NotNull Map<ExternalSystemTaskId, CancellationTokenSource> cancellationMap,
                                    @NotNull ExternalSystemTaskId taskId,
                                    @Nullable ExternalSystemTaskNotificationListener taskNotificationListener) {
    myGradleInvoker = gradleInvoker;
    myProject = project;
    myGradleTasks = gradleTasks;
    myCommandLineArgs = commandLineArgs;
    myCancellationMap = cancellationMap;
    myTaskId = taskId;
    myTaskNotificationListener = taskNotificationListener;
  }

  @NotNull
  public GradleInvoker getGradleInvoker() {
    return myGradleInvoker;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<String> getGradleTasks() {
    return myGradleTasks;
  }

  @NotNull
  public List<String> getCommandLineArgs() {
    return myCommandLineArgs;
  }

  @NotNull
  public ExternalSystemTaskId getTaskId() {
    return myTaskId;
  }

  @Nullable
  public ExternalSystemTaskNotificationListener getTaskNotificationListener() {
    return myTaskNotificationListener;
  }

  @Nullable
  public CancellationTokenSource storeCancellationInfoFor(@NotNull ExternalSystemTaskId taskId, @NotNull CancellationTokenSource token) {
    return myCancellationMap.put(taskId, token);
  }

  @Nullable
  public CancellationTokenSource dropCancellationInfoFor(@NotNull ExternalSystemTaskId taskId) {
    return myCancellationMap.remove(taskId);
  }

  public boolean isActive(@NotNull ExternalSystemTaskId taskId) {
    return myCancellationMap.containsKey(taskId);
  }
}
