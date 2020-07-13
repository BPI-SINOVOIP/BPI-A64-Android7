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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.roots.DependencyScope;
import org.gradle.util.GradleVersion;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleDependenciesImportingTest extends GradleImportingTestCase {

  @Test
  public void testDependencyScopeMerge() throws Exception {
    createSettingsFile("include 'api', 'impl' ");

    importProject(
      "allprojects {\n" +
      "  apply plugin: 'java'\n" +
      "\n" +
      "  sourceCompatibility = 1.5\n" +
      "  version = '1.0'\n" +
      "\n" +
      "  repositories {\n" +
      "    mavenCentral()\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "  compile project(':api')\n" +
      "  testCompile project(':impl'), 'junit:junit:4.11'\n" +
      "  runtime project(':impl')\n" +
      "}"
    );

    assertModules("project", "api", "impl");
    assertModuleModuleDepScope("project", "api", DependencyScope.COMPILE);

    // scope merge works incorrectly in Gradle lower than 1.12 version
    if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("1.12")) >= 0) {
      assertModuleModuleDepScope("project", "impl", DependencyScope.RUNTIME, DependencyScope.TEST);
    }

    assertModuleLibDepScope("project", "Gradle: org.hamcrest:hamcrest-core:1.3", DependencyScope.TEST);
    assertModuleLibDepScope("project", "Gradle: junit:junit:4.11", DependencyScope.TEST);
  }
}
