# Dashboard kho đang thuê ("Kho của tôi")

- **Route:** `/customer/contracts`
- **Actor:** Customer (đã đăng nhập)
- **Flow tham chiếu:** 3.1 (branch `specs/flow-3`)

## Mục đích
Trang tổng quan để khách xem toàn bộ hợp đồng đang `Active`, nhóm theo chi nhánh, nhanh chóng nhận biết hợp đồng nào sắp hết hạn/quá hạn.

## Dữ liệu hiển thị
- `RentalContract WHERE customer_id = current_user AND status = Active`, JOIN `StorageUnit`, `Facility`
- Nhóm kết quả theo `facility_id` (tách danh sách con theo từng chi nhánh — đã chốt)
- Mỗi item: tên/mã khoang, chi nhánh, `end_date`, tình trạng thanh toán (tổng hợp từ `Invoice`)
- Badge tính trực tiếp từ so sánh `today` vs `end_date`: "Còn hiệu lực" / "Sắp hết hạn" (trong N ngày, N = `Policy.contract.expiring_soon_days`) / "Quá hạn"

## Actions
- Bấm vào 1 hợp đồng → điều hướng `customer/07-contract-detail.md`
- Filter theo chi nhánh, khoảng thời hạn hợp đồng
- Sort theo ngày hết hạn gần nhất

## States / UI trạng thái
- Khách chỉ có 1 hợp đồng vẫn hiển thị dashboard bình thường (danh sách 1 item) — không tách luồng riêng
- Empty state: chưa có hợp đồng nào đang `Active` (gợi ý link sang trang đặt kho)
- Loading state khi fetch

## API liên quan
- `GET /api/customer/contracts` — trả về đã nhóm theo `facility_id`, kèm cờ `is_overdue`/`is_expiring_soon` tính sẵn ở BE

## Edge case / Lưu ý UX
- So sánh theo **ngày** (`today`), không phải timestamp `now` — tránh hiển thị quá hạn sớm ngay trong ngày cuối hợp đồng
- Không lưu trạng thái riêng cho "sắp hết hạn/quá hạn" trong DB — luôn tính lại tại thời điểm query, nên FE không nên cache lâu, cần refetch khi quay lại trang
- UI/UX trang này cần thiết kế kỹ vì đây là trang chính khách quay lại thường xuyên nhất trong Flow 3
