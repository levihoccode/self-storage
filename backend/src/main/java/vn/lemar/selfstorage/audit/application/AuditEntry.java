package vn.lemar.selfstorage.audit.application;

import java.util.Objects;
import vn.lemar.selfstorage.audit.domain.AuditAction;
import vn.lemar.selfstorage.audit.domain.AuditEntityType;

/**
 * Một lần ghi audit: một entity bị thay đổi. Thao tác đổi nhiều entity tạo nhiều entry —
 * một entry cho mỗi entity (spec: AuditLog › NOTES).
 *
 * @param action         action theo catalog; phải khớp {@code entityType} (validate lúc ghi)
 * @param entityType     loại entity bị tác động
 * @param entityId       id entity dạng chuỗi, không rỗng
 * @param oldValue       giá trị trước (nullable) — serialize thành JSONB
 * @param newValue       giá trị sau (nullable)
 * @param reason         lý do (nullable) — bắt buộc với thao tác override theo spec flow
 * @param actorAccountId account thực hiện; null = hệ thống/cron/IPN
 */
public record AuditEntry(AuditAction action, AuditEntityType entityType, String entityId,
                         Object oldValue, Object newValue, String reason, Long actorAccountId) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId không được để trống");
        }
    }

    /** Thao tác tối giản: chỉ action + entity, chưa có giá trị cũ/mới/lý do/actor. */
    public static AuditEntry of(AuditAction action, AuditEntityType entityType, String entityId) {
        return new AuditEntry(action, entityType, entityId, null, null, null, null);
    }
}
