// not allowed modifiers
<error descr="Modifier 'private' not allowed here">private</error> 
<error descr="Modifier 'static' not allowed here">static</error>
<error descr="Modifier 'volatile' not allowed here">volatile</error>
class a {

  static 
  <error descr="Modifier 'private' not allowed here">private</error>
  <error descr="Modifier 'public' not allowed here">public</error>
  <error descr="Modifier 'abstract' not allowed here">abstract</error> {
    int i = 4;
  }
  <error descr="Modifier 'synchronized' not allowed here">synchronized</error> Object x;


  private class c1 { 
    private void ff() {}
  }
  static strictfp class c2 {}

  private static interface ii {
    <error descr="Modifier 'private' not allowed here">private</error> int f1 = 2;
    <error descr="Modifier 'protected' not allowed here">protected</error> int f2 = 2;
    public int f3 = 3;


    <error descr="Modifier 'private' not allowed here">private</error> int f1();
    <error descr="Modifier 'protected' not allowed here">protected</error> int f2();
    public int f3();
    void f4();

  }

  void f1(final String i) {
    final int ii = 3;
    <error descr="Modifier 'private' not allowed here">private</error> int i2;
    <error descr="Modifier 'static' not allowed here">static</error> int i3;

    try {
     throw new Exception();
    } catch (final <error descr="Modifier 'static' not allowed here">static</error> Exception e) {
    }
  }

}

interface ff {
  static class cc {}
}

abstract class c {
  <error descr="Modifier 'abstract' not allowed here">abstract</error> c();
  <error descr="Modifier 'static' not allowed here">static</error> c(int i) {}
  <error descr="Modifier 'native' not allowed here">native</error> c(boolean b);
  <error descr="Modifier 'final' not allowed here">final</error> c(char c) {}
  <error descr="Modifier 'strictfp' not allowed here">strictfp</error> c(String s) {}
  <error descr="Modifier 'synchronized' not allowed here">synchronized</error> c(Object o) {}
}

interface i3 {
  <error descr="Modifier 'strictfp' not allowed here">strictfp</error> int f1;
  <error descr="Modifier 'transient' not allowed here">transient</error> int f2;
  <error descr="Modifier 'synchronized' not allowed here">synchronized</error>  int f3;

  <error descr="Modifier 'strictfp' not allowed here">strictfp</error> int m1() { return 0; }
  <error descr="Modifier 'transient' not allowed here">transient</error> int m2() { return 0; }
  <error descr="Modifier 'synchronized' not allowed here">synchronized</error>  int m3() { return 0; }
}

class LocalClassWithInner {
    void foo () {
          class A {
             <error descr="Modifier 'private' not allowed here">private</error> class B {}
             <error descr="Modifier 'public' not allowed here">public</error> class B1 {}
        }
    }
}
