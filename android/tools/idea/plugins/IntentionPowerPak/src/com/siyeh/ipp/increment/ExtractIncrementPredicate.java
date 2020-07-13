/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ExtractIncrementPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiPrefixExpression) &&
        !(element instanceof PsiPostfixExpression)) {
      return false;
    }
    final IElementType tokenType;
    if (element instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)element;
      final PsiExpression operand = postfixExpression.getOperand();
      if (!(operand instanceof PsiReferenceExpression)) {
        return false;
      }
      tokenType = postfixExpression.getOperationTokenType();
    }
    else {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)element;
      final PsiExpression operand = prefixExpression.getOperand();
      if (!(operand instanceof PsiReferenceExpression)) {
        return false;
      }
      tokenType = prefixExpression.getOperationTokenType();
    }
    if (!JavaTokenType.PLUSPLUS.equals(tokenType) &&
        !JavaTokenType.MINUSMINUS.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return false;
    }
    final PsiStatement containingStatement =
      PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    return containingStatement != null;
  }
}