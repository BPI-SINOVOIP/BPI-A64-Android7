public class Bar {
    public int baz(byte blah) {
        return <selection>blah + 3</selection>;
    }
}
class S extends Bar {
    public int baz(byte blah) {
        return super.baz((byte) 0);    //To change body of overridden methods use File | Settings | File Templates.
    }
}