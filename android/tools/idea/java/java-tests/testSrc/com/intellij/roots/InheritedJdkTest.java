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
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import junit.framework.Assert;

/**
 * @author dsl
 */
public class InheritedJdkTest extends ModuleTestCase {
  @Override
  protected void setUpJdk() {
  }

  public void test1() throws Exception {
    final Sdk jdk = IdeaTestUtil.getMockJdk17("java 1.4");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk);
      }
    });
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ProjectRootManagerEx rootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);
        rootManagerEx.setProjectSdkName(jdk.getName());
        ModuleRootModificationUtil.setSdkInherited(myModule);
      }
    });

    assertTrue("JDK is inherited after explicit inheritSdk()", rootManager.isSdkInherited());
    assertEquals("Correct jdk inherited", jdk, rootManager.getSdk());

    ModuleRootModificationUtil.setModuleSdk(myModule, null);

    assertFalse("JDK is not inherited after setJdk(null)", rootManager.isSdkInherited());
    assertNull("No JDK assigned", rootManager.getSdk());

    final Sdk jdk1 = IdeaTestUtil.getMockJdk17("jjj");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk1);
      }
    });
    ModuleRootModificationUtil.setModuleSdk(myModule, jdk1);

    assertFalse("JDK is not inherited after setJdk(jdk1)", rootManager.isSdkInherited());
    assertEquals("jdk1 is assigned", jdk1, rootManager.getSdk());
  }

  public void test2() throws Exception {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    ModuleRootModificationUtil.setSdkInherited(myModule);

    assertTrue("JDK is inherited after inheritSdk()", rootManager.isSdkInherited());
    assertNull("No JDK assigned", rootManager.getSdk());

    final Sdk mockJdk = IdeaTestUtil.getMockJdk17("mock 1.4");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(mockJdk);
      }
    });
    final ProjectRootManagerEx projectRootManager = ProjectRootManagerEx.getInstanceEx(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        projectRootManager.setProjectSdk(mockJdk);
      }
    });

    assertTrue(rootManager.isSdkInherited());
    assertEquals("mockJdk inherited", mockJdk, rootManager.getSdk());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        projectRootManager.setProjectSdkName("jdk1");
      }
    });

    assertTrue(rootManager.isSdkInherited());
    Assert.assertEquals("Correct non-existing JDK inherited", "jdk1",
                        rootManager.orderEntries().process(new RootPolicy<String>() {
                          @Override
                          public String visitInheritedJdkOrderEntry(InheritedJdkOrderEntry inheritedJdkOrderEntry, String s) {
                            return inheritedJdkOrderEntry.getJdkName();
                          }
                        }, null));
    assertNull("Non-existing JDK", rootManager.getSdk());

    final Sdk jdk1 = IdeaTestUtil.getMockJdk17("jdk1");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk1);
      }
    });

    assertTrue(rootManager.isSdkInherited());
    assertNotNull("JDK appeared", rootManager.getSdk());
    assertEquals("jdk1 found", jdk1, rootManager.getSdk());
  }
}
