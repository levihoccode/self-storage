package vn.lemar.selfstorage.booking.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FS/FM xác nhận đã đưa tiền hoàn.
 *
 * @param method             CASH / BANK_TRANSFER / VNPAY
 * @param evidenceUrl        ảnh/PDF chứng từ, bắt buộc
 * @param gatewayRef         mã chuyển khoản/VNPay nếu có
 * @param note               ghi chú thêm
 * @param sourceTransactionId giao dịch PAY gốc; bỏ trống thì lấy giao dịch gần nhất có thể hoàn
 * @param reasonNote         bắt buộc khi lý do của hoá đơn là OTHER
 */
public record RefundHandoverRequest(
        @NotBlank @Size(max = 20) String method,
        @NotBlank @Size(max = 1000) String evidenceUrl,
        @Size(max = 100) String gatewayRef,
        @Size(max = 500) String note,
        Long sourceTransactionId,
        @Size(max = 500) String reasonNote) {
}
