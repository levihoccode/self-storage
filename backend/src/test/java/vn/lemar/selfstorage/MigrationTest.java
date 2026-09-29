package vn.lemar.selfstorage;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy Flyway trên Postgres thật (Testcontainers) — schema/seed hỏng là CI đỏ ngay.
 * Máy không có Docker thì test tự skip (disabledWithoutDocker).
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void migrateTaoDuBangVaSeedDev() throws Exception {
        MigrateResult result = flyway().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(2);

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // 18 bảng nghiệp vụ + flyway_schema_history
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(19);
            }

            assertThat(count(connection, "roles")).isEqualTo(5);
            assertThat(count(connection, "accounts")).isEqualTo(7);
            assertThat(count(connection, "facilities")).isEqualTo(1);
            assertThat(count(connection, "unit_types")).isEqualTo(3);
            assertThat(count(connection, "storage_units")).isEqualTo(8);
            assertThat(count(connection, "policies")).isEqualTo(6);
            assertThat(count(connection, "account_facility_assignments")).isEqualTo(1);
        }

        // Chạy lại không thực thi migration mới (idempotent)
        assertThat(flyway().migrate().migrationsExecuted).isZero();
    }
}
