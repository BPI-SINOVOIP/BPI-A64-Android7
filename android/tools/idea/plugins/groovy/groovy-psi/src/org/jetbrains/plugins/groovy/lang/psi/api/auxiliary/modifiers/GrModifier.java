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
package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

import com.intellij.psi.PsiModifier;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Medvedev
 */
public interface GrModifier extends PsiModifier {
  @NonNls String DEF = "def";

  @GrModifierConstant
  String[] GROOVY_MODIFIERS =
    {PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEF};

  @MagicConstant(stringValues = {DEF, PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, PACKAGE_LOCAL})
  @interface GrModifierConstant {}

}
