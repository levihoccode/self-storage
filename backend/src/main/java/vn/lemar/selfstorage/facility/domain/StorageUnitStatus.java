package vn.lemar.selfstorage.facility.domain;

/**
 * Trạng thái khoang chứa ({@code specs/db-table-draft.md} › StorageUnit).
 *
 * <p>DB lưu dạng {@code Available} theo CHECK constraint của {@code storage_units} (V1);
 * quy đổi qua {@link StorageUnitStatusConverter}.
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
        throw new IllegalArgumentException("Giá trị status không hợp lệ: " + value);
    }
}
