# Quản lý khoang chứa

- **Route:** `/fm/storage-units`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 5.2 (branch `specs/flow-5`)

## Mục đích
FM khai báo và duy trì dữ liệu các khoang chứa vật lý tại cơ sở mình phụ trách — chỉ thực hiện được sau khi `Facility` đã `Active`.

## Dữ liệu hiển thị
- Danh sách `StorageUnit` của facility: `unit_code`, `unit_type` (tên/kích thước, giá — chỉ đọc từ `UnitType`), `location`, `status`
- Enum `status` dùng chung toàn hệ thống: `Available | OnHold | Reserved | Rented | Maintenance`
- Filter theo `status`, `unit_type`

## Actions
- [Tạo khoang mới] — nhập `unit_code`, chọn `unit_type_id`, `location`
- [Chuyển sang Maintenance] (thủ công) — chỉ cho phép từ `Available`; các trạng thái khác cần xử lý gián tiếp (xem Edge case)
- [Chuyển về Available] sau khi xử lý xong sự cố

## States / UI trạng thái
- Badge màu theo `status` (Available=xanh, OnHold=cam, Reserved=vàng, Rented=xanh dương, Maintenance=xám)
- Form tạo khoang bị khóa/ẩn nếu `Facility.status != Active`

## API liên quan
- `GET /api/fm/storage-units?status=&unit_type_id=`
- `POST /api/fm/storage-units`
- `PATCH /api/fm/storage-units/{id}/status` (chuyển Maintenance/Available thủ công)

## Edge case / Lưu ý UX
- FM **không tự nhập giá thuê** cho từng khoang — giá là thuộc tính của `UnitType.monthly_price`, do BOM quản lý (Flow 4/`bom/07-unit-type-pricing.md`). Form tạo khoang không có field giá
- Quy tắc chuyển `Maintenance` thủ công theo trạng thái hiện tại:
  - `Available` → chuyển trực tiếp được
  - `OnHold`/`Reserved` → **không** cho chuyển trực tiếp trên UI này, cần xử lý request/order liên quan trước
  - `Rented` → **không** cho tự ý chuyển, phải tạo yêu cầu xử lý sự cố (link sang `fm/06-support-request-queue.md`) thay vì đổi status thẳng ở đây
- Việc tự động mở lại `Maintenance -> Available` sau bảo trì là cron của Flow 2.5, **không phải** action thủ công ở trang này — không thiết kế nút "tự động mở lại" ở đây
- Mọi thay đổi trạng thái thủ công phải ghi `AuditLog` (người thực hiện, thời điểm, lý do) — nên có input `reason` bắt buộc khi chuyển Maintenance thủ công
