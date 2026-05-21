public enum RoomType {
    STANDARD(2000), DELUXE(5000), SUITE(8000);
    final double price;
    RoomType(double p) { price = p; }
    public double getPrice() { return price; }
}
