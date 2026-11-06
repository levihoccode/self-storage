package vn.lemar.selfstorage.booking.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Khách báo chưa nhận tiền; không tạo thêm refund. */
public record RefundDisputeRequest(
        Long refundTransactionId,
        @NotBlank @Size(max = 500) String note) {
}
