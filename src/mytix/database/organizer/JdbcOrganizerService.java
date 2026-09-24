package mytix.database.organizer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mytix.database.DatabaseLogistics;

public final class JdbcOrganizerService implements OrganizerService {

    
    private static final double ELASTICITY_E = -1.0;

    @Override
    public int createEvent(CreateEventRequest request) {
        String insEvent =
                "INSERT INTO events (organizerID, genreID, title, resaleCapRatio) VALUES (?, ?, ?, ?)";
        String insArtist =
                "INSERT INTO event_artists (eventID, artistID, billingOrder) VALUES (?, ?, ?)";
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            int eventID;
            try (PreparedStatement ps = c.prepareStatement(insEvent, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, request.organizerID());
                ps.setInt(2, request.genreID());
                ps.setString(3, request.title());
                ps.setBigDecimal(4, request.resaleCapRatio());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("No eventID generated.");
                    }
                    eventID = rs.getInt(1);
                }
            }

            List<Integer> artists = request.artistIDsInBillingOrder();
            if (artists != null && !artists.isEmpty()) {
                int n = artists.size();
                try (PreparedStatement ps = c.prepareStatement(insArtist)) {
                    for (int i = 0; i < n; i++) {
                        ps.setInt(1, eventID);
                        ps.setInt(2, artists.get(i));
                        ps.setString(3, billingOrderFor(i, n));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            DatabaseLogistics.commit(c);
            return eventID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("createEvent failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    
    private static String billingOrderFor(int idx, int n) {
        if (idx == 0) {
            return "HEADLINER";
        }
        if (n > 1 && idx == n - 1) {
            return "OPENING_ACT";
        }
        return "SPECIAL_GUEST";
    }

    
    private record TierSample(
            int performanceID,
            int priority,
            BigDecimal price,
            int tierRank,
            long tierCapacity,
            long tierSold,
            long perfCapacity) {}

    @Override
    public List<List<String>> suggestPricing(int venueID, int genreID, LocalDate date) {
        
        
        List<TierSample> samples = fetchToolkitSamples(venueID, genreID);

        Set<Integer> perfIDs = new HashSet<>();
        Map<Integer, Integer> maxRankByPerf = new HashMap<>();
        for (TierSample s : samples) {
            perfIDs.add(s.performanceID());
            maxRankByPerf.merge(s.performanceID(), s.tierRank(), Integer::max);
        }

        if (perfIDs.size() < 3) {
            return fallbackRows();
        }

        int k = modalTierCount(maxRankByPerf.values());

        double[] rawShare = new double[k + 1];
        double[] avgPrice = new double[k + 1];
        double[] avgSell = new double[k + 1];
        boolean[] haveSell = new boolean[k + 1];
        String[] confidence = new String[k + 1];

        for (int r = 1; r <= k; r++) {
            List<TierSample> rankSamples = new ArrayList<>();
            for (TierSample s : samples) {
                if (s.tierRank() == r) {
                    rankSamples.add(s);
                }
            }
            if (rankSamples.isEmpty()) {
                confidence[r] = "INSUFFICIENT";
                continue;
            }

            double sumShare = 0;
            double sumPrice = 0;
            double sumSell = 0;
            int sellCount = 0;
            List<Double> prices = new ArrayList<>(rankSamples.size());
            for (TierSample s : rankSamples) {
                double share = s.perfCapacity() > 0 ? (double) s.tierCapacity() / s.perfCapacity() : 0.0;
                sumShare += share;
                double price = s.price().doubleValue();
                sumPrice += price;
                prices.add(price);
                if (s.tierCapacity() > 0) {
                    sumSell += (double) s.tierSold() / s.tierCapacity();
                    sellCount++;
                }
            }
            rawShare[r] = sumShare / rankSamples.size();
            avgPrice[r] = sumPrice / rankSamples.size();
            if (sellCount > 0) {
                avgSell[r] = sumSell / sellCount;
                haveSell[r] = true;
            }
            confidence[r] = confidenceFor(coefficientOfVariation(prices, avgPrice[r]));
        }

        double shareSum = 0;
        for (int r = 1; r <= k; r++) {
            shareSum += rawShare[r];
        }

        List<List<String>> out = new ArrayList<>();
        for (int r = 1; r <= k; r++) {
            double normShare = shareSum > 0 ? rawShare[r] / shareSum : 1.0 / k;
            out.add(List.of(
                    "P" + r,
                    pct(normShare),
                    money(avgPrice[r]),
                    haveSell[r] ? pct(avgSell[r]) : "n/a",
                    confidence[r],
                    "n/a"));
        }
        return out;
    }

    
    private static List<TierSample> fetchToolkitSamples(int venueID, int genreID) {
        String sql =
                """
                WITH venue_capacity AS (
                  SELECT v.venueID AS venueID, pa.city AS city,
                         COALESCE(SUM(CASE WHEN s.isGeneralAdmission = 1 THEN s.standingCapacity ELSE 0 END), 0)
                         + COALESCE((SELECT COUNT(*) FROM seats se JOIN sections s2 ON s2.sectionID = se.sectionID
                                      WHERE s2.venueID = v.venueID AND s2.isGeneralAdmission = 0), 0) AS capacity
                  FROM venues v
                  JOIN postal_areas pa ON pa.postalCode = v.postalCode
                  LEFT JOIN sections s ON s.venueID = v.venueID AND s.isGeneralAdmission = 1
                  GROUP BY v.venueID, pa.city
                ),
                target AS (
                  SELECT vc.city AS city, vc.capacity AS capacity, g.segmentID AS segmentID
                  FROM venue_capacity vc
                  JOIN genres g ON g.genreID = ?
                  WHERE vc.venueID = ?
                ),
                candidates AS (
                  SELECT p.performanceID AS performanceID, p.date AS pdate, 1 AS priority
                  FROM performances p
                  JOIN events e ON e.eventID = p.eventID
                  JOIN venue_capacity vc ON vc.venueID = p.venueID
                  CROSS JOIN target t
                  WHERE e.genreID = ?
                    AND vc.city = t.city
                    AND vc.capacity BETWEEN 0.7 * t.capacity AND 1.3 * t.capacity
                    AND p.status <> 'CANCELLED'
                    AND p.date < CURDATE()
                    AND p.date >= (CURDATE() - INTERVAL 365 DAY)
                  UNION ALL
                  SELECT p.performanceID, p.date, 2
                  FROM performances p
                  JOIN events e ON e.eventID = p.eventID
                  JOIN venue_capacity vc ON vc.venueID = p.venueID
                  CROSS JOIN target t
                  WHERE e.genreID = ?
                    AND vc.capacity BETWEEN 0.7 * t.capacity AND 1.3 * t.capacity
                    AND p.status <> 'CANCELLED'
                    AND p.date < CURDATE()
                    AND p.date >= (CURDATE() - INTERVAL 365 DAY)
                  UNION ALL
                  SELECT p.performanceID, p.date, 3
                  FROM performances p
                  JOIN events e ON e.eventID = p.eventID
                  JOIN venue_capacity vc ON vc.venueID = p.venueID
                  CROSS JOIN target t
                  WHERE e.genreID IN (SELECT genreID FROM genres WHERE segmentID = t.segmentID)
                    AND vc.capacity BETWEEN 0.7 * t.capacity AND 1.3 * t.capacity
                    AND p.status <> 'CANCELLED'
                    AND p.date < CURDATE()
                    AND p.date >= (CURDATE() - INTERVAL 365 DAY)
                ),
                deduped AS (
                  SELECT performanceID, pdate, priority,
                         ROW_NUMBER() OVER (PARTITION BY performanceID ORDER BY priority ASC) AS rn
                  FROM candidates
                ),
                best AS (
                  SELECT performanceID, pdate, priority FROM deduped WHERE rn = 1
                ),
                topn AS (
                  SELECT performanceID, priority,
                         ROW_NUMBER() OVER (ORDER BY priority ASC, pdate DESC) AS rn
                  FROM best
                )
                SELECT
                  tn.performanceID AS performanceID,
                  tn.priority AS priority,
                  pt.price AS price,
                  ROW_NUMBER() OVER (PARTITION BY tn.performanceID ORDER BY pt.price DESC) AS tierRank,
                  (SELECT COALESCE(SUM(CASE WHEN sec.isGeneralAdmission = 1 THEN sec.standingCapacity
                                            ELSE (SELECT COUNT(*) FROM seats se WHERE se.sectionID = sec.sectionID) END), 0)
                   FROM performance_section_tiers pst2
                   JOIN sections sec ON sec.sectionID = pst2.sectionID
                   WHERE pst2.tierID = pt.tierID) AS tierCapacity,
                  (SELECT COUNT(*) FROM tickets tk
                   WHERE tk.performanceID = pt.performanceID
                     AND tk.status = 'ACTIVE'
                     AND tk.sectionID IN (SELECT pst3.sectionID FROM performance_section_tiers pst3 WHERE pst3.tierID = pt.tierID)
                  ) AS tierSold,
                  (SELECT COALESCE(SUM(CASE WHEN sec4.isGeneralAdmission = 1 THEN sec4.standingCapacity
                                            ELSE (SELECT COUNT(*) FROM seats se4 WHERE se4.sectionID = sec4.sectionID) END), 0)
                   FROM performance_section_tiers pst4
                   JOIN sections sec4 ON sec4.sectionID = pst4.sectionID
                   WHERE pst4.performanceID = pt.performanceID) AS perfCapacity
                FROM topn tn
                JOIN price_tiers pt ON pt.performanceID = tn.performanceID
                WHERE tn.rn <= 50
                ORDER BY tn.performanceID, tierRank
                """;
        List<TierSample> samples = new ArrayList<>();
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, genreID);
            ps.setInt(2, venueID);
            ps.setInt(3, genreID);
            ps.setInt(4, genreID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    samples.add(new TierSample(
                            rs.getInt("performanceID"),
                            rs.getInt("priority"),
                            rs.getBigDecimal("price"),
                            rs.getInt("tierRank"),
                            rs.getLong("tierCapacity"),
                            rs.getLong("tierSold"),
                            rs.getLong("perfCapacity")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("suggestPricing failed: " + e.getMessage(), e);
        }
        return samples;
    }

    
    private static int modalTierCount(Iterable<Integer> tierCounts) {
        Map<Integer, Long> freq = new HashMap<>();
        for (int cnt : tierCounts) {
            freq.merge(cnt, 1L, Long::sum);
        }
        int k = 3;
        long bestFreq = -1;
        for (Map.Entry<Integer, Long> e : freq.entrySet()) {
            if (e.getValue() > bestFreq) {
                bestFreq = e.getValue();
                k = e.getKey();
            }
        }
        return Math.max(2, Math.min(4, k));
    }

    
    private static Double coefficientOfVariation(List<Double> prices, double mean) {
        if (prices.size() < 2 || mean == 0.0) {
            return null;
        }
        double sumSq = 0;
        for (double p : prices) {
            sumSq += (p - mean) * (p - mean);
        }
        double stddevSamp = Math.sqrt(sumSq / (prices.size() - 1));
        return stddevSamp / mean;
    }

    private static String confidenceFor(Double cv) {
        if (cv == null) {
            return "LOW";
        }
        if (cv < 0.2) {
            return "HIGH";
        }
        if (cv <= 0.4) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static List<List<String>> fallbackRows() {
        return List.of(
                List.of("P1", "30.0%", "180.00", "n/a", "INSUFFICIENT", "n/a"),
                List.of("P2", "40.0%", "120.00", "n/a", "INSUFFICIENT", "n/a"),
                List.of("P3", "30.0%", "75.00", "n/a", "INSUFFICIENT", "n/a"));
    }

    private static String money(double v) {
        return String.format("%.2f", v);
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100.0);
    }

    @Override
    public double estimateDeltaRevenue(
            double suggestedPrice, double tierCapacityUnits, double sellThrough, double delta) {
        double r0 = tierCapacityUnits * suggestedPrice * sellThrough;
        double adjustedSellThrough = clamp(sellThrough * (1 + ELASTICITY_E * delta), 0.0, 1.0);
        double r1 = tierCapacityUnits * suggestedPrice * (1 + delta) * adjustedSellThrough;
        return r1 - r0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public int targetCapacity(int venueID) {
        String sql =
                """
                SELECT COALESCE(SUM(CASE WHEN s.isGeneralAdmission = 1 THEN s.standingCapacity ELSE 0 END), 0)
                       + COALESCE((SELECT COUNT(*) FROM seats se JOIN sections s2 ON s2.sectionID = se.sectionID
                                    WHERE s2.venueID = ? AND s2.isGeneralAdmission = 0), 0) AS capacity
                FROM sections s
                WHERE s.venueID = ?
                """;
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, venueID);
            ps.setInt(2, venueID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("targetCapacity failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int createPerformance(CreatePerformanceRequest request) {
        if (request.tierPrices() == null || request.tierPrices().size() < 2) {
            throw new IllegalArgumentException("At least 2 price tiers are required.");
        }
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            try (PreparedStatement ps =
                    c.prepareStatement("SELECT organizerID FROM events WHERE eventID = ?")) {
                ps.setInt(1, request.eventID());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Event not found.");
                    }
                    if (rs.getInt(1) != request.organizerID()) {
                        throw new IllegalStateException("Event does not belong to this organizer.");
                    }
                }
            }

            Set<Integer> venueSections = new HashSet<>();
            try (PreparedStatement ps =
                    c.prepareStatement("SELECT sectionID FROM sections WHERE venueID = ?")) {
                ps.setInt(1, request.venueID());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        venueSections.add(rs.getInt(1));
                    }
                }
            }
            if (venueSections.isEmpty()) {
                throw new IllegalStateException("Venue has no sections defined.");
            }
            Map<Integer, String> sectionToTier = request.sectionToTier();
            if (sectionToTier == null || !sectionToTier.keySet().equals(venueSections)) {
                throw new IllegalStateException(
                        "Every venue section must be mapped to exactly one tier ("
                                + venueSections.size()
                                + " required, "
                                + (sectionToTier == null ? 0 : sectionToTier.size())
                                + " provided).");
            }
            for (String tierName : sectionToTier.values()) {
                if (!request.tierPrices().containsKey(tierName)) {
                    throw new IllegalStateException(
                            "Section maps to an undefined tier name: " + tierName);
                }
            }

            int performanceID;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO performances (eventID, venueID, date, startTime, endTime, status) "
                            + "VALUES (?, ?, ?, ?, ?, 'SCHEDULED')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, request.eventID());
                ps.setInt(2, request.venueID());
                ps.setDate(3, java.sql.Date.valueOf(request.date()));
                ps.setTime(4, java.sql.Time.valueOf(request.startTime()));
                ps.setTime(5, java.sql.Time.valueOf(request.endTime()));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("No performanceID generated.");
                    }
                    performanceID = rs.getInt(1);
                }
            }

            Map<String, Integer> tierIDByName = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO price_tiers (performanceID, tierName, price) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                for (Map.Entry<String, BigDecimal> e : request.tierPrices().entrySet()) {
                    ps.setInt(1, performanceID);
                    ps.setString(2, e.getKey());
                    ps.setBigDecimal(3, e.getValue());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            tierIDByName.put(e.getKey(), rs.getInt(1));
                        }
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO performance_section_tiers (performanceID, sectionID, tierID) "
                            + "VALUES (?, ?, ?)")) {
                for (Map.Entry<Integer, String> e : sectionToTier.entrySet()) {
                    ps.setInt(1, performanceID);
                    ps.setInt(2, e.getKey());
                    ps.setInt(3, tierIDByName.get(e.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            DatabaseLogistics.commit(c);
            return performanceID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("createPerformance failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean updateTierPrice(int organizerID, int tierID, BigDecimal newPrice) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pt.performanceID, e.organizerID FROM price_tiers pt "
                            + "JOIN performances p ON p.performanceID = pt.performanceID "
                            + "JOIN events e ON e.eventID = p.eventID WHERE pt.tierID = ?")) {
                ps.setInt(1, tierID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Tier not found.");
                    }
                    if (rs.getInt(2) != organizerID) {
                        throw new IllegalStateException("Tier does not belong to this organizer.");
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM tickets t "
                            + "JOIN performance_section_tiers pst "
                            + "  ON pst.sectionID = t.sectionID AND pst.performanceID = t.performanceID "
                            + "WHERE pst.tierID = ? AND t.status = 'ACTIVE'")) {
                ps.setInt(1, tierID);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        DatabaseLogistics.rollbackQuietly(c);
                        return false;
                    }
                }
            }

            try (PreparedStatement ps =
                    c.prepareStatement("UPDATE price_tiers SET price = ? WHERE tierID = ?")) {
                ps.setBigDecimal(1, newPrice);
                ps.setInt(2, tierID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("updateTierPrice failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean blockSeat(int organizerID, int performanceID, int seatID, String reason) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);
            requireOwnership(c, organizerID, performanceID);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM seats se "
                            + "JOIN sections sec ON sec.sectionID = se.sectionID "
                            + "JOIN performances p ON p.venueID = sec.venueID "
                            + "WHERE se.seatID = ? AND p.performanceID = ?")) {
                ps.setInt(1, seatID);
                ps.setInt(2, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        throw new IllegalStateException(
                                "Seat does not belong to this performance's venue.");
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM tickets "
                            + "WHERE performanceID = ? AND seatID = ? AND status = 'ACTIVE'")) {
                ps.setInt(1, performanceID);
                ps.setInt(2, seatID);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        DatabaseLogistics.rollbackQuietly(c);
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO blocked_seats (performanceID, seatID, reason) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE reason = VALUES(reason)")) {
                ps.setInt(1, performanceID);
                ps.setInt(2, seatID);
                ps.setString(3, reason);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("blockSeat failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean unblockSeat(int organizerID, int performanceID, int seatID) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);
            requireOwnership(c, organizerID, performanceID);

            int deleted;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM blocked_seats WHERE performanceID = ? AND seatID = ?")) {
                ps.setInt(1, performanceID);
                ps.setInt(2, seatID);
                deleted = ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return deleted > 0;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("unblockSeat failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean cancelPerformance(int organizerID, int performanceID) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);
            requireOwnership(c, organizerID, performanceID);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status FROM performances WHERE performanceID = ? FOR UPDATE")) {
                ps.setInt(1, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Performance not found.");
                    }
                    if ("CANCELLED".equals(rs.getString(1))) {
                        DatabaseLogistics.rollbackQuietly(c);
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE performances SET status = 'CANCELLED', cancelledAt = NOW() "
                            + "WHERE performanceID = ?")) {
                ps.setInt(1, performanceID);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE resale_listings rl JOIN tickets t ON t.ticketID = rl.ticketID "
                            + "SET rl.status = 'WITHDRAWN' "
                            + "WHERE t.performanceID = ? AND rl.status = 'ACTIVE'")) {
                ps.setInt(1, performanceID);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE tickets SET status = 'REFUNDED', cancelledAt = NOW() "
                            + "WHERE performanceID = ? AND status = 'ACTIVE'")) {
                ps.setInt(1, performanceID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("cancelPerformance failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    private static void requireOwnership(Connection c, int organizerID, int performanceID)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT e.organizerID FROM performances p "
                        + "JOIN events e ON e.eventID = p.eventID WHERE p.performanceID = ?")) {
            ps.setInt(1, performanceID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Performance not found.");
                }
                if (rs.getInt(1) != organizerID) {
                    throw new IllegalStateException("Performance does not belong to this organizer.");
                }
            }
        }
    }

    private static void closeQuietly(Connection c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (SQLException ignored) {
            
        }
    }
}
