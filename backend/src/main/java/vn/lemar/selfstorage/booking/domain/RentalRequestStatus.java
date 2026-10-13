package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái yêu cầu thuê ({@code specs/db-table-draft.md} › RentalRequest).
 *
 * <p>DB lưu dạng PascalCase theo CHECK constraint của {@code rental_requests} (V1); quy đổi qua
 * {@link RentalRequestStatusConverter}.
 */
public enum RentalRequestStatus {
    PENDING("Pending"),
    REJECTED("Rejected"),
    APPROVED("Approved"),
    CONVERTED("Converted"),
    EXPIRED("Expired");

    private final String dbValue;

    RentalRequestStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RentalRequestStatus fromDbValue(String value) {
        for (RentalRequestStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị status không hợp lệ: " + value);
    }
}
