// "Suppress for statement" "true"
class a {
public void run() {
//noinspection LocalCanBeFinal
    int <caret>i = 0;
System.out.println(i);
}
}