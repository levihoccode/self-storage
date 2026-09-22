# Quản lý tài khoản

- **Route:** `/admin/accounts`
- **Actor:** System Administrator
- **Flow tham chiếu:** 5.0 (branch `specs/flow-5`)

## Mục đích
Admin là người **duy nhất có quyền thực thi kỹ thuật** việc tạo tài khoản/cập nhật role. Admin không quyết định ai giữ role gì (đó là quyền BOM) — chỉ đảm bảo thao tác được thực hiện đúng, an toàn, có audit trail.

## Navigation

- **Vào từ:** `/login` sau khi Admin đăng nhập; admin shell.
- **Đi tới:** `/admin/staff-requests` để xử lý request; `/admin/rbac`; `/admin/login-history`; `/admin/audit-log`; account detail/edit surface.

## Dữ liệu hiển thị
- Danh sách `Account`: họ tên, email, `role`, `status` (Active/Inactive/Locked), facility (nếu FM/FS — tra ngược qua `Facility.fm_account_id`/`AccountFacilityAssignment`)
- Filter theo role, status

## Actions
- [Tạo account thủ công] — nhập tay từng account (FM/FS/BOM nội bộ; Customer tự đăng ký, Admin không tạo hộ)
- [Import hàng loạt] qua Excel/Sheets — validate từng dòng, trả báo cáo lỗi
- [Đổi role] một account đã tồn tại — **chạy trong 1 transaction**: nếu account đang là FM của 1 Facility, tự động clear `Facility.fm_account_id = null`; nếu đang là FS có `AccountFacilityAssignment`, tự động xoá dòng đó
- [Khóa/Mở khóa tài khoản] (`status = Locked`)

## States / UI trạng thái
- Cảnh báo xác nhận rõ ràng khi đổi role 1 account đang giữ facility assignment: "Account này đang là FM/FS của [Facility X], đổi role sẽ tự động gỡ khỏi facility này"
- Kết quả import hàng loạt: bảng chi tiết dòng thành công / dòng lỗi (kèm lý do)

## API liên quan
- `GET /api/admin/accounts?role=&status=`
- `POST /api/admin/accounts`
- `POST /api/admin/accounts/import` (multipart file)
- `PATCH /api/admin/accounts/{id}/role`
- `PATCH /api/admin/accounts/{id}/status`

## Edge case / Lưu ý UX
- Việc Customer tự tạo account thuộc `shared/02-register-verify.md`, **không** thuộc phạm vi trang này
- Mọi hành động nhạy cảm (tạo account, đổi role, gán/xoá facility assignment) phải ghi `AuditLog` — không cần FE xử lý gì thêm ngoài việc gọi đúng API, nhưng nên có link nhanh sang `admin/05-audit-log.md` để tra cứu hành động vừa thực hiện
- Import hàng loạt được ghi trong tài liệu là "Advanced Features (not MVP)" ở Flow 5 — cần xác nhận với team có nằm trong scope đợt đầu hay chỉ làm tạo thủ công từng account trước
