package vn.lemar.selfstorage.payment.application;

/**
 * Lý do hoàn tiền — dùng chung cho ledger {@code refund_transactions} và nghĩa vụ
 * {@code refund_obligations}. Giá trị lưu DB trùng tên enum (CHECK constraint V22/V24).
 */
public enum RefundReason {
    UNIT_UNAVAILABLE,
    DUPLICATE_PAYMENT,
    DEPOSIT_SURPLUS,
    OTHER;

    /** Snapshot lý do từ {@code invoices.refund_reason}; null/không khớp → OTHER. */
    public static RefundReason fromInvoiceReason(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
