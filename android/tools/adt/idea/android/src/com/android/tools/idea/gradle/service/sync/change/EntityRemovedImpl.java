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
package com.android.tools.idea.gradle.service.sync.change;

import com.intellij.openapi.externalSystem.model.DataNode;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class EntityRemovedImpl<T> extends AbstractProjectStructureChange<T> implements EntityRemoved<T> {

  public EntityRemovedImpl(@NotNull DataNode<T> data) throws IllegalArgumentException {
    super(data);
  }

  public EntityRemovedImpl(@NotNull DataNode<T> data, @NotNull String dataDescription) {
    super(data, dataDescription);
  }

  @NotNull
  @Override
  public DataNode<T> getRemovedEntity() {
    return getData();
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.gradle.project.change.removed", getDataDescription());
  }

  @Override
  public void accept(@NotNull ProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}
