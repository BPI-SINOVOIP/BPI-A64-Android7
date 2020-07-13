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
package com.android.tools.idea.templates;

import com.android.utils.SdkUtils;
import freemarker.cache.TemplateLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;

/**
 * A custom {@link TemplateLoader} which locates templates on disk relative to a specified prefix.
 */
public final class PrefixTemplateLoader implements TemplateLoader {
  private String myPrefix;

  public PrefixTemplateLoader(@Nullable String prefix) {
    myPrefix = prefix;
  }

  public void setTemplateFile(@NotNull File file) {
    setTemplateParent(file.getParentFile());
  }

  public void setTemplateParent(@NotNull File parent) {
    myPrefix = parent.getPath();
  }

  @Override
  @NotNull
  public Reader getReader(@NotNull Object templateSource, @NotNull String encoding) throws IOException {
    InputStream stream = (InputStream)templateSource;
    return new InputStreamReader(stream, encoding);
  }

  @Override
  public long getLastModified(Object templateSource) {
    return 0;
  }

  @Override
  @Nullable
  public Object findTemplateSource(@NotNull String name) throws IOException {
    String path = myPrefix != null ? myPrefix + '/' + name : name;
    File file = new File(path);
    if (file.exists()) {
      return SdkUtils.fileToUrl(file).openStream();
    }
    return null;
  }

  @Override
  public void closeTemplateSource(Object templateSource) throws IOException {
    ((InputStream)templateSource).close();
  }
}
