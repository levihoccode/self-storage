# Xử lý yêu cầu hỗ trợ được phân công

- **Route:** `/staff/support-requests`
- **Actor:** Facility Staff
- **Flow tham chiếu:** Flow 7 (chưa có tài liệu phân tích chi tiết — chỉ có cross-reference từ Flow 3.5)

## Mục đích
FS xem và xử lý các `SupportRequest` được FM phân công cho mình (`assigned_staff_id = current_user`).

## Navigation

- **Vào từ:** `/staff/schedule` hoặc FM assign từ `/fm/support-requests`.
- **Đi tới:** giữ tại queue khi chuyển status; `/staff/incidents/new` nếu phát hiện incident mới; customer nhận cập nhật qua `/notifications`.

## Dữ liệu hiển thị
- Danh sách `SupportRequest WHERE assigned_staff_id = current_user AND status IN (Assigned, InProgress)`
- `issue_type`, `description`, khoang, hợp đồng liên quan (nếu có)

## Actions
- [Bắt đầu xử lý] → `status = InProgress`
- [Đánh dấu hoàn thành] → `status = Resolved`, nhập ghi chú xử lý
- Nếu phát sinh phí: [Tạo hóa đơn dịch vụ] → `Invoice(type=Service)`, gán vào `SupportRequest.invoice_id`

## States / UI trạng thái
- Badge theo `issue_type` giống trang FM
- Trạng thái cuối `Closed` (khách xác nhận hoặc tự động đóng sau một khoảng thời gian — cơ chế đóng tự động **chưa chốt**, chờ Flow 7)

## API liên quan
- `GET /api/staff/support-requests?assigned_staff_id=current_user`
- `POST /api/staff/support-requests/{id}/start`
- `POST /api/staff/support-requests/{id}/resolve`
- `POST /api/staff/support-requests/{id}/invoice` (nếu phát sinh phí — endpoint suy đoán, cần xác nhận khi Flow 7 hoàn thiện)

## Edge case / Lưu ý UX
- **Lưu ý quan trọng:** Flow 7 chưa có tài liệu chi tiết — quy tắc đóng yêu cầu, luồng phát sinh phí đầy đủ, và các trạng thái trung gian có thể thay đổi khi Flow 7 được viết. Trang này chỉ dựng khung tối thiểu dựa trên field có sẵn trong `SupportRequest` (db-table-draft.md) và cross-reference ở Flow 3.5/3.6
- Chỉ phát sinh `invoice_id` khi `SupportRequest.contract_id` khác null (có khách để thu phí) — nếu request không gắn hợp đồng (sự cố trên khoang trống), ẩn hẳn nút [Tạo hóa đơn dịch vụ]
