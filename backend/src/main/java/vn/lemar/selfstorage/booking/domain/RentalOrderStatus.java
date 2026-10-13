package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái đơn thuê kho.
 *
 * <p>Giá trị lưu xuống DB viết hoa chữ đầu theo CHECK constraint của bảng {@code rental_orders}
 * (xem {@code V1__baseline_schema.sql}); quy đổi do {@link RentalOrderStatusConverter} lo.
 */
public enum RentalOrderStatus {
    PENDING("Pending"),
    DEPOSITED("Deposited"),
    SCHEDULED("Scheduled"),
    INPROGRESS("InProgress"),
    CANCELED("Canceled"),
    DONE("Done"),
    EXPIRED("Expired");

    private final String dbValue;

    RentalOrderStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RentalOrderStatus fromDbValue(String value) {
        for (RentalOrderStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho RentalOrderStatus: " + value);
    }
}
