package vn.lemar.selfstorage.booking.domain;

/**
 * Trạng thái đề xuất khoang ({@code specs/db-table-draft.md} › ProposalFeedback).
 *
 * <p>DB lưu dạng PascalCase theo CHECK constraint của {@code proposal_feedbacks} (V1); quy đổi qua
 * {@link ProposalStatusConverter}.
 */
public enum ProposalStatus {
    PENDING("Pending"),
    AGREED("Agreed"),
    REJECTED("Rejected"),
    EXPIRED("Expired");

    private final String dbValue;

    ProposalStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static ProposalStatus fromDbValue(String value) {
        for (ProposalStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Giá trị status không hợp lệ: " + value);
    }
}
