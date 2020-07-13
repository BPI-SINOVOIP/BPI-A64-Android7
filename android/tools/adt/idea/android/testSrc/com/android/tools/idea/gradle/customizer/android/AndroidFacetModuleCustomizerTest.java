/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * Tests for {@link com.android.tools.idea.gradle.customizer.android.AndroidFacetModuleCustomizer}.
 */
public class AndroidFacetModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDir = new File(FileUtil.toSystemDependentName(myProject.getBasePath()));
    myAndroidProject = TestProjects.createBasicProject(rootDir);
    myAndroidProject.setIsLibrary(true);
    myCustomizer = new AndroidFacetModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testCustomizeModule() {
    File rootDir = myAndroidProject.getRootDir();
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    String selectedVariantName = selectedVariant.getName();
    IdeaAndroidProject androidModel = new IdeaAndroidProject(GradleConstants.SYSTEM_ID, myAndroidProject.getName(), rootDir, myAndroidProject,
                                                             selectedVariantName, AndroidProject.ARTIFACT_ANDROID_TEST);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      myCustomizer.customizeModule(myProject, rootModel, androidModel);
    }
    finally {
      rootModel.commit();
    }

    // Verify that AndroidFacet was added and configured.
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    assertSame(androidModel, facet.getAndroidModel());

    JpsAndroidModuleProperties facetState = facet.getProperties();
    assertFalse(facetState.ALLOW_USER_CONFIGURATION);
  }
}
