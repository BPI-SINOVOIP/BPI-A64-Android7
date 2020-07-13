/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class ConvertIntegerToOctalPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression expression = (PsiLiteralExpression)element;
    final PsiType type = expression.getType();
    if (!(PsiType.INT.equals(type) || PsiType.LONG.equals(type))) {
      return false;
    }
    @NonNls final String text = expression.getText();
    if (text.charAt(0) != '0') {
      return true;
    }
    if (text.length() < 2) {
      return true;
    }
    final char c1 = text.charAt(1);
    if (c1 != '_' && (c1 < '0' || c1 > '7')) {
      return true;
    }
    return false;
  }
}