/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * @author max
 */
public abstract class ManagingFS implements FileSystemInterface {
  private static class ManagingFSHolder {
    private static final ManagingFS ourInstance = ApplicationManager.getApplication().getComponent(ManagingFS.class);
  }

  public static ManagingFS getInstance() {
    return ManagingFSHolder.ourInstance;
  }

  @Nullable
  public abstract DataInputStream readAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att);

  @NotNull
  public abstract DataOutputStream writeAttribute(@NotNull VirtualFile file, @NotNull FileAttribute att);

  public abstract int getModificationCount(@NotNull VirtualFile fileOrDirectory);

  // Only counts modifications done in current IDEA session
  public abstract int getCheapFileSystemModificationCount();

  public abstract int getFilesystemModificationCount();

  public abstract long getCreationTimestamp();

  public abstract boolean areChildrenLoaded(@NotNull VirtualFile dir);

  public abstract boolean wereChildrenAccessed(@NotNull VirtualFile dir);

  @Nullable
  public abstract NewVirtualFile findRoot(@NotNull String basePath, @NotNull NewVirtualFileSystem fs);

  @NotNull
  public abstract VirtualFile[] getRoots();

  @NotNull
  public abstract VirtualFile[] getRoots(@NotNull NewVirtualFileSystem fs);

  @NotNull
  public abstract VirtualFile[] getLocalRoots();

  @Nullable
  public abstract VirtualFile findFileById(int id);
}
