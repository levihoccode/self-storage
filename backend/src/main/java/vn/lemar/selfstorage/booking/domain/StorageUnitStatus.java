package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái khoang chứa.
 *
 * <p>Giá trị lưu xuống DB viết hoa chữ đầu theo CHECK constraint của bảng {@code storage_units}
 * (xem {@code V1__baseline_schema.sql}); quy đổi do {@link StorageUnitStatusConverter} lo.
 */
public enum StorageUnitStatus {
    AVAILABLE("Available"),
    RESERVED("Reserved"),
    RENTED("Rented"),
    MAINTENANCE("Maintenance");

    private final String dbValue;

    StorageUnitStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static StorageUnitStatus fromDbValue(String value) {
        for (StorageUnitStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho StorageUnitStatus: " + value);
    }
}
