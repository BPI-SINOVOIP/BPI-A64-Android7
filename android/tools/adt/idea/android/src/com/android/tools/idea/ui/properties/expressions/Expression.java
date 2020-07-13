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
package com.android.tools.idea.ui.properties.expressions;

import com.android.tools.idea.ui.properties.AbstractObservable;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.Observable;
import org.jetbrains.annotations.NotNull;

/**
 * An expression is an observable value that wraps another observable value, modifying the result
 * before passing it on.
 *
 * Child class constructors should make sure they call {@code super} with all target observables,
 * as this will ensure invalidation notifications propagate correctly.
 */
public abstract class Expression extends AbstractObservable implements Observable {

  @SuppressWarnings("FieldCanBeLocal") // must be local to avoid weak garbage collection
  private final InvalidationListener myListener = new InvalidationListener() {
    @Override
    public void onInvalidated(@NotNull Observable sender) {
      notifyInvalidated();
    }
  };

  protected Expression(Observable... values) {
    for (Observable value : values) {
      value.addWeakListener(myListener);
    }
  }
}
