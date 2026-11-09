package vn.lemar.selfstorage.payment.domain;

/** Trạng thái nghĩa vụ hoàn tiền; chưa phải trạng thái chi tiền thực tế. */
public enum RefundObligationStatus {
    OPEN("Open"),
    APPROVED("Approved"),
    DISBURSED("Disbursed"),
    CANCELED("Canceled");

    private final String dbValue;

    RefundObligationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RefundObligationStatus fromDbValue(String value) {
        for (RefundObligationStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị refund obligation status không hợp lệ: " + value);
    }
}
