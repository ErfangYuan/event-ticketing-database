package mytix.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import mytix.config.DatabaseConfig;

public final class DatabaseLogistics {

    private static volatile Boolean reachable;

    private DatabaseLogistics() {}

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(
                DatabaseConfig.jdbcUrl(),
                DatabaseConfig.USER,
                DatabaseConfig.PASSWORD);
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return c;
    }

    
    public static boolean isConfigured() {
        Boolean cached = reachable;
        if (cached != null) {
            return cached;
        }
        try (Connection c = getConnection()) {
            reachable = c.isValid(3);
        } catch (SQLException e) {
            reachable = false;
        }
        return reachable;
    }

    public static void resetReachabilityCache() {
        reachable = null;
    }

    public static String statusMessage() {
        if (isConfigured()) {
            return "DB: connected";
        }
        return "DB: offline (set MYTIX_DB_* if needed)";
    }

    public static void begin(Connection c) throws SQLException {
        c.setAutoCommit(false);
    }

    public static void commit(Connection c) throws SQLException {
        c.commit();
        c.setAutoCommit(true);
    }

    public static void rollbackQuietly(Connection c) {
        if (c == null) {
            return;
        }
        try {
            c.rollback();
        } catch (SQLException ignored) {
            
        }
        try {
            c.setAutoCommit(true);
        } catch (SQLException ignored) {
            
        }
    }
}
