package mytix.database.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mytix.database.DatabaseLogistics;
import mytix.util.PasswordUtil;

public final class JdbcUserRepository implements UserRepository {

    private static final int MIN_AGE = 18;

    @Override
    public Optional<UserRecord> findByEmail(String email) {
        String sql =
                "SELECT userID, email, passwordHash, name, address, birthday, userType, creditCardNumber "
                        + "FROM users WHERE email = ?";
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findByEmail failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<UserRecord> authenticate(String email, String password, String expectedType) {
        Optional<UserRecord> found = findByEmail(email);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserRecord u = found.get();
        if (!PasswordUtil.matches(u.passwordHash(), password)) {
            return Optional.empty();
        }
        if (expectedType != null && !expectedType.equalsIgnoreCase(u.userType())) {
            return Optional.empty();
        }
        return found;
    }

    @Override
    public int createAccount(CreateUserRequest request) {
        String userType = request.userType().toUpperCase();
        if (!userType.equals("CUSTOMER") && !userType.equals("ORGANIZER")) {
            throw new IllegalArgumentException("userType must be CUSTOMER or ORGANIZER.");
        }
        int age = Period.between(request.birthday(), LocalDate.now()).getYears();
        if (age < MIN_AGE) {
            throw new IllegalArgumentException("Account holder must be at least " + MIN_AGE + " years old.");
        }
        boolean isCustomer = userType.equals("CUSTOMER");
        if (isCustomer && (request.cardNumber() == null || request.cardNumber().isBlank())) {
            throw new IllegalArgumentException("CUSTOMER accounts require a payment card.");
        }

        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            if (isCustomer) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO payment_cards (cardNumber, cardExpiry) VALUES (?, ?) "
                                + "ON DUPLICATE KEY UPDATE cardExpiry = ?")) {
                    ps.setString(1, request.cardNumber());
                    ps.setString(2, request.cardExpiry());
                    ps.setString(3, request.cardExpiry());
                    ps.executeUpdate();
                }
            }

            int userID;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (email, passwordHash, name, address, birthday, userType, creditCardNumber) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, request.email());
                ps.setString(2, PasswordUtil.sha256Hex(request.plainPassword()));
                ps.setString(3, request.name());
                ps.setString(4, request.address());
                ps.setDate(5, java.sql.Date.valueOf(request.birthday()));
                ps.setString(6, userType);
                if (isCustomer) {
                    ps.setString(7, request.cardNumber());
                } else {
                    ps.setNull(7, java.sql.Types.VARCHAR);
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    userID = keys.getInt(1);
                }
            }

            DatabaseLogistics.commit(c);
            return userID;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                throw new IllegalStateException("Email already registered: " + request.email(), e);
            }
            throw new IllegalStateException("createAccount failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public boolean deleteAccount(String email, String password) {
        Optional<UserRecord> auth = authenticate(email, password, null);
        if (auth.isEmpty()) {
            return false;
        }
        UserRecord user = auth.get();
        int userID = user.userID();
        boolean isOrganizer = "ORGANIZER".equalsIgnoreCase(user.userType());

        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            List<String> reasons = new ArrayList<>();
            if (existsWhere(c, "SELECT 1 FROM orders WHERE customerID = ?", userID)) {
                reasons.add("has order history");
            }
            if (existsWhere(c, "SELECT 1 FROM tickets WHERE currentOwnerID = ?", userID)) {
                reasons.add("currently owns tickets");
            }
            if (existsWhere(c, "SELECT 1 FROM ticket_ownership WHERE ownerID = ?", userID)) {
                reasons.add("has ticket ownership history");
            }
            if (existsWhere(
                    c, "SELECT 1 FROM resale_listings WHERE sellerID = ? OR buyerID = ?", userID, userID)) {
                reasons.add("has resale listing history");
            }
            if (existsWhere(c, "SELECT 1 FROM reviews WHERE customerID = ?", userID)) {
                reasons.add("has written reviews");
            }
            if (isOrganizer && existsWhere(c, "SELECT 1 FROM events WHERE organizerID = ?", userID)) {
                reasons.add("organizes events");
            }

            if (!reasons.isEmpty()) {
                DatabaseLogistics.rollbackQuietly(c);
                throw new IllegalStateException(
                        "Cannot delete account; existing history: " + String.join(", ", reasons));
            }

            try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE userID = ?")) {
                ps.setInt(1, userID);
                ps.executeUpdate();
            }

            DatabaseLogistics.commit(c);
            return true;
        } catch (SQLException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw new IllegalStateException("deleteAccount failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            DatabaseLogistics.rollbackQuietly(c);
            throw e;
        } finally {
            closeQuietly(c);
        }
    }

    private static boolean existsWhere(Connection c, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
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

    private static UserRecord map(ResultSet rs) throws SQLException {
        LocalDate bday = rs.getDate("birthday").toLocalDate();
        String card = rs.getString("creditCardNumber");
        return new UserRecord(
                rs.getInt("userID"),
                rs.getString("email"),
                rs.getString("passwordHash"),
                rs.getString("name"),
                rs.getString("address"),
                bday,
                rs.getString("userType"),
                card);
    }
}
