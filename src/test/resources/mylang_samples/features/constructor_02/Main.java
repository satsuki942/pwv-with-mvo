package sample;

public class Main {
    public static void main(String[] args) {
        Location loc1 = new Location(4, 5);
        Point point = new Point(3, 4);
        Location loc2 = new Location(point);

        loc1.printLocation();
        loc2.printLocation();
    }
}
