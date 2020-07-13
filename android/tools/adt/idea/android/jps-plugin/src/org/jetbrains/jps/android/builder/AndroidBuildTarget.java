/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.android.builder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AndroidBuildTarget extends BuildTarget<BuildRootDescriptor> {
  private final AndroidBuildTargetType myTargetType;
  protected final JpsModule myModule;

  public AndroidBuildTarget(@NotNull AndroidBuildTargetType targetType, @NotNull JpsModule module) {
    super(targetType);
    myTargetType = targetType;
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @NotNull
  @Override
  public final List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                                ModuleExcludeIndex index,
                                                                IgnoredFileIndex ignoredFileIndex,
                                                                BuildDataPaths dataPaths) {
    JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;
    if (extension.isGradleProject()) {
      return Collections.emptyList();
    }
    return doComputeRootDescriptors(model, index, ignoredFileIndex, dataPaths);
  }

  @NotNull
  protected abstract List<BuildRootDescriptor> doComputeRootDescriptors(JpsModel model,
                                                                        ModuleExcludeIndex index,
                                                                        IgnoredFileIndex ignoredFileIndex,
                                                                        BuildDataPaths dataPaths);

  @Override
  public Collection<BuildTarget<?>> computeDependencies(final BuildTargetRegistry registry, TargetOutputIndex outputIndex) {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>(3);

    fillDependencies(result);
    result.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));

    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    if (extension != null && extension.isPackTestCode()) {
      result.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.TEST));
    }
    return result;
  }

  protected void fillDependencies(List<BuildTarget<?>> result) {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Android " + myTargetType.getPresentableName();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof AndroidBuildTarget)) {
      return false;
    }

    AndroidBuildTarget target = (AndroidBuildTarget)o;
    return getTargetType() == target.getTargetType() && getId().equals(target.getId());
  }

  @Override
  public int hashCode() {
    return 31 * getId().hashCode() + getTargetType().hashCode();
  }
}
