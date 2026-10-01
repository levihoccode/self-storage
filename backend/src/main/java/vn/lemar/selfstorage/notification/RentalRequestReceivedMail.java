package vn.lemar.selfstorage.notification;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dữ liệu để gửi email xác nhận đã nhận yêu cầu đặt kho (Flow 1.1).
 * Là kiểu riêng của notification để module này không phải phụ thuộc booking.
 */
public record RentalRequestReceivedMail(
        String toEmail,
        String customerName,
        String facilityName,
        String unitTypeName,
        LocalDate startDate,
        int periodMonths,
        Instant expiresAt) {
}
