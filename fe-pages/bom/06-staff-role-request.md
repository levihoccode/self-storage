# Chỉ định nhân sự (Role Request)

- **Route:** `/bom/staff-requests`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** 5.1 (branch `specs/flow-5`)

## Mục đích
BOM quyết định role (FM/FS) cho nhân sự và gửi danh sách lên Admin để Admin thực thi kỹ thuật (tạo/cập nhật account). BOM **quyết định nghiệp vụ**, Admin **chỉ thực thi**.

## Dữ liệu hiển thị
- Form tạo batch `AccountRoleRequest`: mỗi dòng gồm họ tên, email, `role` (FM hoặc FS), `target_facility_id`
- Lịch sử các batch đã gửi: `status` (Pending/Done), `account_id` (gán sau khi Admin xử lý xong)

## Actions
- [Thêm dòng] (nhập nhiều nhân sự trong 1 lần gửi — dạng batch)
- [Gửi lên Admin] → tạo các bản ghi `AccountRoleRequest(status=Pending)`
- Sau khi Admin xử lý xong dòng **FM**: BOM quay lại `bom/05-facility-management.md` để tự gán `fm_account_id` (Admin không làm bước này)
- Dòng **FS**: Admin đã tự gán facility luôn, BOM không cần thao tác thêm

## States / UI trạng thái
- Badge trạng thái từng dòng: Pending (đang chờ Admin xử lý) / Done
- Trạng thái batch tổng: hiển thị số dòng Done/Pending trong 1 batch

## API liên quan
- `POST /api/bom/account-role-requests` (batch)
- `GET /api/bom/account-role-requests?batch_id=`

## Edge case / Lưu ý UX
- BOM **không có quyền tạo account trực tiếp** — chỉ chỉ định role và gửi yêu cầu, việc thực thi kỹ thuật (tra cứu email, tạo mới/cập nhật account, gán facility cho FS) hoàn toàn thuộc Admin (`admin/02-account-role-request-queue.md`)
- Nếu dữ liệu 1 dòng bị lỗi (email sai định dạng...), Admin sẽ báo lỗi ngược lại — BOM cần trang này hiển thị được lý do lỗi để sửa và gửi lại, không chỉ có 2 trạng thái Pending/Done đơn thuần (cân nhắc bổ sung trạng thái lỗi khi làm chi tiết, dù `AccountRoleRequest` trong schema hiện chỉ có 2 status)
