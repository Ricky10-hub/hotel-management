import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class OccupiedRow {
    private final SimpleIntegerProperty roomNo;
    private final SimpleStringProperty  guestName, roomType;

    public OccupiedRow(int r, String n, String t) {
        roomNo    = new SimpleIntegerProperty(r);
        guestName = new SimpleStringProperty(n);
        roomType  = new SimpleStringProperty(t);
    }

    public int    getRoomNo()   { return roomNo.get(); }
    public String getGuestName(){ return guestName.get(); }
    public String getRoomType() { return roomType.get(); }
}
