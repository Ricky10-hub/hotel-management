public class CustomerModel {
    String name, contact, status, idProof, roomType;
    int roomNo, days;
    double bill;
    boolean wifi, breakfast, parking, laundry, paid;

    public CustomerModel(String n, String c, int r, int d, double b,
                  boolean w, boolean bf, boolean park, boolean lau, boolean p, String id, String rt) {
        name = n; contact = c; roomNo = r; days = d;
        bill = b; wifi = w; breakfast = bf; parking = park; laundry = lau; paid = p;
        idProof = id;
        roomType = (rt != null && !rt.isEmpty()) ? rt : "STANDARD";
        status = p ? "CHECKED OUT" : "CHECKED IN";
    }
}
