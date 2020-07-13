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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.builder.model.AndroidProject

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Tests the error message rewriting logic.
 */
@CompileStatic
class MessageRewriteTest {

    private static List<String> INVOKED_FROM_IDE_ARGS =
            Collections.singletonList("-P" + AndroidProject.PROPERTY_INVOKED_FROM_IDE + "=true")

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("flavored")
            .withoutNdk()
            .captureStdOut(true)
            .captureStdErr(true)
            .create()

    @BeforeClass
    public static void assemble() {
        project.execute('assembleDebug')
    }

    @Test
    public void "invalid layout file"() {
        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("src/main/res/layout/main.xml", "</LinearLayout>", "");
            project.getStderr().reset()
            project.executeExpectingFailure(INVOKED_FROM_IDE_ARGS, 'assembleF1Debug')
            String err = project.getStderr().toString()
            assertThat(err).contains("src/main/res/layout/main.xml")
        }

        project.execute('assembleDebug')
    }
}
