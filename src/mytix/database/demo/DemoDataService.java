package mytix.database.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mytix.config.DatabaseConfig;
import mytix.database.DatabaseLogistics;

/**
 * Explicit demonstration maintenance, reusable by CLI and an authenticated Web controller.
 * The controller must obtain its exclusive application-wide database gate and invalidate
 * existing sessions after success. The advisory lock also serializes independent loaders.
 */
public final class DemoDataService {
    public record Result(String database, LocalDate anchorDate, Map<String, Long> counts,
                         Map<String, Integer> scenarios, String customerEmail, String organizerEmail,
                         String demoPassword) {}

    public Result resetAndLoad() {
        guardConfiguredDatabase();
        DemoDataset data = DemoDataset.prepare(LocalDate.now(ZoneOffset.UTC));
        return replace(data);
    }

    /** Atomically removes project data while retaining the installed schema and its constraints. */
    public Result clear() {
        guardConfiguredDatabase();
        return replace(null);
    }

    private Result replace(DemoDataset data) {
        try (Connection connection = DatabaseLogistics.getConnection()) {
            verifySchema(connection);
            String lock = "mytix-demo-maintenance:" + DatabaseConfig.DATABASE;
            if (lock.length() > 64) throw new IllegalStateException("Configured database name is too long for the maintenance lock.");
            acquire(connection, lock);
            try {
                DatabaseLogistics.begin(connection);
                try {
                    List<String> deletion = new ArrayList<>(DemoDataset.INSERT_ORDER);
                    Collections.reverse(deletion);
                    try (Statement statement = connection.createStatement()) {
                        for (String table : deletion) statement.executeUpdate("DELETE FROM `" + table + "`");
                    }
                    if (data != null) insert(connection, data);
                    Map<String, Long> counts = counts(connection);
                    if (data != null && !counts.equals(data.counts())) {
                        throw new IllegalStateException("Stored demo counts do not match the prepared dataset.");
                    }
                    if (data == null && counts.values().stream().anyMatch(count -> count != 0)) {
                        throw new IllegalStateException("Project data could not be completely cleared.");
                    }
                    connection.commit();
                    return new Result(DatabaseConfig.DATABASE, data == null ? LocalDate.now(ZoneOffset.UTC) : data.date,
                            Collections.unmodifiableMap(counts), data == null ? Map.of() : Map.copyOf(data.scenarios),
                            data == null ? null : "customer003@demo.mytix.test",
                            data == null ? null : "organizer01@demo.mytix.test", data == null ? null : DemoDataset.PASSWORD);
                } catch (SQLException | RuntimeException error) {
                    DatabaseLogistics.rollbackQuietly(connection);
                    throw error;
                }
            } finally {
                release(connection, lock);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Demo maintenance failed; the data transaction was rolled back.", error);
        }
    }

    private static void guardConfiguredDatabase() {
        String database = DatabaseConfig.DATABASE;
        if (database == null || !database.matches("[A-Za-z0-9_]+")
                || Set.of("mysql", "sys", "information_schema", "performance_schema").contains(database.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Demo maintenance requires an explicitly selected project database.");
        }
    }

    private static void verifySchema(Connection connection) throws SQLException {
        Set<String> installed = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                installed.add(result.getString(1));
                if (!"InnoDB".equalsIgnoreCase(result.getString(2))) {
                    throw new IllegalStateException("Atomic demo maintenance requires InnoDB project tables.");
                }
            }
        }
        if (!installed.equals(new LinkedHashSet<>(DemoDataset.INSERT_ORDER))) {
            throw new IllegalStateException("Expected exactly the 22 MyTix project tables. Install the current sql/schema.sql first.");
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !DatabaseConfig.DATABASE.equals(result.getString(1))) {
                throw new IllegalStateException("Connected database differs from the configured project database.");
            }
        }
        // Fail before DELETE if an obsolete schema lacks the new lifecycle/history columns.
        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT deletedAt FROM users WHERE 1=0").close();
            statement.executeQuery("SELECT orderType,cardExpiry,refundedAmount FROM orders WHERE 1=0").close();
            statement.executeQuery("SELECT orderID,amountPaid FROM ticket_ownership WHERE 1=0").close();
            statement.executeQuery("SELECT ownershipID,capRatio FROM resale_listings WHERE 1=0").close();
            statement.executeQuery("SELECT cancelledBy,cancellationType,refundOrderID,refundedAmount FROM tickets WHERE 1=0").close();
        }
    }

    private static void acquire(Connection connection, String lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            statement.setString(1, lock);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Another demo maintenance operation is running.");
            }
        }
    }
    private static void release(Connection connection, String lock) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lock);
            statement.executeQuery().close();
        } catch (SQLException ignored) {
            // Closing the physical connection releases the advisory lock as well.
        }
    }

    private static void insert(Connection connection, DemoDataset data) throws SQLException {
        for (String table : DemoDataset.INSERT_ORDER) {
            List<Map<String, Object>> rows = data.tables.get(table);
            if (rows.isEmpty()) continue;
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            String names = String.join(",", columns.stream().map(name -> "`" + name + "`").toList());
            String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `" + table + "` (" + names + ") VALUES (" + placeholders + ")")) {
                for (Map<String, Object> row : rows) {
                    if (!row.keySet().equals(new LinkedHashSet<>(columns))) throw new IllegalStateException("Inconsistent demo row shape: " + table);
                    for (int i = 0; i < columns.size(); i++) statement.setObject(i + 1, row.get(columns.get(i)));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private static Map<String, Long> counts(Connection connection) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : DemoDataset.INSERT_ORDER) {
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    result.next(); counts.put(table, result.getLong(1));
                }
            }
        }
        return counts;
    }
}
