# Form yêu cầu đặt kho

- **Route:** `/rental-requests/new` (public, không cần đăng nhập)
- **Actor:** Customer (mới hoặc đã có tài khoản)
- **Flow tham chiếu:** 1.1 (draft.md, cả 2 phiên bản flow-1 và flow-3 base)

## Mục đích
Cho khách điền nhu cầu thuê kho mà không cần chỉ định khoang cụ thể — FM sẽ xét duyệt và gán khoang phù hợp sau.

## Navigation

- **Vào từ:** `/units`, CTA public hoặc link trực tiếp.
- **Đi tới:** trạng thái success; customer có thể sang `/register` nếu cần tạo tài khoản; quay lại `/units` để tiếp tục xem kho.

## Dữ liệu hiển thị / Input form
- `customer_name`, `customer_email` (normalize trước khi lưu), `customer_phone`
- `unit_type` (chọn từ danh sách `UnitType`)
- `facility` (chọn chi nhánh)
- `start_date` (MM/DD/YYYY)
- `period` — số tháng thuê

## Actions
- [Gửi yêu cầu] → tạo `RentalRequest(status=Pending)`
- Sau khi gửi: hiển thị màn hình xác nhận "Yêu cầu đã được gửi, chúng tôi sẽ phản hồi qua email/điện thoại"

## States / UI trạng thái
- Validate lỗi từng field (email hợp lệ, phone hợp lệ, `start_date` không ở quá khứ)
- Success state sau submit
- Lỗi submit (network/500)

## API liên quan
- `POST /api/public/rental-requests`

## Edge case / Lưu ý UX
- Không cho chỉ định `unit_id` cụ thể (theo thiết kế MVP — "không tối ưu layout khi để khách tự chọn")
- MVP chỉ cho 1 khoang / 1 request (không hỗ trợ multi-unit trong 1 lần gửi)
- Nên có rate-limit / captcha ở BE để chống spam form (được flow-1 branch note là "Advanced Features", nhưng nên cảnh báo FE cân nhắc UX chống double-submit — disable nút Gửi khi đang loading)
- Nếu khách đã đăng nhập sẵn, có thể prefill `customer_email`/`customer_phone` từ `Account`, và sau khi duyệt hệ thống tự liên kết vào tài khoản luôn (không cần qua bước xác minh email lại)
