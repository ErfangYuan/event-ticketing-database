package mytix.database.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mytix.database.DatabaseLogistics;

public final class JdbcBookingService implements BookingService {

    private static final int CANCEL_MIN_DAYS_BEFORE = 7;
    private static final int REVIEW_WINDOW_DAYS = 60;

    @Override
    public int bookTickets(BookRequest request) {
        List<Integer> seatIDs = request.seatIDs() == null ? List.of() : request.seatIDs();
        Integer gaSectionID = request.gaSectionID();
        int gaQuantity = request.gaQuantity();
        if (seatIDs.isEmpty() && gaQuantity <= 0) {
            throw new IllegalArgumentException("Select at least one reserved seat or a GA quantity.");
        }
        if (gaQuantity > 0 && gaSectionID == null) {
            throw new IllegalArgumentException("gaSectionID is required when gaQuantity > 0.");
        }

        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            String cardNumber = fetchCustomerCard(c, request.customerID());

            List<SeatRow> seatRows = new ArrayList<>();
            for (int seatID : seatIDs) {
                seatRows.add(lockAndValidateSeat(c, request.performanceID(), seatID));
            }

            BigDecimal gaPrice = null;
            if (gaQuantity > 0) {
                gaPrice = lockAndValidateGa(c, request.performanceID(), gaSectionID, gaQuantity);
            }

            BigDecimal total = BigDecimal.ZERO;
            for (SeatRow row : seatRows) {
                total = total.add(row.price);
            }
            if (gaQuantity > 0) {
                total = total.add(gaPrice.multiply(BigDecimal.valueOf(gaQuantity)));
            }

            int orderID = insertOrder(c, request.customerID(), total, cardNumber);

            for (SeatRow row : seatRows) {
                int ticketID = insertTicket(
                        c, orderID, request.performanceID(), row.sectionID, row.seatID, request.customerID(), row.price);
                insertOwnership(c, ticketID, request.customerID(), "PURCHASE");
            }
            for (int i = 0; i < gaQuantity; i++) {
                int ticketID = insertTicket(
                        c, orderID, request.performanceID(), gaSectionID, null, request.customerID(), gaPrice);
                insertOwnership(c, ticketID, request.customerID(), "PURCHASE");
            }

            DatabaseLogistics.commit(c);
            return orderID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("bookTickets failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean cancelTicket(int customerID, int ticketID) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            String sql =
                    "SELECT t.currentOwnerID, t.status, p.date "
                            + "FROM tickets t JOIN performances p ON p.performanceID = t.performanceID "
                            + "WHERE t.ticketID = ? FOR UPDATE";
            int ownerID;
            String status;
            LocalDate perfDate;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Ticket not found: " + ticketID);
                    }
                    ownerID = rs.getInt(1);
                    status = rs.getString(2);
                    perfDate = rs.getDate(3).toLocalDate();
                }
            }
            if (ownerID != customerID) {
                throw new IllegalStateException("You are not the current owner of this ticket.");
            }
            if (!"ACTIVE".equals(status)) {
                throw new IllegalStateException("Only ACTIVE tickets can be cancelled (status=" + status + ").");
            }
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), perfDate);
            if (daysUntil <= CANCEL_MIN_DAYS_BEFORE) {
                throw new IllegalStateException(
                        "Cancellation window closed: must cancel more than "
                                + CANCEL_MIN_DAYS_BEFORE + " days before the performance.");
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE tickets SET status = 'CANCELLED', cancelledAt = NOW() WHERE ticketID = ?")) {
                ps.setInt(1, ticketID);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE resale_listings SET status = 'WITHDRAWN' WHERE ticketID = ? AND status = 'ACTIVE'")) {
                ps.setInt(1, ticketID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("cancelTicket failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public int listForResale(int customerID, int ticketID, BigDecimal askingPrice) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            String sql =
                    "SELECT t.currentOwnerID, t.status, t.faceValue, e.resaleCapRatio, p.status "
                            + "FROM tickets t "
                            + "JOIN performances p ON p.performanceID = t.performanceID "
                            + "JOIN events e ON e.eventID = p.eventID "
                            + "WHERE t.ticketID = ? FOR UPDATE";
            int ownerID;
            String ticketStatus;
            BigDecimal faceValue;
            BigDecimal resaleCapRatio;
            String perfStatus;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Ticket not found: " + ticketID);
                    }
                    ownerID = rs.getInt(1);
                    ticketStatus = rs.getString(2);
                    faceValue = rs.getBigDecimal(3);
                    resaleCapRatio = rs.getBigDecimal(4);
                    perfStatus = rs.getString(5);
                }
            }
            if (ownerID != customerID) {
                throw new IllegalStateException("You are not the current owner of this ticket.");
            }
            if (!"ACTIVE".equals(ticketStatus)) {
                throw new IllegalStateException("Only ACTIVE tickets can be listed (status=" + ticketStatus + ").");
            }
            if ("CANCELLED".equals(perfStatus)) {
                throw new IllegalStateException("Performance was cancelled; ticket cannot be listed.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM resale_listings WHERE ticketID = ? AND status = 'ACTIVE'")) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalStateException("Ticket already has an ACTIVE resale listing.");
                    }
                }
            }
            BigDecimal cap = faceValue.multiply(resaleCapRatio);
            if (askingPrice.compareTo(cap) > 0) {
                throw new IllegalStateException(
                        "Asking price " + askingPrice + " exceeds resale cap " + cap
                                + " (faceValue " + faceValue + " x cap ratio " + resaleCapRatio + ").");
            }

            int listingID;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO resale_listings (ticketID, sellerID, listingPrice, status) "
                            + "VALUES (?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ticketID);
                ps.setInt(2, customerID);
                ps.setBigDecimal(3, askingPrice);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    listingID = keys.getInt(1);
                }
            }

            DatabaseLogistics.commit(c);
            return listingID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("listForResale failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean withdrawListing(int customerID, int listingID) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            int sellerID;
            String status;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sellerID, status FROM resale_listings WHERE listingID = ? FOR UPDATE")) {
                ps.setInt(1, listingID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Listing not found: " + listingID);
                    }
                    sellerID = rs.getInt(1);
                    status = rs.getString(2);
                }
            }
            if (sellerID != customerID) {
                throw new IllegalStateException("You are not the seller of this listing.");
            }
            if (!"ACTIVE".equals(status)) {
                throw new IllegalStateException("Only ACTIVE listings can be withdrawn (status=" + status + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE resale_listings SET status = 'WITHDRAWN' WHERE listingID = ?")) {
                ps.setInt(1, listingID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("withdrawListing failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public int buyResaleListing(int buyerID, int listingID) {
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            int sellerID;
            String listingStatus;
            int ticketID;
            BigDecimal listingPrice;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sellerID, status, ticketID, listingPrice FROM resale_listings "
                            + "WHERE listingID = ? FOR UPDATE")) {
                ps.setInt(1, listingID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Listing not found: " + listingID);
                    }
                    sellerID = rs.getInt(1);
                    listingStatus = rs.getString(2);
                    ticketID = rs.getInt(3);
                    listingPrice = rs.getBigDecimal(4);
                }
            }
            if (!"ACTIVE".equals(listingStatus)) {
                throw new IllegalStateException("Listing is not ACTIVE (status=" + listingStatus + ").");
            }
            if (sellerID == buyerID) {
                throw new IllegalStateException("You cannot buy your own listing.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status FROM tickets WHERE ticketID = ? FOR UPDATE")) {
                ps.setInt(1, ticketID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Ticket not found: " + ticketID);
                    }
                    if (!"ACTIVE".equals(rs.getString(1))) {
                        throw new IllegalStateException("Ticket is no longer ACTIVE.");
                    }
                }
            }

            String buyerCard = fetchCustomerCard(c, buyerID);

            int orderID;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (customerID, totalAmount, cardNumber, status) "
                            + "VALUES (?, ?, ?, 'COMPLETED')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, buyerID);
                ps.setBigDecimal(2, listingPrice);
                ps.setString(3, buyerCard);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    orderID = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE tickets SET currentOwnerID = ?, orderID = ? WHERE ticketID = ?")) {
                ps.setInt(1, buyerID);
                ps.setInt(2, orderID);
                ps.setInt(3, ticketID);
                ps.executeUpdate();
            }
            insertOwnership(c, ticketID, buyerID, "RESALE");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE resale_listings SET status = 'SOLD', buyerID = ?, soldAt = NOW() WHERE listingID = ?")) {
                ps.setInt(1, buyerID);
                ps.setInt(2, listingID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return orderID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("buyResaleListing failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public void writeReview(
            int customerID, int performanceID, int eventRating, int venueRating, String comment) {
        if (eventRating < 1 || eventRating > 5 || venueRating < 1 || venueRating > 5) {
            throw new IllegalArgumentException("Ratings must be between 1 and 5.");
        }
        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            String eligibilitySql =
                    "SELECT 1 FROM ticket_ownership tow "
                            + "JOIN tickets t ON t.ticketID = tow.ticketID "
                            + "WHERE tow.ownerID = ? AND t.performanceID = ? AND t.status = 'ACTIVE' LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(eligibilitySql)) {
                ps.setInt(1, customerID);
                ps.setInt(2, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("No eligible (held, non-cancelled) ticket for this performance.");
                    }
                }
            }

            LocalDate perfDate;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT date FROM performances WHERE performanceID = ?")) {
                ps.setInt(1, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Performance not found: " + performanceID);
                    }
                    perfDate = rs.getDate(1).toLocalDate();
                }
            }
            LocalDate today = LocalDate.now();
            if (!perfDate.isBefore(today)) {
                throw new IllegalStateException("Performance has not ended yet.");
            }
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(perfDate, today);
            if (daysSince > REVIEW_WINDOW_DAYS) {
                throw new IllegalStateException(
                        "Review window closed: more than " + REVIEW_WINDOW_DAYS + " days since the performance.");
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM reviews WHERE customerID = ? AND performanceID = ?")) {
                ps.setInt(1, customerID);
                ps.setInt(2, performanceID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalStateException("You already reviewed this performance.");
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO reviews (customerID, performanceID, eventRating, venueRating, commentText) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, customerID);
                ps.setInt(2, performanceID);
                ps.setInt(3, eventRating);
                ps.setInt(4, venueRating);
                ps.setString(5, comment);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("writeReview failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    private static String fetchCustomerCard(Connection c, int customerID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT creditCardNumber FROM users WHERE userID = ?")) {
            ps.setInt(1, customerID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Customer not found: " + customerID);
                }
                String card = rs.getString(1);
                if (card == null) {
                    throw new IllegalStateException("Customer has no payment card on file.");
                }
                return card;
            }
        }
    }

    
    private static SeatRow lockAndValidateSeat(Connection c, int performanceID, int seatID) throws SQLException {
        String sql =
                "SELECT se.seatID, se.sectionID, sec.isGeneralAdmission, pt.price "
                        + "FROM seats se "
                        + "JOIN sections sec ON sec.sectionID = se.sectionID "
                        + "JOIN performance_section_tiers pst "
                        + "    ON pst.sectionID = sec.sectionID AND pst.performanceID = ? "
                        + "JOIN price_tiers pt ON pt.tierID = pst.tierID "
                        + "WHERE se.seatID = ? FOR UPDATE";
        int sectionID;
        boolean isGa;
        BigDecimal price;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, performanceID);
            ps.setInt(2, seatID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Seat " + seatID + " is not part of this performance's venue.");
                }
                sectionID = rs.getInt(2);
                isGa = rs.getBoolean(3);
                price = rs.getBigDecimal(4);
            }
        }
        if (isGa) {
            throw new IllegalStateException("Seat " + seatID + " belongs to a general-admission section.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM blocked_seats WHERE performanceID = ? AND seatID = ?")) {
            ps.setInt(1, performanceID);
            ps.setInt(2, seatID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Seat " + seatID + " is blocked by the organizer.");
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM tickets WHERE performanceID = ? AND seatID = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, performanceID);
            ps.setInt(2, seatID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("Seat " + seatID + " is already sold.");
                }
            }
        }
        return new SeatRow(seatID, sectionID, price);
    }

    
    private static BigDecimal lockAndValidateGa(
            Connection c, int performanceID, int gaSectionID, int quantity) throws SQLException {
        String sql =
                "SELECT sec.standingCapacity, pt.price "
                        + "FROM sections sec "
                        + "JOIN performance_section_tiers pst "
                        + "    ON pst.sectionID = sec.sectionID AND pst.performanceID = ? "
                        + "JOIN price_tiers pt ON pt.tierID = pst.tierID "
                        + "WHERE sec.sectionID = ? AND sec.isGeneralAdmission = 1 FOR UPDATE";
        int capacity;
        BigDecimal price;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, performanceID);
            ps.setInt(2, gaSectionID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Section " + gaSectionID + " is not a GA section for this performance.");
                }
                capacity = rs.getInt(1);
                price = rs.getBigDecimal(2);
            }
        }
        int sold;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM tickets WHERE performanceID = ? AND sectionID = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, performanceID);
            ps.setInt(2, gaSectionID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                sold = rs.getInt(1);
            }
        }
        int available = capacity - sold;
        if (quantity > available) {
            throw new IllegalStateException(
                    "Only " + available + " GA ticket(s) remain in section " + gaSectionID + ".");
        }
        return price;
    }

    private static int insertOrder(Connection c, int customerID, BigDecimal total, String cardNumber)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (customerID, totalAmount, cardNumber, status) VALUES (?, ?, ?, 'COMPLETED')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerID);
            ps.setBigDecimal(2, total);
            ps.setString(3, cardNumber);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static int insertTicket(
            Connection c,
            int orderID,
            int performanceID,
            int sectionID,
            Integer seatID,
            int ownerID,
            BigDecimal faceValue)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tickets (orderID, performanceID, sectionID, seatID, currentOwnerID, faceValue, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderID);
            ps.setInt(2, performanceID);
            ps.setInt(3, sectionID);
            if (seatID == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, seatID);
            }
            ps.setInt(5, ownerID);
            ps.setBigDecimal(6, faceValue);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                throw new IllegalStateException("Seat was just booked by another customer; please retry.", e);
            }
            throw e;
        }
    }

    private static void insertOwnership(Connection c, int ticketID, int ownerID, String source)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ticket_ownership (ticketID, ownerID, source) VALUES (?, ?, ?)")) {
            ps.setInt(1, ticketID);
            ps.setInt(2, ownerID);
            ps.setString(3, source);
            ps.executeUpdate();
        }
    }

    private static boolean isDuplicateKey(SQLException e) {
        return "23000".equals(e.getSQLState()) || e.getErrorCode() == 1062;
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

    private record SeatRow(int seatID, int sectionID, BigDecimal price) {}
}
