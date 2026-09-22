# Quản lý discount

- **Route:** `/bom/discounts`
- **Actor:** Business Operation Manager
- **Flow tham chiếu:** `Discount` entity (db-table-draft.md) — chưa có mô tả UI/flow chi tiết ở branch nào, được liệt kê là "not MVP / có thể cắt" trong issue pre-merge report Flow-3 (#9)

## Mục đích
BOM quản lý các chương trình giảm giá áp dụng cho hóa đơn (đặt cọc, tiền thuê, gia hạn).

## Navigation

- **Vào từ:** BOM shell hoặc invoice/policy context.
- **Đi tới:** discount detail/edit surface; `/bom/policies`; `/bom/revenue`; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `Discount`: `code` (unique), `name`, `discount_type` (Percent/Fixed), `value`, `apply_to` (Deposit/Rental/Extension/All), `min_months` (nullable), `start_at`, `end_at`, `is_active`

## Actions
- [Tạo discount mới] — nhập đầy đủ field trên
- [Deactivate]/[Activate]

## States / UI trạng thái
- Validate: nếu `discount_type = Percent` thì `value ∈ [0,100]`; `start_at < end_at`

## API liên quan
- `GET /api/bom/discounts`
- `POST /api/bom/discounts`
- `PATCH /api/bom/discounts/{id}/status`

## Edge case / Lưu ý UX
- **Lưu ý MVP:** báo cáo pre-merge Flow 3 (issue #9) đề xuất **có thể omit hoàn toàn `Discount` khỏi MVP**, để dành cho giai đoạn sau — trước khi thiết kế UI chi tiết, nên xác nhận với team xem trang này có nằm trong scope đợt đầu không
- Nếu triển khai, `Invoice.discount_amount` là field đã có sẵn để lưu số tiền giảm — trang này chỉ là nơi BOM cấu hình rule, việc áp dụng discount vào từng hóa đơn cụ thể là logic ở BE khi tạo `Invoice`
