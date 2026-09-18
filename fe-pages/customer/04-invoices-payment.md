# Hóa đơn & thanh toán

- **Route:** `/customer/invoices` (danh sách) + `/customer/invoices/{id}` (chi tiết/thanh toán)
- **Actor:** Customer (đã đăng nhập)
- **Flow tham chiếu:** 1.4 (đặt cọc), dùng chung cho mọi loại hóa đơn xuyên suốt hệ thống (Deposit/Rental/Extension/Penalty/Service — xem `Invoice` trong db-table-draft.md)

## Mục đích
Trang trung tâm để khách xem và thanh toán mọi loại hóa đơn phát sinh trong suốt vòng đời thuê kho: cọc, tiền thuê tháng đầu, gia hạn, phạt, dịch vụ.

## Dữ liệu hiển thị
- Danh sách `Invoice WHERE customer_id = current_user`, mỗi dòng: `code`, `type` (badge màu riêng theo Deposit/Rental/Extension/Penalty/Service), `title`, `amount`, `discount_amount`, `status` (Unpaid/Paid/Canceled), `due_date`
- Chi tiết 1 hóa đơn: `desc`, liên kết ngược tới `order_id`/`contract_id` (hiển thị tên khoang, hợp đồng liên quan)

## Actions
- [Tiến hành thanh toán] (chỉ hiện khi `status = Unpaid`) → redirect sang cổng VNPay
- Filter theo `type`, `status`

## States / UI trạng thái
- Badge trạng thái: Unpaid (vàng), Paid (xanh), Canceled (xám)
- Loading khi đang chuyển hướng sang cổng thanh toán
- Trang kết quả thanh toán (return từ VNPay): Thành công / Thất bại — chỉ mang tính hiển thị, trạng thái thật lấy từ IPN webhook (không tin `vnp_ReturnUrl` để cập nhật UI ngay, cần poll hoặc refetch trạng thái hóa đơn)

## API liên quan
- `GET /api/customer/invoices?type=&status=`
- `GET /api/customer/invoices/{id}`
- `POST /api/invoices/{id}/pay` → tạo `PaymentTransaction(Pending)`, trả URL redirect VNPay
- Webhook `POST /api/webhooks/vnpay` (BE xử lý, FE không gọi trực tiếp)

## Edge case / Lưu ý UX
- Trước khi cho thanh toán hóa đơn Deposit, BE kiểm tra lại trạng thái khoang (đã bị người khác cọc / đang bảo trì / đang có giao dịch khác xử lý) — FE cần hiển thị đúng 3 thông báo lỗi cụ thể theo flow 1.4 thay vì lỗi chung chung
- Hóa đơn `Canceled` (do request/order gốc bị hủy hoặc hết hạn) không cho thao tác gì, chỉ xem lịch sử
- `due_date` gần hết hạn nên có cảnh báo màu đỏ/đếm ngược, đặc biệt hóa đơn Deposit (nếu quá hạn → `RentalOrder` bị hủy)
- Idempotency: nếu khách bấm thanh toán 2 lần liên tiếp (2 tab), BE có cơ chế khóa theo `invoice_id` — FE nên disable nút sau khi bấm để giảm khả năng va chạm
