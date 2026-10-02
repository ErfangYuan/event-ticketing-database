package mytix.database.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import mytix.database.DatabaseLogistics;
import mytix.util.PasswordUtil;

public final class JdbcUserRepository implements UserRepository {

    private static final int MIN_AGE = 18;

    @Override
    public Optional<UserRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql =
                "SELECT userID, email, passwordHash, name, address, birthday, userType, creditCardNumber "
                        + "FROM users WHERE email = ? AND deletedAt IS NULL";
        try (Connection c = DatabaseLogistics.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalizeEmail(email));
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
        if (PasswordUtil.needsUpgrade(u.passwordHash())) {
            String upgradedHash = PasswordUtil.hash(password);
            try (Connection c = DatabaseLogistics.getConnection();
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE users SET passwordHash = ? WHERE userID = ? AND passwordHash = ? AND deletedAt IS NULL")) {
                ps.setString(1, upgradedHash);
                ps.setInt(2, u.userID());
                ps.setString(3, u.passwordHash());
                if (ps.executeUpdate() == 0) {
                    // A concurrent login/reset/deletion won the compare-and-set: verify current credentials.
                    Optional<UserRecord> current = findByEmail(email);
                    return current.filter(record -> PasswordUtil.matches(record.passwordHash(), password)
                            && (expectedType == null || expectedType.equalsIgnoreCase(record.userType())));
                }
                return Optional.of(new UserRecord(u.userID(), u.email(), upgradedHash, u.name(),
                        u.address(), u.birthday(), u.userType(), u.creditCardNumber()));
            } catch (SQLException e) {
                throw new IllegalStateException("Authentication could not be completed.", e);
            }
        }
        return found;
    }

    @Override
    public int createAccount(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Account details are required.");
        }
        requireText(request.email(), "Email", 255);
        requireText(request.name(), "Name", 128);
        requireText(request.address(), "Address", 255);
        requireText(request.plainPassword(), "Password", PasswordUtil.MAX_PASSWORD_LENGTH);
        requireText(request.userType(), "Account type", 16);
        if (request.birthday() == null) {
            throw new IllegalArgumentException("Birthday is required.");
        }
        String userType = request.userType().trim().toUpperCase(Locale.ROOT);
        if (!userType.equals("CUSTOMER") && !userType.equals("ORGANIZER")) {
            throw new IllegalArgumentException("userType must be CUSTOMER or ORGANIZER.");
        }
        int age = Period.between(request.birthday(), LocalDate.now(ZoneOffset.UTC)).getYears();
        if (age < MIN_AGE) {
            throw new IllegalArgumentException("Account holder must be at least " + MIN_AGE + " years old.");
        }
        boolean isCustomer = userType.equals("CUSTOMER");
        if (isCustomer) {
            requireText(request.cardNumber(), "Payment card", 32);
            requireText(request.cardExpiry(), "Card expiry", 7);
            if (!request.cardExpiry().matches("[0-9]{4}-[0-9]{2}")) {
                throw new IllegalArgumentException("Card expiry must use YYYY-MM.");
            }
            try {
                YearMonth.parse(request.cardExpiry());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("Card expiry must use a valid YYYY-MM.");
            }
        }
        String passwordHash = PasswordUtil.hash(request.plainPassword());

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
                ps.setString(1, normalizeEmail(request.email()));
                ps.setString(2, passwordHash);
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
        // Deletion must not upgrade a hash before a later refusal/rollback.
        Optional<UserRecord> auth = findByEmail(email);
        if (auth.isEmpty() || !PasswordUtil.matches(auth.get().passwordHash(), password)) {
            return false;
        }
        UserRecord user = auth.get();
        int userID = user.userID();

        Connection c = null;
        try {
            c = DatabaseLogistics.getConnection();
            DatabaseLogistics.begin(c);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT userID, email, passwordHash, name, address, birthday, userType, creditCardNumber "
                            + "FROM users WHERE userID = ? AND deletedAt IS NULL FOR UPDATE")) {
                ps.setInt(1, userID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !PasswordUtil.matches(rs.getString("passwordHash"), password)) {
                        DatabaseLogistics.rollbackQuietly(c);
                        return false;
                    }
                    user = map(rs);
                }
            }

            // Do not lock performances here: inventory operations lock performance then user.
            // Holding the user lock prevents new acquisitions/performances while these current reads run.
            List<String> unresolved = new ArrayList<>();
            if (existsWhere(c, "SELECT 1 FROM tickets t JOIN performances p ON p.performanceID = t.performanceID "
                    + "WHERE t.currentOwnerID = ? AND t.status = 'ACTIVE' "
                    + "AND TIMESTAMP(p.date, p.startTime) > CURRENT_TIMESTAMP", userID)) {
                unresolved.add("cancel or transfer tickets for future performances first");
            }
            if ("ORGANIZER".equalsIgnoreCase(user.userType()) && existsWhere(c,
                    "SELECT 1 FROM events e JOIN performances p ON p.eventID = e.eventID "
                            + "WHERE e.organizerID = ? AND p.status = 'SCHEDULED' "
                            + "AND TIMESTAMP(p.date, p.startTime) > CURRENT_TIMESTAMP", userID)) {
                unresolved.add("cancel future scheduled performances first");
            }
            if (!unresolved.isEmpty()) {
                throw new IllegalStateException("Cannot delete account yet; " + String.join("; ", unresolved) + ".");
            }

            boolean hasHistory = existsWhere(c, "SELECT 1 FROM orders WHERE customerID = ?", userID)
                    || existsWhere(c, "SELECT 1 FROM tickets WHERE currentOwnerID = ? OR cancelledBy = ?", userID, userID)
                    || existsWhere(c, "SELECT 1 FROM ticket_ownership WHERE ownerID = ?", userID)
                    || existsWhere(c, "SELECT 1 FROM resale_listings WHERE sellerID = ? OR buyerID = ?", userID, userID)
                    || existsWhere(c, "SELECT 1 FROM reviews WHERE customerID = ?", userID)
                    || existsWhere(c, "SELECT 1 FROM events WHERE organizerID = ?", userID);

            if (hasHistory) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE users SET email = ?, passwordHash = '!deleted!', name = ?, address = '[removed]', "
                                + "birthday = '1900-01-01', creditCardNumber = NULL, deletedAt = CURRENT_TIMESTAMP "
                                + "WHERE userID = ? AND deletedAt IS NULL")) {
                    ps.setString(1, "deleted+" + userID + "." + UUID.randomUUID() + "@invalid.example");
                    ps.setString(2, "Deleted account #" + userID);
                    ps.setInt(3, userID);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE userID = ?")) {
                    ps.setInt(1, userID);
                    ps.executeUpdate();
                }
            }

            if (user.creditCardNumber() != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM payment_cards WHERE cardNumber = ? "
                                + "AND NOT EXISTS (SELECT 1 FROM users WHERE creditCardNumber = ?) "
                                + "AND NOT EXISTS (SELECT 1 FROM orders WHERE cardNumber = ?)")) {
                    ps.setString(1, user.creditCardNumber());
                    ps.setString(2, user.creditCardNumber());
                    ps.setString(3, user.creditCardNumber());
                    ps.executeUpdate();
                }
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is required and must contain at most " + maxLength + " characters.");
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
