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

package com.android.tools.idea;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;

import static org.junit.Assert.assertNotNull;

public class AndroidTestCaseHelper {
  @NotNull
  public static Sdk createAndSetJdk(@NotNull final Project project) {
    String[] names = {"JAVA6_HOME", "JAVA_HOME"};
    String jdkHomePath = AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable(names);
    assertNotNull("Please set one of the following env vars (or system properties) to point to the JDK: " + Joiner.on(",").join(names),
                  jdkHomePath);
    final Sdk jdk = SdkConfigurationUtil.createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    assertNotNull(jdk);

    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        NewProjectUtil.applyJdkToProject(project, jdk);
      }
    });
    return jdk;
  }

  @NotNull
  public static File getAndroidSdkPath() {
    String path = AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable(AndroidTestBase.SDK_PATH_PROPERTY);
    if (Strings.isNullOrEmpty(path)) {
      String format = "Please specify the path of an Android SDK in the system property or environment variable '%1$s'";
      Assert.fail(String.format(format, AndroidTestBase.SDK_PATH_PROPERTY));
    }
    // If we got here is because the path is not null or empty.
    return new File(path);
  }

  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(String... names) {
    for (String name : names) {
      String s = getSystemPropertyOrEnvironmentVariable(name);
      if (!Strings.isNullOrEmpty(s)) {
        return s;
      }
    }

    return null;
  }

  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    return s == null ? System.getenv(name) : s;
  }

  public static void removeExistingAndroidSdks() {
    final ProjectJdkTable table = ProjectJdkTable.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (Sdk sdk : table.getAllJdks()) {
          table.removeJdk(sdk);
        }
      }
    });
  }
}
