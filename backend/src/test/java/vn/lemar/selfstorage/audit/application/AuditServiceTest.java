package vn.lemar.selfstorage.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;
import vn.lemar.selfstorage.audit.domain.AuditLog;
import vn.lemar.selfstorage.audit.repository.AuditLogRepository;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService service = new AuditService(repository, new ObjectMapper());

    @Test
    void recordsMinimalEntry() {
        service.record(AuditEntry.of(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT, "42"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.ACCOUNT_REGISTERED);
        assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.ACCOUNT);
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getActorAccountId()).isNull();
        assertThat(saved.getOldValue()).isNull();
        assertThat(saved.getNewValue()).isNull();
        assertThat(saved.getReason()).isNull();
    }

    @Test
    void serializesOldAndNewValuesToJson() {
        service.record(new AuditEntry(AuditAction.STORAGE_UNIT_RESERVED, AuditEntityType.STORAGE_UNIT, "3",
                Map.of("status", "Available"), Map.of("status", "Reserved"), null, 9L));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOldValue().get("status").asText()).isEqualTo("Available");
        assertThat(saved.getNewValue().get("status").asText()).isEqualTo("Reserved");
        assertThat(saved.getActorAccountId()).isEqualTo(9L);
    }

    @Test
    void multiEntityActionAcceptedForEachCatalogEntity() {
        service.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.RENTAL_ORDER, "5"));
        service.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.STORAGE_UNIT, "3"));
        service.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.PROPOSAL_FEEDBACK, "8"));
        service.record(AuditEntry.of(AuditAction.RENTAL_ORDER_CANCELED, AuditEntityType.RENTAL_CONTRACT, "1"));

        verify(repository, times(4)).save(any(AuditLog.class));
    }

    @Test
    void actionNotAllowedForEntityRejected() {
        AuditEntry wrong = AuditEntry.of(AuditAction.RENTAL_REQUEST_APPROVED, AuditEntityType.INVOICE, "1");

        assertThatThrownBy(() -> service.record(wrong)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void entryRequiresActionEntityTypeAndEntityId() {
        assertThatThrownBy(() -> AuditEntry.of(null, AuditEntityType.ACCOUNT, "1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEntry.of(AuditAction.ACCOUNT_REGISTERED, null, "1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEntry.of(AuditAction.ACCOUNT_REGISTERED, AuditEntityType.ACCOUNT, null))
                .isInstanceOf(NullPointerException.class);
    }
}
