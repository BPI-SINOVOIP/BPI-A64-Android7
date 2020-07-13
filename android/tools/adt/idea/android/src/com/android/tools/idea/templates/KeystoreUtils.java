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
package com.android.tools.idea.templates;

import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.prefs.AndroidLocation;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.List;

/**
 * Functions for dealing with singing configurations and keystore files.
 */
public class KeystoreUtils {

  /**
   * Get the debug keystore path.
   *
   * @return the keystore file.
   * @throws Exception if the keystore file could not be obtained.
   */
  @NotNull
  public static File getDebugKeystore(@NotNull AndroidFacet facet) throws Exception {
    File gradleDebugKeystore = getGradleDebugKeystore(facet);
    if (gradleDebugKeystore != null) {
      return gradleDebugKeystore;
    }

    JpsAndroidModuleProperties state = facet.getConfiguration().getState();
    if (state != null && !Strings.isNullOrEmpty(state.CUSTOM_DEBUG_KEYSTORE_PATH)) {
      return new File(state.CUSTOM_DEBUG_KEYSTORE_PATH);
    }
    return getOrCreateDefaultDebugKeystore();
  }


  public static File getOrCreateDefaultDebugKeystore() throws Exception {
    try {
      File debugLocation = new File(KeystoreHelper.defaultDebugKeystoreLocation());
      if (!debugLocation.exists()) {
        ILogger logger = new StdLogger(StdLogger.Level.ERROR);
        // Default values taken from http://developer.android.com/tools/publishing/app-signing.html
        // Keystore name: "debug.keystore"
        // Keystore password: "android"
        // Keystore alias: "androiddebugkey"
        // Key password: "android"
        KeystoreHelper.createDebugStore(null, debugLocation, "android", "android", "AndroidDebugKey",logger);
      }
      if (!debugLocation.exists()) {
        throw new AndroidLocation.AndroidLocationException("Could not create debug keystore");
      }
      return debugLocation;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      throw new Exception("Failed to get debug keystore path", e);
    }
  }


  /**
   * Gets a custom debug keystore defined in the build.gradle file for this module
   *
   * @return null if there is no custom debug keystore configured, or if the project is not a Gradle project.
   */
  @Nullable
  private static File getGradleDebugKeystore(@NotNull AndroidFacet facet) {
    GradleSettingsFile gradleSettingsFile = GradleSettingsFile.get(facet.getModule().getProject());
    if (gradleSettingsFile == null) {
      return null;
    }

    String modulePath = GradleSettingsFile.getModuleGradlePath(facet.getModule());
    if (modulePath == null) {
      return null;
    }

    GradleBuildFile moduleBuildFile = gradleSettingsFile.getModuleBuildFile(modulePath);
    if (moduleBuildFile == null) {
      return null;
    }

    @SuppressWarnings("unchecked") List<NamedObject> signingConfigs =
      (List<NamedObject>)moduleBuildFile.getValue(BuildFileKey.SIGNING_CONFIGS);
    if (signingConfigs == null) {
      return null;
    }

    for (NamedObject namedObject : signingConfigs) {
      if (!"debug".equals(namedObject.getName())) {
        continue;
      }
      File debugKey = (File)namedObject.getValue(BuildFileKey.STORE_FILE);
      if (debugKey == null) {
        continue;
      }
      VirtualFile moduleFile = facet.getModule().getModuleFile();
      if (moduleFile == null) {
        continue;
      }
      // NOTE: debugKey.getParent() is the current working directory.
      return new File(moduleFile.getParent().getPath(), debugKey.getPath());
    }
    return null;
  }

  /**
   * Get the SHA1 hash of the first signing certificate inside a keystore, encoded as base16 (each byte separated by ':').
   *
   * @param keystoreFile the keystore file. Must be readable.
   * @throws Exception when the sha1 couldn't be computed for any reason.
   */
  public static String sha1(File keystoreFile) throws Exception {
    Certificate signingCert;

    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      keyStore.load(new FileInputStream(keystoreFile), "android".toCharArray());
      String keyAlias = keyStore.aliases().nextElement();
      signingCert = keyStore.getCertificate(keyAlias);
    }
    catch (Exception e) {
      throw new Exception("Could not extract certificate from file.", e);
    }

    // Produce SHA1 fingerprint.

    try {
      byte[] certBytes = MessageDigest.getInstance("SHA1").digest(signingCert.getEncoded());
      // Add a separator every 2 characters (i.e. every byte from hash)
      return BaseEncoding.base16().withSeparator(":", 2).encode(certBytes);
    }
    catch (Exception e) {
      throw new Exception("Could not compute SHA1 hash from certificate", e);
    }
  }
}
