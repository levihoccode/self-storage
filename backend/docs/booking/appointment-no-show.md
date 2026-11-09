---
purpose: Cron no-show cho lịch check-in quá giờ chưa đến — guard, audit, notification.
---

# Quá giờ và khách không đến (no-show)

- `AppointmentNoShowJob` chạy mỗi **5 phút** (lần đầu sau 60 giây); quét `Appointment` type `CHECKIN`,
  `status = Pending`, `end_at < now`. Job chạy trên một instance; không dùng lock phân tán.
- Action `arrive`/`cancel` khi phát hiện quá giờ cũng gọi **cùng logic huỷ no-show ngay**, rồi trả 409;
  cron chỉ là backstop cho trường hợp FS không mở app.
- `started_at`/`end_at` NOT NULL từ V15; notification dùng trực tiếp hai mốc này.
- Guard dùng chung `Appointment.isNoShowDue(now)`: cron, read (`noShowDue`), action FS dùng lại; không
  rải điều kiện riêng.
- Mỗi lịch: `status = Canceled`, `cancel_reason = "Quá giờ và khách không đến"`; audit `APPOINTMENT_CANCELED_NO_SHOW`
  (actor System); notification khách của lịch `APPOINTMENT_CANCELED_NO_SHOW` (`appointmentTime` +
  `rescheduleLink = FRONTEND_URL/appointments/new`); lịch có đơn thì nối thêm order, không có đơn thì chỉ
  ghi notification. Idempotent: chỉ quét `Pending`.
- Lịch có đơn (`rental_appointments`): phát `AppointmentNoShow(appointmentId, reason)` để `handover`
  đóng phiên bàn giao — `HandoverRecord` đang mở → `CANCELED` + `reject_reason`, contract
  `Draft`/`Signed` → `Canceled`, cùng lý do `"Quá giờ và khách không đến"`. Listener chạy ngay khi
  publish (không phải `BEFORE_COMMIT`) nên thứ tự khoá là
  `appointments → handover_records → rental_contracts → rental_orders`.
- Đơn của lịch (`rental_orders`, chỉ khi `InProgress`):
  - còn hạn giữ kho (`expires_at` null hoặc `now < expires_at`) → đơn về `Deposited`, audit
    `RENTAL_ORDER_DEPOSITED`, khoang giữ `Reserved` để khách đặt lịch mới;
  - hết hạn (`now >= expires_at`) → Cancellation Cascade với lý do `"Quá giờ và khách không đến"`,
    đơn/khoang/hoá đơn xử lý theo luồng huỷ; khách nhận thêm `RENTAL_ORDER_CANCELED`.
- Đơn không `InProgress` (chưa phân công FS, đã `Done`/`Canceled`) hoặc lịch không có đơn: chỉ đóng
  lịch/phiên bàn giao, log cảnh báo, không đoán trạng thái đơn.
- Khách đến muộn phải trong khung giờ: `arrive` sau `end_at` → 409; `cancel` sau `end_at` → 409. Nếu
  cron chưa chạy, guard vẫn phản ánh qua `noShowDue`/409 — không đợi cron.
- Read trả `noShowDue` (không side effect) và `cancelReason = "Quá giờ và khách không đến"`; FE ẩn 2 nút trên row
  đó và hiển thị raw. Notification dùng `appointmentTime` dạng `HH:mm–HH:mm dd/MM/yyyy` (giờ cơ sở) và
  `rescheduleLink`.
