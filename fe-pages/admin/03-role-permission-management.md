# Quản lý Role & Permission (RBAC)

- **Route:** `/admin/rbac`
- **Actor:** System Administrator
- **Flow tham chiếu:** 5.0 (branch `specs/flow-5`)

## Mục đích
Admin thiết lập quyền truy cập dữ liệu cho từng role theo model RBAC — data-driven, không hard-code trong source code.

## Dữ liệu hiển thị
- Danh sách `Role` (Admin/BOM/FM/FS/Customer) với mô tả
- Danh sách `Permission`: `code` (vd `rental_request.approve`, `invoice.read`, `policy.update`), `description`
- Ma trận `RolePermission` (N-N) — bảng checkbox: hàng là Role, cột là Permission (hoặc ngược lại)
- Bảng RBAC tham khảo mặc định (seed data MVP) đã có sẵn trong tài liệu Flow 5, phần "Bảng phân quyền tổng quát"

## Actions
- Tick/untick checkbox trong ma trận để gán/gỡ permission cho 1 role → cập nhật `RolePermission`
- [Tạo Permission mới] (nếu cần mở rộng ngoài seed data)

## States / UI trạng thái
- Cảnh báo xác nhận khi gỡ 1 permission quan trọng khỏi role đang có nhiều account (vd gỡ quyền `invoice.read` khỏi FM)

## API liên quan
- `GET /api/admin/roles`
- `GET /api/admin/permissions`
- `GET /api/admin/role-permissions`
- `PUT /api/admin/role-permissions` (batch update ma trận)

## Edge case / Lưu ý UX
- Bảng RBAC mặc định trong tài liệu là **seed data khởi tạo**, không phải giới hạn cứng — Admin có thể điều chỉnh chi tiết hơn qua trang này khi cần, không cần deploy lại code
- Đây là trang có rủi ro bảo mật cao nếu thao tác sai (có thể vô tình cấp/gỡ nhầm quyền ảnh hưởng toàn hệ thống) — nên có bước xác nhận rõ ràng trước khi lưu thay đổi hàng loạt, và log lại vào `AuditLog`
