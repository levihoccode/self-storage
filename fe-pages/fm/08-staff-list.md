# Danh sách nhân viên (FS)

- **Route:** `/fm/staff`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 5.3 (branch `specs/flow-5`)

## Mục đích
FM xem danh sách FS được gán vào cơ sở mình phụ trách, làm cơ sở để phân công công việc ở các trang khác (lịch hẹn, return/support request).

## Navigation

- **Vào từ:** FM shell hoặc `/fm/appointments`.
- **Đi tới:** `/fm/appointments` để phân công; `/staff/schedule` là workspace của FS; `/fm/support-requests` và `/fm/return-requests` để xem workload.

## Dữ liệu hiển thị
- Danh sách FS qua `AccountFacilityAssignment WHERE facility_id = FM.facility`: tên, email, `assigned_at`
- Số lượng công việc đang xử lý của mỗi FS (tổng hợp từ `Appointment`/`SupportRequest`/`ReturnRequest` đang mở, nếu BE hỗ trợ — không thấy chốt rõ trong tài liệu, đề xuất bổ sung)

## Actions
- Xem chi tiết 1 FS — danh sách công việc đang được gán (tham chiếu, chỉ đọc)
- MVP **không có** nút thêm/xoá FS ở đây — việc gán FS vào facility thuộc quyền Admin (`admin/01-account-management.md`), FM chỉ xem

## States / UI trạng thái
- Empty state: chưa có FS nào được gán vào facility

## API liên quan
- `GET /api/fm/staff`

## Edge case / Lưu ý UX
- FM không có quyền tạo/xoá tài khoản FS — nếu cần thêm nhân sự, FM gửi yêu cầu qua BOM (`bom/06-staff-role-request.md`), không thao tác trực tiếp ở trang này
- MVP scope: chỉ xem danh sách + việc đang gán, **chưa quản lý ca làm việc (shift)** chi tiết
