class Test {
    final Extracted extracted = new Extracted();

    {
        extracted.setMyT(0);
  }
  
  void bar(){
    int i = extracted.setMyT(extracted.getMyT() + 1);
  }
}