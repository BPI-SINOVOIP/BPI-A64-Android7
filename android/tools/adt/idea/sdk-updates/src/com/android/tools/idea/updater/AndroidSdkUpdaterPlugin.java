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
package com.android.tools.idea.updater;

import com.intellij.ide.externalComponents.ExternalComponentManagerImpl;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin to set up the android sdk {@link UpdatableExternalComponent} and
 * {@link com.android.tools.idea.updater.configure.SdkUpdaterConfigurable}.
 */
public class AndroidSdkUpdaterPlugin implements ApplicationComponent {
  @Override
  public void initComponent() {
    ExternalComponentManagerImpl.getInstance().registerComponentSource(new SdkComponentSource());
  }

  @Override
  public void disposeComponent() {
    // nothing
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Android Sdk Updater";
  }
}
