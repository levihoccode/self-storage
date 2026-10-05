package vn.lemar.selfstorage;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy Flyway trên Postgres thật (Testcontainers) — schema/seed hỏng là CI đỏ ngay.
 * Máy không có Docker thì test tự skip (disabledWithoutDocker).
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

    /** Bảng lõi phải tồn tại sau V1. Không đếm tổng số bảng — thêm bảng mới không nên làm đỏ test. */
    private static final List<String> CORE_TABLES = List.of(
            "roles", "accounts", "facilities", "account_facility_assignments",
            "permissions", "role_permissions",
            "unit_types", "policies", "storage_units",
            "rental_requests", "rental_orders", "rental_contracts", "invoices",
            "proposal_feedbacks", "appointments", "rental_appointments",
            "handover_records", "unit_access_keys", "payment_transactions", "audit_logs",
            "notifications", "order_notifications");

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

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, "public." + table);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    @Test
    void migrateCreatesAllTablesAndDevSeed() throws Exception {
        MigrateResult result = flyway().migrate();
        assertThat(result.migrationsExecuted).isPositive();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            for (String table : CORE_TABLES) {
                assertThat(tableExists(connection, table)).as(table).isTrue();
            }

            assertThat(count(connection, "notifications")).isZero();
            assertThat(count(connection, "roles")).isEqualTo(5);
            assertThat(count(connection, "accounts")).isEqualTo(8);
            assertThat(count(connection, "facilities")).isEqualTo(2);
            assertThat(count(connection, "unit_types")).isEqualTo(3);
            assertThat(count(connection, "storage_units")).isEqualTo(8);
            assertThat(count(connection, "policies")).isEqualTo(6);
            assertThat(count(connection, "account_facility_assignments")).isEqualTo(1);

            // #57: quan hệ FM–Facility 1–1 trên facilities.
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT count(*) FROM information_schema.columns"
                                 + " WHERE table_name = 'facilities' AND column_name = 'fm_account_id'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            // Seed dev: cả 2 facility đã gán FM (đủ để test cross-facility 403).
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT count(*) FROM facilities WHERE fm_account_id IS NOT NULL")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }

        // Chạy lại không thực thi migration mới (idempotent)
        assertThat(flyway().migrate().migrationsExecuted).isZero();
    }
}
