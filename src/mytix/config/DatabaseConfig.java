package mytix.config;

public final class DatabaseConfig {

    public static final String HOST = env("MYTIX_DB_HOST", "127.0.0.1");
    public static final int PORT = Integer.parseInt(env("MYTIX_DB_PORT", "3306"));
    public static final String DATABASE = env("MYTIX_DB_NAME", "mytix");
    public static final String USER = env("MYTIX_DB_USER", "root");
    public static final String PASSWORD = env("MYTIX_DB_PASSWORD", "");

    private DatabaseConfig() {}

    public static String jdbcUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
