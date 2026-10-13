package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái đơn thuê ({@code specs/db-table-draft.md} › RentalOrder).
 *
 * <p>DB lưu dạng PascalCase theo CHECK constraint của {@code rental_orders} (V1); quy đổi qua
 * {@link RentalOrderStatusConverter}. Không có {@code Expired}: quá hạn cũng là {@code Canceled} kèm
 * {@code cancel_reason} (#87, chốt #113).
 */
public enum RentalOrderStatus {
    PENDING("Pending"),
    DEPOSITED("Deposited"),
    SCHEDULED("Scheduled"),
    IN_PROGRESS("InProgress"),
    CANCELED("Canceled"),
    DONE("Done");

    private final String dbValue;

    RentalOrderStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** Đơn đã đóng (huỷ hoặc hoàn tất) — không còn đề xuất khoang được nữa. */
    public boolean isClosed() {
        return this == CANCELED || this == DONE;
    }

    public static RentalOrderStatus fromDbValue(String value) {
        for (RentalOrderStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị status không hợp lệ: " + value);
    }
}
