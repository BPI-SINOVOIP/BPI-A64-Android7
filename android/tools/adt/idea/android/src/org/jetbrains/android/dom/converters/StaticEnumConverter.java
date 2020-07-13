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
package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author coyote
 */
public class StaticEnumConverter extends ResolvingConverter<String> {
  private final Set<String> myValues = new HashSet<String>();

  public StaticEnumConverter(String... values) {
    Collections.addAll(myValues, values);
  }

  @Override
  @NotNull
  public Collection<? extends String> getVariants(ConvertContext context) {
    return Collections.unmodifiableCollection(myValues);
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return myValues.contains(s) ? s : null;
  }

  @Override
  public String toString() {
    return "StaticEnumConverter " + myValues.toString();
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
