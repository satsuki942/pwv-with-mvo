package sample;

// Positive Test:
// - 2 version of the Test class
// - Calling a method that is defined in multiple versions
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.display();
        obj.display();
    }
}
