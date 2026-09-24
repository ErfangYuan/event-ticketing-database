package mytix.database.catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import mytix.database.DatabaseLogistics;

public final class JdbcCatalogRepository implements CatalogRepository {

    @Override
    public List<List<String>> listSegments() {
        return query(
                "SELECT segmentID, segmentName FROM segments ORDER BY segmentID",
                rs -> List.of(String.valueOf(rs.getInt(1)), rs.getString(2)));
    }

    @Override
    public List<List<String>> listGenres() {
        return query(
                "SELECT genreID, genreName, segmentID FROM genres ORDER BY genreID",
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        String.valueOf(rs.getInt(3))));
    }

    @Override
    public List<List<String>> listVenues() {
        return query(
                """
                SELECT v.venueID, v.venueName, pa.city, v.postalCode
                FROM venues v
                JOIN postal_areas pa ON pa.postalCode = v.postalCode
                ORDER BY v.venueID
                """,
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)));
    }

    @Override
    public List<List<String>> listVenuesByCity(String city) {
        return query(
                """
                SELECT v.venueID, v.venueName, pa.city, v.postalCode
                FROM venues v
                JOIN postal_areas pa ON pa.postalCode = v.postalCode
                WHERE pa.city = ?
                ORDER BY v.venueName
                """,
                ps -> ps.setString(1, city),
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)));
    }

    @Override
    public List<List<String>> listEvents(Integer organizerIDOrNull) {
        if (organizerIDOrNull == null) {
            return query(
                    """
                    SELECT e.eventID, e.title, g.genreName, e.organizerID
                    FROM events e
                    JOIN genres g ON g.genreID = e.genreID
                    ORDER BY e.eventID
                    """,
                    rs -> List.of(
                            String.valueOf(rs.getInt(1)),
                            rs.getString(2),
                            rs.getString(3),
                            String.valueOf(rs.getInt(4))));
        }
        String sql =
                """
                SELECT e.eventID, e.title, g.genreName, e.organizerID
                FROM events e
                JOIN genres g ON g.genreID = e.genreID
                WHERE e.organizerID = ?
                ORDER BY e.eventID
                """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, organizerIDOrNull);
            return collect(ps, rs -> List.of(
                    String.valueOf(rs.getInt(1)),
                    rs.getString(2),
                    rs.getString(3),
                    String.valueOf(rs.getInt(4))));
        } catch (SQLException e) {
            throw new IllegalStateException("listEvents failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<String>> listArtists() {
        return query(
                "SELECT artistID, artistName FROM artists ORDER BY artistID",
                rs -> List.of(String.valueOf(rs.getInt(1)), rs.getString(2)));
    }

    @Override
    public List<List<String>> listUpcomingEvents() {
        return query(
                """
                SELECT DISTINCT e.eventID, e.title, g.genreName, s.segmentName
                FROM events e
                JOIN genres g ON g.genreID = e.genreID
                JOIN segments s ON s.segmentID = g.segmentID
                JOIN performances p ON p.eventID = e.eventID
                WHERE p.status = 'SCHEDULED' AND p.date >= CURDATE()
                ORDER BY e.eventID
                """,
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)));
    }

    @Override
    public List<List<String>> listUpcomingPerformances(int eventID) {
        String sql =
                """
                SELECT p.performanceID, p.date, p.startTime, p.endTime, v.venueName, pa.city
                FROM performances p
                JOIN venues v ON v.venueID = p.venueID
                JOIN postal_areas pa ON pa.postalCode = v.postalCode
                WHERE p.eventID = ? AND p.status = 'SCHEDULED' AND p.date >= CURDATE()
                ORDER BY p.date, p.startTime
                """;
        return query(sql, ps -> ps.setInt(1, eventID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getDate(2).toLocalDate().toString(),
                rs.getTime(3).toString(),
                rs.getTime(4).toString(),
                rs.getString(5),
                rs.getString(6)));
    }

    @Override
    public List<List<String>> listSectionsForVenue(int venueID) {
        String sql =
                "SELECT sectionID, sectionName, isGeneralAdmission, standingCapacity "
                        + "FROM sections WHERE venueID = ? ORDER BY sectionID";
        return query(sql, ps -> ps.setInt(1, venueID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getBoolean(3) ? "GA" : "RESERVED",
                rs.getObject(4) == null ? "" : String.valueOf(rs.getInt(4))));
    }

    @Override
    public List<List<String>> listAvailableReservedSeats(int performanceID) {
        String sql =
                """
                SELECT se.seatID, sec.sectionName, se.rowName, se.seatNumber, pt.tierName, pt.price
                FROM seats se
                JOIN sections sec ON sec.sectionID = se.sectionID
                JOIN performance_section_tiers pst
                    ON pst.sectionID = sec.sectionID AND pst.performanceID = ?
                JOIN price_tiers pt ON pt.tierID = pst.tierID
                WHERE sec.isGeneralAdmission = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM tickets t
                      WHERE t.performanceID = ? AND t.seatID = se.seatID AND t.status = 'ACTIVE'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM blocked_seats bs
                      WHERE bs.performanceID = ? AND bs.seatID = se.seatID
                  )
                ORDER BY sec.sectionID, se.rowName, se.seatNumber
                """;
        return query(
                sql,
                ps -> {
                    ps.setInt(1, performanceID);
                    ps.setInt(2, performanceID);
                    ps.setInt(3, performanceID);
                },
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        rs.getString(3) + "-" + rs.getInt(4),
                        rs.getString(5),
                        rs.getBigDecimal(6).toPlainString()));
    }

    @Override
    public List<List<String>> listSeatMapPreview(int performanceID) {
        String sql =
                """
                SELECT sec.sectionID, sec.sectionName, 'RESERVED' AS kind, pt.tierName, pt.price,
                       (SELECT COUNT(*) FROM seats se2 WHERE se2.sectionID = sec.sectionID) AS capacity,
                       (SELECT COUNT(*) FROM tickets t2
                        WHERE t2.performanceID = ? AND t2.sectionID = sec.sectionID AND t2.status = 'ACTIVE') AS sold,
                       (SELECT COUNT(*) FROM blocked_seats bs2
                        JOIN seats se3 ON se3.seatID = bs2.seatID
                        WHERE bs2.performanceID = ? AND se3.sectionID = sec.sectionID) AS blocked
                FROM sections sec
                JOIN performance_section_tiers pst ON pst.sectionID = sec.sectionID AND pst.performanceID = ?
                JOIN price_tiers pt ON pt.tierID = pst.tierID
                WHERE sec.isGeneralAdmission = 0

                UNION ALL

                SELECT sec.sectionID, sec.sectionName, 'GA' AS kind, pt.tierName, pt.price,
                       sec.standingCapacity AS capacity,
                       (SELECT COUNT(*) FROM tickets t2
                        WHERE t2.performanceID = ? AND t2.sectionID = sec.sectionID AND t2.status = 'ACTIVE') AS sold,
                       0 AS blocked
                FROM sections sec
                JOIN performance_section_tiers pst ON pst.sectionID = sec.sectionID AND pst.performanceID = ?
                JOIN price_tiers pt ON pt.tierID = pst.tierID
                WHERE sec.isGeneralAdmission = 1

                ORDER BY sectionID
                """;
        return query(
                sql,
                ps -> {
                    ps.setInt(1, performanceID);
                    ps.setInt(2, performanceID);
                    ps.setInt(3, performanceID);
                    ps.setInt(4, performanceID);
                    ps.setInt(5, performanceID);
                },
                rs -> {
                    int capacity = rs.getInt(6);
                    int sold = rs.getInt(7);
                    int blocked = rs.getInt(8);
                    int available = capacity - sold - blocked;
                    return List.of(
                            String.valueOf(rs.getInt(1)),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getBigDecimal(5).toPlainString(),
                            String.valueOf(available),
                            String.valueOf(sold),
                            String.valueOf(blocked));
                });
    }

    @Override
    public List<List<String>> listCustomerTickets(int customerID) {
        String sql =
                """
                SELECT t.ticketID, e.title, p.date, sec.sectionName,
                       CASE WHEN se.seatID IS NULL THEN 'GA' ELSE CONCAT(se.rowName, '-', se.seatNumber) END,
                       t.status, t.faceValue
                FROM tickets t
                JOIN performances p ON p.performanceID = t.performanceID
                JOIN events e ON e.eventID = p.eventID
                JOIN sections sec ON sec.sectionID = t.sectionID
                LEFT JOIN seats se ON se.seatID = t.seatID
                WHERE t.currentOwnerID = ?
                ORDER BY t.ticketID DESC
                """;
        return query(sql, ps -> ps.setInt(1, customerID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getDate(3).toLocalDate().toString(),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getBigDecimal(7).toPlainString()));
    }

    @Override
    public List<List<String>> ticketDetail(int ticketID) {
        String sql =
                """
                SELECT t.ticketID,
                       (SELECT COUNT(*) FROM ticket_ownership o WHERE o.ticketID = t.ticketID) AS ownerships,
                       (SELECT rl.status FROM resale_listings rl
                        WHERE rl.ticketID = t.ticketID AND rl.status = 'ACTIVE' LIMIT 1) AS activeListing
                FROM tickets t
                WHERE t.ticketID = ?
                """;
        return query(sql, ps -> ps.setInt(1, ticketID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                String.valueOf(rs.getInt(2)),
                rs.getString(3) == null ? "none" : "ACTIVE"));
    }

    @Override
    public List<List<String>> listActiveResaleListings(int excludeSellerID) {
        String sql =
                """
                SELECT rl.listingID, e.title, p.date, sec.sectionName,
                       CASE WHEN se.seatID IS NULL THEN 'GA' ELSE CONCAT(se.rowName, '-', se.seatNumber) END,
                       t.faceValue, rl.listingPrice, rl.sellerID
                FROM resale_listings rl
                JOIN tickets t ON t.ticketID = rl.ticketID
                JOIN performances p ON p.performanceID = t.performanceID
                JOIN events e ON e.eventID = p.eventID
                JOIN sections sec ON sec.sectionID = t.sectionID
                LEFT JOIN seats se ON se.seatID = t.seatID
                WHERE rl.status = 'ACTIVE'
                  AND rl.sellerID <> ?
                ORDER BY rl.listingID
                """;
        return query(sql, ps -> ps.setInt(1, excludeSellerID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getDate(3).toLocalDate().toString(),
                rs.getString(4),
                rs.getString(5),
                rs.getBigDecimal(6).toPlainString(),
                rs.getBigDecimal(7).toPlainString(),
                String.valueOf(rs.getInt(8))));
    }

    @Override
    public List<List<String>> listActiveListingsForSeller(int customerID) {
        String sql =
                """
                SELECT rl.listingID, rl.ticketID, e.title, p.date, sec.sectionName,
                       CASE WHEN se.seatID IS NULL THEN 'GA' ELSE CONCAT(se.rowName, '-', se.seatNumber) END,
                       rl.listingPrice
                FROM resale_listings rl
                JOIN tickets t ON t.ticketID = rl.ticketID
                JOIN performances p ON p.performanceID = t.performanceID
                JOIN events e ON e.eventID = p.eventID
                JOIN sections sec ON sec.sectionID = t.sectionID
                LEFT JOIN seats se ON se.seatID = t.seatID
                WHERE rl.sellerID = ? AND rl.status = 'ACTIVE'
                ORDER BY rl.listingID
                """;
        return query(sql, ps -> ps.setInt(1, customerID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                String.valueOf(rs.getInt(2)),
                rs.getString(3),
                rs.getDate(4).toLocalDate().toString(),
                rs.getString(5),
                rs.getString(6),
                rs.getBigDecimal(7).toPlainString()));
    }

    @Override
    public List<List<String>> listEligibleReviewPerformances(int customerID) {
        String sql =
                """
                SELECT DISTINCT p.performanceID, e.title, v.venueName, p.date
                FROM ticket_ownership tow
                JOIN tickets t ON t.ticketID = tow.ticketID
                JOIN performances p ON p.performanceID = t.performanceID
                JOIN events e ON e.eventID = p.eventID
                JOIN venues v ON v.venueID = p.venueID
                WHERE tow.ownerID = ?
                  AND t.status = 'ACTIVE'
                  AND p.date < CURDATE()
                  AND DATEDIFF(CURDATE(), p.date) <= 60
                  AND NOT EXISTS (
                      SELECT 1 FROM reviews r
                      WHERE r.customerID = ? AND r.performanceID = p.performanceID
                  )
                ORDER BY p.date DESC
                """;
        return query(
                sql,
                ps -> {
                    ps.setInt(1, customerID);
                    ps.setInt(2, customerID);
                },
                rs -> List.of(
                        String.valueOf(rs.getInt(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getDate(4).toLocalDate().toString()));
    }

    @Override
    public Integer eventGenreID(int eventID) {
        String sql = "SELECT genreID FROM events WHERE eventID = ?";
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, eventID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("eventGenreID failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<String>> listMyPerformances(int organizerID) {
        String sql =
                """
                SELECT p.performanceID, e.title, v.venueName, p.date, p.startTime, p.status
                FROM performances p
                JOIN events e ON e.eventID = p.eventID
                JOIN venues v ON v.venueID = p.venueID
                WHERE e.organizerID = ?
                ORDER BY p.date DESC, p.performanceID DESC
                """;
        return query(sql, ps -> ps.setInt(1, organizerID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getString(3),
                rs.getDate(4).toLocalDate().toString(),
                rs.getTime(5).toString(),
                rs.getString(6)));
    }

    @Override
    public List<List<String>> listTiers(int performanceID) {
        String sql =
                """
                SELECT pt.tierID, pt.tierName, pt.price,
                       COUNT(t.ticketID) AS activeTickets
                FROM price_tiers pt
                LEFT JOIN performance_section_tiers pst ON pst.tierID = pt.tierID
                LEFT JOIN tickets t ON t.performanceID = pt.performanceID
                    AND t.sectionID = pst.sectionID AND t.status = 'ACTIVE'
                WHERE pt.performanceID = ?
                GROUP BY pt.tierID, pt.tierName, pt.price
                ORDER BY pt.price DESC
                """;
        return query(sql, ps -> ps.setInt(1, performanceID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getBigDecimal(3).toPlainString(),
                String.valueOf(rs.getInt(4))));
    }

    @Override
    public List<List<String>> listBlockedSeats(int performanceID) {
        String sql =
                """
                SELECT bs.seatID, sec.sectionName, se.rowName, se.seatNumber, bs.reason
                FROM blocked_seats bs
                JOIN seats se ON se.seatID = bs.seatID
                JOIN sections sec ON sec.sectionID = se.sectionID
                WHERE bs.performanceID = ?
                ORDER BY sec.sectionID, se.rowName, se.seatNumber
                """;
        return query(sql, ps -> ps.setInt(1, performanceID), rs -> List.of(
                String.valueOf(rs.getInt(1)),
                rs.getString(2),
                rs.getString(3) + "-" + rs.getInt(4),
                rs.getString(5) == null ? "" : rs.getString(5)));
    }

    @FunctionalInterface
    private interface RowMapper {
        List<String> map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface ParamBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private static List<List<String>> query(String sql, RowMapper mapper) {
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            return collect(ps, mapper);
        } catch (SQLException e) {
            throw new IllegalStateException("catalog query failed: " + e.getMessage(), e);
        }
    }

    private static List<List<String>> query(String sql, ParamBinder binder, RowMapper mapper) {
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return collect(ps, mapper);
        } catch (SQLException e) {
            throw new IllegalStateException("catalog query failed: " + e.getMessage(), e);
        }
    }

    private static List<List<String>> collect(PreparedStatement ps, RowMapper mapper)
            throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
        }
        return rows;
    }
}
