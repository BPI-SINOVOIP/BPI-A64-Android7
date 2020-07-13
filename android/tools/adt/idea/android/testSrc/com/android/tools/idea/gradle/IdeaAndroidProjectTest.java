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
 */package com.android.tools.idea.gradle;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;

/**
 * Tests for {@link IdeaAndroidProject}.
 */
public class IdeaAndroidProjectTest extends AndroidGradleTestCase {
  private AndroidProjectStub myAndroidProject;
  private IdeaAndroidProject myAndroidModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDirPath = new File(getProject().getBasePath());
    myAndroidProject = TestProjects.createFlavorsProject();
    myAndroidModel = new IdeaAndroidProject(GradleConstants.SYSTEM_ID, myAndroidProject.getName(), rootDirPath, myAndroidProject, "f1fa-debug",
                                            AndroidProject.ARTIFACT_ANDROID_TEST);
  }

  public void testFindBuildType() throws Exception {
    String buildTypeName = "debug";
    BuildTypeContainer buildType = myAndroidModel.findBuildType(buildTypeName);
    assertNotNull(buildType);
    assertSame(myAndroidProject.findBuildType(buildTypeName), buildType);
  }

  public void testFindProductFlavor() throws Exception {
    String flavorName = "fa";
    ProductFlavorContainer flavor = myAndroidModel.findProductFlavor(flavorName);
    assertNotNull(flavor);
    assertSame(myAndroidProject.findProductFlavor(flavorName), flavor);
  }

  public void testFindSelectedTestArtifactInSelectedVariant() throws Exception {
    BaseArtifact instrumentationTestArtifact = myAndroidModel.findSelectedTestArtifactInSelectedVariant();
    VariantStub firstVariant = myAndroidProject.getFirstVariant();
    assertNotNull(firstVariant);
    assertSame(firstVariant.getInstrumentTestArtifact(), instrumentationTestArtifact);
  }

  public void testGetSelectedVariant() throws Exception {
    Variant selectedVariant = myAndroidModel.getSelectedVariant();
    assertNotNull(selectedVariant);
    assertSame(myAndroidProject.getFirstVariant(), selectedVariant);
  }

  public void testReadWriteObject() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("IdeaAndroidProjectDataSerializationTest.testReadWriteObject temporarily disabled");
      return;
    }

    loadProject("projects/projectWithAppandLib");

    IdeaAndroidProject androidModel = myAndroidFacet.getAndroidModel();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos;
    oos = new ObjectOutputStream(outputStream);
    oos.writeObject(androidModel);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    IdeaAndroidProject newAndroidModel = (IdeaAndroidProject)ois.readObject();
    ois.close();

    assertEquals(androidModel.getProjectSystemId(), newAndroidModel.getProjectSystemId());
    assertEquals(androidModel.getModuleName(), newAndroidModel.getModuleName());
    assertEquals(androidModel.getRootDirPath(), newAndroidModel.getRootDirPath());
    assertEquals(androidModel.getAndroidProject().getName(), newAndroidModel.getAndroidProject().getName());
    assertEquals(androidModel.getSelectedVariant().getName(), newAndroidModel.getSelectedVariant().getName());
    assertEquals(androidModel.getSelectedTestArtifactName(), newAndroidModel.getSelectedTestArtifactName());
  }
}
