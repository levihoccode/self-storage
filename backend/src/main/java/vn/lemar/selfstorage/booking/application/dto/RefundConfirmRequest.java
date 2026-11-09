package vn.lemar.selfstorage.booking.application.dto;

import jakarta.validation.constraints.Size;

/** Khách xác nhận đã nhận tiền; bỏ trống id thì lấy lần hoàn Success gần nhất của hoá đơn. */
public record RefundConfirmRequest(
        Long refundTransactionId,
        @Size(max = 500) String note) {
}
