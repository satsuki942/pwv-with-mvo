package sample;

// Positive Test:
// - 2 version of the Test class
// - Calling a method defined in multiple versions and a method defined in only one version
// - Dynamic dispatch based on current version attribute
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