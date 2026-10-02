package mytix.database.query;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mytix.database.DatabaseLogistics;

public final class JdbcQueryService implements QueryService {

    private static final String UPCOMING =
            " p.status = 'SCHEDULED' AND TIMESTAMP(p.date, p.startTime) > CURRENT_TIMESTAMP ";

    private static final String POSTAL_PREDICATE = """
            EXISTS (
              SELECT 1 FROM postal_areas requested
              WHERE requested.normalizedCode = ? AND (
                pa.postalCode = requested.postalCode OR EXISTS (
                  SELECT 1 FROM postal_adjacencies adjacency
                  WHERE (adjacency.postalCode = requested.postalCode AND adjacency.adjacentPostalCode = pa.postalCode)
                     OR (adjacency.adjacentPostalCode = requested.postalCode AND adjacency.postalCode = pa.postalCode)
                )
              )
            )
            """;

    
    private static final String SECTION_AVAIL_CTE =
            """
            section_avail AS (
              SELECT pst.performanceID, pst.sectionID, pst.tierID, sec.isGeneralAdmission,
                CASE WHEN sec.isGeneralAdmission = 1
                     THEN sec.standingCapacity - COALESCE(sold.soldCnt, 0)
                     ELSE (SELECT COUNT(*) FROM seats se WHERE se.sectionID = sec.sectionID)
                          - COALESCE(sold.soldCnt, 0) - COALESCE(bl.blockedCnt, 0)
                END AS available
              FROM performance_section_tiers pst
              JOIN sections sec ON sec.sectionID = pst.sectionID
              LEFT JOIN (
                SELECT performanceID, sectionID, COUNT(*) AS soldCnt
                FROM tickets WHERE status = 'ACTIVE'
                GROUP BY performanceID, sectionID
              ) sold ON sold.performanceID = pst.performanceID AND sold.sectionID = pst.sectionID
              LEFT JOIN (
                SELECT b.performanceID, se.sectionID, COUNT(*) AS blockedCnt
                FROM blocked_seats b JOIN seats se ON se.seatID = b.seatID
                GROUP BY b.performanceID, se.sectionID
              ) bl ON bl.performanceID = pst.performanceID AND bl.sectionID = pst.sectionID
            )
            """;

    
    private static final String PERF_AVAIL_CTE =
            """
            perf_avail AS (
              SELECT performanceID, SUM(available) AS available
              FROM section_avail
              GROUP BY performanceID
            )
            """;

    
    private static final String PERF_CHEAPEST_CTE =
            """
            perf_cheapest AS (
              SELECT sa.performanceID, MIN(pt.price) AS cheapestPrice
              FROM section_avail sa
              JOIN price_tiers pt ON pt.tierID = sa.tierID
              WHERE sa.available > 0
              GROUP BY sa.performanceID
            )
            """;

    

    @Override
    public List<List<String>> q1Vicinity(double lat, double lng, double radiusKm, String rankBy) {
        validateCoordinates(lat, lng, radiusKm);
        String sql =
                "WITH " + SECTION_AVAIL_CTE + ", " + PERF_CHEAPEST_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, v.venueName, pa.city,
                               (2 * 6371.0088 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                                  POWER(SIN(RADIANS(v.latitude - ?) / 2), 2)
                                  + COS(RADIANS(?)) * COS(RADIANS(v.latitude))
                                    * POWER(SIN(RADIANS(v.longitude - ?) / 2), 2)
                                ))))) AS distanceKm,
                               pc.cheapestPrice
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        LEFT JOIN perf_cheapest pc ON pc.performanceID = p.performanceID
                        WHERE """
                        + UPCOMING
                        + """
                        HAVING distanceKm <= ?
                        ORDER BY
                        """
                        + orderByFor(rankBy);

        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lat);
            ps.setDouble(3, lng);
            ps.setDouble(4, radiusKm);
            return collect(
                    ps,
                    rs -> List.of(
                            String.valueOf(rs.getInt("performanceID")),
                            rs.getString("title"),
                            rs.getString("venueName"),
                            rs.getString("city"),
                            String.format("%.2f", rs.getDouble("distanceKm")),
                            fmtMoneyOrNa(rs, "cheapestPrice")));
        } catch (SQLException e) {
            throw new IllegalStateException("q1Vicinity failed: " + e.getMessage(), e);
        }
    }

    private static String orderByFor(String rankBy) {
        String key = rankBy == null ? "" : rankBy.trim().toLowerCase();
        return switch (key) {
            case "price_asc" -> "pc.cheapestPrice IS NULL, pc.cheapestPrice ASC, distanceKm, p.performanceID";
            case "price_desc" -> "pc.cheapestPrice IS NULL, pc.cheapestPrice DESC, distanceKm, p.performanceID";
            default -> "distanceKm ASC, p.performanceID";
        };
    }

    

    @Override
    public List<List<String>> q2PostalAdjacent(String postalCode) {
        requireText(postalCode, "Postal code");
        String sql =
                """
                SELECT p.performanceID, e.title, v.venueName, pa.city, pa.postalCode
                FROM performances p
                JOIN events e ON e.eventID = p.eventID
                JOIN venues v ON v.venueID = p.venueID
                JOIN postal_areas pa ON pa.postalCode = v.postalCode
                WHERE """
                        + UPCOMING
                        + " AND " + POSTAL_PREDICATE
                        + " ORDER BY p.date, p.startTime, p.performanceID";
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizePostal(postalCode));
            return collect(
                    ps,
                    rs -> List.of(
                            String.valueOf(rs.getInt("performanceID")),
                            rs.getString("title"),
                            rs.getString("venueName"),
                            rs.getString("city"),
                            rs.getString("postalCode")));
        } catch (SQLException e) {
            throw new IllegalStateException("q2PostalAdjacent failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q3ExactAddress(String address) {
        requireText(address, "Address");
        String sql =
                """
                SELECT v.venueID, v.venueName, v.address, p.performanceID, e.title, p.date, p.startTime
                FROM venues v
                LEFT JOIN performances p ON p.venueID = v.venueID AND """
                        + UPCOMING
                        + """
                        LEFT JOIN events e ON e.eventID = p.eventID
                        WHERE LOWER(TRIM(v.address)) = LOWER(TRIM(?))
                        ORDER BY p.date, p.startTime
                        """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, address);
            return collect(
                    ps,
                    rs -> List.of(
                            String.valueOf(rs.getInt("venueID")),
                            rs.getString("venueName"),
                            rs.getString("address"),
                            rs.getString("performanceID") == null ? "" : rs.getString("performanceID"),
                            rs.getString("title") == null ? "" : rs.getString("title"),
                            rs.getString("date") == null ? "" : rs.getString("date"),
                            rs.getString("startTime") == null ? "" : rs.getString("startTime")));
        } catch (SQLException e) {
            throw new IllegalStateException("q3ExactAddress failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q4TemporalAvailability(LocalDate from, LocalDate to, int minAvailable) {
        return q4TemporalAvailability(from, to, minAvailable, GeographicFilter.all());
    }

    @Override
    public List<List<String>> q4TemporalAvailability(
            LocalDate from, LocalDate to, int minAvailable, GeographicFilter geography) {
        validateDates(from, to, true);
        requireNonnegative(minAvailable, "Minimum available tickets");
        if (geography == null || geography.mode() == null) {
            throw new IllegalStateException("Geographic search mode is required.");
        }
        String mode = geography.mode().toUpperCase(Locale.ROOT);
        List<Object> bind = new ArrayList<>();
        String distance = "NULL";
        if (mode.equals("VICINITY")) {
            if (geography.latitude() == null || geography.longitude() == null || geography.radiusKm() == null) {
                throw new IllegalStateException("Vicinity search requires coordinates and distance.");
            }
            validateCoordinates(geography.latitude(), geography.longitude(), geography.radiusKm());
            distance = "(2 * 6371.0088 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0, "
                    + "POWER(SIN(RADIANS(v.latitude - ?) / 2), 2) "
                    + "+ COS(RADIANS(?)) * COS(RADIANS(v.latitude)) "
                    + "* POWER(SIN(RADIANS(v.longitude - ?) / 2), 2))))))";
            bind.add(geography.latitude());
            bind.add(geography.latitude());
            bind.add(geography.longitude());
        } else if (!List.of("ALL", "POSTAL", "ADDRESS").contains(mode)) {
            throw new IllegalStateException("Unknown geographic search mode.");
        }
        StringBuilder sql = new StringBuilder(
                "WITH " + SECTION_AVAIL_CTE + ", " + PERF_AVAIL_CTE + ", " + PERF_CHEAPEST_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, v.venueName, pa.city, p.date, p.startTime,
                               COALESCE(pa2.available, 0) AS available,
                        """ + distance + " AS distanceKm, pc.cheapestPrice " + """
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        LEFT JOIN perf_avail pa2 ON pa2.performanceID = p.performanceID
                        LEFT JOIN perf_cheapest pc ON pc.performanceID = p.performanceID
                        WHERE """ + UPCOMING + """
                          AND p.date BETWEEN ? AND ?
                        """);
        bind.add(from);
        bind.add(to);
        if (mode.equals("POSTAL")) {
            requireText(geography.postalCode(), "Postal code");
            sql.append(" AND ").append(POSTAL_PREDICATE);
            bind.add(normalizePostal(geography.postalCode()));
        } else if (mode.equals("ADDRESS")) {
            requireText(geography.address(), "Address");
            sql.append(" AND LOWER(TRIM(v.address)) = LOWER(TRIM(?)) ");
            bind.add(geography.address());
        }
        sql.append(" HAVING available >= ? ");
        bind.add(minAvailable);
        if (mode.equals("VICINITY")) {
            sql.append("AND distanceKm <= ? ORDER BY ").append(orderByFor(geography.rankBy()));
            bind.add(geography.radiusKm());
        } else {
            sql.append("ORDER BY p.date, p.startTime, p.performanceID");
        }
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < bind.size(); i++) {
                ps.setObject(i + 1, bind.get(i));
            }
            return collect(
                    ps,
                    rs -> List.of(
                            String.valueOf(rs.getInt("performanceID")),
                            rs.getString("title"),
                            rs.getString("venueName"),
                            rs.getString("city"),
                            rs.getString("date"),
                            rs.getString("startTime"),
                            String.valueOf(rs.getInt("available"))));
        } catch (SQLException e) {
            throw new IllegalStateException("q4TemporalAvailability failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q5Combined(Q5Filter filter) {
        if (filter == null) {
            throw new IllegalStateException("Search filters are required.");
        }
        validateDates(filter.dateFrom(), filter.dateTo(), false);
        validateMoney(filter.minPrice(), "Minimum price");
        validateMoney(filter.maxPrice(), "Maximum price");
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new IllegalStateException("Minimum price must not exceed maximum price.");
        }
        if (filter.minAvailable() != null) {
            requireNonnegative(filter.minAvailable(), "Minimum available tickets");
        }
        if (filter.sectionType() != null && !filter.sectionType().isBlank()
                && !filter.reservedOnly() && !filter.gaOnly()) {
            throw new IllegalStateException("Section type must be RESERVED or GA.");
        }
        String sectionPredicate = filter.reservedOnly() ? "sa.isGeneralAdmission = 0"
                : filter.gaOnly() ? "sa.isGeneralAdmission = 1" : "1 = 1";
        String matchingInventory = """
                matching_inventory AS (
                  SELECT sa.performanceID, SUM(sa.available) AS available,
                         MIN(CASE WHEN sa.available > 0 THEN pt.price END) AS cheapestPrice
                  FROM section_avail sa
                  JOIN price_tiers pt ON pt.tierID = sa.tierID AND pt.performanceID = sa.performanceID
                  WHERE %s
                  GROUP BY sa.performanceID
                )
                """.formatted(sectionPredicate);
        StringBuilder sql = new StringBuilder(
                "WITH " + SECTION_AVAIL_CTE + ", " + matchingInventory + " "
                        + """
                        SELECT p.performanceID, e.title, g.genreName, sg.segmentName, v.venueName, pa.city,
                               p.date, COALESCE(mi.available, 0) AS available, mi.cheapestPrice
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN genres g ON g.genreID = e.genreID
                        JOIN segments sg ON sg.segmentID = g.segmentID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        LEFT JOIN matching_inventory mi ON mi.performanceID = p.performanceID
                        """);
        List<Object> params = new ArrayList<>();
        sql.append("WHERE ").append(UPCOMING).append(' ');

        if (filter.city() != null && !filter.city().isBlank()) {
            sql.append("AND pa.city = ? ");
            params.add(filter.city());
        }
        if (filter.segmentName() != null && !filter.segmentName().isBlank()) {
            sql.append("AND sg.segmentName = ? ");
            params.add(filter.segmentName());
        }
        if (filter.genreName() != null && !filter.genreName().isBlank()) {
            sql.append("AND g.genreName = ? ");
            params.add(filter.genreName());
        }
        if (filter.dateFrom() != null) {
            sql.append("AND p.date >= ? ");
            params.add(filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            sql.append("AND p.date <= ? ");
            params.add(filter.dateTo());
        }
        if (filter.reservedOnly() || filter.gaOnly()) {
            sql.append("AND mi.performanceID IS NOT NULL ");
        }

        List<String> having = new ArrayList<>();
        if (filter.minAvailable() != null) {
            having.add("available >= ?");
            params.add(filter.minAvailable());
        }
        if (filter.minPrice() != null) {
            having.add("mi.cheapestPrice >= ?");
            params.add(filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            having.add("mi.cheapestPrice <= ?");
            params.add(filter.maxPrice());
        }
        if (!having.isEmpty()) {
            sql.append("HAVING ").append(String.join(" AND ", having)).append(' ');
        }
        sql.append("ORDER BY p.date, p.startTime, p.performanceID");

        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return collect(
                    ps,
                    rs -> List.of(
                            String.valueOf(rs.getInt("performanceID")),
                            rs.getString("title"),
                            rs.getString("genreName"),
                            rs.getString("city"),
                            rs.getString("date"),
                            String.valueOf(rs.getInt("available")),
                            fmtMoneyOrNa(rs, "cheapestPrice")));
        } catch (SQLException e) {
            throw new IllegalStateException("q5Combined failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q6SeatMapSummary(int performanceID) {
        requirePositive(performanceID, "Performance ID");
        String sql =
                """
                SELECT sec.sectionID, sec.sectionName, sec.isGeneralAdmission, pt.tierName, pt.price,
                       CASE WHEN sec.isGeneralAdmission = 1
                            THEN sec.standingCapacity
                            ELSE (SELECT COUNT(*) FROM seats se WHERE se.sectionID = sec.sectionID)
                       END AS capacity,
                       COALESCE(sold.soldCnt, 0) AS sold,
                       CASE WHEN sec.isGeneralAdmission = 1 THEN 0 ELSE COALESCE(bl.blockedCnt, 0) END AS blocked
                FROM performance_section_tiers pst
                JOIN sections sec ON sec.sectionID = pst.sectionID
                JOIN price_tiers pt ON pt.tierID = pst.tierID
                LEFT JOIN (
                  SELECT performanceID, sectionID, COUNT(*) AS soldCnt
                  FROM tickets WHERE status = 'ACTIVE' AND performanceID = ?
                  GROUP BY performanceID, sectionID
                ) sold ON sold.performanceID = pst.performanceID AND sold.sectionID = pst.sectionID
                LEFT JOIN (
                  SELECT b.performanceID, se.sectionID, COUNT(*) AS blockedCnt
                  FROM blocked_seats b JOIN seats se ON se.seatID = b.seatID
                  WHERE b.performanceID = ?
                  GROUP BY b.performanceID, se.sectionID
                ) bl ON bl.performanceID = pst.performanceID AND bl.sectionID = pst.sectionID
                WHERE pst.performanceID = ?
                ORDER BY sec.sectionName
                """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, performanceID);
            ps.setInt(2, performanceID);
            ps.setInt(3, performanceID);
            return collect(
                    ps,
                    rs -> {
                        int capacity = rs.getInt("capacity");
                        int sold = rs.getInt("sold");
                        int blocked = rs.getInt("blocked");
                        int available = capacity - sold - blocked;
                        return List.of(
                                rs.getString("sectionName"),
                                rs.getString("tierName"),
                                fmtMoney(rs.getBigDecimal("price")),
                                String.valueOf(Math.max(available, 0)),
                                String.valueOf(sold),
                                String.valueOf(blocked));
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("q6SeatMapSummary failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q7BestConsecutive(int performanceID, int quantity, BigDecimal budgetOrNull) {
        requirePositive(performanceID, "Performance ID");
        requirePositive(quantity, "Consecutive ticket quantity");
        validateMoney(budgetOrNull, "Budget");
        int offset = quantity - 1;
        StringBuilder sql = new StringBuilder(
                """
                WITH avail AS (
                  SELECT se.seatID, se.sectionID, se.rowName, se.seatNumber, pt.price
                  FROM seats se
                  JOIN sections sec ON sec.sectionID = se.sectionID
                  JOIN performance_section_tiers pst
                    ON pst.sectionID = se.sectionID AND pst.performanceID = ?
                  JOIN performances p ON p.performanceID = pst.performanceID AND p.venueID = sec.venueID
                  JOIN price_tiers pt ON pt.tierID = pst.tierID AND pt.performanceID = p.performanceID
                  WHERE """ + UPCOMING + """
                    AND sec.isGeneralAdmission = 0
                    AND se.seatID NOT IN (
                      SELECT seatID FROM blocked_seats WHERE performanceID = ?
                    )
                    AND se.seatID NOT IN (
                      SELECT seatID FROM tickets
                      WHERE performanceID = ? AND status = 'ACTIVE' AND seatID IS NOT NULL
                    )
                ),
                lead_calc AS (
                  SELECT sectionID, rowName, seatNumber,
                         LEAD(seatNumber, ?) OVER (
                           PARTITION BY sectionID, rowName ORDER BY seatNumber
                         ) AS endSeat
                  FROM avail
                ),
                starts AS (
                  SELECT sectionID, rowName, seatNumber AS startSeat, endSeat
                  FROM lead_calc
                  WHERE endSeat = seatNumber + ?
                ),
                priced AS (
                  SELECT s.sectionID, sec.sectionName, s.rowName, s.startSeat, s.endSeat,
                         (SELECT SUM(a2.price) FROM avail a2
                          WHERE a2.sectionID = s.sectionID AND a2.rowName = s.rowName
                            AND a2.seatNumber BETWEEN s.startSeat AND s.endSeat) AS totalPrice
                  FROM starts s
                  JOIN sections sec ON sec.sectionID = s.sectionID
                )
                SELECT * FROM priced
                """);
        List<Object> params = new ArrayList<>(List.of(performanceID, performanceID, performanceID, offset, offset));
        if (budgetOrNull != null) {
            sql.append("WHERE totalPrice <= ? ");
            params.add(budgetOrNull);
        }
        sql.append(budgetOrNull == null ? "WHERE " : "AND ");
        sql.append("totalPrice = (SELECT MIN(totalPrice) FROM priced) ");
        sql.append("ORDER BY sectionID, rowName, startSeat");

        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return collect(
                    ps,
                    rs -> List.of(
                            rs.getString("sectionName"),
                            rs.getString("rowName"),
                            String.valueOf(rs.getInt("startSeat")),
                            String.valueOf(rs.getInt("endSeat")),
                            fmtMoney(rs.getBigDecimal("totalPrice"))));
        } catch (SQLException e) {
            throw new IllegalStateException("q7BestConsecutive failed: " + e.getMessage(), e);
        }
    }

    

    @FunctionalInterface
    private interface RowMapper {
        List<String> map(ResultSet rs) throws SQLException;
    }

    private static List<List<String>> collect(PreparedStatement ps, RowMapper mapper) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
        }
        return rows;
    }

    private static String fmtMoney(BigDecimal v) {
        return v == null ? "n/a" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String fmtMoneyOrNa(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return rs.wasNull() || v == null ? "n/a" : fmtMoney(v);
    }

    private static void validateDates(LocalDate from, LocalDate to, boolean required) {
        if ((required && (from == null || to == null))
                || (from != null && to != null && from.isAfter(to))) {
            throw new IllegalStateException("Date range must have a start no later than its end.");
        }
    }

    private static void validateMoney(BigDecimal value, String label) {
        if (value != null && (value.signum() < 0 || value.stripTrailingZeros().scale() > 2)) {
            throw new IllegalStateException(label + " must be nonnegative with at most two decimal places.");
        }
    }

    private static void requireNonnegative(int value, String label) {
        if (value < 0) {
            throw new IllegalStateException(label + " must be nonnegative.");
        }
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalStateException(label + " must be positive.");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " is required.");
        }
    }

    private static void validateCoordinates(double lat, double lng, double radiusKm) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lng) || lng < -180 || lng > 180
                || !Double.isFinite(radiusKm) || radiusKm < 0) {
            throw new IllegalStateException("Latitude, longitude and distance must be finite and in range.");
        }
    }

    private static String normalizePostal(String value) {
        return value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
