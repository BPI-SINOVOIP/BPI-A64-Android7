class Sup {
  protected void f(int i){}
}
class Foo extends Sup {
  public void foo(int <warning descr="Parameter 'i' is never used">i</warning>){}
  public void g(int i){}
  protected void f(int i){}
}
class Sub extends Foo {
  public void g(int i){}
}


interface fff {
  void f(int ggggg);//
}
abstract class ab {
  public abstract void f(int ggggg);//
}