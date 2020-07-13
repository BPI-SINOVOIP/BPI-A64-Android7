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
package org.jetbrains.android.run;

import com.android.SdkConstants;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;

/**
 * Common static utilities used by the ApkProvider implementations.
 * TODO: The Gradle and non-Gradle logic here should be split and inlined into its respective provider implementations.
 */
public class ApkProviderUtil {
  @NotNull
  static String computePackageName(@NotNull final AndroidFacet facet) throws ApkProvisionException {
    // TODO: Separate Gradle and non-Gradle logic here.
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    else if (facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
      // Ensure the local file system is up to date to enable accurate calculation of the package name.
      LocalFileSystem.getInstance().refresh(false);

      File manifestCopy = null;
      final Manifest manifest;
      final String manifestLocalPath;

      try {
        Pair<File, String> pair;
        try {
          pair = AndroidRunConfigurationBase.getCopyOfCompilerManifestFile(facet);
        } catch (IOException e) {
          throw new ApkProvisionException("Could not compute package name because of I/O error: " + e.getMessage(), e);
        }
        manifestCopy = pair != null ? pair.getFirst() : null;
        VirtualFile
            manifestVFile = manifestCopy != null ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(manifestCopy) : null;
        if (manifestVFile != null) {
          manifestVFile.refresh(false, false);
          manifest = AndroidUtils.loadDomElement(facet.getModule(), manifestVFile, Manifest.class);
        }
        else {
          manifest = null;
        }
        manifestLocalPath = pair != null ? pair.getSecond() : null;

        final Module module = facet.getModule();
        final String moduleName = module.getName();

        if (manifest == null) {
          throw new ApkProvisionException("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file for module " + moduleName);
        }

        return ApplicationManager.getApplication().runReadAction(new ThrowableComputable<String, ApkProvisionException>() {
          @Override
          public String compute() throws ApkProvisionException {
            final GenericAttributeValue<String> packageAttrValue = manifest.getPackage();
            final String aPackage = packageAttrValue.getValue();

            if (aPackage == null || aPackage.isEmpty()) {
              throw new ApkProvisionException("[" + moduleName + "] Main package is not specified in file " + manifestLocalPath);
            }
            return aPackage;
          }
        });
      }
      finally {
        if (manifestCopy != null) {
          FileUtil.delete(manifestCopy.getParentFile());
        }
      }
    }
    else {
      String pkg = AndroidModuleInfo.get(facet).getPackage();
      if (pkg == null || pkg.isEmpty()) {
        throw new ApkProvisionException("[" + facet.getModule().getName() + "] Unable to obtain main package from manifest.");
      }
      return pkg;
    }
  }

}
