import java.util.ArrayList;

public class TestCompletion {
    public static void test() {
        A<ArrayList<String>> ref = new A<>();
        ref.set(new <caret> );
    }
}

class A<V> {
    A() {
    }

    void set(V v){}
}