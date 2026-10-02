package mytix.database.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** One acquisition-cohort definition shared by R4 and trading authorization. */
public final class ScalperPolicy {
    public static final String ANNUAL_BOUND = "DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 YEAR)";

    private ScalperPolicy() {}

    /** lowerBoundSql is an internal SQL expression, never user-supplied text. */
    public static String cohortSql(String lowerBoundSql) {
        return """
                SELECT a.ownerID AS customerID,pa.country,pa.city,COUNT(*) AS purchased,
                       SUM(CASE WHEN EXISTS (
                         SELECT 1 FROM resale_listings rl
                         WHERE rl.ownershipID=a.ownershipID
                           AND rl.createdAt>=bounds.since AND rl.createdAt<=bounds.untilTime
                       ) THEN 1 ELSE 0 END) AS listed
                FROM ticket_ownership a
                JOIN orders o ON o.orderID=a.orderID
                JOIN performances p ON p.performanceID=o.performanceID
                JOIN venues v ON v.venueID=p.venueID
                JOIN postal_areas pa ON pa.postalCode=v.postalCode
                CROSS JOIN (SELECT %s AS since,CURRENT_TIMESTAMP AS untilTime) bounds
                WHERE a.acquiredAt>=bounds.since AND a.acquiredAt<=bounds.untilTime
                GROUP BY a.ownerID,pa.country,pa.city
                """.formatted(lowerBoundSql);
    }

    /** Call after the performance lock; serializes this customer's trading decisions. */
    public static void requireTradingAllowed(Connection c, int customerID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT userType FROM users WHERE userID=? AND deletedAt IS NULL FOR UPDATE")) {
            ps.setInt(1, customerID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"CUSTOMER".equals(rs.getString(1))) {
                    throw new IllegalStateException("An active customer account is required.");
                }
            }
        }
        String sql = "SELECT 1 FROM (" + cohortSql(ANNUAL_BOUND)
                + ") cohorts WHERE customerID=? AND purchased>=10 AND listed*2>purchased LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Account flagged for excessive resale activity; new purchases and listings are prohibited.");
                }
            }
        }
    }
}
