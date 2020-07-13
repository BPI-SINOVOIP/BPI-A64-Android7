import org.jetbrains.annotations.*;

class Test {
  String foo1() {
    return null;
  }

  String foo2() {
    return "";
  }

  String foo3(String s) {
    return s;
  }

  String foo4(String s) {
    return s.substring(0);
  }

  Integer foo5(Integer i) {
    return i++;
  }

  Integer foo6(Integer i) {
    if (i == 0) return 1;
    return i * foo6(i--);
  }

  Integer foo7(boolean flag) {
    return flag ? null : 1;
  }

  Integer foo8(boolean flag) {
    if (flag) {
      return null;
    }
    else {
      return 1;
    }
  }

  @Nullable
  String bar9() {
    return foo3("");
  }

  String foo9() {
    return bar9();
  }


  @Nullable
  String bar10() {
    return foo3("");
  }

  @NotNull
  String bar101() {
    return foo3("");
  }

  String foo10(boolean flag) {
    return flag ? bar10() : bar101();
  }

  String foo11() {
    class Foo{
      String mess() {
        return null;
      }
    }
    return "";
  }
}