class BarImpl extends foo.Bar {
    {
        new Runnable() {
            public void run() {
                BarImpl.super.fo<caret>
            }
        };
    }
}
