# Form báo sự cố

- **Route:** modal/panel trên `/customer/contracts/{id}` (không phải route riêng)
- **Actor:** Customer (chủ hợp đồng)
- **Flow tham chiếu:** 3.5 (branch `specs/flow-3`)

## Mục đích
Cho khách báo các sự cố liên quan tới khoang/khóa truy cập trong lúc hợp đồng còn `Active`, không phụ thuộc trạng thái quá hạn hay đang có request khác mở.

## Dữ liệu hiển thị / Input form
- `issue_type` (LostKey / AccessCode / UnitDamage / Other)
- `description` (mô tả chi tiết)

## Actions
- [Gửi báo sự cố] → tạo `SupportRequest(contract_id, unit_id, reporter_id=current_user, status=Open)`

## States / UI trạng thái
- Sau khi gửi: hiển thị trong danh sách `SupportRequest` của hợp đồng với trạng thái (Open/Assigned/InProgress/Resolved/Closed)
- Một hợp đồng có thể có **nhiều** `SupportRequest` cùng lúc (khác với Extend/Return chỉ cho 1 request mở) — hiển thị dạng danh sách, không giới hạn 1 item

## API liên quan
- `POST /api/customer/contracts/{id}/support-requests`

## Edge case / Lưu ý UX
- Nút [Báo sự cố] luôn hiện khi hợp đồng `Active`, kể cả đang quá hạn hoặc đang có `ExtendRequest`/`ReturnRequest` mở — không bị chi phối bởi bảng action như 2 panel kia
- Nếu sự cố phát sinh phí (vd làm lại chìa khóa), hóa đơn `Invoice(type=Service)` sẽ tự xuất hiện ở trang Hóa đơn sau khi FS xử lý xong (Flow 7) — form này không thu phí trực tiếp
- Action này không ảnh hưởng gì tới `RentalContract`
