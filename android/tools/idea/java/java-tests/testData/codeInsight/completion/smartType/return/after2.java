class A{
  class B{
    int fooo(){
    }
  }

  int fooo(){
    A b = null;

    return b.fooo();<caret>
  }
}