package sample;

public class Location__1__ {
    private int lat;
    private int loc;

    public Location__1__(int lat, int loc) {
        this.lat = lat;
        this.loc = loc;
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
