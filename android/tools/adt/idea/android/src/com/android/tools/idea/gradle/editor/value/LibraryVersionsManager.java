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
package com.android.tools.idea.gradle.editor.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * {@link GradleEditorEntityValueManager} implementation which checks remote repos for available versions of particular library.
 */
public class LibraryVersionsManager implements GradleEditorEntityValueManager {

  @NotNull private final String myGroupId;
  @NotNull private final String myArtifactId;

  public LibraryVersionsManager(@NotNull String groupId, @NotNull String artifactId) {
    myGroupId = groupId;
    myArtifactId = artifactId;
  }

  @Nullable
  @Override
  public String validate(@NotNull String newValue, boolean strict) {
    // TODO den validate against available versions
    return null;
  }

  @Override
  public boolean isAvailableVersionsHintReady() {
    // TODO den implement
    return true;
  }

  @Nullable
  @Override
  public List<String> hintAvailableVersions() {
    // TODO den implement
    return null;
  }
}
