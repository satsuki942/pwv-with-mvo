package sample;


// Positive Test: : Static Version Dispatching
// Expected Result:
/*
 * This method is defined only in version 2 of Test class.
 */
public class Main {
    public static void main(String[] args) {
        Test obj = new Test();

        obj.new_method();
    }
}
