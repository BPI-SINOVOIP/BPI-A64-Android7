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
package com.android.tools.idea.ui.properties.expressions.string;

import com.android.tools.idea.ui.properties.Observable;
import com.android.tools.idea.ui.properties.core.ObservableString;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for String expressions, providing a default implementation for the {@link ObservableString} interface.
 */
public abstract class StringExpression extends Expression implements ObservableString {

  protected StringExpression(Observable... values) {
    super(values);
  }

  @NotNull
  @Override
  public ObservableBool isEmpty() {
    return new IsEmptyExpression(this);
  }

  @NotNull
  @Override
  public ObservableString trim() {
    return new TrimExpression(this);
  }
}
