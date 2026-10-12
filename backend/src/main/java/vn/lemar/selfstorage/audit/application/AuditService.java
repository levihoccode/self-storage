package vn.lemar.selfstorage.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.lemar.selfstorage.audit.domain.AuditLog;
import vn.lemar.selfstorage.audit.repository.AuditLogRepository;

/** Ghi audit — append-only, chạy trong transaction của caller (propagation REQUIRED). */
@Service
public class AuditService implements AuditRecorder {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        if (!entry.action().allowedFor(entry.entityType())) {
            throw new IllegalStateException("Action " + entry.action() + " không áp dụng cho entity "
                    + entry.entityType() + " theo catalog (specs/db-table-draft.md > AuditLog)");
        }
        repository.save(new AuditLog(entry.actorAccountId(), entry.action(), entry.entityType(),
                entry.entityId(), toJson(entry.oldValue()), toJson(entry.newValue()), entry.reason()));
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }
}
