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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.CompositeException;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

import static com.android.tools.idea.gradle.AndroidProjectKeys.*;
import static com.intellij.openapi.module.StdModuleTypes.JAVA;
import static org.easymock.classextension.EasyMock.createMock;

/**
 * Tests for {@link GradleProjectImporter}.
 */
public class GradleProjectImporterTest extends IdeaTestCase {
  private String myProjectName;
  private File myProjectRootDir;
  private DataNode<ProjectData> myCachedProject;

  private GradleProjectImporter myImporter;
  private DataNode<ModuleData> myCachedModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProjectName = "test";
    myProjectRootDir = createTempDir(myProjectName);

    String projectRootDirPath = myProjectRootDir.getPath();
    final File projectFile = new File(myProjectRootDir, SdkConstants.FN_BUILD_GRADLE);
    final String configPath = projectFile.getPath();
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, myProjectName, projectRootDirPath, configPath);
    myCachedProject = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    ModuleData moduleData =
      new ModuleData("", GradleConstants.SYSTEM_ID, JAVA.getId(), myModule.getName(), projectRootDirPath, configPath);
    myCachedModule = myCachedProject.createChild(ProjectKeys.MODULE, moduleData);

    GradleProjectImporter.ImporterDelegate delegate = new GradleProjectImporter.ImporterDelegate() {
      @Override
      void importProject(@NotNull Project project,
                         @NotNull ExternalProjectRefreshCallback callback,
                         @NotNull final ProgressExecutionMode progressTaskMode) throws ConfigurationException {
        assertNotNull(project);
        assertEquals(myProjectName, project.getName());
        callback.onSuccess(myCachedProject);
      }
    };

    myImporter = new GradleProjectImporter(delegate);
  }

  @Override
  protected void tearDown() throws Exception {
    Project[] projects = myProjectManager.getOpenProjects();
    for (Project project : projects) {
      if (project != getProject()) {
        myProjectManager.closeAndDispose(project);
      }
    }
    super.tearDown();
  }

  @Override
  protected CompositeException checkForSettingsDamage() throws Exception {
    return new CompositeException();
  }

  public void testImportNewlyCreatedProject() throws Exception {
    MyGradleSyncListener callback = new MyGradleSyncListener();
    myImporter.importNewlyCreatedProject(myProjectName, myProjectRootDir, callback, null, null);
  }

  public void testIsCacheMissingModelsWhenCacheHasAndroidModel() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
      model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
    } finally {
      model.commit();
    }

    myCachedModule.createChild(IDE_GRADLE_PROJECT, createMock(IdeaGradleProject.class));
    myCachedModule.createChild(IDE_ANDROID_PROJECT, createMock(IdeaAndroidProject.class));
    assertFalse(GradleProjectImporter.isCacheMissingModels(myCachedProject, myProject));
  }

  public void testIsCacheMissingModelsWhenCacheHasJavaModel() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
      model.addFacet(facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null));
    } finally {
      model.commit();
    }

    myCachedModule.createChild(IDE_GRADLE_PROJECT, createMock(IdeaGradleProject.class));
    myCachedModule.createChild(IDE_JAVA_PROJECT, createMock(IdeaJavaProject.class));
    assertFalse(GradleProjectImporter.isCacheMissingModels(myCachedProject, myProject));
  }

  public void testIsCacheMissingModelsWhenCacheIsMissingAndroidModel() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
      model.addFacet(facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null));
    } finally {
      model.commit();
    }

    myCachedModule.createChild(IDE_GRADLE_PROJECT, createMock(IdeaGradleProject.class));
    assertTrue(GradleProjectImporter.isCacheMissingModels(myCachedProject, myProject));
  }

  public void testIsCacheMissingModelsWhenCacheIsMissingJavaModel() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      model.addFacet(facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null));
      model.addFacet(facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null));
    } finally {
      model.commit();
    }

    myCachedModule.createChild(IDE_GRADLE_PROJECT, createMock(IdeaGradleProject.class));
    assertTrue(GradleProjectImporter.isCacheMissingModels(myCachedProject, myProject));
  }

  private class MyGradleSyncListener extends GradleSyncListener.Adapter {
    @Override
    public void syncSucceeded(@NotNull Project project) {
      disposeOnTearDown(project);
      // Verify that project was imported correctly.
      assertEquals(myProjectName, project.getName());
      assertEquals(myProjectRootDir.getPath(), project.getBasePath());

      // Verify that '.idea' directory was created.
      File ideaProjectDir = new File(myProjectRootDir, Project.DIRECTORY_STORE_FOLDER);
      assertTrue(ideaProjectDir.isDirectory());

      // Verify that module was created.
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module[] modules = moduleManager.getModules();
      assertEquals(1, modules.length);
      assertEquals(myModule.getName(), modules[0].getName());
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      fail(errorMessage);
    }
  }
}
