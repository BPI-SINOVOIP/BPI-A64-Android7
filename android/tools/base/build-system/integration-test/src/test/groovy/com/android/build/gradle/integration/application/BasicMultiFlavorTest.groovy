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
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.SourceProviderHelper
import com.android.utils.StringHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static com.google.common.truth.Truth.assertThat

/**
 * Assemble tests for basicMultiFlavors
 */
@CompileStatic
class BasicMultiFlavorTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basicMultiFlavors")
            .create()

    static public AndroidProject model

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
    void "check source providers"() {
        File projectDir = project.getTestDir()
        ModelHelper.testDefaultSourceSets(model, projectDir)

        // test the source provider for the flavor
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors()
        assertThat(productFlavors).hasSize(4)

        for (ProductFlavorContainer pfContainer : productFlavors) {
            String name = pfContainer.getProductFlavor().getName()
            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    name,
                    pfContainer.getSourceProvider())
                    .test()

            // Unit tests and android tests.
            assertThat(pfContainer.getExtraSourceProviders()).hasSize(2)
            SourceProviderContainer container = ModelHelper.getSourceProviderContainer(
                    pfContainer.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST)
            assertThat(container).isNotNull()

            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    ANDROID_TEST.prefix + StringHelper.capitalize(name),
                    container.getSourceProvider())
                    .test()
        }

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact()
            assertThat(artifact.getVariantSourceProvider()).isNotNull()
            assertThat(artifact.getMultiFlavorSourceProvider()).isNotNull()
        }
    }
}
