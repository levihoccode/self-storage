package vn.lemar.selfstorage.payment.application.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cổng đã xác nhận thu tiền thành công.
 *
 * <p>Module sở hữu hóa đơn nghe sự kiện này để chuyển `Invoice` sang `Paid`. Module `payment`
 * không tự đụng vào hóa đơn — ranh giới module theo issue #15 mục 1.
 */
public record PaymentSucceeded(Long invoiceId, String vnpTxnRef, BigDecimal amount, Instant paidAt) {}
