package vn.lemar.selfstorage.booking.application;

/**
 * Kết quả khách bấm "Đồng ý".
 *
 * <p>Nhánh khoang hết {@code Available} không ném exception: spec 1.3 yêu cầu vẫn lưu proposal
 * {@code Expired} + báo FM, nên transaction phải commit; controller tự đổi {@link Outcome#UNIT_UNAVAILABLE}
 * thành 409 cho khách.
 */
public record AgreeResult(Outcome outcome, ProposalView proposal) {

    public enum Outcome {
        AGREED,
        UNIT_UNAVAILABLE
    }
}
