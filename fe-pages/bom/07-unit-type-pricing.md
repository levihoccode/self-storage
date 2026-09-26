# Quản lý loại kho & giá thuê

- **Route:** `/bom/unit-types`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** `UnitType` (db-table-draft.md); BOM là owner giá theo Flow 5 ("Giá thuê theo UnitType — thuộc Flow 4, Flow 5 chỉ đọc")

## Mục đích
BOM định nghĩa các loại khoang chứa (kích thước, mô tả) và quản lý mức giá thuê áp dụng toàn hệ thống — đây là nguồn giá **duy nhất**, FM không tự đặt giá cho từng khoang.

## Navigation

- **Vào từ:** BOM shell hoặc revenue/facility context.
- **Đi tới:** unit type detail/edit surface; public `/units` để preview customer-facing catalog; `/bom/revenue`; `/notifications` sau cập nhật giá.

## Dữ liệu hiển thị
- Danh sách `UnitType`: `name` (Small/Medium/Large), `width`/`depth`/`height`/`area`, `description`, `monthly_price`, `updated_by`, `updated_at`

## Actions
- [Tạo loại kho mới]
- [Cập nhật giá] — đổi `monthly_price`, ghi `updated_by`/`updated_at`

## States / UI trạng thái
- Cảnh báo rõ khi đổi giá: "Thay đổi này chỉ áp dụng cho khoang chưa ký hợp đồng — các hợp đồng đã ký giữ nguyên `monthly_price` đã chốt lúc ký" (theo `RentalContract.monthly_price` NOTES)

## API liên quan
- `GET /api/bom/unit-types`
- `POST /api/bom/unit-types`
- `PATCH /api/bom/unit-types/{id}/price`

## Edge case / Lưu ý UX
- Nếu mỗi chi nhánh cần giá khác nhau theo `UnitType`, hiện tại schema **chưa hỗ trợ** (ghi chú "nếu cần thì tách bảng riêng theo `facility_id` + `unit_type_id`") — MVP dùng 1 giá áp dụng toàn hệ thống cho mỗi loại kho, không có UI chọn theo chi nhánh
- Đổi giá ở đây **không** ảnh hưởng ngược tới các `StorageUnit` đang hiển thị cho FM — FM chỉ đọc giá hiện tại khi tạo khoang mới, không có action đồng bộ lại giá cho khoang cũ
