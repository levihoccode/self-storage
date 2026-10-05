package vn.lemar.selfstorage.notification.application;

/**
 * Catalog notification type theo spec `db-table-draft.md › Notification`.
 *
 * <p>Type lưu vào DB dưới dạng tên enum (VARCHAR 60). {@link #OTHER} không dùng template —
 * phải tạo qua {@link NotificationService#createOther}.
 */
public enum NotificationType {
    RENTAL_REQUEST_APPROVED,
    RENTAL_REQUEST_REJECTED,
    PROPOSAL_REJECTED,
    PROPOSAL_REPROPOSAL_REQUIRED,
    PROPOSAL_REPROPOSED,
    DEPOSIT_PAYMENT_SUCCEEDED,
    APPOINTMENT_CREATED,
    APPOINTMENT_CANCELED_NO_SHOW,
    FS_ASSIGNED,
    FS_ASSIGNMENT_REQUIRED,
    HANDOVER_REJECTED,
    HANDOVER_OVERDUE,
    RENTAL_ORDER_EXPIRING_SOON,
    RENTAL_ORDER_CANCELED,
    HANDOVER_COMPLETED,
    OTHER
}
