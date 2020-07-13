/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.test
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy
import junit.framework.TestCase
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

import java.security.CodeSource
/**
 * Base class for tests.
 */
public abstract class BaseTest extends TestCase {

    /**
     * Returns the root dir for the gradle plugin project
     */
    protected File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource()
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI())
                assertTrue(dir.getPath(), dir.exists())

                File f= dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                return  new File(f, "tools" + File.separator + "build")
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage())
            }
        }

        fail("Fail to get the tools/build folder")
    }

    /**
     * Returns the root folder for the tests projects.
     */
    protected File getTestDir() {
        File rootDir = getRootDir()
        return new File(rootDir, "tests")
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return
     */
    protected File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find SDK in ANDROID_HOME=" + androidHome)
            }
        }

        // get the gradle project root dir.
        File rootDir = getRootDir()

        // go up twice and get the root Android dir.
        File androidRootDir = rootDir.getParentFile().getParentFile()

        // get the sdk folder
        String outFolder = "out" + File.separatorChar + "host" + File.separatorChar + "darwin-x86" + File.separatorChar + "sdk";
        File sdk = new File(androidRootDir, outFolder)

        File[] files = sdk.listFiles(new FilenameFilter() {

            @Override
            boolean accept(File file, String s) {
                return s.startsWith("android-sdk_") && new File(file,s ).isDirectory()
            }
        })

        if (files != null && files.length == 1) {
            return files[0]
        }

        fail(String.format(
                "Failed to find a valid SDK. Make sure %s is present at the root of the Android tree, or that ANDROID_HOME is defined.",
                outFolder))
        return null
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return
     */
    protected File getNdkDir() {
        String androidHome = System.getenv("ANDROID_NDK_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find NDK in ANDROID_NDK_HOME=" + androidHome)
            }
        }
    }

    protected static File createLocalProp(@NonNull File project,
                                          @NonNull File sdkDir,
                                          @Nullable File ndkDir) {
        ProjectPropertiesWorkingCopy localProp = ProjectProperties.create(
                project.absolutePath, ProjectProperties.PropertyType.LOCAL)
        localProp.setProperty(ProjectProperties.PROPERTY_SDK, sdkDir.absolutePath)
        if (ndkDir != null) {
            localProp.setProperty(ProjectProperties.PROPERTY_NDK, ndkDir.absolutePath)
        }
        localProp.save()

        return (File) localProp.file
    }

    protected File runTasksOn(String name, String gradleVersion, String... tasks) {
        File project = new File(testDir, name)

        runGradleTasks(sdkDir, ndkDir, gradleVersion, project, tasks)

        return project;
    }

    protected static void runGradleTasks(File sdkDir, File ndkDir,
                                         String gradleVersion,
                                         File project, String... tasks) {
        File localProp = createLocalProp(project, sdkDir, ndkDir)

        try {

            GradleConnector connector = GradleConnector.newConnector()

            ProjectConnection connection = connector
                    .useGradleVersion(gradleVersion)
                    .forProjectDirectory(project)
                    .connect()
            try {
                connection.newBuild().forTasks(tasks).withArguments("-i").run()
            } finally {
                connection.close()
            }
        } finally {
            localProp.delete()
        }
    }

    protected static void deleteFolder(File folder) {
        File[] files = folder.listFiles()
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file)
                } else {
                    file.delete()
                }
            }
        }

        folder.delete()
    }

    /**
     * Returns the name item from the collection of items. The items *must* have a "name" property.
     * @param items the item collection to search for a match
     * @param name the name of the item to return
     * @return the found item or null
     */
    protected static <T> T findNamedItemMaybe(@NonNull Collection<T> items,
                                              @NonNull String name) {
        for (T item : items) {
            if (name.equals(item.name)) {
                return item
            }
        }

        return null
    }

    /**
     * Returns the name item from the collection of items. The items *must* have a "name" property.
     * @param items the item collection to search for a match
     * @param name the name of the item to return
     * @return the found item or null
     */
    protected static <T> T findNamedItem(@NonNull Collection<T> items,
                                         @NonNull String name,
                                         @NonNull String typeName) {
        T foundItem = findNamedItemMaybe(items, name);
        assertNotNull("$name $typeName null-check", foundItem)
        return foundItem
    }
}
