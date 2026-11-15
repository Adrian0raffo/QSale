package com.example.qsale;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TravelEstimateMigrationTest extends AbstractContainerBaseTest {
    @Test
    void upgradesAnExistingSchemaWithoutInventingTheSourceOfStoredMeasurements() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = postgresqlContainer.getJdbcUrl();
        String username = postgresqlContainer.getUsername();
        String password = postgresqlContainer.getPassword();
        Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users (id, created_at, email, name, password, role) "
                        + "VALUES (1, now(), 'upgrade@qsale.test', 'Member', 'test-only', 'USER')");
                statement.executeUpdate("INSERT INTO locations (id, latitude, longitude) VALUES (1, -12, -77)");
                statement.executeUpdate("INSERT INTO plans (id, created_at, feasibility_score, feasibility_status, "
                        + "invite_code, min_participants, name, status, type, version, creator_id) "
                        + "VALUES (1, now(), 0, 'NOT_VIABLE', 'UPGRADE1', 2, 'Existing plan', 'OPEN', 'DINNER', 0, 1)");
                statement.executeUpdate("INSERT INTO plan_options (id, label, selected, type, version, plan_id, "
                        + "proposed_by_id, location_id, avg_distance_km, avg_travel_minutes) "
                        + "VALUES (1, 'Existing place', false, 'PLACE', 0, 1, 1, 1, 4.5, 12)");
                statement.executeUpdate("INSERT INTO plan_options (id, label, selected, type, version, plan_id, proposed_by_id, starts_at, ends_at) "
                        + "VALUES (2, 'Existing date', false, 'DATE', 0, 1, 1, '2027-02-01 18:30:00', '2027-02-02 12:00:00')");
                statement.executeUpdate("UPDATE plans SET status = 'CLOSED' WHERE id = 1");
            }
        }
        Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema).load().migrate();
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT * FROM plan_options ORDER BY id")) {
                assertTrue(result.next());
                assertEquals("READY", result.getString("estimate_status"));
                assertEquals("UNKNOWN", result.getString("estimate_source"));
                assertEquals(4.5, result.getDouble("avg_distance_km"));
                assertEquals(12, result.getInt("avg_travel_minutes"));
                assertNull(result.getTimestamp("estimate_updated_at"));
                assertTrue(result.next());
                assertEquals("NOT_APPLICABLE", result.getString("estimate_status"));
                assertEquals("2027-02-01", result.getDate("starts_on").toString());
                assertEquals("2027-02-02", result.getDate("ends_on").toString());
                assertEquals("18:30", result.getTimestamp("starts_at").toLocalDateTime().toLocalTime().toString());
                assertFalse(result.next());
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT agreement_version FROM plans WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }
}
