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

package com.android.build.gradle.integration.testing
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.PASSED
import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.SKIPPED
import static com.google.common.truth.Truth.assertThat
/**
 * Meta-level tests for the app-level unit testing support. Checks the default values mode.
 */
class UnitTestingSupportTest {
    @ClassRule
    public static GradleTestProject appProject = GradleTestProject.builder()
            .fromTestProject("unitTesting")
            .create()

    @ClassRule
    public static GradleTestProject libProject = GradleTestProject.builder()
            .fromTestProject("unitTestingLibraryModules")
            .create()

    @AfterClass
    public static void freeResources() throws Exception {
        appProject = null
        libProject = null
    }

    @Test
    public void appProject() throws Exception {
        doTestProject(appProject)
    }

    @Test
    public void libProject() throws Exception {
        doTestProject(libProject)
    }

    private static void doTestProject(GradleTestProject project) {
        project.execute("clean", "test")

        for (variant in ["debug", "release"]) {
            def unitTestXml = "build/test-results/${variant}/TEST-com.android.tests.UnitTest.xml"
            def unitTextResults = new JUnitResults(project.file(unitTestXml))

            assertThat(unitTextResults.stdErr).contains("INFO: I can use commons-logging")

            checkResults(
                    unitTestXml,
                    ["thisIsIgnored"],
                    [
                            "aarDependencies",
                            "commonsLogging",
                            "enums",
                            "exceptions",
                            "instanceFields",
                            "javaResourcesOnClasspath",
                            "mockFinalClass",
                            "mockFinalMethod",
                            "mockInnerClass",
                            "prodJavaResourcesOnClasspath",
                            "prodRClass",
                            "referenceProductionCode",
                            "taskConfiguration",
                    ], project)

            checkResults(
                    "build/test-results/${variant}/TEST-com.android.tests.NonStandardName.xml",
                    [],
                    ["passingTest"], project)
        }
    }

    private static void checkResults(
            String xmlPath,
            ArrayList<String> ignored,
            ArrayList<String> passed,
            GradleTestProject project) {
        def results = new JUnitResults(project.file(xmlPath))
        assertThat(results.allTestCases).containsExactlyElementsIn(ignored + passed)
        passed.each { assert results.outcome(it) == PASSED }
        ignored.each { assert results.outcome(it) == SKIPPED }
    }
}
