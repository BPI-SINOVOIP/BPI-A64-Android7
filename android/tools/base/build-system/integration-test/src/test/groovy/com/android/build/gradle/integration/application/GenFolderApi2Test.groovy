/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.JavaArtifact
import com.android.builder.model.Variant
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
/**
 * Assemble tests for genFolderApi2.
 */
@CompileStatic
class GenFolderApi2Test {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("genFolderApi2")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.getSingleModel()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check Java Folder in Model"() throws Exception {
        File projectDir = project.testDir

        File buildDir = new File(projectDir, "build")

        for (Variant variant : model.variants) {

            AndroidArtifact mainInfo = variant.mainArtifact
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.displayName,
                    mainInfo)

            // Get the generated source folders.
            Collection<File> genSourceFolder = mainInfo.generatedSourceFolders

            // We're looking for a custom folder.
            String sourceFolderStart = new File(buildDir, "customCode").absolutePath + File.separatorChar
            assertTrue("custom generated source folder check", genSourceFolder.any {
              it.absolutePath.startsWith(sourceFolderStart)
            })

            // Unit testing artifact:
            assertThat(variant.getExtraJavaArtifacts()).hasSize(1)
            JavaArtifact unitTestArtifact = variant.extraJavaArtifacts.first()
            def sortedFolders = unitTestArtifact.generatedSourceFolders.sort()
            assertThat(sortedFolders).hasSize(2)
            assertThat(sortedFolders[0].absolutePath).startsWith(sourceFolderStart)
            assertThat(sortedFolders[0].absolutePath).endsWith("-1")
            assertThat(sortedFolders[1].absolutePath).startsWith(sourceFolderStart)
            assertThat(sortedFolders[1].absolutePath).endsWith("-2")
        }
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(FileUtils.sha1(project.file("build.gradle")))
                .isEqualTo("93b7507ce31a087a4efa4cae66474ad320e25b6c")
    }
}
