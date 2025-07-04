package sample;


// Positive Test: : Static Version Dispatching
// Expected Result:
/*
 * Hello from version 1 of Test class.
 * Hello from version 1 of Test class.
 */
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.display();
        obj.display();
    }
}
