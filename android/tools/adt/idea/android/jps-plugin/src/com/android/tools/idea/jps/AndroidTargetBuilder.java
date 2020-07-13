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
package com.android.tools.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidSourceGeneratingBuilder;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.io.IOException;
import java.util.Collection;

public abstract class AndroidTargetBuilder<R extends BuildRootDescriptor, T extends BuildTarget<R>> extends TargetBuilder<R, T> {
  protected AndroidTargetBuilder(Collection<? extends BuildTargetType<? extends T>> buildTargetTypes) {
    super(buildTargetTypes);
  }

  @Override
  public final void build(@NotNull T target,
                          @NotNull DirtyFilesHolder<R, T> holder,
                          @NotNull BuildOutputConsumer outputConsumer,
                          @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (AndroidSourceGeneratingBuilder.IS_ENABLED.get(context, true)) {
      // Only build targets for non-Gradle Android project.
      buildTarget(target, holder, outputConsumer, context);
    }
  }

  protected abstract void buildTarget(@NotNull T target,
                                      @NotNull DirtyFilesHolder<R, T> holder,
                                      @NotNull BuildOutputConsumer outputConsumer,
                                      @NotNull CompileContext context) throws ProjectBuildException, IOException;
}
