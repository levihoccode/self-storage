package vn.lemar.selfstorage.payment.domain;

/** Trạng thái nghĩa vụ hoàn tiền; chưa phải trạng thái chi tiền thực tế. */
public enum RefundRequestStatus {
    REQUESTED("Requested"),
    APPROVED("Approved"),
    DISBURSED("Disbursed"),
    CANCELED("Canceled");

    private final String dbValue;

    RefundRequestStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RefundRequestStatus fromDbValue(String value) {
        for (RefundRequestStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị refund request status không hợp lệ: " + value);
    }
}
