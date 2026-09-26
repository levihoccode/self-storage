# Quản lý cơ sở (Facility)

- **Route:** `/bom/facilities`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** 5.1 (branch `specs/flow-5`)

## Mục đích
BOM là người duy nhất có quyền tạo mới cơ sở/chi nhánh và gán FM phụ trách.

## Navigation

- **Vào từ:** BOM shell hoặc system report.
- **Đi tới:** facility detail/edit surface; `/bom/staff-requests`; `/bom/reports`; `/notifications` sau assignment/status change.

## Dữ liệu hiển thị
- Danh sách `Facility`: `code` (mã chi nhánh, vd "Q7"), `name`, `address`, `phone`, `operating_hours`, `fm_account_id` (tên FM phụ trách, có thể trống), `status` (Active/Inactive)

## Actions
- [Tạo cơ sở mới] — nhập thông tin cơ bản, mặc định `status = Inactive`
- [Gán/Đổi FM phụ trách] — set `Facility.fm_account_id`:
  - Nếu đã có account FM rảnh: chọn trực tiếp
  - Nếu chưa có: chuyển sang `bom/06-staff-role-request.md` để chỉ định role FM cho 1 người rồi gửi Admin xử lý, sau đó quay lại đây gán
- [Kích hoạt] (`Active`) — chỉ enable khi `fm_account_id` đã có giá trị
- [Vô hiệu hoá] (không xoá cứng — còn liên kết `StorageUnit`, `RentalOrder`...)

## States / UI trạng thái
- Nút [Kích hoạt] bị disable + tooltip giải thích khi chưa có FM
- Badge trạng thái Active/Inactive

## API liên quan
- `GET /api/bom/facilities`
- `POST /api/bom/facilities`
- `PATCH /api/bom/facilities/{id}/fm` (gán FM)
- `PATCH /api/bom/facilities/{id}/status`

## Edge case / Lưu ý UX
- Facility `Inactive` **không hiển thị cho khách ở Flow 1** và FM không tạo được `StorageUnit` cho tới khi `Active`
- Quan hệ FM–Facility là **1–1**, `Facility.fm_account_id` là field duy nhất lưu quan hệ này trong toàn hệ thống (không có field ngược trên `Account`)
- Khi đổi FM sang role khác, `fm_account_id` được hệ thống tự động clear về null trong cùng transaction (xử lý ở phía Admin — trang này chỉ cần refetch để thấy cập nhật)
