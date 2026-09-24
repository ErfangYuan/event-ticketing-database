package mytix.database.query;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mytix.database.DatabaseLogistics;

public final class JdbcQueryService implements QueryService {

    private static final String UPCOMING = " p.status = 'SCHEDULED' AND p.date >= CURDATE() ";

    
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
        String sql =
                "WITH " + SECTION_AVAIL_CTE + ", " + PERF_CHEAPEST_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, v.venueName, pa.city,
                               (2 * 6371 * ASIN(SQRT(
                                  POWER(SIN(RADIANS(v.latitude - ?) / 2), 2)
                                  + COS(RADIANS(?)) * COS(RADIANS(v.latitude))
                                    * POWER(SIN(RADIANS(v.longitude - ?) / 2), 2)
                                ))) AS distanceKm,
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
            case "price_asc" -> "pc.cheapestPrice ASC";
            case "price_desc" -> "pc.cheapestPrice DESC";
            default -> "distanceKm ASC";
        };
    }

    

    @Override
    public List<List<String>> q2PostalAdjacent(String postalCode) {
        String sql =
                """
                SELECT p.performanceID, e.title, v.venueName, pa.city, pa.postalCode
                FROM performances p
                JOIN events e ON e.eventID = p.eventID
                JOIN venues v ON v.venueID = p.venueID
                JOIN postal_areas pa ON pa.postalCode = v.postalCode
                WHERE """
                        + UPCOMING
                        + """
                        AND LEFT(pa.postalCode, 3) = LEFT(?, 3)
                        ORDER BY p.date, p.startTime
                        """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, postalCode);
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
        String sql =
                "WITH " + SECTION_AVAIL_CTE + ", " + PERF_AVAIL_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, v.venueName, pa.city, p.date, p.startTime,
                               COALESCE(pa2.available, 0) AS available
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        LEFT JOIN perf_avail pa2 ON pa2.performanceID = p.performanceID
                        WHERE p.status <> 'CANCELLED'
                          AND p.date BETWEEN ? AND ?
                        HAVING available >= ?
                        ORDER BY p.date, p.startTime
                        """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            ps.setInt(3, minAvailable);
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
        StringBuilder sql = new StringBuilder(
                "WITH " + SECTION_AVAIL_CTE + ", " + PERF_AVAIL_CTE + ", " + PERF_CHEAPEST_CTE + " "
                        + """
                        SELECT p.performanceID, e.title, g.genreName, sg.segmentName, v.venueName, pa.city,
                               p.date, COALESCE(pa2.available, 0) AS available, pc.cheapestPrice
                        FROM performances p
                        JOIN events e ON e.eventID = p.eventID
                        JOIN genres g ON g.genreID = e.genreID
                        JOIN segments sg ON sg.segmentID = g.segmentID
                        JOIN venues v ON v.venueID = p.venueID
                        JOIN postal_areas pa ON pa.postalCode = v.postalCode
                        LEFT JOIN perf_avail pa2 ON pa2.performanceID = p.performanceID
                        LEFT JOIN perf_cheapest pc ON pc.performanceID = p.performanceID
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
        if (filter.reservedOnly()) {
            sql.append(
                    "AND EXISTS (SELECT 1 FROM section_avail sa JOIN sections sec ON sec.sectionID = sa.sectionID "
                            + "WHERE sa.performanceID = p.performanceID AND sec.isGeneralAdmission = 0 AND sa.available > 0) ");
        } else if (filter.gaOnly()) {
            sql.append(
                    "AND EXISTS (SELECT 1 FROM section_avail sa JOIN sections sec ON sec.sectionID = sa.sectionID "
                            + "WHERE sa.performanceID = p.performanceID AND sec.isGeneralAdmission = 1 AND sa.available > 0) ");
        }

        List<String> having = new ArrayList<>();
        if (filter.minAvailable() != null) {
            having.add("available >= ?");
            params.add(filter.minAvailable());
        }
        if (filter.minPrice() != null) {
            having.add("(pc.cheapestPrice IS NULL OR pc.cheapestPrice >= ?)");
            params.add(filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            having.add("(pc.cheapestPrice IS NULL OR pc.cheapestPrice <= ?)");
            params.add(filter.maxPrice());
        }
        if (!having.isEmpty()) {
            sql.append("HAVING ").append(String.join(" AND ", having)).append(' ');
        }
        sql.append("ORDER BY p.date, p.startTime");

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
                                rs.getBoolean("isGeneralAdmission") ? "n/a" : String.valueOf(blocked));
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("q6SeatMapSummary failed: " + e.getMessage(), e);
        }
    }

    

    @Override
    public List<List<String>> q7BestConsecutive(int performanceID, int quantity, BigDecimal budgetOrNull) {
        int offset = Math.max(quantity - 1, 0);
        StringBuilder sql = new StringBuilder(
                """
                WITH avail AS (
                  SELECT se.seatID, se.sectionID, se.rowName, se.seatNumber, pt.price
                  FROM seats se
                  JOIN sections sec ON sec.sectionID = se.sectionID
                  JOIN performance_section_tiers pst
                    ON pst.sectionID = se.sectionID AND pst.performanceID = ?
                  JOIN price_tiers pt ON pt.tierID = pst.tierID
                  WHERE sec.isGeneralAdmission = 0
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
        sql.append("ORDER BY totalPrice ASC LIMIT 1");

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
}
