package vn.lemar.selfstorage.payment.domain;

/** Trạng thái của một lần chi tiền hoàn; DB lưu PascalCase. */
public enum RefundTransactionStatus {
    PENDING("Pending"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELED("Canceled");

    private final String dbValue;

    RefundTransactionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RefundTransactionStatus fromDbValue(String value) {
        for (RefundTransactionStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị refund status không hợp lệ: " + value);
    }
}
