package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái hóa đơn.
 *
 * <p>Giá trị lưu xuống DB viết hoa chữ đầu theo CHECK constraint của bảng {@code invoices}
 * (xem {@code V1__baseline_schema.sql}); quy đổi do {@link InvoiceStatusConverter} lo.
 */
public enum InvoiceStatus {
    UNPAID("Unpaid"),
    PAID("Paid"),
    CANCELED("Canceled"),
    EXPIRED("Expired");

    private final String dbValue;

    InvoiceStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static InvoiceStatus fromDbValue(String value) {
        for (InvoiceStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho InvoiceStatus: " + value);
    }
}
