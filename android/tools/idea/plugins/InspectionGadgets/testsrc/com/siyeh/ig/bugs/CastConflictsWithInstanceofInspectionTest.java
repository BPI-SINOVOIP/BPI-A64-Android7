/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class CastConflictsWithInstanceofInspectionTest extends IGInspectionTestCase {

  public void testElseElse() throws Exception {
    doTest();
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testElseElseOrOr() throws Exception {
    doTest();
  }

  public void testAndAnd() throws Exception {
    doTest();
  }

  public void testPolyadic() throws Exception {
    doTest();
  }
  
  public void testNotOr() throws Exception {
    doTest();
  }

  public void testOrInstanceofOrInstanceof() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest("com/siyeh/igtest/bugs/castConflictingInstanceof/" + getTestName(true), new CastConflictsWithInstanceofInspection());
  }
}
