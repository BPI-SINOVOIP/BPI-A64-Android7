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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * This utility class contains utility methods for selecting paths.
 *
 * @author Constantine.Plotnikov
 */
public class PathUIUtils {
  public static final RootDetector JAVA_SOURCE_ROOT_DETECTOR = new RootDetector(OrderRootType.SOURCES, false, "sources") {
    @NotNull
    @Override
    public Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate,
                                               @NotNull ProgressIndicator progressIndicator) {
      return JavaVfsSourceRootDetectionUtil.suggestRoots(rootCandidate, progressIndicator);
    }
  };

  private PathUIUtils() {
  }

  /**
   * This method takes a candidates for the project root, then scans the candidates and
   * if multiple candidates or non root source directories are found whithin some
   * directories, it shows a dialog that allows selecting or deselecting them.
   * @param parent a parent parent or project
   * @param rootCandidates a candidates for roots
   * @return a array of source folders or empty array if non was selected or dialog was canceled.
   */
  public static VirtualFile[] scanAndSelectDetectedJavaSourceRoots(Component parentComponent, final VirtualFile[] rootCandidates) {
    final List<OrderRoot> orderRoots = RootDetectionUtil.detectRoots(Arrays.asList(rootCandidates), parentComponent, null,
                                                                     new LibraryRootsDetectorImpl(Collections.singletonList(JAVA_SOURCE_ROOT_DETECTOR)),
                                                                     new OrderRootType[0]);
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (OrderRoot root : orderRoots) {
      result.add(root.getFile());
    }
    return VfsUtil.toVirtualFileArray(result);
  }
}
