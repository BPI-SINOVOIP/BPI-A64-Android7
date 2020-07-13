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

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

/**
 * A reference to a particular file in the project
 */
public class IncludeReference {
  @SuppressWarnings("ConstantConditions")
  public static final IncludeReference NONE = new IncludeReference(null, null, null);

  /** Tools namespace attribute for declaring a surrounding layout to be used */
  public static final String ATTR_RENDER_IN = "showIn";

  /**
   * The source file of the reference (included from)
   */
  @NotNull
  private final VirtualFile myFromFile;

  /**
   * The destination file of the reference (the file being included)
   */
  @Nullable
  private final VirtualFile myToFile;

  /**
   * The module containing the file
   */
  @NotNull
  private final Module myModule;

  /**
   * Creates a new include reference
   */
  private IncludeReference(@NonNull Module module, @NonNull VirtualFile fromFile,  @Nullable VirtualFile toFile) {
    myModule = module;
    myFromFile = fromFile;
    myToFile = toFile;
  }

  /**
   * Creates a new include reference
   */
  public static IncludeReference create(@NonNull Module module, @NonNull VirtualFile fromFile,  @Nullable VirtualFile toFile) {
    return new IncludeReference(module, fromFile, toFile);
  }

  /**
   * Returns the associated module
   * @return the module
   */
  @NotNull
  public Module getModule() {
    return myModule;
  }

  /**
   * Returns the file for the include reference
   * @return the file
   */
  @NotNull
  public VirtualFile getFromFile() {
    return myFromFile;
  }

  /**
   * Returns the path for the include reference
   * @return the path
   */
  @NotNull
  public File getFromPath() {
    return VfsUtilCore.virtualToIoFile(myFromFile);
  }

  /**
   * Returns the destination file for the include reference
   * @return the destination file
   */
  @Nullable
  public VirtualFile getToFile() {
    return myToFile;
  }

  /**
   * Returns the destination path for the include reference
   * @return the destination path, if known
   */
  @Nullable
  public File getToPath() {
    return myToFile != null ? VfsUtilCore.virtualToIoFile(myToFile) : null;
  }

  /**
   * Returns a description of this reference, suitable to be shown to the user
   *
   * @return a display name for the reference
   */
  @NotNull
  public String getFromDisplayName() {
    // The ID is deliberately kept in a pretty user-readable format but we could
    // consider prepending layout/ on ids that don't have it (to make the display
    // more uniform) or ripping out all layout[-constraint] prefixes out and
    // instead prepending @ etc.
    if (myToFile != null && myToFile.getParent() != null && myToFile.getParent().equals(myFromFile.getParent())) {
      return myFromFile.getName();
    }

    return myFromFile.getParent().getName() + '/' + myFromFile.getName();
  }

  /**
   * Returns the resource name of this layout
   *
   * @return the resource name
   */
  @NotNull
  public String getFromResourceName() {
    return ResourceHelper.getResourceName(myFromFile);
  }

  /**
   * Returns the resource URL for this layout, such as {@code @layout/foo}.
   *
   * @return the resource URL
   */
  @NotNull
  public String getFromResourceUrl() {
    return LAYOUT_RESOURCE_PREFIX + getFromResourceName();
  }

  @Nullable
  public static String getIncludingLayout(@NotNull final XmlFile file) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          return getIncludingLayout(file);
        }
      });
    }
    XmlTag rootTag = file.getRootTag();
    if (rootTag != null && rootTag.isValid()) {
      return rootTag.getAttributeValue(ATTR_RENDER_IN, TOOLS_URI);
    }

    return null;
  }

  public static void setIncludingLayout(@NotNull Project project, @NotNull XmlFile xmlFile, @Nullable String layout) {
    XmlTag tag = xmlFile.getRootTag();
    if (tag != null) {
      SetAttributeFix fix = new SetAttributeFix(project, tag, ATTR_RENDER_IN, TOOLS_URI, layout);
      fix.execute();
    }
  }

  /** Returns an {link IncludeReference} specified for the given file, or {@link #NONE} if no include should be performed from the
   * given file */
  @NotNull
  public static IncludeReference get(@NotNull final Module module, @NotNull final XmlFile file, @NotNull final RenderResources resolver) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<IncludeReference>() {
        @NotNull
        @Override
        public IncludeReference compute() {
          return get(module, file, resolver);
        }
      });
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlTag rootTag = file.getRootTag();
    if (rootTag != null) {
      String layout = rootTag.getAttributeValue(ATTR_RENDER_IN, TOOLS_URI);
      if (layout != null) {
        ResourceValue resValue = resolver.findResValue(layout, false);
        if (resValue != null) {
          // TODO: Do some sort of picking based on best configuration!!
          // I should make sure I also get a configuration that is compatible with
          // my target include! I could stash it in the include reference!
          File path = ResourceHelper.resolveLayout(resolver, resValue);
          if (path != null) {
            VirtualFile source = LocalFileSystem.getInstance().findFileByIoFile(path);
            if (source != null) {
              VirtualFile target = file.getVirtualFile();
              return create(module, source, target);
            }
          }
        }
      }
    }

    return NONE;
  }
}
