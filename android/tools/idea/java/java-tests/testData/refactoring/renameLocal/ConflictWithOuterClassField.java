public class HelloWorld {
    private String f;

    public class A {

        public void test() {
            String <caret>s = f;
        }
    }
}