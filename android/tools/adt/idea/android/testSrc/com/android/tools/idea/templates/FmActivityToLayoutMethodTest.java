/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.templates;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;

import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class FmActivityToLayoutMethodTest extends TestCase {
  @SuppressWarnings("rawtypes")
  private static void check(String s, String expected) throws TemplateModelException {
    FmActivityToLayoutMethod method = new FmActivityToLayoutMethod();
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  public void test1() throws Exception {
    check("FooActivity", "activity_foo");
  }

  public void test2() throws Exception {
    check("FooActiv", "activity_foo");
  }

  public void test3() throws Exception {
    check("Foo", "activity_foo");
  }

  public void test4() throws Exception {
    check("", "");
  }

  public void test5() throws Exception {
    check("a", "activity_a");
  }

  public void test6() throws Exception {
    check("A", "activity_a");
  }

  public void test7() throws Exception {
    check("FooActivity2", "activity_foo2");
  }

  public void test8() throws Exception {
    check("FooActivity200", "activity_foo200");
  }

  public void test9() throws Exception {
    check("Activity200", "activity_200");
  }
}
