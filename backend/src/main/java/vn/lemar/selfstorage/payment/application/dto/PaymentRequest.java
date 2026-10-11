package vn.lemar.selfstorage.payment.application.dto;

import java.math.BigDecimal;

/**
 * Yêu cầu tạo một lượt thanh toán.
 *
 * @param txnRef     mã tham chiếu của phía mình, không được trùng trong ngày (VNPay yêu cầu)
 * @param amount     số tiền VND như người dùng thấy; việc nhân 100 do gateway lo
 * @param orderInfo  mô tả hiển thị trên cổng thanh toán
 * @param ipAddr     IP của khách, VNPay bắt buộc
 */
public record PaymentRequest(String txnRef, BigDecimal amount, String orderInfo, String ipAddr) {}
