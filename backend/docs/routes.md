# Route Index

Toàn bộ HTTP route backend đang cung cấp. Chi tiết contract từng route nằm trong `routes/`.

| Method | Path | Quyền | Mô tả | Chi tiết |
|---|---|---|---|---|
| GET | `/api/health` | Public | Kiểm tra ứng dụng còn sống | [routes/health.md](routes/health.md) |
| POST | `/api/auth/login` | Public | Đăng nhập, nhận access token (JWT) | [routes/auth/login.md](routes/auth/login.md) |
| GET | `/api/auth/me` | Bearer + `ACCOUNT_ACTIVE` | Xác minh session hiện tại | [routes/auth/me.md](routes/auth/me.md) |
| GET | `/api/notifications` | Bearer + `ACCOUNT_ACTIVE` | Danh sách thông báo của tôi (lọc `is_read`, phân trang) | [routes/notification/list.md](routes/notification/list.md) |
| GET | `/api/notifications/{notificationId}` | Chủ tài nguyên | Chi tiết thông báo | [routes/notification/detail.md](routes/notification/detail.md) |
| GET | `/api/notifications/unread-count` | Bearer + `ACCOUNT_ACTIVE` | Số thông báo chưa đọc | [routes/notification/unread-count.md](routes/notification/unread-count.md) |
| POST | `/api/notifications` | `ADMIN` / `BOM` | Tạo thông báo `OTHER` thủ công | [routes/notification/create-other.md](routes/notification/create-other.md) |
| PATCH | `/api/notifications/{notificationId}/read` | Chủ tài nguyên | Đánh dấu một thông báo đã đọc | [routes/notification/mark-read.md](routes/notification/mark-read.md) |
| PATCH | `/api/notifications/read-all` | Bearer + `ACCOUNT_ACTIVE` | Đánh dấu tất cả đã đọc | [routes/notification/read-all.md](routes/notification/read-all.md) |
| GET | `/api/payments/vnpay/ipn` | Public (chữ ký VNPay) | VNPay xác nhận thanh toán — nguồn xác nhận duy nhất | [routes/payment/vnpay-ipn.md](routes/payment/vnpay-ipn.md) |
| GET | `/api/payments/vnpay/return` | Public (redirect VNPay) | Trang kết quả cho khách — không ghi dữ liệu | [routes/payment/vnpay-return.md](routes/payment/vnpay-return.md) |

Ghi chú:

- Thêm route mới → bắt đầu từ template [templates/route.md](templates/route.md),
  thêm một dòng vào bảng này **và** tạo file chi tiết trong `routes/`.
- Quy ước chung (base URL, error body, 401 vs 403): xem
  [index.md › Quy ước data contract](index.md#quy-ước-data-contract).
