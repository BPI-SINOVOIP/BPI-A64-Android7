class Test {
  Object r() {
    try {
      return 2
    } catch (e) {

    }
  <warning descr="Not all execution paths return a value">}</warning>
}