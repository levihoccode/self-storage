package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái của {@link RentalRequest} — khớp CHECK trong V1 (spec db-table-draft.md).
 */
public enum RentalRequestStatus {
    Pending,
    Rejected,
    Approved,
    Converted,
    Expired
}
