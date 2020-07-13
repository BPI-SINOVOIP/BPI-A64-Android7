/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.devkit.util.PsiUtil;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryImplicitPropertyUsageProvider extends ImplicitPropertyUsageProvider {
  @Override
  protected boolean isUsed(Property property) {
    if (PsiUtil.isIdeaProject(property.getProject())) {
      final PsiFile file = property.getContainingFile();
      if (file != null && file.getName().equals("registry.properties")) {
        final String name = property.getName();
        return name.endsWith(".description") || name.endsWith(".restartRequired");
      }
    }
    return false;
  }
}
