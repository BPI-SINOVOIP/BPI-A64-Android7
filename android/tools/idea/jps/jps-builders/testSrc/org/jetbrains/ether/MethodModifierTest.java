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
package org.jetbrains.ether;

/**
 * @author: db
 * Date: 04.10.11
 */
public class MethodModifierTest extends IncrementalTestCase {
  public MethodModifierTest() throws Exception {
    super("methodModifiers");
  }

  public void testDecConstructorAccess() throws Exception {
    doTest();
  }

  public void testIncAccess() throws Exception {
    doTest();
  }

  public void testSetAbstract() throws Exception {
    doTest();
  }

  public void testSetFinal() throws Exception {
    doTest();
  }

  public void testSetPrivate() throws Exception {
    doTest();
  }

  public void testSetProtected() throws Exception {
    doTest();
  }


  public void testUnsetFinal() throws Exception {
    doTest();
  }

  public void testUnsetStatic() throws Exception {
    doTest();
  }

  public void testSetStatic() throws Exception {
    doTest();
  }
}
