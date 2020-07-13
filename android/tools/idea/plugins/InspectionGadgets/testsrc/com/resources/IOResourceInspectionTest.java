/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.resources;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;

/**
 * @author Alexey
 */
public class IOResourceInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return IdeaTestUtil.getMockJdk17();
  }

  public void test() throws Exception {
    doTest("com/siyeh/igtest/resources/io/plain", new IOResourceInspection());
  }

  public void testInsideTry() throws Exception {
    final IOResourceInspection inspection = new IOResourceInspection();
    inspection.insideTryAllowed = true;
    doTest("com/siyeh/igtest/resources/io/inside_try", inspection);
  }
}
