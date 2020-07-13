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
package org.jetbrains.idea.devkit.util;

import com.intellij.psi.SmartPsiElementPointer;

/**
 * @author yole
 */
public class ExtensionPointCandidate {
  public final SmartPsiElementPointer pointer;
  public final String epName;
  public final String attributeName;
  public final String tagName;
  public final String beanClassName;

  public ExtensionPointCandidate(SmartPsiElementPointer pointer,
                                 String epName,
                                 String attributeName,
                                 String tagName,
                                 String beanClassName) {
    this.pointer = pointer;
    this.epName = epName;
    this.attributeName = attributeName;
    this.tagName = tagName;
    this.beanClassName = beanClassName;
  }

  public ExtensionPointCandidate(SmartPsiElementPointer pointer,
                                 String epName) {
    this.pointer = pointer;
    this.epName = epName;
    this.attributeName = "implementation";
    this.tagName = null;
    this.beanClassName = null;
  }

  @Override
  public String toString() {
    return epName;
  }
}
