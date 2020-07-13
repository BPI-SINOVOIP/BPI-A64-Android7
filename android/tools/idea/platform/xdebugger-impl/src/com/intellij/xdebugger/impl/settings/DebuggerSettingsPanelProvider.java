/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

@Deprecated
/**
 * @deprecated Use {@link com.intellij.xdebugger.settings.DebuggerConfigurableProvider}
 */
public abstract class DebuggerSettingsPanelProvider {
  public int getPriority() {
    return 0;
  }

  @NotNull
  public Collection<? extends Configurable> getConfigurables() {
    return Collections.emptyList();
  }

  @Deprecated
  /**
   * @deprecated Please use {@link com.intellij.xdebugger.settings.DebuggerConfigurableProvider#generalApplied(com.intellij.xdebugger.settings.DebuggerSettingsCategory)}
   */
  public void apply() {
  }

  @Nullable
  @Deprecated
  /**
   * @deprecated Please use {@link com.intellij.xdebugger.settings.DebuggerConfigurableProvider#getConfigurables(com.intellij.xdebugger.settings.DebuggerSettingsCategory)} and
   * check {@link com.intellij.xdebugger.settings.DebuggerSettingsCategory#GENERAL}
   */
  public Configurable getRootConfigurable() {
    return null;
  }
}
