public class Test9 {
    protected int count;
}

class Test10 extends Test9 {
    private class Test10i {
        void test() {
            final int count = Test10.this.count;
            System.out.println(count);
        }
    }
}
