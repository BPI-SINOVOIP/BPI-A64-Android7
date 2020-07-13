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

package com.android.build.gradle.integration.performance
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.VariantBuildScriptGenerator
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.EVALUATION

/**
 * Performance test on gradle plugin with a large number of variants
 */
class LargeVariantAndroidTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .useExperimentalGradleVersion(true)
            .create()

    @BeforeClass
    static void setUp() {
        project.buildFile << new VariantBuildScriptGenerator(
                buildTypes: VariantBuildScriptGenerator.LARGE_NUMBER,
                productFlavors: VariantBuildScriptGenerator.LARGE_NUMBER,
                """
                apply plugin: "com.android.application"

                android {
                    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                    buildTypes {
                        \${buildTypes}
                    }

                    productFlavors {
                        \${productFlavors}
                    }
                }
                """.stripIndent()).createBuildScript()

        // Execute before performance test to warm up the cache.
        project.execute("help");
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void performanceTest() {
        project.executeWithBenchmark("LargeVariantAndroid", EVALUATION, "projects")
    }
}
