package com.uptrail.support;

import org.testcontainers.mysql.MySQLContainer;

/**
 * The MySQL instance used by integration tests: a Testcontainers {@code mysql:8.4} container shared by the
 * whole test JVM, or an isolated database given by {@code UPTRAIL_TEST_DB_URL}. There is no in-memory
 * fallback; without Docker or that variable the tests fail with an explanation.
 */
public final class MySqlTestDatabase {

    private static final String IMAGE = "mysql:8.4";
    private static MySqlTestDatabase instance;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private MySqlTestDatabase(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static synchronized MySqlTestDatabase get() {
        if (instance == null) {
            instance = create();
        }
        return instance;
    }

    private static MySqlTestDatabase create() {
        String externalUrl = System.getenv("UPTRAIL_TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            return new MySqlTestDatabase(externalUrl, System.getenv("UPTRAIL_TEST_DB_USER"),
                    System.getenv("UPTRAIL_TEST_DB_PASSWORD"));
        }
        try {
            MySQLContainer container = new MySQLContainer(IMAGE)
                    .withDatabaseName("uptrail_test")
                    .withUsername("uptrail")
                    .withPassword("uptrail_test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
            container.start();
            String url = container.getJdbcUrl()
                    + (container.getJdbcUrl().contains("?") ? "&" : "?")
                    + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
            return new MySqlTestDatabase(url, container.getUsername(), container.getPassword());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Integration tests need MySQL. Start Docker so Testcontainers can run "
                    + IMAGE + ", or set UPTRAIL_TEST_DB_URL, UPTRAIL_TEST_DB_USER and UPTRAIL_TEST_DB_PASSWORD "
                    + "to an isolated, disposable test database.", e);
        }
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
