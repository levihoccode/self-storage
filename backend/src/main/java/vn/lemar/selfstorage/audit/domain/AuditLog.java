package vn.lemar.selfstorage.audit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một vết thao tác nghiệp vụ nhạy cảm — append-only, không có đường sửa/xóa trong code.
 *
 * <p>Không {@code @ManyToOne} sang identity cho {@code actorAccountId} — tránh phụ thuộc module
 * chéo; FK do DB giữ (theo cùng quy ước với {@code Notification}).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Convert(converter = AuditEntityTypeConverter.class)
    @Column(name = "entity_type", nullable = false, length = 60)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, columnDefinition = "text")
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private JsonNode oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private JsonNode newValue;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
        // JPA
    }

    public AuditLog(Long actorAccountId, AuditAction action, AuditEntityType entityType, String entityId,
                    JsonNode oldValue, JsonNode newValue, String reason) {
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getActorAccountId() {
        return actorAccountId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public JsonNode getOldValue() {
        return oldValue;
    }

    public JsonNode getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
