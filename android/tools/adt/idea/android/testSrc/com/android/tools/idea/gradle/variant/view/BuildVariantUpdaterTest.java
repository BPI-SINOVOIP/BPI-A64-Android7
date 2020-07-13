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
package com.android.tools.idea.gradle.variant.view;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaJavaProject;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;

import static org.easymock.EasyMock.*;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends TestCase {

  public void testGetCustomizers() {
    BuildVariantModuleCustomizer<?> customizer1 = createCustomizer(GradleConstants.SYSTEM_ID, IdeaAndroidProjectStub.class);
    BuildVariantModuleCustomizer<?> customizer2 = createCustomizer(ProjectSystemId.IDE, IdeaAndroidProject.class);
    BuildVariantModuleCustomizer<?> customizer3 = createCustomizer(GradleConstants.SYSTEM_ID, IdeaJavaProject.class);
    BuildVariantModuleCustomizer<?> customizer4 = createCustomizer(ProjectSystemId.IDE, IdeaJavaProject.class);

    replay(customizer1, customizer2, customizer3, customizer4);

    List<BuildVariantModuleCustomizer<IdeaAndroidProject>> customizers =
      BuildVariantUpdater.getCustomizers(GradleConstants.SYSTEM_ID, customizer1, customizer2, customizer3, customizer4);

    verify(customizer1, customizer2, customizer3, customizer4);

    // The list should include only the customizers that:
    // 1. Have a matching ProjectSystemId, or have ProjectSystemId.IDE as their ProjectSystemId
    // 2. Have IdeaAndroidProject (or subclass) as the supported model type
    assertThat(customizers).containsOnly(customizer1, customizer2);
  }

  @NotNull
  private static BuildVariantModuleCustomizer<?> createCustomizer(@NotNull ProjectSystemId projectSystemId,
                                                                  @NotNull Class<?> supportedModelType) {
    BuildVariantModuleCustomizer<?> customizer = createMock(BuildVariantModuleCustomizer.class);
    expect(customizer.getProjectSystemId()).andStubReturn(projectSystemId);

    customizer.getSupportedModelType();
    expectLastCall().andStubReturn(supportedModelType);

    return customizer;
  }

  private static class IdeaAndroidProjectStub extends IdeaAndroidProject {
    public IdeaAndroidProjectStub(@NotNull ProjectSystemId projectSystemId,
                                  @NotNull String moduleName,
                                  @NotNull File rootDir,
                                  @NotNull AndroidProject delegate,
                                  @NotNull String selectedVariantName,
                                  @NotNull String selectedTestArtifactName) {
      super(projectSystemId, moduleName, rootDir, delegate, selectedVariantName, selectedTestArtifactName);
    }
  }
}