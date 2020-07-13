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
package com.android.tools.idea.run;


import com.android.tools.idea.run.CloudConfiguration.Kind;
import org.jetbrains.android.run.TargetChooser;

public class CloudTargetChooser implements TargetChooser {
  private final Kind configurationKind;
  private final int cloudConfigurationId;
  private final String cloudProjectId;

  public CloudTargetChooser(Kind configurationKind, int selectedCloudConfigurationId, String chosenCloudProjectId) {
    this.configurationKind = configurationKind;
    cloudConfigurationId = selectedCloudConfigurationId;
    cloudProjectId = chosenCloudProjectId;
  }

  public Kind getConfigurationKind() {
    return configurationKind;
  }

  public int getCloudConfigurationId() {
    return cloudConfigurationId;
  }

  public String getCloudProjectId() {
    return cloudProjectId;
  }
}
