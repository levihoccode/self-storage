package vn.lemar.selfstorage.booking.domain;

/**
 * Bộ giá trị chuẩn cho lý do từ chối (B1 phần 4) — khớp CHECK trong V1 / db-table-draft.md.
 * {@code OTHER} bắt buộc kèm ghi chú.
 */
public enum RentalRequestRejectReason {
    UNIT_UNAVAILABLE,
    UNIT_MISMATCH,
    CUSTOMER_REQUEST,
    OTHER
}
