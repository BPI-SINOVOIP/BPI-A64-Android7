public class Test {
    public int anObject;
    public void method() {
    }
}

public class Test1 extends Test {
    public void method() {
        System.out.println(<selection>1 + 2</selection>);
        System.out.println(anObject);
    }
}

public class Test2 extends Test1 {
    public void method() {
        System.out.println(anObject);
    }
}

public class Usage {
    {
        Test t = new Test2();
        t.method();
    }
}