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
package com.android.tools.idea.gradle.util;

import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.google.common.io.Files.createTempDir;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest extends TestCase {
  private File myTempDir;

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testGetGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  public void testGetGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true", GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  public void testGetGradleWrapperPropertiesFilePath() throws IOException {
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    createIfNotExists(wrapper);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testLeaveGradleWrapperAloneBin() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -bin.zip with a -all.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-bin.zip", distributionUrl);
  }

  public void testLeaveGradleWrapperAloneAll() throws IOException {
    // Ensure that if we already have the right version, we don't replace a -all.zip with a -bin.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-all.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.9", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.9-all.zip", distributionUrl);
  }

  public void testReplaceGradleWrapper() throws IOException {
    // Test that when we replace to a new version we use -all.zip
    myTempDir = createTempDir();
    File wrapper = new File(myTempDir, FN_GRADLE_WRAPPER_PROPERTIES);
    write("#Wed Apr 10 15:27:10 PDT 2013\n" +
          "distributionBase=GRADLE_USER_HOME\n" +
          "distributionPath=wrapper/dists\n" +
          "zipStoreBase=GRADLE_USER_HOME\n" +
          "zipStorePath=wrapper/dists\n" +
          "distributionUrl=https\\://services.gradle.org/distributions/gradle-1.9-bin.zip", wrapper, Charsets.UTF_8);
    GradleUtil.updateGradleDistributionUrl("1.6", wrapper);

    Properties properties = getProperties(wrapper);
    String distributionUrl = properties.getProperty("distributionUrl");
    assertEquals("https://services.gradle.org/distributions/gradle-1.6-all.zip", distributionUrl);
  }

  public void testUpdateGradleDistributionUrl() {
    myTempDir = createTempDir();
    File wrapperPath = GradleUtil.getGradleWrapperPropertiesFilePath(myTempDir);

    List<String> expected = Lists.newArrayList(FileUtil.splitPath(myTempDir.getPath()));
    expected.addAll(FileUtil.splitPath(FD_GRADLE_WRAPPER));
    expected.add(FN_GRADLE_WRAPPER_PROPERTIES);

    assertEquals(expected, FileUtil.splitPath(wrapperPath.getPath()));
  }

  public void testGetPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  public void testGetPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  public void testGetGradleBuildFilePath() {
    myTempDir = createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }

  public void testGetGradleVersionFromJarUsingGradleLibraryJar() {
    File jarFile = new File("gradle-core-2.0.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNotNull(gradleVersion);
    assertEquals(FullRevision.parseRevision("2.0"), gradleVersion);
  }

  public void testGetGradleVersionFromJarUsingGradleLibraryJarWithoutVersion() {
    File jarFile = new File("gradle-core-two.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  public void testGetGradleVersionFromJarUsingNonGradleLibraryJar() {
    File jarFile = new File("ant-1.9.3.jar");
    FullRevision gradleVersion = GradleUtil.getGradleVersionFromJar(jarFile);
    assertNull(gradleVersion);
  }

  public void testGetAndroidGradleModelVersion() throws IOException {
    String contents = "buildscript {\n" +
                      "    repositories {\n" +
                      "        jcenter()\n" +
                      "    }\n" +
                      "    dependencies {\n" +
                      "        classpath 'com.android.tools.build:gradle:0.13.0'\n" +
                      "    }\n" +
                      "}";
    FullRevision revision = GradleUtil.getAndroidGradleModelVersionFromBuildFile(contents, null);
    assertNotNull(revision);
    assertEquals("0.13.0", revision.toString());
  }

  public void testGetAndroidGradleModelVersionWithPlusInMicro() throws IOException {
    String contents = "buildscript {\n" +
                      "    repositories {\n" +
                      "        jcenter()\n" +
                      "    }\n" +
                      "    dependencies {\n" +
                      "        classpath 'com.android.tools.build:gradle:0.13.+'\n" +
                      "    }\n" +
                      "}";
    FullRevision revision = GradleUtil.getAndroidGradleModelVersionFromBuildFile(contents, null);
    assertNotNull(revision);
    assertEquals("0.13.0", revision.toString());
  }

  public void testGetAndroidGradleModelVersionWithPlusNotation() throws IOException {
    String contents = "buildscript {\n" +
                      "    repositories {\n" +
                      "        jcenter()\n" +
                      "    }\n" +
                      "    dependencies {\n" +
                      "        classpath 'com.android.tools.build:gradle:+'\n" +
                      "    }\n" +
                      "}";
    FullRevision revision = GradleUtil.getAndroidGradleModelVersionFromBuildFile(contents, null);
    assertNotNull(revision);
  }

  public void testAddLocalMavenRepoInitScriptCommandLineOption() throws IOException {
    File repoPath = new File("/xyz/repo");
    List<String> cmdOptions = Lists.newArrayList();

    File initScriptPath = GradleUtil.addLocalMavenRepoInitScriptCommandLineOption(cmdOptions, repoPath);
    assertNotNull(initScriptPath);

    assertEquals(2, cmdOptions.size());
    assertEquals("--init-script", cmdOptions.get(0));
    assertEquals(initScriptPath.getPath(), cmdOptions.get(1));

    String expectedScript = "allprojects {\n" +
                            "  buildscript {\n" +
                            "    repositories {\n" +
                            "      maven { url '" + GradleImport.escapeGroovyStringLiteral(repoPath.getPath()) + "'}\n" +
                            "    }\n" +
                            "  }\n" +
                            "}\n";

    String initScript = FileUtil.loadFile(initScriptPath);
    assertEquals(expectedScript, initScript);
  }

  public void testGetGradleWrapperVersionWithUrl() {
    // Tries both http and https, bin and all. Also versions 2.2.1, 2.2 and 1.12
    String url = "https://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    String version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    // Use custom URL.
    url = "http://myown.com/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertNull(version);
  }

  public void testHasLayoutRenderingIssue() {
    AndroidProjectStub model = new AndroidProjectStub("app");

    model.setModelVersion("1.1.0");
    assertFalse(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.0");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.1");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.2");
    assertTrue(GradleUtil.hasLayoutRenderingIssue(model));

    model.setModelVersion("1.2.3");
    assertFalse(GradleUtil.hasLayoutRenderingIssue(model));
  }
}
