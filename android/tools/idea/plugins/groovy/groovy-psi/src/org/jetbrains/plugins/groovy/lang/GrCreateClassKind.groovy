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
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.daemon.impl.quickfix.ClassKind

/**
 * Created by Max Medvedev on 28/05/14
 */
enum GrCreateClassKind implements ClassKind {
  CLASS     ("class"),
  INTERFACE ("interface"),
  TRAIT     ("trait"),
  ENUM      ("enum"),
  ANNOTATION("annotation");

  private final String myDescription;

  public GrCreateClassKind(final String description) {
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }
}
