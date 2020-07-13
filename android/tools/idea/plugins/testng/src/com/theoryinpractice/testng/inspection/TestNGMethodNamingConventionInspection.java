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
package com.theoryinpractice.testng.inspection;

import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.naming.ConventionInspection;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class TestNGMethodNamingConventionInspection extends ConventionInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "TestNG test method naming convention";
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    final int length = methodName.length();
    if (length < getMinLength()) {
      return "TestNG test method name <code>#ref</code> is too short (" + length + " < " + getMinLength() + ") #loc";
    }
    else if (length > getMaxLength()) {
      return "TestNG test method name <code>#ref</code> is too long (" + length + " > " + getMaxLength() + ") #loc";
    }
    return "JUnit4 test method name <code>#ref</code> doesn't match regex '{0}' #loc";
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z_\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return 4;
  }

  @Override
  protected int getDefaultMaxLength() {
    return 64;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestNGMethodNamingConventionVisitor();
  }

  private class TestNGMethodNamingConventionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!TestNGUtil.hasTest(method)) {
        return;
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final String name = method.getName();
      if (isValid(name)) {
        return;
      }
      if (!isOnTheFly() && MethodUtils.hasSuper(method)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method, name);
    }
  }
}
