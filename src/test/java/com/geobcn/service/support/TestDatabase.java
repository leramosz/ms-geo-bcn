package com.geobcn.service.support;

import org.postgresql.PGConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Bases PostGIS efímeras para los tests. Si existe GEOBCN_TEST_DB_URL (más _USER y _PASSWORD, apuntando a un
 * Postgres con PostGIS) se usa ese servidor; si no, se levanta un contenedor postgis/postgis con Testcontainers.
 */
public final class TestDatabase {

    private static PostgreSQLContainer<?> container;

    private final String url;
    private final String user;
    private final String password;

    private TestDatabase(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public String url() { return url; }

    public String user() { return user; }

    public String password() { return password; }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Crea una base nueva con el esquema de db/migration aplicado. */
    public static TestDatabase createWithSchema() {
        try {
            String adminUrl = System.getenv("GEOBCN_TEST_DB_URL");
            String adminUser = env("GEOBCN_TEST_DB_USER", "geobcn");
            String adminPassword = env("GEOBCN_TEST_DB_PASSWORD", "geobcn");
            if (adminUrl == null) {
                synchronized (TestDatabase.class) {
                    if (container == null) {
                        container = new PostgreSQLContainer<>(
                                DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
                        container.start();
                    }
                }
                adminUrl = container.getJdbcUrl();
                adminUser = container.getUsername();
                adminPassword = container.getPassword();
            }
            String name = "geobcn_test_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection c = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                 Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE " + name);
            }
            TestDatabase db = new TestDatabase(adminUrl.replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"),
                    adminUser, adminPassword);
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute(Files.readString(Path.of("db/migration/V1__create_drinking_water_fountains.sql")));
            }
            return db;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la base PostGIS de test", e);
        }
    }

    /** Carga fonts_beure_2026.csv con el mismo SQL que db/load/load_fonts_beure.sql (COPY en lugar de \copy). */
    public void loadFountains() {
        try (Connection c = connect()) {
            String script = Files.readString(Path.of("db/load/load_fonts_beure.sql"));
            int copy = script.indexOf("\\copy");
            int end = script.indexOf('\n', copy);
            try (Statement s = c.createStatement()) {
                s.execute(script.substring(0, copy));
            }
            try (InputStream csv = Files.newInputStream(Path.of("db/data/fonts_beure_2026.csv"))) {
                c.unwrap(PGConnection.class).getCopyAPI().copyIn(
                        "COPY stg_fonts_beure FROM STDIN WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')", csv);
            }
            try (Statement s = c.createStatement()) {
                s.execute(script.substring(end));
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar fonts_beure_2026.csv", e);
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null ? fallback : v;
    }
}
