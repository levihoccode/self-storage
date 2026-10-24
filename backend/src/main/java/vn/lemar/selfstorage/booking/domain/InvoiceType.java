package vn.lemar.selfstorage.booking.domain;

/**
 * Loại hoá đơn ({@code specs/db-table-draft.md} › Invoice) — xác định nguồn gốc hoá đơn.
 *
 * <p>DB lưu dạng PascalCase theo CHECK constraint của {@code invoices} (V1); quy đổi qua
 * {@link InvoiceTypeConverter}.
 */
public enum InvoiceType {
    DEPOSIT("Deposit"),
    DEPOSIT_SUPPLEMENT("DepositSupplement"),
    RENTAL("Rental"),
    EXTENSION("Extension"),
    PENALTY("Penalty"),
    SERVICE("Service");

    private final String dbValue;

    InvoiceType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static InvoiceType fromDbValue(String value) {
        for (InvoiceType item : values()) {
            if (item.dbValue.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ: " + value);
    }
}
