# Quản lý các loại phí

- **Route:** `/bom/fees`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** 4.2 (branch `specs/flow-4`)

## Mục đích
BOM thiết lập và quản lý danh mục các khoản phí (phạt, dịch vụ, phụ phí) dùng để tạo hóa đơn xuyên suốt hệ thống.

## Navigation

- **Vào từ:** BOM shell hoặc policy/invoice context.
- **Đi tới:** fee detail/edit surface; `/bom/policies`; `/bom/reports`; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `Fee`: `id`, `name`, `category`, `amount`, `calculation` (fixed/daily/monthly/%), `desc`, `status`, `created_by`, `created_at`
- Ví dụ: "Mất chìa khóa" (LOST-KEY, 50k, fixed); "Wifi" (WIFI, 2tr, monthly)

## Actions
- [Add] / [View → Edit] — cùng 1 trang, phân biệt bằng ID đã tồn tại hay chưa (giống trang Business Rules)
- **[Delete] bị khóa nếu fee đã từng được sử dụng** (đã gắn vào ít nhất 1 hóa đơn) — chỉ cho [Deactivate]

## States / UI trạng thái
- Badge cách tính phí (`calculation`): Fixed / Daily / Monthly / % / Lock — hiển thị công thức tương ứng ngay trên form để BOM dễ hình dung (vd: "amount × số ngày phát sinh")
- Case đặc biệt `calculation = LOCK`: dùng cho khóa tài khoản, `amount` = thời gian khóa (đơn vị theo `type`), MAX_VALUE = khóa vĩnh viễn

## API liên quan
- `GET /api/bom/fees`
- `POST /api/bom/fees` / `PUT /api/bom/fees/{id}`
- `PATCH /api/bom/fees/{id}/status`

## Edge case / Lưu ý UX
- Khung giá thuê/gia hạn **không** quản lý ở đây — đó là `UnitType.monthly_price`, xem `bom/07-unit-type-pricing.md`
- Đối với case khóa tài khoản (`calculation = LOCK`), bản ghi khóa được lưu vào bảng `Account disabled` riêng — trang này chỉ định nghĩa "loại phí gây khóa", không phải nơi xem danh sách tài khoản đang bị khóa (đó thuộc phạm vi Admin)
- Nội dung Payment/tạo hóa đơn cho fee chỉ mới ở dạng ý tưởng trong tài liệu flow-4 (chưa chi tiết đầy đủ như `Invoice` ở db-table-draft.md) — khi code cần đối chiếu lại 2 bản để tránh trùng lặp khái niệm hóa đơn
