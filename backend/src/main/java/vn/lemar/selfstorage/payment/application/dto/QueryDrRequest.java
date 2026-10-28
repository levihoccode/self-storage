package vn.lemar.selfstorage.payment.application.dto;

import java.time.Instant;

/**
 * Yêu cầu đối soát một giao dịch đã mở với cổng thanh toán.
 *
 * @param txnRef          mã tham chiếu gửi cổng lúc mở phiên
 * @param orderInfo       mô tả giao dịch
 * @param transactionDate thời điểm tạo giao dịch — phải khớp {@code vnp_CreateDate} của URL pay
 * @param ipAddr          IP của server gọi API đối soát
 */
public record QueryDrRequest(String txnRef, String orderInfo, Instant transactionDate, String ipAddr) {
}
