# Quản lý business rules / chính sách

- **Route:** `/bom/policies`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** 4.1 (branch `specs/flow-4`) — **có xung đột thiết kế schema với `Policy` key-value trong db-table-draft.md, xem Edge case**

## Mục đích
BOM thiết lập và quản lý các chính sách vận hành áp dụng toàn hệ thống: đặt cọc, hủy, trả kho, gia hạn, xử lý quá hạn.

## Navigation

- **Vào từ:** BOM shell sau login.
- **Đi tới:** policy detail/edit surface; `/bom/fees`, `/bom/discounts`, `/bom/reports`; `/notifications` sau thay đổi cần theo dõi.

## Dữ liệu hiển thị
- Danh sách chính sách hiện hành (theo bản flow-4: bảng `rental-policies` — `id`, `name`, `deposit_type` ($/%), `deposit_value`, `cancel_policy`, `return_policy`, `renewal_policy`, `overdue_policy`, `effective_from`, `effective_to`, `status`, `created_by`, `created_at`)
- **Tại 1 thời điểm chỉ có 1 policy đang hoạt động** trên hệ thống

## Actions
- [Add] → trang trắng nhập thông tin chính sách mới
- [View] (từ list) → trang đã điền sẵn dữ liệu, cho phép edit (Add/Update dùng chung 1 trang, phân biệt bằng việc ID đã tồn tại hay chưa)
- [Deactivate]/[Activate], [Expire] (chức năng phụ)

## States / UI trạng thái
- Validate: `effective_from < effective_to`; nếu `deposit_type = %` thì `deposit_value ∈ [0,100]`
- Cảnh báo khi 2 policy có thời gian hiệu lực đan chéo — hệ thống ưu tiên policy có `effective_from` lớn hơn, UI nên cảnh báo rõ trước khi lưu thay vì âm thầm ghi đè

## API liên quan
- `GET /api/bom/policies`
- `POST /api/bom/policies` / `PUT /api/bom/policies/{id}`
- `PATCH /api/bom/policies/{id}/status`

## Edge case / Lưu ý UX
- **Xung đột chưa giải quyết giữa các branch:** `db-table-draft.md` (dùng ở Flow 3) định nghĩa `Policy` là bảng **key-value đơn giản** (`key`, `value`, `value_type`, `description`) dùng cho các giá trị như `contract.expiring_soon_days`, `overdue.fee_per_day`, `unit.maintenance_days`. Trong khi branch `specs/flow-4` lại đề xuất bảng `rental-policies` có cấu trúc field cố định, phức tạp hơn nhiều. **Đây là 1 trong các điểm được liệt kê ở issue #13 (tracker) cần BOM/team chốt trước khi code** — trang này tạm mô tả theo hướng flow-4 (rental-policies) vì đó là bản chi tiết nhất, nhưng FE cần chờ xác nhận cuối cùng
- Chính sách chỉ liên quan chi phí/vận hành kho, **không** liên quan tới quy định sử dụng ứng dụng của khách hàng
- Cus chỉ bị áp dụng policy đang hiệu lực tại thời điểm họ đồng ý lần đầu (snapshot `terms_version` trên `RentalContract`) — không hồi tố khi BOM đổi policy sau này
