import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class RoomRow {
    private final SimpleIntegerProperty roomNo, floor;
    private final SimpleStringProperty  type, availability;
    private final SimpleDoubleProperty  basePrice;

    public RoomRow(int n, String t, double p, boolean avail, int f) {
        roomNo       = new SimpleIntegerProperty(n);
        type         = new SimpleStringProperty(t);
        basePrice    = new SimpleDoubleProperty(p);
        availability = new SimpleStringProperty(avail ? "AVAILABLE" : "OCCUPIED");
        floor        = new SimpleIntegerProperty(f);
    }

    public int    getRoomNo()      { return roomNo.get(); }
    public String getType()        { return type.get(); }
    public double getBasePrice()   { return basePrice.get(); }
    public String getAvailability(){ return availability.get(); }
    public int    getFloor()       { return floor.get(); }
}
