package sample;


// Positive Test: Dynamic Version Dispatching
// Expected Result:
/*
 * V1 display: Called.
 * V1 log: 123
 * V1 display: Called.
 * V2 log: hello
 * V2 display: Called.
 */
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.display();
        obj.log(123);
        obj.display();
        obj.log("hello");
        obj.display();
    }
}