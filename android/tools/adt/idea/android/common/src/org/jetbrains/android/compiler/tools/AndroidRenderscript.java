/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenderscript {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidRenderscriptCompiler");

  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull final String sdkLocation,
                                                                      @NotNull IAndroidTarget target,
                                                                      @NotNull String sourceFilePath,
                                                                      @NotNull final String genFolderPath,
                                                                      @Nullable String depFolderPath,
                                                                      @NotNull final String rawDirPath)
    throws IOException {

    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    final List<String> command = new ArrayList<String>();
    command.add(buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC));
    command.add("-I");
    command.add(buildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS_CLANG));
    command.add("-I");
    command.add(buildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS));
    command.add("-p");
    command.add(FileUtil.toSystemDependentName(genFolderPath));
    command.add("-o");
    command.add(FileUtil.toSystemDependentName(rawDirPath));

    command.add("-target-api");
    int targetApi = target.getVersion().getApiLevel();
    if (targetApi < 11) {
      targetApi = 11;
    }
    command.add(Integer.toString(targetApi));

    if (depFolderPath != null) {
      command.add("-d");
      command.add(FileUtil.toSystemDependentName(depFolderPath));
    }

    command.add("-MD");
    command.add(FileUtil.toSystemDependentName(sourceFilePath));

    LOG.info(AndroidCommonUtils.command2string(command));
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(command));
  }
}
