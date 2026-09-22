# Audit Log

- **Route:** `/admin/audit-log`
- **Actor:** System Administrator
- **Flow tham chiếu:** 5.0 / 3.6 (branch `specs/flow-5`, `specs/flow-3`), `AuditLog` (db-table-draft.md)

## Mục đích
Admin theo dõi lịch sử hoạt động nhạy cảm của nhân viên trong hệ thống (duyệt yêu cầu, cập nhật trạng thái, đổi giá, đổi role...).

## Navigation

- **Vào từ:** admin shell hoặc detail/action surface.
- **Đi tới:** `/admin/accounts`, `/admin/rbac`, `/admin/login-history` từ actor/entity context; detail drawer cho diff lớn.

## Dữ liệu hiển thị
- Danh sách `AuditLog`: `account_id` (người thực hiện), `action` (vd `RentalRequest.Approve`, `Policy.Update`, `Account.UpdateRole`), `entity_type`, `entity_id`, `old_value`/`new_value` (JSON, dạng diff), `created_at`
- Filter theo `account_id`, `entity_type`, khoảng thời gian

## Actions
- Xem chi tiết 1 log — hiển thị `old_value`/`new_value` dạng before/after dễ đọc (không phải raw JSON)
- Tra cứu theo `entity_type` + `entity_id` để xem toàn bộ lịch sử thay đổi của 1 bản ghi cụ thể (vd toàn bộ log liên quan tới 1 `RentalContract`)

## API liên quan
- `GET /api/admin/audit-log?account_id=&entity_type=&entity_id=&from=&to=`

## Edge case / Lưu ý UX
- **Mức tối thiểu cho MVP** (theo Flow 3.6): chỉ ghi các thao tác của nhân viên làm thay đổi quyền lợi của khách (FM duyệt/từ chối `ExtendRequest`, FM phân công FS...) — **không** ghi thao tác khách tự tạo/hủy yêu cầu và cập nhật của cron job (đã có `status`/`processed_at`/`cancel_reason` trên chính bản ghi đó). FE không cần hiển thị các log này vì BE vốn không ghi
- Cấu trúc bảng dùng chung xuyên suốt Flow 3 và Flow 5 — không có phiên bản rút gọn riêng cho từng flow, nên trang này là nơi tra cứu **tập trung duy nhất**, không tách theo module
- Trang thuần read-only
