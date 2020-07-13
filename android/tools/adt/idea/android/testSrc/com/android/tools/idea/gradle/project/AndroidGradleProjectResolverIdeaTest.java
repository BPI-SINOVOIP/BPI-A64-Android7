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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

import java.util.Collection;

import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_ANDROID_PROJECT;
import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_GRADLE_PROJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.*;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverIdeaTest extends IdeaTestCase {
  private IdeaProjectStub myIdeaProject;
  private AndroidProjectStub myAndroidProject;

  private ProjectResolverContext myResolverCtx;
  private AndroidGradleProjectResolver myProjectResolver;

  private IdeaModuleStub myAndroidModule;
  private IdeaModuleStub myUtilModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeaProject = new IdeaProjectStub("multiProject");
    myAndroidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());

    myAndroidModule = myIdeaProject.addModule(myAndroidProject.getName(), "androidTask");
    myUtilModule = myIdeaProject.addModule("util", "compileJava", "jar", "classes");
    myIdeaProject.addModule("notReallyAGradleProject");

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myIdeaProject);
    allModels.addExtraProject(myAndroidProject, AndroidProject.class, myAndroidModule);

    ExternalSystemTaskId id = ExternalSystemTaskId.create(SYSTEM_ID, RESOLVE_PROJECT, myIdeaProject.getName());
    String projectPath = FileUtil.toSystemDependentName(myIdeaProject.getBuildFile().getParent());
    ExternalSystemTaskNotificationListener notificationListener = new ExternalSystemTaskNotificationListenerAdapter() {
    };
    myResolverCtx = new ProjectResolverContext(id, projectPath, null, createMock(ProjectConnection.class), notificationListener, true);
    myResolverCtx.setModels(allModels);

    myProjectResolver = new AndroidGradleProjectResolver(createMock(ProjectImportErrorHandler.class));
    myProjectResolver.setProjectResolverContext(myResolverCtx);

    GradleProjectResolverExtension next = new BaseGradleProjectResolverExtension();
    next.setProjectResolverContext(myResolverCtx);
    myProjectResolver.setNext(next);
  }

  @Override
  protected void tearDown() throws Exception {
    if (myIdeaProject != null) {
      myIdeaProject.dispose();
    }
    super.tearDown();
  }

  public void testCreateModuleWithOldModelVersion() {
    AndroidProject androidProject = createMock(AndroidProject.class);
    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myIdeaProject);
    allModels.addExtraProject(androidProject, AndroidProject.class, myAndroidModule);
    myResolverCtx.setModels(allModels);

    expect(androidProject.getModelVersion()).andStubReturn("0.0.1");
    replay(androidProject);

    try {
      ProjectData project = myProjectResolver.createProject();
      myProjectResolver.createModule(myAndroidModule, project);
      fail();
    }
    catch (IllegalStateException e) {
    }

    verify(androidProject);
  }

  public void testPopulateModuleContentRootsWithAndroidProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, project, null);
    ModuleData module = myProjectResolver.createModule(myAndroidModule, project);
    DataNode<ModuleData> moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, module);

    myProjectResolver.populateModuleContentRoots(myAndroidModule, moduleDataNode);

    // Verify module has IdeaAndroidProject.
    Collection<DataNode<IdeaAndroidProject>> androidProjectNodes = getChildren(moduleDataNode, IDE_ANDROID_PROJECT);
    assertEquals(1, androidProjectNodes.size());
    DataNode<IdeaAndroidProject> androidProjectNode = getFirstItem(androidProjectNodes);
    assertNotNull(androidProjectNode);
    assertSame(myAndroidProject, androidProjectNode.getData().getAndroidProject());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<IdeaGradleProject>> gradleProjects = getChildren(moduleDataNode, IDE_GRADLE_PROJECT);
    assertEquals(1, gradleProjects.size());
    DataNode<IdeaGradleProject> gradleProjectNode = getFirstItem(gradleProjects);
    assertNotNull(gradleProjectNode);
    assertEquals(myAndroidModule.getGradleProject().getPath(), gradleProjectNode.getData().getGradlePath());
  }

  public void testPopulateModuleContentRootsWithJavaProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, project, null);
    ModuleData module = myProjectResolver.createModule(myUtilModule, project);
    DataNode<ModuleData> moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, module);

    myProjectResolver.populateModuleContentRoots(myUtilModule, moduleDataNode);

    // Verify module does not have IdeaAndroidProject.
    Collection<DataNode<IdeaAndroidProject>> androidProjectNodes = getChildren(moduleDataNode, IDE_ANDROID_PROJECT);
    assertEquals(0, androidProjectNodes.size());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<IdeaGradleProject>> gradleProjects = getChildren(moduleDataNode, IDE_GRADLE_PROJECT);
    assertEquals(1, gradleProjects.size());
    DataNode<IdeaGradleProject> gradleProjectNode = getFirstItem(gradleProjects);
    assertNotNull(gradleProjectNode);
    assertEquals(myUtilModule.getGradleProject().getPath(), gradleProjectNode.getData().getGradlePath());
  }
}
