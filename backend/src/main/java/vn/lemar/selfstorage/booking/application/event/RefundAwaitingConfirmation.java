package vn.lemar.selfstorage.booking.application.event;

import java.math.BigDecimal;

/** FS/FM đã đưa tiền; gửi sau commit để mời khách xác nhận, không chặn nghiệp vụ. */
public record RefundAwaitingConfirmation(Long invoiceId, Long orderId, Long customerId,
                                         String invoiceCode, BigDecimal amount) {
}
