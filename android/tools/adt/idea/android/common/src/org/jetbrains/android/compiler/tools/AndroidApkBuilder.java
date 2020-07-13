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

import com.android.SdkConstants;
import com.android.jarutils.DebugKeyProvider;
import com.android.jarutils.JavaResourceFilter;
import com.android.jarutils.SignedJarBuilder;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jetbrains.android.util.AndroidCompilerMessageKind.*;

/**
 * @author yole
 */
public class AndroidApkBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidApkBuilder");

  @NonNls private static final String UNALIGNED_SUFFIX = ".unaligned";
  @NonNls private static final String EXT_NATIVE_LIB = "so";

  private AndroidApkBuilder() {
  }

  private static Map<AndroidCompilerMessageKind, List<String>> filterUsingKeystoreMessages(Map<AndroidCompilerMessageKind, List<String>> messages) {
    List<String> infoMessages = messages.get(INFORMATION);
    if (infoMessages == null) {
      infoMessages = new ArrayList<String>();
      messages.put(INFORMATION, infoMessages);
    }
    final List<String> errors = messages.get(ERROR);
    for (Iterator<String> iterator = errors.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      if (s.startsWith("Using keystore:")) {
        // not actually an error
        infoMessages.add(s);
        iterator.remove();
      }
    }
    return messages;
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private static void collectDuplicateEntries(@NotNull String rootFile, @NotNull Set<String> entries, @NotNull Set<String> result)
    throws IOException {
    final JavaResourceFilter javaResourceFilter = new JavaResourceFilter();

    FileInputStream fis = null;
    ZipInputStream zis = null;
    try {
      fis = new FileInputStream(rootFile);
      zis = new ZipInputStream(fis);

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String name = entry.getName();
          if (javaResourceFilter.checkEntry(name) && !entries.add(name)) {
            result.add(name);
          }
          zis.closeEntry();
        }
      }
    }
    finally {
      if (zis != null) {
        zis.close();
      }
      if (fis != null) {
        fis.close();
      }
    }
  }

  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull String resPackagePath,
                                                                      @NotNull String dexPath,
                                                                      @NotNull String[] resourceRoots,
                                                                      @NotNull String[] externalJars,
                                                                      @NotNull String[] nativeLibsFolders,
                                                                      @NotNull Collection<AndroidNativeLibData> additionalNativeLibs,
                                                                      @NotNull String finalApk,
                                                                      boolean unsigned,
                                                                      @NotNull String sdkPath,
                                                                      @NotNull IAndroidTarget target,
                                                                      @Nullable String customKeystorePath,
                                                                      @NotNull Condition<File> resourceFilter) throws IOException {
    final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

    if (testingManager != null) {
      testingManager.getCommandExecutor().log(StringUtil.join(new String[]{
        "apk_builder",
        resPackagePath,
        dexPath,
        AndroidBuildTestingManager.arrayToString(resourceRoots),
        AndroidBuildTestingManager.arrayToString(externalJars),
        AndroidBuildTestingManager.arrayToString(nativeLibsFolders),
        additionalNativeLibs.toString(),
        finalApk,
        Boolean.toString(unsigned),
        sdkPath,
        customKeystorePath}, "\n"));
    }
    final Map<AndroidCompilerMessageKind, List<String>> map = new HashMap<AndroidCompilerMessageKind, List<String>>();
    map.put(ERROR, new ArrayList<String>());
    map.put(WARNING, new ArrayList<String>());

    final File outputDir = new File(finalApk).getParentFile();
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      map.get(ERROR).add("Cannot create directory " + outputDir.getPath());
      return map;
    }
    File additionalLibsDir = null;
    try {
      if (additionalNativeLibs.size() > 0) {
        additionalLibsDir = FileUtil.createTempDirectory("android_additional_libs", "tmp");

        if (!copyNativeLibs(additionalNativeLibs, additionalLibsDir, map)) {
          return map;
        }
        nativeLibsFolders = ArrayUtil.append(nativeLibsFolders, additionalLibsDir.getPath());
      }

      if (unsigned) {
        return filterUsingKeystoreMessages(
          finalPackage(dexPath, resourceRoots, externalJars, nativeLibsFolders, finalApk, resPackagePath, customKeystorePath, false,
                       resourceFilter));
      }
      final String zipAlignPath = AndroidCommonUtils.getZipAlign(sdkPath, target);
      boolean withAlignment = new File(zipAlignPath).exists();
      String unalignedApk = AndroidCommonUtils.addSuffixToFileName(finalApk, UNALIGNED_SUFFIX);

      Map<AndroidCompilerMessageKind, List<String>> map2 = filterUsingKeystoreMessages(
        finalPackage(dexPath, resourceRoots, externalJars, nativeLibsFolders, withAlignment ? unalignedApk : finalApk, resPackagePath,
                     customKeystorePath, true, resourceFilter));
      map.putAll(map2);

      if (withAlignment && map.get(ERROR).size() == 0) {
        map2 = AndroidExecutionUtil.doExecute(zipAlignPath, "-f", "4", unalignedApk, finalApk);
        map.putAll(map2);
      }
      return map;
    }
    finally {
      if (additionalLibsDir != null) {
        FileUtil.delete(additionalLibsDir);
      }
    }
  }

  private static boolean copyNativeLibs(@NotNull Collection<AndroidNativeLibData> libs,
                                        @NotNull File targetDir,
                                        @NotNull Map<AndroidCompilerMessageKind, List<String>> map) throws IOException {
    for (AndroidNativeLibData lib : libs) {
      final String path = lib.getPath();
      final File srcFile = new File(path);
      if (!srcFile.exists()) {
        map.get(WARNING).add("File not found: " + FileUtil.toSystemDependentName(path) + ". The native library won't be placed into APK");
        continue;
      }
      final File dstDir = new File(targetDir, lib.getArchitecture());

      final File dstFile = new File(dstDir, lib.getTargetFileName());
      if (dstFile.exists()) {
        map.get(WARNING).add("Duplicate native library " + dstFile.getName() + "; " + dstFile.getPath() + " already exists");
        continue;
      }

      if (!dstDir.exists() && !dstDir.mkdirs()) {
        map.get(ERROR).add("Cannot create directory: " + FileUtil.toSystemDependentName(dstDir.getPath()));
        continue;
      }
      FileUtil.copy(srcFile, dstFile);
    }
    return map.get(ERROR).size() == 0;
  }

  private static Map<AndroidCompilerMessageKind, List<String>> finalPackage(@NotNull String dexPath,
                                                                            @NotNull String[] javaResourceRoots,
                                                                            @NotNull String[] externalJars,
                                                                            @NotNull String[] nativeLibsFolders,
                                                                            @NotNull String outputApk,
                                                                            @NotNull String apkPath,
                                                                            @Nullable String customKeystorePath,
                                                                            boolean signed,
                                                                            @NotNull Condition<File> resourceFilter) {
    final Map<AndroidCompilerMessageKind, List<String>> result = new HashMap<AndroidCompilerMessageKind, List<String>>();
    result.put(ERROR, new ArrayList<String>());
    result.put(INFORMATION, new ArrayList<String>());
    result.put(WARNING, new ArrayList<String>());

    FileOutputStream fos = null;
    SignedJarBuilder builder = null;
    try {

      String keyStoreOsPath = customKeystorePath != null && customKeystorePath.length() > 0
                              ? customKeystorePath 
                              : DebugKeyProvider.getDefaultKeyStoreOsPath();
      
      DebugKeyProvider provider = createDebugKeyProvider(result, keyStoreOsPath);

      X509Certificate certificate = signed ? (X509Certificate)provider.getCertificate() : null;

      if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
        // generate a new one
        File keyStoreFile = new File(keyStoreOsPath);
        if (keyStoreFile.exists()) {
          keyStoreFile.delete();
        }
        provider = createDebugKeyProvider(result, keyStoreOsPath);
        certificate = (X509Certificate)provider.getCertificate();
      }

      if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
        String date = DateFormatUtil.formatPrettyDateTime(certificate.getNotAfter());
        result.get(ERROR).add(
          ("Debug certificate expired on " + date + ". Cannot regenerate it, please delete file \"" + keyStoreOsPath + "\" manually."));
        return result;
      }

      PrivateKey key = provider.getDebugKey();

      if (key == null) {
        result.get(ERROR).add("Cannot create new key or keystore");
        return result;
      }

      if (!new File(apkPath).exists()) {
        result.get(ERROR).add("File " + apkPath + " not found. Try to rebuild project");
        return result;
      }

      File dexEntryFile = new File(dexPath);
      if (!dexEntryFile.exists()) {
        result.get(ERROR).add("File " + dexEntryFile.getPath() + " not found. Try to rebuild project");
        return result;
      }

      for (String externalJar : externalJars) {
        if (new File(externalJar).isDirectory()) {
          result.get(ERROR).add(externalJar + " is directory. Directory libraries are not supported");
        }
      }

      if (result.get(ERROR).size() > 0) {
        return result;
      }

      fos = new FileOutputStream(outputApk);
      builder = new SafeSignedJarBuilder(fos, key, certificate, outputApk);

      FileInputStream fis = new FileInputStream(apkPath);
      try {
        builder.writeZip(fis, null);
      }
      finally {
        fis.close();
      }

      builder.writeFile(dexEntryFile, AndroidCommonUtils.CLASSES_FILE_NAME);

      final HashSet<String> added = new HashSet<String>();
      for (String resourceRootPath : javaResourceRoots) {
        final HashSet<File> javaResources = new HashSet<File>();
        final File resourceRoot = new File(resourceRootPath);
        collectStandardJavaResources(resourceRoot, javaResources, resourceFilter);
        writeStandardJavaResources(javaResources, resourceRoot, builder, added);
      }

      Set<String> duplicates = new HashSet<String>();
      Set<String> entries = new HashSet<String>();
      for (String externalJar : externalJars) {
        collectDuplicateEntries(externalJar, entries, duplicates);
      }

      for (String duplicate : duplicates) {
        result.get(WARNING).add("Duplicate entry " + duplicate + ". The file won't be added");
      }

      MyResourceFilter filter = new MyResourceFilter(duplicates);

      for (String externalJar : externalJars) {
        fis = new FileInputStream(externalJar);
        try {
          builder.writeZip(fis, filter);
        }
        finally {
          fis.close();
        }
      }

      final HashSet<String> nativeLibs = new HashSet<String>();
      for (String nativeLibsFolderPath : nativeLibsFolders) {
        final File nativeLibsFolder = new File(nativeLibsFolderPath);
        final File[] children = nativeLibsFolder.listFiles();

        if (children != null) {
          for (File child : children) {
            writeNativeLibraries(builder, nativeLibsFolder, child, signed, nativeLibs);
          }
        }
      }
    }
    catch (IOException e) {
      return addExceptionMessage(e, result);
    }
    catch (CertificateException e) {
      return addExceptionMessage(e, result);
    }
    catch (DebugKeyProvider.KeytoolException e) {
      return addExceptionMessage(e, result);
    }
    catch (AndroidLocation.AndroidLocationException e) {
      return addExceptionMessage(e, result);
    }
    catch (NoSuchAlgorithmException e) {
      return addExceptionMessage(e, result);
    }
    catch (UnrecoverableEntryException e) {
      return addExceptionMessage(e, result);
    }
    catch (KeyStoreException e) {
      return addExceptionMessage(e, result);
    }
    catch (GeneralSecurityException e) {
      return addExceptionMessage(e, result);
    }
    finally {
      if (builder != null) {
        try {
          builder.close();
        }
        catch (IOException e) {
          addExceptionMessage(e, result);
        }
        catch (GeneralSecurityException e) {
          addExceptionMessage(e, result);
        }
      }

      if (fos != null) {
        try {
          fos.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    return result;
  }

  private static DebugKeyProvider createDebugKeyProvider(final Map<AndroidCompilerMessageKind, List<String>> result, String path) throws
                                                                                                                               KeyStoreException,
                                                                                                                               NoSuchAlgorithmException,
                                                                                                                               CertificateException,
                                                                                                                               UnrecoverableEntryException,
                                                                                                                               IOException,
                                                                                                                               DebugKeyProvider.KeytoolException,
                                                                                                                               AndroidLocation.AndroidLocationException {

    return new DebugKeyProvider(path, null, new DebugKeyProvider.IKeyGenOutput() {
      public void err(String message) {
        result.get(ERROR).add("Error during key creation: " + message);
      }

      public void out(String message) {
        result.get(INFORMATION).add("Info message during key creation: " + message);
      }
    });
  }

  private static void writeNativeLibraries(SignedJarBuilder builder,
                                           File nativeLibsFolder,
                                           File child,
                                           boolean debugBuild,
                                           Set<String> added)
    throws IOException {
    ArrayList<File> list = new ArrayList<File>();
    collectNativeLibraries(child, list, debugBuild);

    for (File file : list) {
      final String relativePath = FileUtil.getRelativePath(nativeLibsFolder, file);
      String path = FileUtil.toSystemIndependentName(SdkConstants.FD_APK_NATIVE_LIBS + File.separator + relativePath);

      if (added.add(path)) {
        builder.writeFile(file, path);
        LOG.info("Native lib file added to APK: " + file.getPath());
      }
      else {
        LOG.info("Duplicate in APK: native lib file " + file.getPath() + " won't be added.");
      }
    }
  }

  private static Map<AndroidCompilerMessageKind, List<String>> addExceptionMessage(Exception e,
                                                                                Map<AndroidCompilerMessageKind, List<String>> result) {
    LOG.info(e);
    String simpleExceptionName = e.getClass().getCanonicalName();
    result.get(ERROR).add(simpleExceptionName + ": " + e.getMessage());
    return result;
  }

  public static void collectNativeLibraries(@NotNull File file, @NotNull List<File> result, boolean debugBuild) {
    if (!file.isDirectory()) {

      // some users store jars and *.so libs in the same directory. Do not pack JARs to APKs "lib" folder!
      if (FileUtilRt.extensionEquals(file.getName(), EXT_NATIVE_LIB) ||
          (debugBuild && !(FileUtilRt.extensionEquals(file.getName(), "jar")))) {
        result.add(file);
      }
    }
    else if (JavaResourceFilter.checkFolderForPackaging(file.getName())) {
      final File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          collectNativeLibraries(child, result, debugBuild);
        }
      }
    }
  }

  public static void collectStandardJavaResources(@NotNull File folder,
                                                  @NotNull Collection<File> result,
                                                  @NotNull Condition<File> filter) {
    final File[] children = folder.listFiles();

    if (children != null) {
      for (File child : children) {
        if (child.exists()) {
          if (child.isDirectory()) {
            if (JavaResourceFilter.checkFolderForPackaging(child.getName()) && filter.value(child)) {
              collectStandardJavaResources(child, result, filter);
            }
          }
          else if (checkFileForPackaging(child) && filter.value(child)) {
            result.add(child);
          }
        }
      }
    }
  }

  private static void writeStandardJavaResources(Collection<File> resources,
                                                 File sourceRoot,
                                                 SignedJarBuilder jarBuilder,
                                                 Set<String> added) throws IOException {
    for (File child : resources) {
      final String relativePath = FileUtil.getRelativePath(sourceRoot, child);
      if (relativePath != null && !added.contains(relativePath)) {
        jarBuilder.writeFile(child, FileUtil.toSystemIndependentName(relativePath));
        added.add(relativePath);
      }
    }
  }

  public static boolean checkFileForPackaging(@NotNull File file) {
    String fileName = FileUtil.getNameWithoutExtension(file);
    if (fileName.length() > 0) {
      final String extension = FileUtilRt.getExtension(file.getName());

      if (SdkConstants.EXT_ANDROID_PACKAGE.equals(extension)) {
        return false;
      }
      return JavaResourceFilter.checkFileForPackaging(fileName, extension);
    }
    return false;
  }

  private static class MyResourceFilter extends JavaResourceFilter {
    private final Set<String> myExcludedEntries;

    public MyResourceFilter(@NotNull Set<String> excludedEntries) {
      myExcludedEntries = excludedEntries;
    }

    @Override
    public boolean checkEntry(String name) {
      if (myExcludedEntries.contains(name)) {
        return false;
      }
      return super.checkEntry(name);
    }
  }
}
