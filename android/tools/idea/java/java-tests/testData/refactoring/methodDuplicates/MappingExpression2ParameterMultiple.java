class Mapping {
  private int myInt;

  public void <caret>method(int p1, boolean p2) {
    myInt = p2 ? p1 : 0;
    if (!p2) {
      myInt *= p1;
    }
  }

  public boolean sayYes() {
    return true;
  }

  public void context() {
    myInt = sayYes() ? 15 / 5 : 0;
    if (!sayYes()) {
      myInt *= 15 / 5;
    }
  }
}