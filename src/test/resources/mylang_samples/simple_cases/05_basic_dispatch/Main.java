package sample;

// Positive Test:
// - 2 version of the Test class
// - Calling methods which return a non-void type value
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        System.out.println(obj.idString("Hello, World!"));
        System.out.println(obj.idInt(42));
    }
}