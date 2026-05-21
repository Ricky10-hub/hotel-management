import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class GuestHistoryRow {
    private final SimpleIntegerProperty roomNo, days;
    private final SimpleStringProperty  name, phone, wifi, breakfast, parking, status, idProof, roomType;
    private final SimpleDoubleProperty  bill;

    public GuestHistoryRow(int r, String n, String p, int d, double b,
                           boolean w, boolean bf, boolean park, String s, String id, String rt) {
        roomNo    = new SimpleIntegerProperty(r);
        name      = new SimpleStringProperty(n);
        phone     = new SimpleStringProperty(p);
        days      = new SimpleIntegerProperty(d);
        bill      = new SimpleDoubleProperty(b);
        wifi      = new SimpleStringProperty(w ? "YES" : "NO");
        breakfast = new SimpleStringProperty(bf ? "YES" : "NO");
        parking   = new SimpleStringProperty(park ? "YES" : "NO");
        status    = new SimpleStringProperty(s);
        idProof   = new SimpleStringProperty(id);
        roomType  = new SimpleStringProperty(rt != null ? rt : "STANDARD");
    }

    public int    getRoomNo()   { return roomNo.get(); }
    public String getName()     { return name.get(); }
    public String getPhone()    { return phone.get(); }
    public int    getDays()     { return days.get(); }
    public double getBill()     { return bill.get(); }
    public String getWifi()     { return wifi.get(); }
    public String getBreakfast(){ return breakfast.get(); }
    public String getParking()  { return parking.get(); }
    public String getStatus()   { return status.get(); }
    public String getIdProof()  { return idProof.get(); }
    public String getRoomType() { return roomType.get(); }
}
