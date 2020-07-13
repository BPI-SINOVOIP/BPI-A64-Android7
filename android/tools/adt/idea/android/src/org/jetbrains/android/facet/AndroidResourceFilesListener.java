/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.fileTypes.AndroidRenderscriptFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import com.android.tools.idea.lang.aidl.AidlFileType;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.findSourceRoot;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 31, 2009
 * Time: 4:49:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidResourceFilesListener extends BulkFileListener.Adapter implements Disposable {
  private static final Key<String> CACHED_PACKAGE_KEY = Key.create("ANDROID_RESOURCE_LISTENER_CACHED_PACKAGE");

  private final MergingUpdateQueue myQueue;
  private final Project myProject;

  public AndroidResourceFilesListener(@NotNull Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("AndroidResourcesCompilationQueue", 300, true, null, this, null, false);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    final Set<VirtualFile> filesToProcess = getFilesToProcess(events);

    if (filesToProcess.size() > 0) {
      myQueue.queue(new MyUpdate(filesToProcess));
    }
  }

  @NotNull
  private static Set<VirtualFile> getFilesToProcess(@NotNull List<? extends VFileEvent> events) {
    final Set<VirtualFile> result = new HashSet<VirtualFile>();

    for (VFileEvent event : events) {
      final VirtualFile file = event.getFile();

      if (file != null && shouldScheduleUpdate(file)) {
        result.add(file);
      }
    }
    return result;
  }

  private static boolean shouldScheduleUpdate(@NotNull VirtualFile file) {
    final FileType fileType = file.getFileType();

    if (fileType == AidlFileType.INSTANCE ||
        fileType == AndroidRenderscriptFileType.INSTANCE ||
        SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      return true;
    }
    else if (fileType == StdFileTypes.XML) {
      final VirtualFile parent = file.getParent();

      if (parent != null && parent.isDirectory()) {
        final String resType = AndroidCommonUtils.getResourceTypeByDirName(parent.getName());
        return ResourceFolderType.VALUES.getName().equals(resType);
      }
    }
    return false;
  }

  @Override
  public void dispose() {
  }

  public static void notifyFacetInitialized(@NotNull final AndroidFacet facet) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final Manifest manifest = facet.getManifest();

        if (manifest != null) {
          facet.putUserData(CACHED_PACKAGE_KEY, manifest.getPackage().getValue());
        }
      }
    });
  }

  private class MyUpdate extends Update {
    private final Set<VirtualFile> myFiles;

    public MyUpdate(@NotNull Set<VirtualFile> files) {
      super(files);
      myFiles = files;
    }

    @Override
    public void run() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      final MultiMap<Module, AndroidAutogeneratorMode> map =
        ApplicationManager.getApplication().runReadAction(new Computable<MultiMap<Module, AndroidAutogeneratorMode>>() {
          @Override
          @Nullable
          public MultiMap<Module, AndroidAutogeneratorMode> compute() {
            return computeCompilersToRunAndInvalidateLocalAttributesMap();
          }
        });

      if (map.isEmpty()) {
        return;
      }

      for (Map.Entry<Module, Collection<AndroidAutogeneratorMode>> entry : map.entrySet()) {
        final Module module = entry.getKey();

        for (AndroidAutogeneratorMode mode : entry.getValue()) {
          AndroidCompileUtil.generate(module, mode);
        }
      }
    }

    @NotNull
    private MultiMap<Module, AndroidAutogeneratorMode> computeCompilersToRunAndInvalidateLocalAttributesMap() {
      if (myProject.isDisposed()) {
        return MultiMap.emptyInstance();
      }
      final MultiMap<Module, AndroidAutogeneratorMode> result = MultiMap.create();
      final Set<Module> modulesToInvalidateAttributeDefs = new HashSet<Module>();

      for (VirtualFile file : myFiles) {
        final Module module = ModuleUtilCore.findModuleForFile(file, myProject);

        if (module == null || module.isDisposed()) {
          continue;
        }
        final AndroidFacet facet = AndroidFacet.getInstance(module);

        if (facet == null) {
          continue;
        }
        final VirtualFile parent = file.getParent();
        final VirtualFile gp = parent != null ? parent.getParent() : null;
        final VirtualFile resourceDir = AndroidRootUtil.getResourceDir(facet);

        if (gp != null &&
            Comparing.equal(gp, resourceDir) &&
            ResourceFolderType.VALUES.getName().equals(AndroidCommonUtils.getResourceTypeByDirName(parent.getName()))) {
          modulesToInvalidateAttributeDefs.add(module);
        }
        final List<AndroidAutogeneratorMode> modes = computeCompilersToRunAndInvalidateLocalAttributesMap(facet, file);

        if (modes.size() > 0) {
          result.putValues(module, modes);
        }
      }
      invalidateAttributeDefinitions(modulesToInvalidateAttributeDefs);
      return result;
    }

    @NotNull
    private List<AndroidAutogeneratorMode> computeCompilersToRunAndInvalidateLocalAttributesMap(AndroidFacet facet, VirtualFile file) {
      final VirtualFile parent = file.getParent();

      if (parent == null) {
        return Collections.emptyList();
      }
      final Module module = facet.getModule();
      final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(facet);
      final List<AndroidAutogeneratorMode> modes = new ArrayList<AndroidAutogeneratorMode>();

      if (Comparing.equal(manifestFile, file)) {
        final Manifest manifest = facet.getManifest();
        final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
        final String cachedPackage = facet.getUserData(CACHED_PACKAGE_KEY);

        if (cachedPackage != null && !cachedPackage.equals(aPackage)) {
          String aptGenDirPath = AndroidRootUtil.getAptGenSourceRootPath(facet);
          AndroidCompileUtil.removeDuplicatingClasses(module, cachedPackage, AndroidUtils.R_CLASS_NAME, null, aptGenDirPath);
        }
        facet.putUserData(CACHED_PACKAGE_KEY, aPackage);
        modes.add(AndroidAutogeneratorMode.AAPT);
        modes.add(AndroidAutogeneratorMode.BUILDCONFIG);
      }
      else if (file.getFileType() == AidlFileType.INSTANCE) {
        VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getAidlGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.AIDL);
        }
      }
      else if (file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
        final VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getRenderscriptGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.RENDERSCRIPT);
        }
      }
      return modes;
    }

    private void invalidateAttributeDefinitions(@NotNull Collection<Module> modules) {
      for (Module module : AndroidUtils.getSetWithBackwardDependencies(modules)) {
        final AndroidFacet facet = AndroidFacet.getInstance(module);

        if (facet != null) {
          facet.getLocalResourceManager().invalidateAttributeDefinitions();
        }
      }
    }

    @Override
    public boolean canEat(Update update) {
      return update instanceof MyUpdate && myFiles.containsAll(((MyUpdate)update).myFiles);
    }
  }
}
