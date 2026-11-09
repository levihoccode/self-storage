package vn.lemar.selfstorage.handover.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.lemar.selfstorage.booking.application.AppointmentNoShow;

/**
 * Flow 2: lịch check-in no-show → đóng phiên bàn giao đang mở và contract chưa bàn giao.
 *
 * <p>Listener chạy <b>ngay khi publish</b> (không phải {@code @TransactionalEventListener}) để giữ
 * thứ tự khoá {@code appointments → handover_records → rental_orders}: booking đang giữ row lịch,
 * listener khoá record/contract, rồi booking mới cập nhật đơn. Chạy trong transaction của caller
 * nên lỗi ở đây rollback cả lần huỷ no-show.
 *
 * <p>Lịch chưa có record (Flow 1 chưa tạo) hoặc record đã {@code COMPLETED}/{@code REJECTED} là
 * no-op.
 */
@Component
class AppointmentNoShowListener {

    private final HandoverService handoverService;

    AppointmentNoShowListener(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @EventListener
    void onAppointmentNoShow(AppointmentNoShow event) {
        handoverService.cancelByAppointmentId(event.appointmentId(), event.reason());
    }
}
