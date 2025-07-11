package sample;

public class Location__2__ {
    private int lat;
    private int loc;

    public Location__2__(Point p) {
        this.lat = p.getX();
        this.loc = p.getY();
    }

    public int getLat() {
        return lat;
    }

    public int getLoc() {
        return loc;
    }

    public void printLocation() {
        System.out.println("Location: (" + lat + ", " + loc + ")");
    }
}
