# Lịch làm việc trong ngày

- **Route:** `/staff/schedule`
- **Actor:** Facility Staff
- **Flow tham chiếu:** 2.1 (branch `specs/flow-2-2.5`)

## Mục đích
FS xem toàn bộ `Appointment` (check-in, trả kho) được FM phân công cho mình trong ngày, làm điểm bắt đầu cho các thao tác on-site.

## Dữ liệu hiển thị
- `Appointment WHERE staff_id = current_user AND appointment_date = :date`: khách hàng, khoang chứa, `type` (CHECKIN/RETURN), khung giờ, `status`
- Chọn ngày để xem lịch khác (mặc định hôm nay)

## Actions
- Bấm vào 1 lịch hẹn CHECKIN → `fs/02-onsite-handover-checklist.md`
- Bấm vào 1 lịch hẹn RETURN → `fs/03-onsite-return-checklist.md`
- [Ghi nhận khách đến] (`arrive`) — set `Appointment.arrived_at`, `status = Done`

## States / UI trạng thái
- Sắp xếp theo khung giờ tăng dần trong ngày
- Đánh dấu lịch đã qua giờ hẹn mà khách chưa đến (sắp bị cron chuyển `Canceled`/no-show)
- Empty state: không có lịch hẹn nào hôm nay

## API liên quan
- `GET /api/staff/appointments?date=`
- `POST /api/staff/appointments/{id}/arrive`

## Edge case / Lưu ý UX
- FS chỉ thấy lịch hẹn thuộc facility mình được gán (`AccountFacilityAssignment`) và đúng `staff_id` của chính mình — kiểm tra ở BE, 403 nếu sai
- No-show: cron tự động chuyển `status = Canceled` với `cancel_reason = NoShow` khi quá `end_at` mà chưa `arrive` — FS không cần thao tác thủ công cho case này, chỉ cần biết để không tìm nhầm lịch đã biến mất khỏi danh sách hoạt động
