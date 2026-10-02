package mytix.database.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import mytix.database.DatabaseLogistics;
import mytix.database.query.ScalperPolicy;
import mytix.nlp.ReviewNounPhraseProjection;

public final class JdbcBookingService implements BookingService {
    @FunctionalInterface
    private interface Work<T> { T run(Connection c) throws SQLException; }

    private static <T> T transaction(String action, Work<T> work) {
        try (Connection c = DatabaseLogistics.getConnection()) {
            DatabaseLogistics.begin(c);
            try {
                T result = work.run(c);
                DatabaseLogistics.commit(c);
                return result;
            } catch (SQLException | RuntimeException e) {
                DatabaseLogistics.rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(action + " failed; no changes were committed.", e);
        }
    }

    @Override
    public int bookTickets(BookRequest request) {
        if (request == null) throw new IllegalArgumentException("Booking is required.");
        TreeSet<Integer> selected = new TreeSet<>();
        if (request.seatIDs() != null) {
            for (Integer id : request.seatIDs()) {
                if (id == null || id <= 0) throw new IllegalArgumentException("Invalid seat ID.");
                selected.add(id);
            }
        }
        int quantity = request.gaQuantity();
        if (quantity < 0 || (selected.isEmpty() && quantity == 0)) {
            throw new IllegalArgumentException("Select seats or a positive GA quantity; quantities cannot be negative.");
        }
        if (quantity > 0 && request.gaSectionID() == null) throw new IllegalArgumentException("GA section is required.");
        return transaction("Booking", c -> {
            var performance = TicketLedger.lockPerformance(c, request.performanceID());
            ScalperPolicy.requireTradingAllowed(c, request.customerID());
            performance.requireUpcoming(c);
            Card card = customerCard(c, request.customerID());
            List<Seat> seats = new ArrayList<>();
            for (int seatID : selected) seats.add(availableSeat(c, performance, seatID));
            BigDecimal gaPrice = quantity > 0 ? availableGa(c, performance, request.gaSectionID(), quantity) : BigDecimal.ZERO;
            BigDecimal total = gaPrice.multiply(BigDecimal.valueOf(quantity));
            for (Seat seat : seats) total = total.add(seat.price());
            int order = insertOrder(c, request.customerID(), request.performanceID(), "PRIMARY", total, card);
            for (Seat seat : seats) {
                int ticket = insertTicket(c, order, request.performanceID(), seat.sectionID(), seat.id(), request.customerID(), seat.price());
                insertOwnership(c, ticket, request.customerID(), order, seat.price(), "PURCHASE");
            }
            for (int i = 0; i < quantity; i++) {
                int ticket = insertTicket(c, order, request.performanceID(), request.gaSectionID(), null, request.customerID(), gaPrice);
                insertOwnership(c, ticket, request.customerID(), order, gaPrice, "PURCHASE");
            }
            return order;
        });
    }

    @Override
    public boolean cancelTicket(int customerID, int ticketID) {
        return transaction("Cancellation", c -> {
            var performance = TicketLedger.lockPerformance(c, TicketLedger.ticketPerformance(c, ticketID));
            TicketLedger.requireRole(c, customerID, "CUSTOMER");
            requireOwner(ticket(c, ticketID), customerID);
            if (!"SCHEDULED".equals(performance.status()) || performance.startsAt().minusDays(7).isBefore(TicketLedger.currentTime(c))) {
                throw new IllegalStateException("Cancellation window closed: at least seven days must remain before the start time.");
            }
            TicketLedger.refundTicket(c, ticketID, customerID, "CUSTOMER");
            return true;
        });
    }

    @Override
    public int listForResale(int customerID, int ticketID, BigDecimal askingPrice) {
        BigDecimal price = TicketLedger.money(askingPrice, "Asking price");
        return transaction("Resale listing", c -> {
            var performance = TicketLedger.lockPerformance(c, TicketLedger.ticketPerformance(c, ticketID));
            ScalperPolicy.requireTradingAllowed(c, customerID);
            performance.requireUpcoming(c);
            Ticket item = ticket(c, ticketID);
            requireOwner(item, customerID);
            BigDecimal ratio;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT e.resaleCapRatio FROM events e JOIN performances p ON p.eventID=e.eventID
                    WHERE p.performanceID=?
                    """)) {
                ps.setInt(1, performance.id());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); ratio = rs.getBigDecimal(1); }
            }
            BigDecimal cap = item.faceValue().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            if (price.compareTo(cap) > 0) throw new IllegalStateException("Asking price exceeds resale cap " + cap + ".");
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM resale_listings WHERE ticketID=? AND status='ACTIVE'")) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException("Ticket already has an ACTIVE resale listing.");
                }
            }
            int ownership;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT ownershipID,ownerID FROM ticket_ownership WHERE ticketID=?
                    ORDER BY acquiredAt DESC,ownershipID DESC LIMIT 1
                    """)) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(2) != customerID) throw new IllegalStateException("Ticket ownership ledger is inconsistent.");
                    ownership = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO resale_listings(ticketID,sellerID,ownershipID,capRatio,listingPrice,status)
                    VALUES(?,?,?,?,?,'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ticketID); ps.setInt(2, customerID); ps.setInt(3, ownership);
                ps.setBigDecimal(4, ratio); ps.setBigDecimal(5, price); ps.executeUpdate();
                return generatedKey(ps);
            }
        });
    }

    @Override
    public boolean withdrawListing(int customerID, int listingID) {
        return transaction("Listing withdrawal", c -> {
            int ticketID = listingTicket(c, listingID);
            TicketLedger.lockPerformance(c, TicketLedger.ticketPerformance(c, ticketID));
            TicketLedger.requireRole(c, customerID, "CUSTOMER");
            Listing listing = listing(c, listingID);
            if (listing.sellerID() != customerID) throw new IllegalStateException("You are not the seller of this listing.");
            if (!"ACTIVE".equals(listing.status())) throw new IllegalStateException("Only ACTIVE listings can be withdrawn.");
            try (PreparedStatement ps = c.prepareStatement("UPDATE resale_listings SET status='WITHDRAWN' WHERE listingID=?")) {
                ps.setInt(1, listingID); ps.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public int buyResaleListing(int buyerID, int listingID) {
        return transaction("Resale purchase", c -> {
            int ticketID = listingTicket(c, listingID);
            var performance = TicketLedger.lockPerformance(c, TicketLedger.ticketPerformance(c, ticketID));
            ScalperPolicy.requireTradingAllowed(c, buyerID);
            performance.requireUpcoming(c);
            Listing sale = listing(c, listingID);
            if (!"ACTIVE".equals(sale.status())) throw new IllegalStateException("Listing is no longer ACTIVE.");
            if (sale.sellerID() == buyerID) throw new IllegalStateException("You cannot buy your own listing.");
            requireOwner(ticket(c, ticketID), sale.sellerID());
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT 1 FROM ticket_ownership a WHERE a.ownershipID=? AND a.ticketID=? AND a.ownerID=?
                      AND NOT EXISTS (SELECT 1 FROM ticket_ownership later WHERE later.ticketID=a.ticketID
                        AND (later.acquiredAt>a.acquiredAt OR (later.acquiredAt=a.acquiredAt AND later.ownershipID>a.ownershipID)))
                    """)) {
                ps.setInt(1, sale.ownershipID()); ps.setInt(2, ticketID); ps.setInt(3, sale.sellerID());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Listing no longer matches the current ownership acquisition.");
                }
            }
            int order = insertOrder(c, buyerID, performance.id(), "RESALE", sale.price(), customerCard(c, buyerID));
            try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET currentOwnerID=? WHERE ticketID=?")) {
                ps.setInt(1, buyerID); ps.setInt(2, ticketID); ps.executeUpdate();
            }
            insertOwnership(c, ticketID, buyerID, order, sale.price(), "RESALE");
            try (PreparedStatement ps = c.prepareStatement("UPDATE resale_listings SET status='SOLD',buyerID=?,soldAt=CURRENT_TIMESTAMP WHERE listingID=?")) {
                ps.setInt(1, buyerID); ps.setInt(2, listingID); ps.executeUpdate();
            }
            return order;
        });
    }

    @Override
    public void writeReview(int customerID, int performanceID, int eventRating, int venueRating, String comment) {
        if (eventRating < 1 || eventRating > 5 || venueRating < 1 || venueRating > 5) throw new IllegalArgumentException("Ratings must be between 1 and 5.");
        if (comment == null || comment.isBlank()) throw new IllegalArgumentException("A comment is required.");
        transaction("Review", c -> {
            var performance = TicketLedger.lockPerformance(c, performanceID);
            TicketLedger.requireRole(c, customerID, "CUSTOMER");
            var now = TicketLedger.currentTime(c);
            if (!"SCHEDULED".equals(performance.status()) || performance.endsAt().isAfter(now)
                    || performance.endsAt().isBefore(now.minusDays(365))) {
                throw new IllegalStateException("Review requires a performance completed within the last 365 days.");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT 1 FROM ticket_ownership a JOIN tickets t ON t.ticketID=a.ticketID
                    JOIN performances p ON p.performanceID=t.performanceID
                    WHERE a.ownerID=? AND t.performanceID=? AND t.status='ACTIVE'
                      AND a.acquiredAt<=TIMESTAMP(p.date,p.endTime)
                      AND NOT EXISTS (
                        SELECT 1 FROM ticket_ownership later WHERE later.ticketID=a.ticketID
                          AND later.acquiredAt<=TIMESTAMP(p.date,p.endTime)
                          AND (later.acquiredAt>a.acquiredAt OR (later.acquiredAt=a.acquiredAt AND later.ownershipID>a.ownershipID))
                      ) LIMIT 1
                    """)) {
                ps.setInt(1, customerID); ps.setInt(2, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("You did not hold a valid ticket when this performance ended.");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM reviews WHERE customerID=? AND performanceID=?")) {
                ps.setInt(1, customerID); ps.setInt(2, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException("You already reviewed this performance.");
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO reviews(customerID,performanceID,eventRating,venueRating,commentText) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, customerID); ps.setInt(2, performanceID); ps.setInt(3, eventRating); ps.setInt(4, venueRating); ps.setString(5, comment);
                ps.executeUpdate();
                ReviewNounPhraseProjection.replace(c, generatedKey(ps), comment);
            }
            return null;
        });
    }

    private static Seat availableSeat(Connection c, TicketLedger.Performance p, int seatID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT se.sectionID,pt.price FROM seats se JOIN sections sec ON sec.sectionID=se.sectionID
                JOIN performance_section_tiers pst ON pst.sectionID=sec.sectionID AND pst.performanceID=?
                JOIN price_tiers pt ON pt.tierID=pst.tierID AND pt.performanceID=pst.performanceID
                WHERE se.seatID=? AND sec.venueID=? AND sec.isGeneralAdmission=0
                  AND NOT EXISTS(SELECT 1 FROM tickets t WHERE t.performanceID=? AND t.seatID=se.seatID AND t.status='ACTIVE')
                  AND NOT EXISTS(SELECT 1 FROM blocked_seats bs WHERE bs.performanceID=? AND bs.seatID=se.seatID)
                """)) {
            ps.setInt(1, p.id()); ps.setInt(2, seatID); ps.setInt(3, p.venueID()); ps.setInt(4, p.id()); ps.setInt(5, p.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Requested seat is sold, blocked, or outside this performance.");
                return new Seat(seatID, rs.getInt(1), rs.getBigDecimal(2));
            }
        }
    }

    private static BigDecimal availableGa(Connection c, TicketLedger.Performance p, int section, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT sec.standingCapacity,pt.price,
                  (SELECT COUNT(*) FROM tickets t WHERE t.performanceID=? AND t.sectionID=sec.sectionID AND t.status='ACTIVE')
                FROM sections sec JOIN performance_section_tiers pst ON pst.sectionID=sec.sectionID AND pst.performanceID=?
                JOIN price_tiers pt ON pt.tierID=pst.tierID AND pt.performanceID=pst.performanceID
                WHERE sec.sectionID=? AND sec.venueID=? AND sec.isGeneralAdmission=1
                """)) {
            ps.setInt(1, p.id()); ps.setInt(2, p.id()); ps.setInt(3, section); ps.setInt(4, p.venueID());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Section is not GA inventory for this performance.");
                if (quantity > rs.getInt(1) - rs.getInt(3)) throw new IllegalStateException("Requested GA quantity exceeds remaining capacity.");
                return rs.getBigDecimal(2);
            }
        }
    }

    private static Card customerCard(Connection c, int customerID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT pc.cardNumber,pc.cardExpiry FROM users u JOIN payment_cards pc ON pc.cardNumber=u.creditCardNumber
                WHERE u.userID=? AND u.userType='CUSTOMER' AND u.deletedAt IS NULL
                """)) {
            ps.setInt(1, customerID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Customer payment information is missing.");
                return new Card(rs.getString(1), rs.getString(2));
            }
        }
    }

    private static int insertOrder(Connection c, int customer, int performance, String type, BigDecimal total, Card card) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO orders(customerID,performanceID,orderType,totalAmount,cardNumber,cardExpiry,status)
                VALUES(?,?,?,?,?,?,'COMPLETED')
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customer); ps.setInt(2, performance); ps.setString(3, type); ps.setBigDecimal(4, total);
            ps.setString(5, card.number()); ps.setString(6, card.expiry()); ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static int insertTicket(Connection c, int order, int performance, int section, Integer seat, int owner, BigDecimal face) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tickets(orderID,performanceID,sectionID,seatID,currentOwnerID,faceValue,status)
                VALUES(?,?,?,?,?,?,'ACTIVE')
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order); ps.setInt(2, performance); ps.setInt(3, section);
            if (seat == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, seat);
            ps.setInt(5, owner); ps.setBigDecimal(6, face); ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static void insertOwnership(Connection c, int ticket, int owner, int order, BigDecimal paid, String source) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_ownership(ticketID,ownerID,orderID,amountPaid,source) VALUES(?,?,?,?,?)")) {
            ps.setInt(1, ticket); ps.setInt(2, owner); ps.setInt(3, order); ps.setBigDecimal(4, paid); ps.setString(5, source); ps.executeUpdate();
        }
    }

    private static int generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new SQLException("No generated identifier returned.");
            return rs.getInt(1);
        }
    }

    private static int listingTicket(Connection c, int listingID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ticketID FROM resale_listings WHERE listingID=?")) {
            ps.setInt(1, listingID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Listing not found.");
                return rs.getInt(1);
            }
        }
    }

    private static Listing listing(Connection c, int listingID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT sellerID,status,listingPrice,ownershipID FROM resale_listings WHERE listingID=? FOR UPDATE")) {
            ps.setInt(1, listingID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Listing not found.");
                return new Listing(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4));
            }
        }
    }

    private static Ticket ticket(Connection c, int ticketID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT currentOwnerID,status,faceValue FROM tickets WHERE ticketID=? FOR UPDATE")) {
            ps.setInt(1, ticketID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Ticket not found.");
                return new Ticket(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3));
            }
        }
    }

    private static void requireOwner(Ticket ticket, int owner) {
        if (ticket.ownerID() != owner) throw new IllegalStateException("You are not the current owner of this ticket.");
        if (!"ACTIVE".equals(ticket.status())) throw new IllegalStateException("Ticket is no longer ACTIVE.");
    }

    private record Seat(int id, int sectionID, BigDecimal price) {}
    private record Card(String number, String expiry) {}
    private record Ticket(int ownerID, String status, BigDecimal faceValue) {}
    private record Listing(int sellerID, String status, BigDecimal price, int ownershipID) {}
}
