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
package com.intellij.codeInspection

import com.intellij.codeInspection.dataFlow.ContractInference
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class ContractInferenceFromSourceTest extends LightCodeInsightFixtureTestCase {

  void "test if null return null"() {
    def c = inferContract("""
  String smth(String s) {
    if (s == null) return null;
    return smth();
  }
""")
    assert c == 'null -> null'
  }

  void "test if not null return true"() {
    def c = inferContract("""
  boolean smth(int a, String s) {
    if (s != null) { return true; }
    return a == 2;
  }
""")
    assert c == '_, !null -> true'
  }

  void "test if null fail"() {
    def c = inferContract("""
  boolean smth(int a, String s) {
    if (null == s) { throw new RuntimeException(); }
    return a == 2;
  }
""")
    assert c == '_, null -> fail'
  }

  void "test if true return the same"() {
    def c = inferContract("""
  boolean smth(boolean b, int a) {
    if (b) return b;
    return a == 2;
  }
""")
    assert c == 'true, _ -> true'
  }

  void "test if false return negation"() {
    def c = inferContract("""
  boolean smth(boolean b, int a) {
    if (!b) return !(b);
    return a == 2;
  }
""")
    assert c == 'false, _ -> true'
  }

  void "test nested if"() {
    def c = inferContract("""
  boolean smth(boolean b, Object o) {
    if (!b) if (o != null) return true;
    return a == 2;
  }
""")
    assert c == 'false, !null -> true'
  }

  void "test conjunction"() {
    def c = inferContract("""
  boolean smth(boolean b, Object o) {
    if (!b && o != null) return true;
    return a == 2;
  }
""")
    assert c == 'false, !null -> true'
  }

  void "test disjunction"() {
    def c = inferContracts("""
  boolean smth(boolean b, Object o) {
    if (!b || o != null) return true;
    return a == 2;
  }
""")
    assert c == ['false, _ -> true', 'true, !null -> true']
  }

  void "test ternary"() {
    def c = inferContracts("""
  boolean smth(boolean b, Object o, Object o1) {
    return (!b || o != null) ? true : (o1 != null && o1.hashCode() == 3);
  }
""")
    assert c == ['false, _, _ -> true', 'true, !null, _ -> true', 'true, null, null -> false']
  }

  void "test instanceof"() {
    def c = inferContracts("""
  boolean smth(Object o) {
    return o instanceof String;
  }
""")
    assert c == ['null -> false']
  }

  void "test if-else"() {
    def c = inferContracts("""
  boolean smth(Object o) {
    if (o instanceof String) return false;
    else return true;
  }
""")
    assert c == ['null -> true']
  }

  void "test if return without else"() {
    def c = inferContracts("""
  boolean smth(Object o) {
    if (o instanceof String) return false;
    return true;
  }
""")
    assert c == ['null -> true']
  }

  void "test if no-return without else"() {
    def c = inferContracts("""
  boolean smth(Object o) {
    if (o instanceof String) callSomething();
    return true;
  }
""")
    assert c == []
  }

  void "test assertion"() {
    def c = inferContracts("""
  boolean smth(Object o) {
    assert o instanceof String;
    return true;
  }
""")
    assert c == ['null -> fail']
  }

  void "test no return value NotNull duplication"() {
    def c = inferContracts("""
  @org.jetbrains.annotations.NotNull String smth(Object o) {
    return "abc";
  }
""")
    assert c == []
  }

  public void "test plain delegation"() {
    def c = inferContracts("""
  boolean delegating(Object o) {
    return smth(o);
  }
  boolean smth(Object o) {
    assert o instanceof String;
    return true;
  }
""")
    assert c == ['null -> fail']
  }

  public void "test arg swapping delegation"() {
    def c = inferContracts("""
  boolean delegating(Object o, Object o1) {
    return smth(o1, o);
  }
  boolean smth(Object o, Object o1) {
    return o == null && o1 != null;
  }
""")
    assert c == ['_, !null -> false', 'null, null -> false', '!null, null -> true']
  }

  public void "test negating delegation"() {
    def c = inferContracts("""
  boolean delegating(Object o) {
    return !smth(o);
  }
  boolean smth(Object o) {
    return o == null;
  }
""")
    assert c == ['null -> false', '!null -> true']
  }

  public void "test delegation with constant"() {
    def c = inferContracts("""
  boolean delegating(Object o) {
    return smth(null);
  }
  boolean smth(Object o) {
    return o == null;
  }
""")
    assert c == ['_ -> true']
  }

  public void "test boolean autoboxing"() {
    def c = inferContracts("""
    static Object test1(Object o1) {
        return o1 == null;
    }""")
    assert c == []
  }

  public void "test boolean autoboxing in delegation"() {
    def c = inferContracts("""
    static Boolean test04(String s) {
        return test03(s);
    }
    static boolean test03(String s) {
        return s == null;
    }
    """)
    assert c == []
  }

  public void "test boolean auto-unboxing"() {
    def c = inferContracts("""
      static boolean test02(String s) {
          return test01(s);
      }

      static Boolean test01(String s) {
          if (s == null)
              return new Boolean(false);
          else
             return null;
      }
    """)
    assert c == []
  }

  public void "test non-returning delegation"() {
    def c = inferContracts("""
    static void test2(Object o) {
        assertNotNull(o);
    }

    static boolean assertNotNull(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return true;
    }
    """)
    assert c == ['null -> fail']
  }

  public void "test instanceof notnull"() {
    def c = inferContracts("""
    public boolean test2(Object o) {
        if (o != null) {
            return o instanceof String;
        } else {
            return test1(o);
        }
    }
    static boolean test1(Object o1) {
        return o1 == null;
    }
    """)
    assert c == []
  }

  public void "test no duplicates in delegation"() {
    def c = inferContracts("""
    static boolean test2(Object o1, Object o2) {
        return  test1(o1, o1);
    }
    static boolean test1(Object o1, Object o2) {
        return  o1 != null && o2 != null;
    }
    """)
    assert c == ['null, _ -> false', '!null, _ -> true']
  }

  public void "test take explicit parameter notnull into account"() {
    def c = inferContracts("""
    final Object foo(@org.jetbrains.annotations.NotNull Object bar) {
        if (!(bar instanceof CharSequence)) return null;
        return new String("abc");
    }
    """)
    assert c == []
  }

  public void "test skip empty declarations"() {
    def c = inferContracts("""
    final Object foo(Object bar) {
        Object o = 2;
        if (bar == null) return null;
        return new String("abc");
    }
    """)
    assert c == ['null -> null', '!null -> !null']
  }

  public void "test go inside do-while"() {
    def c = inferContracts("""
    final Object foo(Object bar) {
        do {
          if (bar == null) return null;
          bar = smth(bar);
        } while (smthElse());
        return new String("abc");
    }
    """)
    assert c == ['null -> null']
  }

  public void "test use invoked method notnull"() {
    def c = inferContracts("""
    final Object foo(Object bar) {
        if (bar == null) return null;
        return doo();
    }

    @org.jetbrains.annotations.NotNull Object doo() {}
    """)
    assert c == ['null -> null', '!null -> !null']
  }

  public void "test use delegated method notnull"() {
    def c = inferContracts("""
    final Object foo(Object bar) {
        return doo();
    }

    @org.jetbrains.annotations.NotNull Object doo() {}
    """)
    assert c == ['_ -> !null']
  }

  public void "test use delegated method notnull with contracts"() {
    def c = inferContracts("""
    final Object foo(Object bar, Object o2) {
        return doo(o2);
    }

    @org.jetbrains.annotations.NotNull Object doo(Object o) {
      if (o == null) throw new RuntimeException();
      return smth();
    }
    """)
    assert c == ['_, null -> fail', '_, _ -> !null']
  }

  private String inferContract(String method) {
    return assertOneElement(inferContracts(method))
  }

  private List<String> inferContracts(String method) {
    def clazz = myFixture.addClass("final class Foo { $method }")
    return ContractInference.inferContracts(clazz.methods[0]).collect { it as String }
  }
}
