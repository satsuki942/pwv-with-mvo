package sample;

// Positive Test: 
// - 2 version of the Test class
// - Calling methods that are defined in only one version
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.display();
        obj.display("World");
    }
}
