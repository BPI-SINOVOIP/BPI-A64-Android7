class Foo {
  void foo(int i) {
    switch (i) {
      case Constants.BAR0: return;
      case Constants.BAR1:<caret>
    }
  }
}

interface Constants {
  int BAR0 = 0;
  int BAR1 = 1;
}