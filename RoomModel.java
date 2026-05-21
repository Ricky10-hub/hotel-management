public class RoomModel {
    int number, floor;
    String type;
    double basePrice;
    boolean occupied;

    public RoomModel(int n, String t, double p, boolean o, int f) {
        number = n; type = t; basePrice = p; occupied = o; floor = f;
    }

    public double calculateBill(int days, boolean wifi, boolean breakfast, boolean parking, boolean laundry) {
        double total = basePrice * days;
        if (type.equalsIgnoreCase("DELUXE") || type.equalsIgnoreCase("SUITE")) total += 500;
        if (wifi)      total += 300;
        if (breakfast) total += (300.0 * days);
        if (parking)   total += (150.0 * days);
        if (laundry)   total += 200;
        return total;
    }
}
