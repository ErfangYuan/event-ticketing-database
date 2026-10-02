package mytix.database.query;

/** Geographic predicate combined with Q4's time and availability bounds in SQL. */
public record GeographicFilter(
        String mode, Double latitude, Double longitude, Double radiusKm,
        String rankBy, String postalCode, String address) {
    public static GeographicFilter all() {
        return new GeographicFilter("ALL", null, null, null, null, null, null);
    }
    public static GeographicFilter vicinity(double latitude, double longitude, double radiusKm, String rankBy) {
        return new GeographicFilter("VICINITY", latitude, longitude, radiusKm, rankBy, null, null);
    }
    public static GeographicFilter postal(String postalCode) {
        return new GeographicFilter("POSTAL", null, null, null, null, postalCode, null);
    }
    public static GeographicFilter address(String address) {
        return new GeographicFilter("ADDRESS", null, null, null, null, null, address);
    }
}
