/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.impl;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.DirtBuilder;
import com.intellij.openapi.vcs.changes.FilePathUnderVcs;
import com.intellij.openapi.vcs.changes.VcsGuess;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ModuleDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;
  private final ModuleManager myModuleManager;

  public ModuleDefaultVcsRootPolicy(final Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
    myModuleManager = ModuleManager.getInstance(myProject);
  }

  @Override
  public void addDefaultVcsRoots(final NewMappings mappingList, @NotNull final String vcsName, final List<VirtualFile> result) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (myBaseDir != null && vcsName.equals(mappingList.getVcsFor(myBaseDir))) {
      final AbstractVcs vcsFor = vcsManager.getVcsFor(myBaseDir);
      if (vcsFor != null && vcsName.equals(vcsFor.getName())) {
        result.add(myBaseDir);
      }
    }
    final StorageScheme storageScheme = ((ProjectEx) myProject).getStateStore().getStorageScheme();
    if (StorageScheme.DIRECTORY_BASED.equals(storageScheme) && (myBaseDir != null)) {
      final VirtualFile ideaDir = myBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
        final AbstractVcs vcsFor = vcsManager.getVcsFor(ideaDir);
        if (vcsFor != null && vcsName.equals(vcsFor.getName())) {
          result.add(ideaDir);
        }
      }
    }
    // assertion for read access inside
    final Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      public Module[] compute() {
        return myModuleManager.getModules();
      }
    });
    for(Module module: modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        // if we're currently processing moduleAdded notification, getModuleForFile() will return null, so we pass the module
        // explicitly (we know it anyway)
        VcsDirectoryMapping mapping = mappingList.getMappingFor(file, module);
        final String mappingVcs = mapping != null ? mapping.getVcs() : null;
        if (vcsName.equals(mappingVcs) && !result.contains(file)) {
          result.add(file);
        }
      }
    }
  }

  @Override
  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    if (matchContext != null) {
      return true;
    }
    return myBaseDir != null && VfsUtilCore.isAncestor(myBaseDir, file, false);
  }

  @Override
  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return ModuleUtilCore.findModuleForFile(file, myProject);
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    if (myBaseDir != null && PeriodicalTasksCloser.getInstance().safeGetService(myProject, FileIndexFacade.class)
      .isValidAncestor(myBaseDir, file)) {
      return myBaseDir;
    }
    final VirtualFile contentRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getContentRootForFile(file, Registry.is("ide.hide.excluded.files"));
    if (contentRoot != null) {
      return contentRoot;
    }
    final StorageScheme storageScheme = ((ProjectEx) myProject).getStateStore().getStorageScheme();
    if (StorageScheme.DIRECTORY_BASED.equals(storageScheme) && (myBaseDir != null)) {
      final VirtualFile ideaDir = myBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
        if (VfsUtilCore.isAncestor(ideaDir, file, false)) {
          return ideaDir;
        }
      }
    }
    return null;
  }

  @Override
  public void markDefaultRootsDirty(final DirtBuilder builder, final VcsGuess vcsGuess) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final Module[] modules = myModuleManager.getModules();
        final StorageScheme storageScheme = ((ProjectEx) myProject).getStateStore().getStorageScheme();
        if (StorageScheme.DIRECTORY_BASED.equals(storageScheme)) {
          final FilePathImpl fp = new FilePathImpl(myBaseDir, Project.DIRECTORY_STORE_FOLDER, true);
          final AbstractVcs vcs = vcsGuess.getVcsForDirty(fp);
          if (vcs != null) {
            builder.addDirtyDirRecursively(new FilePathUnderVcs(fp, vcs));
          }
        }

        for(Module module: modules) {
          final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
          for(VirtualFile file: files) {
            final AbstractVcs vcs = vcsGuess.getVcsForDirty(file);
            if (vcs != null) {
              builder.addDirtyDirRecursively(new VcsRoot(vcs, file));
            }
          }
        }

        final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(myProject);
        final String defaultMapping = ((ProjectLevelVcsManagerEx)plVcsManager).haveDefaultMapping();
        final boolean haveDefaultMapping = (defaultMapping != null) && (!defaultMapping.isEmpty());
        if (haveDefaultMapping && (myBaseDir != null)) {
          final AbstractVcs vcs = vcsGuess.getVcsForDirty(myBaseDir);
          if (vcs != null) {
            builder.addDirtyFile(new VcsRoot(vcs, myBaseDir));
          }
        }
      }
    });
  }
}
