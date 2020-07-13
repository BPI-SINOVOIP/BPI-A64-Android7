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
package com.android.tools.idea.ui.properties;

import com.google.common.collect.Lists;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implementation for {@link Observable}, providing the logic for adding/removing listeners.
 */
public abstract class AbstractObservable implements Observable {
  private final List<InvalidationListener> myListeners = Lists.newArrayListWithCapacity(0);
  private final UnsafeWeakList<InvalidationListener> myWeakListeners = new UnsafeWeakList<InvalidationListener>(0);
  private boolean myNotificationsEnabled = true;

  @Override
  public final void addListener(@NotNull InvalidationListener listener) {
    myListeners.add(listener);
  }

  @Override
  public final void removeListener(@NotNull InvalidationListener listener) {
    myListeners.remove(listener);
    myWeakListeners.remove(listener);
  }

  @Override
  public final void addWeakListener(@NotNull InvalidationListener listener) {
    myWeakListeners.add(listener);
  }

  /**
   * Call to let our listeners know that our value has changed, and they should consider themselves
   * invalidated.
   */
  protected final void notifyInvalidated() {
    if (!myNotificationsEnabled) {
      return;
    }

    for (InvalidationListener listener : myListeners) {
      listener.onInvalidated(this);
    }

    for (InvalidationListener listener : myWeakListeners) {
      listener.onInvalidated(this);
    }
  }

  /**
   * Call to enable / disable the firing of listeners. Child classes may use this (with caution!)
   * to prevent multiple listeners being fired for the same invalidation event.
   */
  protected final void setNotificationsEnabled(boolean enabled) {
    myNotificationsEnabled = enabled;
  }
}
