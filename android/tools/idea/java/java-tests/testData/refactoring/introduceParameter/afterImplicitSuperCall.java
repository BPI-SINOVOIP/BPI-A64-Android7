class A {
    int i;
    public A(int anObject) {
        i = anObject;
    }
}

class B extends A {
    int k;

    public B() {
        super(27);
        k = 10;
    }
}

class Usage {
    A a = new B();
}