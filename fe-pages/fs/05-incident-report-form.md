# Form tự ghi nhận sự cố (FS)

- **Route:** `/staff/incidents/new`
- **Actor:** Facility Staff
- **Flow tham chiếu:** 3.5 (branch `specs/flow-3`, đoạn "FS cũng có thể tự ghi nhận sự cố tại kho")

## Mục đích
Cho FS chủ động ghi nhận sự cố phát hiện tại kho (kể cả khi khoang không có hợp đồng `Active`, ví dụ khoang trống bị hư hỏng), khác với `fs/04-support-request-handling.md` (xử lý request đã được phân công).

## Dữ liệu hiển thị / Input form
- Chọn `unit_id` (khoang tại facility mình phụ trách)
- `issue_type` (LostKey/AccessCode/UnitDamage/Other)
- `description`, ảnh đính kèm (nếu có — không thấy field ảnh trong schema `SupportRequest`, cân nhắc bổ sung khi làm chi tiết)

## Actions
- [Gửi ghi nhận sự cố] → `POST /api/staff/support-requests` với `reporter_id = current_user`

## States / UI trạng thái
- `contract_id` tự động gán theo hợp đồng `Active` của khoang nếu có, ngược lại để trống — FE không cho FS tự nhập `contract_id`, chỉ hiển thị kết quả BE trả về sau khi tạo

## API liên quan
- `POST /api/staff/support-requests` (body: `unit_id`, `issue_type`, `description`)

## Edge case / Lưu ý UX
- FS phải thuộc facility của khoang (`AccountFacilityAssignment`) — nếu chọn khoang ngoài facility mình, BE trả 403; FE chỉ nên hiển thị danh sách khoang thuộc facility hiện tại trong dropdown chọn `unit_id`
- Sau khi tạo, request rơi vào hàng chờ phân công của FM (`fm/06-support-request-queue.md`) — FS không tự xử lý ngay request do chính mình tạo trừ khi FM phân công lại (tùy nghiệp vụ thực tế, cần xác nhận thêm)
