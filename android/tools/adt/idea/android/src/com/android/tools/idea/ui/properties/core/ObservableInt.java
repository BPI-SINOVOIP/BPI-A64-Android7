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
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.ObservableValue;
import org.jetbrains.annotations.NotNull;

/**
 * Interface which, when implemented, allows the chaining of Integer values.
 */
public interface ObservableInt extends ObservableValue<Integer> {
  @NotNull
  ObservableBool isEqualTo(@NotNull ObservableValue<Integer> value);

  @NotNull
  ObservableBool isGreaterThan(@NotNull ObservableValue<Integer> value);

  @NotNull
  ObservableBool isLessThan(@NotNull ObservableValue<Integer> value);

  @NotNull
  ObservableBool isGreaterThanEqualTo(@NotNull ObservableValue<Integer> value);

  @NotNull
  ObservableBool isLessThanEqualTo(@NotNull ObservableValue<Integer> value);

  @NotNull
  ObservableBool isEqualTo(int value);

  @NotNull
  ObservableBool isGreaterThan(int value);

  @NotNull
  ObservableBool isLessThan(int value);

  @NotNull
  ObservableBool isGreaterThanEqualTo(int value);

  @NotNull
  ObservableBool isLessThanEqualTo(int value);
}
