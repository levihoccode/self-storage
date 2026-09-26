# Hàng chờ Account Role Request

- **Route:** `/admin/staff-requests`
- **Actor:** System Administrator
- **Flow tham chiếu:** 5.0 / 5.1 (branch `specs/flow-5`)

## Mục đích
Admin xử lý danh sách `AccountRoleRequest` do BOM gửi lên — mỗi dòng kèm role đã được BOM **chỉ định trực tiếp** (không phải đề xuất chờ duyệt).

## Navigation

- **Vào từ:** `/admin/accounts`, notification hoặc admin shell.
- **Đi tới:** `/admin/accounts` sau khi thực thi; `/admin/audit-log`; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `AccountRoleRequest WHERE status = Pending`: `batch_id`, `target_name`, `target_email`, `role` (FM/FS), `target_facility_id`, `requested_by`, `created_at`

## Actions
- Với mỗi dòng, Admin **thực thi** (không có quyền Reject vì đây là lệnh thực thi, không phải đề xuất):
  - Tra cứu email: chưa có account → tạo mới + gán `role_id`; đã có account → chỉ cập nhật `role_id`
  - Nếu role = **FS**: sau khi tạo/cập nhật, Admin gán luôn account vào `target_facility_id` (tạo `AccountFacilityAssignment`)
  - Nếu role = **FM**: chỉ tạo/cập nhật account, **không** gán facility (BOM tự làm ở `bom/05-facility-management.md`)
  - [Xử lý xong] → `status = Done`
- Nếu dòng lỗi dữ liệu (email sai định dạng...): [Báo lỗi lại cho BOM] — ghi chú lý do, **không** tự ý đổi role khác

## States / UI trạng thái
- Badge số lượng đang `Pending`, nhóm theo `batch_id` để xử lý theo lô
- Trạng thái dòng: Pending / Done (không có Rejected trong schema — lỗi xử lý riêng ngoài status chính)

## API liên quan
- `GET /api/admin/account-role-requests?status=Pending`
- `POST /api/admin/account-role-requests/{id}/execute`
- `POST /api/admin/account-role-requests/{id}/report-error` (body: lý do — endpoint suy đoán, cần xác nhận thêm)

## Edge case / Lưu ý UX
- Toàn bộ thao tác đổi role + clear/xoá assignment cũ (nếu account đã tồn tại và đang giữ role khác) phải nằm trong **1 transaction** — tránh để lại data-scope "mồ côi"
- Admin **không quyết định nghiệp vụ** ai giữ role gì — chỉ nên thiết kế UI thuần "thực thi", không có khái niệm Approve/Reject như các queue khác (FM duyệt request) để tránh gây hiểu lầm vai trò
