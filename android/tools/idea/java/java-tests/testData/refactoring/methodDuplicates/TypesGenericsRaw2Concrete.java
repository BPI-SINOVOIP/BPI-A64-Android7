import java.util.List;

class Types {
  private List myList;

  public void <caret>method(List<String> v) {
    v.clear();
  }

  public void context() {
    myList.clear();
  }
}