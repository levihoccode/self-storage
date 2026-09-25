# Lịch sử đăng nhập

- **Route:** `/admin/login-history`
- **Actor:** System Administrator
- **Flow tham chiếu:** 5.0 (branch `specs/flow-5`), `LoginHistory` (db-table-draft.md)

## Mục đích
Admin theo dõi hoạt động và lịch sử đăng nhập của toàn bộ user trong hệ thống.

## Navigation

- **Vào từ:** admin shell hoặc account detail.
- **Đi tới:** `/admin/accounts` khi cần xử lý account; `/admin/audit-log` để xem hành động liên quan.

## Dữ liệu hiển thị
- Danh sách `LoginHistory`: `account_id` (nullable — null khi đăng nhập bằng email không tồn tại), `email`, `ip_address`, `user_agent`, `status` (Success/Failed), `failure_reason`, `created_at`
- Filter theo email, khoảng thời gian, `status`

## Actions
- Tra cứu theo email/IP để điều tra bất thường (nhiều lần login thất bại liên tiếp)

## States / UI trạng thái
- Highlight các chuỗi `Failed` liên tiếp cùng email/IP (dấu hiệu brute-force) — không thấy cơ chế khóa tự động trong tài liệu, đây là trang thuần theo dõi thủ công cho MVP

## API liên quan
- `GET /api/admin/login-history?email=&status=&from=&to=`

## Edge case / Lưu ý UX
- `account_id` có thể null (đăng nhập bằng email không tồn tại trong hệ thống) — UI hiển thị "Không xác định" thay vì lỗi hoặc để trống gây hiểu lầm
- Trang thuần read-only, không có action chỉnh sửa dữ liệu
