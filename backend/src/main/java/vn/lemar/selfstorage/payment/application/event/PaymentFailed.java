package vn.lemar.selfstorage.payment.application.event;

import java.math.BigDecimal;

/** Cổng báo giao dịch thất bại. Hóa đơn giữ nguyên chưa thanh toán. */
public record PaymentFailed(Long invoiceId, String vnpTxnRef, BigDecimal amount, String reason) {}
