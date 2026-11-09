package vn.lemar.selfstorage.booking.application;

/**
 * Flow 2: lịch check-in quá giờ chưa đến đã bị huỷ no-show. {@code handover} nghe event này để đóng
 * phiên bàn giao đang mở; booking tự xử lý đơn sau đó.
 *
 * <p>{@code reason} là lý do đã ghi lên {@code Appointment.cancel_reason} — listener dùng lại đúng
 * chuỗi đó cho {@code HandoverRecord.reject_reason} để FM chỉ thấy một lý do duy nhất.
 */
public record AppointmentNoShow(Long appointmentId, String reason) {
}
