/**
 * AuditLog (A8) — ghi vết thao tác nghiệp vụ nhạy cảm theo Audit action catalog
 * ({@code specs/db-table-draft.md} › AuditLog). Bảng {@code audit_logs} append-only.
 *
 * <p>Module khác gọi qua named interface {@code audit::application}: inject
 * {@link vn.lemar.selfstorage.audit.application.AuditRecorder} và gọi trong cùng transaction
 * nghiệp vụ — rollback thì audit rollback theo.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package vn.lemar.selfstorage.audit;
