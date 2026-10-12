package vn.lemar.selfstorage.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.audit.application.AuditEntry;
import vn.lemar.selfstorage.audit.application.AuditRecorder;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.audit.domain.AuditLog;
import vn.lemar.selfstorage.audit.repository.AuditLogRepository;

/**
 * Ghi thật vào Postgres (Testcontainers): CHECK constraint V1 phải nhận đủ catalog — gồm
 * {@code ACCOUNT_REGISTERED} + {@code APPOINTMENT_CANCELED} + entity {@code Account} (vá drift
 * phát hiện khi đối chiếu) — và thao tác nhiều entity ghi nhiều bản ghi. Máy không có Docker
 * thì test tự skip.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AuditRecorderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditRecorder recorder;
    @Autowired
    private AuditLogRepository repository;

    @Test
    void recordsAccountRegisteredAgainstRealCheckConstraint() {
        recorder.record(AuditEntry.of(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT, "42"));

        List<AuditLog> logs = repository.findAll();
        assertThat(logs).hasSize(1);
        AuditLog saved = logs.get(0);
        assertThat(saved.getAction()).isEqualTo(AuditAction.ACCOUNT_REGISTERED);
        assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.ACCOUNT);
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getActorAccountId()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsAppointmentCanceledAgainstRealCheckConstraint() {
        recorder.record(AuditEntry.of(AuditAction.APPOINTMENT_CANCELED, AuditEntityType.APPOINTMENT, "7"));

        assertThat(repository.findAll())
                .extracting(AuditLog::getAction, AuditLog::getEntityId)
                .containsExactly(tuple(AuditAction.APPOINTMENT_CANCELED, "7"));
    }

    @Test
    void storesOldAndNewValueAsJsonb() {
        recorder.record(new AuditEntry(AuditAction.STORAGE_UNIT_RESERVED, AuditEntityType.STORAGE_UNIT, "3",
                Map.of("status", "Available"), Map.of("status", "Reserved"), null, null));

        AuditLog saved = repository.findAll().get(0);
        assertThat(saved.getOldValue().get("status").asText()).isEqualTo("Available");
        assertThat(saved.getNewValue().get("status").asText()).isEqualTo("Reserved");
    }

    @Test
    void multiEntityActionWritesOneRowPerEntity() {
        recorder.record(new AuditEntry(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.RENTAL_ORDER, "5",
                Map.of("status", "Pending"), Map.of("status", "Canceled"), "quá hạn thanh toán", 9L));
        recorder.record(new AuditEntry(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.STORAGE_UNIT, "3",
                Map.of("status", "Reserved"), Map.of("status", "Available"), null, 9L));
        recorder.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.PROPOSAL_FEEDBACK, "8"));
        recorder.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.RENTAL_CONTRACT, "1"));

        List<AuditLog> logs = repository.findAll();
        assertThat(logs).hasSize(4);
        assertThat(logs)
                .extracting(AuditLog::getEntityType, AuditLog::getEntityId)
                .containsExactlyInAnyOrder(
                        tuple(AuditEntityType.RENTAL_ORDER, "5"),
                        tuple(AuditEntityType.STORAGE_UNIT, "3"),
                        tuple(AuditEntityType.PROPOSAL_FEEDBACK, "8"),
                        tuple(AuditEntityType.RENTAL_CONTRACT, "1"));
        assertThat(logs.get(0).getReason()).isEqualTo("quá hạn thanh toán");
    }
}
