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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.AssetRepository;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Finds an asset in all the asset directories and returns the input stream.
 */
public class AssetRepositoryImpl extends AssetRepository {

  private AndroidFacet myFacet;

  public AssetRepositoryImpl(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  /**
   * @param mode one of ACCESS_UNKNOWN, ACCESS_STREAMING, ACCESS_RANDOM or ACCESS_BUFFER (int values 0-3).
   */
  @Nullable
  @Override
  public InputStream openAsset(String path, int mode) throws IOException {
    // mode is currently ignored. It can help in optimizing read performance, but it shouldn't matter here.
    List<IdeaSourceProvider> sourceProviders = IdeaSourceProvider.getAllIdeaSourceProviders(myFacet);
    for (int i = sourceProviders.size() - 1; i >= 0; i--) {
      for (VirtualFile assetDir : sourceProviders.get(i).getAssetsDirectories()) {
        VirtualFile asset = assetDir.findFileByRelativePath(path);
        if (asset != null) {
          // TODO: ensure that this asset is within the asset directory. Files outside the asset directory shouldn't be accessible.
          return asset.getInputStream();
        }
      }
    }

    return null;
  }
}
