# Hàng chờ yêu cầu hỗ trợ

- **Route:** `/fm/support-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 3.5 / Flow 7 (Flow 7 chưa có tài liệu chi tiết riêng)

## Mục đích
FM phân công FS xử lý các yêu cầu hỗ trợ sự cố do khách gửi hoặc do FS tự ghi nhận tại kho.

## Navigation

- **Vào từ:** FM shell hoặc notification về support request.
- **Đi tới:** `/staff/support-requests` sau khi assign; `/fm/contracts` hoặc unit detail để xem context; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `SupportRequest WHERE status = Open` thuộc facility của khoang
- `issue_type` (LostKey/AccessCode/UnitDamage/Other), `description`, người báo (`reporter_id` — Customer hoặc FS), `contract_id` (nullable)

## Actions
- Chọn 1 request → chọn FS → [Phân công] → `SupportRequest.status = Assigned`

## States / UI trạng thái
- Badge phân loại theo `issue_type` (màu khác nhau, vd đỏ cho UnitDamage, vàng cho LostKey)
- Một hợp đồng có thể có nhiều `SupportRequest` cùng lúc — danh sách không gộp theo hợp đồng

## API liên quan
- `GET /api/fm/support-requests?status=Open`
- `POST /api/fm/support-requests/{id}/assign` (body: `assigned_staff_id`)

## Edge case / Lưu ý UX
- `contract_id` có thể null (khoang trống bị hư hỏng, không có khách thuê) — UI cần xử lý hiển thị "Không có hợp đồng liên quan" thay vì để trống gây hiểu lầm lỗi dữ liệu
- **Lưu ý:** chi tiết xử lý on-site và quy tắc đóng yêu cầu thuộc Flow 7, hiện chưa có tài liệu — trang này chỉ dựng được phần "phân công", cần bổ sung khi Flow 7 hoàn thiện
