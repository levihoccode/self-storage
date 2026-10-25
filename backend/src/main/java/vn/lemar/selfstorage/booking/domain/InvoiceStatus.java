package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái hoá đơn ({@code specs/db-table-draft.md} › Invoice).
 *
 * <p>DB lưu dạng PascalCase theo CHECK constraint của {@code invoices} (V1); quy đổi qua
 * {@link InvoiceStatusConverter}.
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
        for (InvoiceStatus item : values()) {
            if (item.dbValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ: " + value);
    }
}
