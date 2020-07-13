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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.TestProjects.createBasicProject;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Collections.sort;

/**
 * Tests for {@link ContentRootModuleCustomizer}.
 */
public class ContentRootModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private IdeaAndroidProject myAndroidModel;

  private ContentRootModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    String basePath = myProject.getBasePath();
    assertNotNull(basePath);
    File baseDir = new File(basePath);
    myAndroidProject = createBasicProject(baseDir, myProject.getName());

    Collection<Variant> variants = myAndroidProject.getVariants();
    Variant selectedVariant = getFirstItem(variants);
    assertNotNull(selectedVariant);
    myAndroidModel = new IdeaAndroidProject(GradleConstants.SYSTEM_ID, myAndroidProject.getName(), baseDir, myAndroidProject,
                                            selectedVariant.getName(), ARTIFACT_ANDROID_TEST);

    addContentEntry();
    myCustomizer = new ContentRootModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  private void addContentEntry() {
    VirtualFile moduleFile = myModule.getModuleFile();
    assertNotNull(moduleFile);
    final VirtualFile moduleDir = moduleFile.getParent();

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
        ModifiableRootModel model = moduleRootManager.getModifiableModel();
        model.addContentEntry(moduleDir);
        model.commit();
      }
    });
  }

  public void testCustomizeModule() throws Exception {

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      myCustomizer.customizeModule(myProject, rootModel, myAndroidModel);
    }
    finally {
      rootModel.commit();
    }
    ContentEntry contentEntry = moduleRootManager.getContentEntries()[0];

    SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    List<String> sourcePaths = Lists.newArrayListWithExpectedSize(sourceFolders.length);

    for (SourceFolder folder : sourceFolders) {
      if (!folder.isTestSource()) {
        VirtualFile file = folder.getFile();
        assertNotNull(file);
        sourcePaths.add(file.getPath());
      }
    }

    ContentRootSourcePaths expectedPaths = new ContentRootSourcePaths();
    expectedPaths.storeExpectedSourcePaths(myAndroidProject);


    List<String> allExpectedPaths = Lists.newArrayList();
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE_GENERATED));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.RESOURCE));
    sort(allExpectedPaths);

    sort(sourcePaths);

    assertEquals(allExpectedPaths, sourcePaths);
  }
}
