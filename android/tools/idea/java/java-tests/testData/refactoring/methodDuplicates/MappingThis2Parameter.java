class Mapping {
  private int myInt;
  private Mapping myField;

  public void <caret>method(Mapping m) {
    myInt = m.hashCode();
  }

  public void context1() {
    myInt = hashCode();
  }

  public void context2() {
    myInt = this.hashCode();
  }
}
