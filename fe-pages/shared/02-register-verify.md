# Đăng ký & xác minh email

- **Route:** `/register`, `/verify-email?token=...`
- **Actor:** Customer only (FM/FS/BOM/Admin không tự đăng ký — xem `fe-pages/admin/01-account-management.md`)
- **Flow tham chiếu:** 1.2 (cả 2 phiên bản flow-1 và flow-3 base)

## Mục đích
Cho khách mới tạo tài khoản. Nếu email trùng với 1 `RentalRequest` đã được FM duyệt (`status=Approved`) trong thời hạn quy định, hệ thống tự động liên kết và tạo `RentalOrder` + `ProposalFeedback`/`Invoice` cọc ngay sau khi xác minh email.

## Dữ liệu hiển thị / Input form
- **Đăng ký:** họ tên, email, số điện thoại, mật khẩu
- **Xác minh:** trang thông báo "Vui lòng kiểm tra email" sau đăng ký; trang kết quả khi khách bấm link xác minh trong email

## Actions
- [Đăng ký] → tạo `Account(role=Customer)`, gửi email xác minh
- Bấm link trong email → xác minh, BE tự động chạy logic liên kết `RentalRequest` (Case A/B ở 1.2)
- Sau xác minh thành công: điều hướng khách tới trang phù hợp:
  - Có request được liên kết → `customer/03-my-proposals.md` (nếu dùng schema flow-1 có `ProposalFeedback`) hoặc trang Hóa đơn (nếu dùng schema flow-3 tạo thẳng `Invoice` cọc)
  - Không có request nào → `customer/01-browse-units.md`

## States / UI trạng thái
- Trạng thái "chưa xác minh" nếu khách cố đăng nhập trước khi bấm link email
- Lỗi email đã tồn tại khi đăng ký
- Lỗi token xác minh hết hạn/không hợp lệ → cho gửi lại email xác minh

## API liên quan
- `POST /api/auth/register`
- `GET /api/auth/verify-email?token=...`
- `POST /api/auth/resend-verification`

## Edge case / Lưu ý UX
- **Đơn chỉ được liên kết SAU khi khách click xác minh email** (chống chiếm đơn bằng email giả) — theo flow-1, KHÔNG liên kết ngay lúc đăng ký
- Hai schema đang có khác biệt giữa các branch (flow-3 base dùng thẳng `Invoice` cọc, flow-1 dùng thêm bước `ProposalFeedback` để khách duyệt online trước khi có hóa đơn cọc) — đây là điểm **chưa chốt giữa các flow**, cần xác nhận với team trước khi FE code cứng luồng điều hướng sau xác minh
- Nếu request đã quá `expires_at` (mặc định 7 ngày) trước khi khách đăng ký xong, hệ thống KHÔNG khôi phục — hiển thị rõ cho khách biết cần gửi yêu cầu đặt kho mới
