public class Foo {

    {
        Foo foo = null;
        Foo bar = id(foo, <caret>Foo.class.toString() + "[]"));
    }

    Foo id(Foo foo, String a) {return foo;}

}
