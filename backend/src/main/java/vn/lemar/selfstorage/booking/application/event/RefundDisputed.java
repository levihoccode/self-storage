package vn.lemar.selfstorage.booking.application.event;

import java.math.BigDecimal;

/** Khách báo chưa nhận tiền; ghi nhận để đối soát, không tạo refund mới. */
public record RefundDisputed(Long invoiceId, Long orderId, Long customerId,
                             String invoiceCode, BigDecimal amount, String note) {
}
