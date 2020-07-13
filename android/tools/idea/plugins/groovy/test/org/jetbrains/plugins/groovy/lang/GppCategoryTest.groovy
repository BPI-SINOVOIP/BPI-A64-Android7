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

import com.intellij.psi.PsiReference
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod

/**
 * @author peter
 */
class GppCategoryTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GppProjectDescriptor.instance
  }

  public void testFromSuperClassClosureSyntax() {
    assertResolves """
abstract class Super implements Runnable {
  static def category(Object foo, int bar) {}
}

Super s = { "".cate<caret>gory(2) }
"""
  }

  public void testFromSuperClassMapSyntax() {
    assertResolves """
class Super {
  static def category(Object foo, int bar) {}
  void xxx() {}
}

Super s = [xxx:{ "".cate<caret>gory(2) }]
"""
  }

  private def assertResolves(String text) {
    myFixture.configureByText "a.gpp", text
    assert findReference().resolve() instanceof GrGdkMethod
  }

  private PsiReference findReference() {
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

}
