public class Try {
    public int test() {
        int i = another();
        return i;
    }
 
    public int another<caret>() {
        try {
            return Integer.parseInt("1");
        }
        catch (NumberFormatException ex) {
            throw ex;
        }
    }
}
