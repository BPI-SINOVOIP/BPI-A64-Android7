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
package com.android.tools.idea.rendering;

import com.android.io.FileWrapper;
import com.android.xml.AndroidManifest;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;

/**
 * A registry for class lookup of resource classes (R classes) in AAR libraries.
 */
public class AarResourceClassRegistry implements ProjectComponent {

  private final Map<AppResourceRepository, AarResourceClassGenerator> myGeneratorMap = Maps.newHashMap();
  private final Project myProject;
  private Collection<String> myPackages;

  @SuppressWarnings("WeakerAccess")  // Accessed via reflection.
  public AarResourceClassRegistry(Project project) {
    myProject = project;
  }

  public void addLibrary(AppResourceRepository appResources, File aarDir) {
    String path = aarDir.getPath();
    if (path.endsWith(DOT_AAR) || path.contains(EXPLODED_AAR)) {
      FileResourceRepository repository = appResources.findRepositoryFor(aarDir);
      if (repository != null) {
        String pkg = getAarPackage(aarDir);
        if (pkg != null) {
          if (myPackages == null) {
            myPackages = new HashSet<String>();
          }
          myPackages.add(pkg);
        }
        if (!myGeneratorMap.containsKey(appResources)) {
          AarResourceClassGenerator generator = AarResourceClassGenerator.create(appResources);
          myGeneratorMap.put(appResources, generator);
        }
      }
    }
  }

  @Nullable
  private static String getAarPackage(@NotNull File aarDir) {
    File manifest = new File(aarDir, ANDROID_MANIFEST_XML);
    if (manifest.exists()) {
      try {
        // TODO: Come up with something more efficient! A pull parser can do this quickly
        return AndroidManifest.getPackage(new FileWrapper(manifest));
      }
      catch (Exception e) {
        // No go
        return null;
      }
    }

    return null;
  }

  /** Looks up a class definition for the given name, if possible */
  @Nullable
  public byte[] findClassDefinition(@NotNull String name, @NotNull AppResourceRepository appRepo) {
    int index = name.lastIndexOf('.');
    if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
      // If this is an R class or one of its inner classes.
      String pkg = name.substring(0, index);
      if (myPackages != null && myPackages.contains(pkg)) {
        AarResourceClassGenerator generator = myGeneratorMap.get(appRepo);
        if (generator != null) {
          return generator.generate(name);
        }
      }
    }
    return null;
  }

  /**
   * Ideally, this method will not exist. But there are potential bugs in the caching mechanism.
   * So, the method should be called when rendering fails due to hard to explain causes: like
   * NoSuchFieldError. The method also resets the dynamic ids generated in {@link AppResourceRepository}.
   */
  public void clearCache() {
    myGeneratorMap.clear();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AppResourceRepository appResources = AppResourceRepository.getAppResources(module, false);
      if (appResources != null) {
        appResources.resetDynamicIds(false);
      }
    }
  }

  void clearCache(AppResourceRepository appResources) {
    myGeneratorMap.remove(appResources);
  }

  /**
   * Lazily instantiate a registry with the target project.
   */
  public static AarResourceClassRegistry get(@NotNull Project project) {
    return project.getComponent(AarResourceClassRegistry.class);
  }

  // ProjectComponent methods.

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return AarResourceClassRegistry.class.getName();
  }
}