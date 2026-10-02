package mytix.database.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Shared locking and financial invariants for every ticket mutation. */
public final class TicketLedger {
    private TicketLedger() {}

    public record Performance(int id, int venueID, int organizerID, String status,
                              LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        public void requireUpcoming(Connection c) throws SQLException {
            if (!"SCHEDULED".equals(status) || !startsAt.isAfter(currentTime(c))) {
                throw new IllegalStateException("Performance is cancelled or has already started.");
            }
        }
    }

    public static LocalDateTime currentTime(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT CURRENT_TIMESTAMP"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getObject(1, LocalDateTime.class);
        }
    }

    /** Always acquire this lock before ticket, listing, tier, or inventory locks. */
    public static Performance lockPerformance(Connection c, int performanceID) throws SQLException {
        int eventID;
        int venueID;
        String status;
        LocalDateTime starts, ends, now;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT eventID, venueID, status, TIMESTAMP(date,startTime),
                       TIMESTAMP(date,endTime), CURRENT_TIMESTAMP
                FROM performances WHERE performanceID=? FOR UPDATE
                """)) {
            ps.setInt(1, performanceID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Performance not found.");
                eventID = rs.getInt(1);
                venueID = rs.getInt(2);
                status = rs.getString(3);
                starts = rs.getObject(4, LocalDateTime.class);
                ends = rs.getObject(5, LocalDateTime.class);
                now = rs.getObject(6, LocalDateTime.class);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT organizerID FROM events WHERE eventID=?")) {
            ps.setInt(1, eventID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Event not found.");
                return new Performance(performanceID, venueID, rs.getInt(1), status, starts, ends, now);
            }
        }
    }

    public static int ticketPerformance(Connection c, int ticketID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT performanceID FROM tickets WHERE ticketID=?")) {
            ps.setInt(1, ticketID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Ticket not found.");
                return rs.getInt(1);
            }
        }
    }

    public static void requireRole(Connection c, int userID, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT userType FROM users WHERE userID=? AND deletedAt IS NULL FOR UPDATE")) {
            ps.setInt(1, userID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !role.equals(rs.getString(1))) {
                    throw new IllegalStateException("An active " + role + " account is required.");
                }
            }
        }
    }

    public static BigDecimal money(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException(label + " must be a nonnegative amount with at most two decimals.");
        }
        return amount.setScale(2);
    }

    /** Caller holds the performance lock. Refund the current acquisition exactly once. */
    public static void refundTicket(Connection c, int ticketID, int actorID, String actorType)
            throws SQLException {
        int owner;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT currentOwnerID,status FROM tickets WHERE ticketID=? FOR UPDATE")) {
            ps.setInt(1, ticketID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"ACTIVE".equals(rs.getString(2))) {
                    throw new IllegalStateException("Only ACTIVE tickets can be refunded.");
                }
                owner = rs.getInt(1);
            }
        }
        int orderID;
        BigDecimal refund;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT a.orderID,a.amountPaid
                FROM ticket_ownership a JOIN orders o ON o.orderID=a.orderID
                WHERE a.ticketID=? AND a.ownerID=? AND o.customerID=a.ownerID
                ORDER BY a.acquiredAt DESC,a.ownershipID DESC LIMIT 1 FOR UPDATE
                """)) {
            ps.setInt(1, ticketID);
            ps.setInt(2, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Ticket acquisition ledger is incomplete; refund refused.");
                orderID = rs.getInt(1);
                refund = rs.getBigDecimal(2);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE tickets SET status=?,cancelledAt=CURRENT_TIMESTAMP,cancelledBy=?,
                    cancellationType=?,refundOrderID=?,refundedAmount=?
                WHERE ticketID=? AND status='ACTIVE'
                """)) {
            ps.setString(1, "CUSTOMER".equals(actorType) ? "CANCELLED" : "REFUNDED");
            ps.setInt(2, actorID);
            ps.setString(3, actorType);
            ps.setInt(4, orderID);
            ps.setBigDecimal(5, refund);
            ps.setInt(6, ticketID);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Ticket was already refunded.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE resale_listings SET status='WITHDRAWN' WHERE ticketID=? AND status='ACTIVE'")) {
            ps.setInt(1, ticketID);
            ps.executeUpdate();
        }
        // Count acquisitions, not money: zero-price and partially refunded orders matter.
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE orders o SET
                  refundedAmount=(SELECT COALESCE(SUM(t.refundedAmount),0) FROM tickets t WHERE t.refundOrderID=o.orderID),
                  status=CASE WHEN NOT EXISTS (
                    SELECT 1 FROM ticket_ownership a
                    WHERE a.orderID=o.orderID AND NOT EXISTS (
                      SELECT 1 FROM tickets t WHERE t.ticketID=a.ticketID
                        AND t.refundOrderID=o.orderID AND t.status IN ('CANCELLED','REFUNDED')
                    )
                  ) THEN 'CANCELLED' ELSE 'COMPLETED' END
                WHERE o.orderID=?
                """)) {
            ps.setInt(1, orderID);
            ps.executeUpdate();
        }
    }
}
