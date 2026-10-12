package vn.lemar.selfstorage.audit.application;

/**
 * API công khai của module audit. Module khác inject interface này và gọi <b>trong cùng
 * transaction nghiệp vụ</b> — audit và thay đổi nghiệp vụ commit/rollback cùng nhau.
 *
 * <p>Ghi một entry cho mỗi entity bị thay đổi; thao tác đổi nhiều entity gọi nhiều lần
 * trong cùng một transaction.
 */
public interface AuditRecorder {

    /**
     * Ghi một bản ghi append-only vào {@code audit_logs}.
     *
     * @throws IllegalStateException action không khớp entity_type theo catalog — lỗi lập trình,
     *                               không phải lỗi dữ liệu đầu vào
     */
    void record(AuditEntry entry);
}
