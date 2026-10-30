package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trạng thái giao dịch tại cổng, đã chuẩn hoá từ mã riêng của từng provider.
 *
 * @param amount      số tiền cổng ghi nhận; có thể null khi cổng không trả (vd ZaloPay chưa thanh toán)
 * @param rawResponse body gốc của cổng, lưu để đối soát — không chứa secret
 */
public record TransactionQueryResult(Outcome outcome, String transactionNo, Instant paidAt, BigDecimal amount,
                                     String message, String rawResponse) {

    public enum Outcome {
        /** Cổng xác nhận đã thu tiền. */
        SUCCESS,
        /** Cổng xác nhận giao dịch thất bại / bị huỷ. */
        FAILED,
        /** Giao dịch còn đang xử lý / khách chưa thanh toán. */
        PENDING,
        /** Cổng không có giao dịch với mã này. */
        NOT_FOUND,
        /** Lời gọi API lỗi hoặc mã chưa biết — giữ nguyên Pending, thử lại lần sau. */
        UNKNOWN
    }
}
