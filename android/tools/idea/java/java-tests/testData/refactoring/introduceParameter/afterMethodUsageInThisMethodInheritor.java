class C {
  int foo(int x, int anObject) {
     return anObject;
  }
}

class D extends C {
  int foo(int x, int anObject) {
    return super.foo(x, anObject);
  }
}