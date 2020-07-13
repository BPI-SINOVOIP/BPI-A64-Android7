/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.test.BaseTest
import com.android.builder.DefaultBuildType
import com.android.builder.BuilderConstants
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * test that the build type are properly initialized
 */
public class BuildTypeDslTest extends BaseTest {

    public void testDebug() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin

        DefaultBuildType type = plugin.buildTypes.get(BuilderConstants.DEBUG).buildType

        assertTrue(type.isDebuggable())
        assertFalse(type.isJniDebugBuild())
        assertFalse(type.isRenderscriptDebugBuild())
        assertNotNull(type.getSigningConfig())
        assertTrue(type.getSigningConfig().isSigningReady())
        assertFalse(type.isZipAlign())
    }

    public void testRelease() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            compileSdkVersion 15
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin

        DefaultBuildType type = plugin.buildTypes.get(BuilderConstants.RELEASE).buildType

        assertFalse(type.isDebuggable())
        assertFalse(type.isJniDebugBuild())
        assertFalse(type.isRenderscriptDebugBuild())
        assertTrue(type.isZipAlign())
    }

    public void testInitWith() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        BuildTypeDsl object1 = new BuildTypeDsl("foo", project.fileResolver)

        // change every value from their default.
        object1.setDebuggable(true)
        object1.setJniDebugBuild(true)
        object1.setRenderscriptDebugBuild(true)
        object1.setRenderscriptOptimLevel(0)
        object1.setPackageNameSuffix("foo")
        object1.setVersionNameSuffix("foo")
        object1.setRunProguard(true)
        object1.setSigningConfig(new SigningConfigDsl("blah"))
        object1.setZipAlign(false)

        BuildTypeDsl object2 = new BuildTypeDsl(object1.name, project.fileResolver)
        object2.initWith(object1)

        assertEquals(object1, object2)
    }
}
