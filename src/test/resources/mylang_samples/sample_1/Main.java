package sample;

// Positive Test: Static Version Dispatching
// Expected Result:
/*
 * This is version 1 of Test class.
 * This is VERSION 2 of Test class, World
 */
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.display();
        obj.display("World");
    }
}
