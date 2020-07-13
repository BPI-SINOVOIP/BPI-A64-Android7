import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract(<warning descr="A contract clause must be in form arg1, ..., argN -> return-value">"a"</warning>)
  void malformedContract() {}

  @Contract(<warning descr="Method takes 2 parameters, while contract clause number 1 expects 1">"null -> _"</warning>)
  void wrongParameterCount(Object a, boolean b) {}

  @Contract(pure=<warning descr="Pure methods must return something, void is not allowed as a return type">true</warning>)
  void voidPureMethod() {}

}
