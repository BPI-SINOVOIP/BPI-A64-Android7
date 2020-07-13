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
package org.jetbrains.jps.android.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsAndroidModuleExtensionImpl extends JpsElementBase<JpsAndroidModuleExtensionImpl> implements JpsAndroidModuleExtension {
  public static final JpsElementChildRoleBase<JpsAndroidModuleExtension> KIND = JpsElementChildRoleBase.create("android extension");
  private final JpsAndroidModuleProperties myProperties;

  public JpsAndroidModuleExtensionImpl(JpsAndroidModuleProperties properties) {
    myProperties = properties;
  }

  @Override
  public JpsModule getModule() {
    return (JpsModule)getParent();
  }

  @NotNull
  @Override
  public JpsAndroidModuleExtensionImpl createCopy() {
    return new JpsAndroidModuleExtensionImpl(XmlSerializerUtil.createCopy(myProperties));
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidModuleExtensionImpl modified) {
    XmlSerializerUtil.copyBean(modified.myProperties, myProperties);
    fireElementChanged();
  }

  @Override
  public String getCustomDebugKeyStorePath() {
    return JpsPathUtil.urlToPath(myProperties.CUSTOM_DEBUG_KEYSTORE_PATH);
  }

  @Override
  public List<AndroidNativeLibData> getAdditionalNativeLibs() {
    final List<AndroidNativeLibData> libDatas = new ArrayList<AndroidNativeLibData>();
    for (JpsAndroidModuleProperties.AndroidNativeLibDataEntry nativeLib : myProperties.myNativeLibs) {
      if (nativeLib.myArchitecture != null && nativeLib.myUrl != null && nativeLib.myTargetFileName != null) {
        libDatas.add(new AndroidNativeLibData(nativeLib.myArchitecture, JpsPathUtil.urlToPath(nativeLib.myUrl), nativeLib.myTargetFileName));
      }
    }
    return libDatas;
  }

  @Override
  public boolean isUseCustomManifestPackage() {
    return myProperties.USE_CUSTOM_MANIFEST_PACKAGE;
  }

  @Override
  public String getCustomManifestPackage() {
    return myProperties.CUSTOM_MANIFEST_PACKAGE;
  }

  @Override
  public String getAdditionalPackagingCommandLineParameters() {
    return myProperties.ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS;
  }

  @Override
  public boolean isManifestMergingEnabled() {
    return myProperties.ENABLE_MANIFEST_MERGING;
  }

  @Override
  public boolean isPreDexingEnabled() {
    return myProperties.ENABLE_PRE_DEXING;
  }

  @Override
  public boolean isCopyCustomGeneratedSources() {
    return myProperties.COMPILE_CUSTOM_GENERATED_SOURCES;
  }

  @Override
  public File getResourceDir() {
    File resDir = findFileByRelativeModulePath(myProperties.RES_FOLDER_RELATIVE_PATH, false);
    return resDir != null ? canonizeFilePath(resDir) : null;
  }

  @NotNull
  @Override
  public List<File> getResourceOverlayDirs() {
    final List<String> paths = myProperties.RES_OVERLAY_FOLDERS;

    if (paths == null || paths.isEmpty()) {
      return Collections.emptyList();
    }
    return ContainerUtil.mapNotNull(paths, new Function<String, File>() {
      @Override
      public File fun(String s) {
        final File resDir = findFileByRelativeModulePath(s, false);
        return resDir != null ? canonizeFilePath(resDir) : null;
      }
    });
  }

  @Override
  public File getResourceDirForCompilation() {
    File resDir = findFileByRelativeModulePath(myProperties.CUSTOM_APK_RESOURCE_FOLDER, false);
    return resDir != null ? canonizeFilePath(resDir) : null;
  }

  @Override
  public File getManifestFile() {
    File manifestFile = findFileByRelativeModulePath(myProperties.MANIFEST_FILE_RELATIVE_PATH, false);
    return manifestFile != null ? canonizeFilePath(manifestFile) : null;
  }

  @Override
  public File getManifestFileForCompilation() {
    File manifestFile = findFileByRelativeModulePath(myProperties.CUSTOM_COMPILER_MANIFEST, false);
    return manifestFile != null ? canonizeFilePath(manifestFile) : null;
  }

  @Nullable
  @Override
  public List<File> getProguardConfigFiles(@NotNull JpsModule module) throws IOException {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
    final String sdkHomePath = sdk != null ? FileUtil.toSystemIndependentName(sdk.getHomePath()) : null;
    final List<String> urls = myProperties.myProGuardCfgFiles;

    if (urls == null) {
      return null;
    }
    if (urls.isEmpty()) {
      return Collections.emptyList();
    }
    final List<File> result = new ArrayList<File>();

    for (String url : urls) {
      if (sdkHomePath != null) {
        url = StringUtil.replace(url, AndroidCommonUtils.SDK_HOME_MACRO, sdkHomePath);
      }
      result.add(JpsPathUtil.urlToFile(url));
    }
    return result;
  }

  @Override
  public File getAssetsDir() {
    File manifestFile = findFileByRelativeModulePath(myProperties.ASSETS_FOLDER_RELATIVE_PATH, false);
    return manifestFile != null ? canonizeFilePath(manifestFile) : null;
  }

  public File getAaptGenDir() throws IOException {
    File aaptGenDir = findFileByRelativeModulePath(myProperties.GEN_FOLDER_RELATIVE_PATH_APT, true);
    return aaptGenDir != null ? canonizeFilePath(aaptGenDir) : null;
  }

  public File getAidlGenDir() throws IOException {
    File aidlGenDir = findFileByRelativeModulePath(myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL, true);
    return aidlGenDir != null ? canonizeFilePath(aidlGenDir) : null;
  }

  public JpsAndroidModuleProperties getProperties() {
    return myProperties;
  }

  @Override
  public File getNativeLibsDir() {
    File nativeLibsFolder = findFileByRelativeModulePath(myProperties.LIBS_FOLDER_RELATIVE_PATH, true);
    return nativeLibsFolder != null ? canonizeFilePath(nativeLibsFolder) : null;
  }

  @Override
  public File getProguardLogsDir() {
    File proguardLogsDir = findFileByRelativeModulePath(myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH, false);
    return proguardLogsDir != null ? canonizeFilePath(proguardLogsDir) : null;
  }

  private static File canonizeFilePath(@NotNull File file) {
    return new File(FileUtil.toCanonicalPath(file.getPath()));
  }

  @Nullable
  private File findFileByRelativeModulePath(String relativePath, boolean checkExistence) {
    if (relativePath == null || relativePath.length() == 0) {
      return null;
    }

    final JpsModule module = getModule();
    File moduleBaseDir = JpsModelSerializationDataService.getBaseDirectory(module);
    if (moduleBaseDir != null) {
      String absPath = FileUtil.toSystemDependentName(moduleBaseDir.getAbsolutePath() + relativePath);
      File f = new File(absPath);

      if (!checkExistence || f.exists()) {
        return f;
      }
    }
    return null;
  }

  @Override
  public boolean isGradleProject() {
    return !myProperties.ALLOW_USER_CONFIGURATION;
  }

  @Override
  public boolean isLibrary() {
    return myProperties.LIBRARY_PROJECT;
  }

  @Override
  public boolean useCustomResFolderForCompilation() {
    return myProperties.USE_CUSTOM_APK_RESOURCE_FOLDER;
  }

  @Override
  public boolean useCustomManifestForCompilation() {
    return myProperties.USE_CUSTOM_COMPILER_MANIFEST;
  }

  @Override
  public boolean isPackTestCode() {
    return myProperties.PACK_TEST_CODE;
  }

  @Override
  public boolean isIncludeAssetsFromLibraries() {
    return myProperties.myIncludeAssetsFromLibraries;
  }

  @Override
  public boolean isRunProcessResourcesMavenTask() {
    return myProperties.RUN_PROCESS_RESOURCES_MAVEN_TASK;
  }

  @Override
  public boolean isRunProguard() {
    return myProperties.RUN_PROGUARD;
  }

  @Override
  public String getApkRelativePath() {
    return myProperties.APK_PATH;
  }
}
