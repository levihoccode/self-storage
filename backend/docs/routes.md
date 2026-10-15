---
purpose: Index toàn bộ HTTP route đang cung cấp — method, path, quyền, link chi tiết.
---
# Route Index

Toàn bộ HTTP route backend đang cung cấp. Chi tiết contract từng route nằm trong `routes/`.

| Method | Path | Quyền | Mô tả | Chi tiết |
|---|---|---|---|---|
| GET | `/api/health` | Public | Kiểm tra ứng dụng còn sống | [routes/health.md](routes/health.md) |
| POST | `/api/auth/login` | Public | Đăng nhập, nhận access token (JWT) | [routes/auth/login.md](routes/auth/login.md) |
| GET | `/api/auth/me` | Bearer + `ACCOUNT_ACTIVE` | Xác minh session hiện tại | [routes/auth/me.md](routes/auth/me.md) |
| GET | `/api/facility-access/ping/{facilityId}` | Bearer + `ACCOUNT_ACTIVE`; `facility.access` + facility scope | Endpoint demo kiểm tra RBAC và quyền theo cơ sở | [routes/identity/facility-access-ping.md](routes/identity/facility-access-ping.md) |
| GET | `/api/notifications` | Bearer + `ACCOUNT_ACTIVE` | Danh sách thông báo của tôi (lọc `is_read`, phân trang) | [routes/notification/list.md](routes/notification/list.md) |
| GET | `/api/notifications/{notificationId}` | Chủ tài nguyên | Chi tiết thông báo | [routes/notification/detail.md](routes/notification/detail.md) |
| GET | `/api/notifications/unread-count` | Bearer + `ACCOUNT_ACTIVE` | Số thông báo chưa đọc | [routes/notification/unread-count.md](routes/notification/unread-count.md) |
| POST | `/api/notifications` | Bearer + `ACCOUNT_ACTIVE`; `notification.create_other` (ADMIN/BOM) | Tạo thông báo `OTHER` thủ công | [routes/notification/create-other.md](routes/notification/create-other.md) |
| PATCH | `/api/notifications/{notificationId}/read` | Chủ tài nguyên | Đánh dấu một thông báo đã đọc | [routes/notification/mark-read.md](routes/notification/mark-read.md) |
| PATCH | `/api/notifications/read-all` | Bearer + `ACCOUNT_ACTIVE` | Đánh dấu tất cả đã đọc | [routes/notification/read-all.md](routes/notification/read-all.md) |
| GET | `/api/proposals` | Bearer + `ACCOUNT_ACTIVE`; chủ tài nguyên | Đề xuất khoang của tôi (lọc `status`) | [routes/booking/proposals-list.md](routes/booking/proposals-list.md) |
| POST | `/api/proposals/{proposalId}/agree` | Bearer + `ACCOUNT_ACTIVE`; chủ tài nguyên | Đồng ý đề xuất, tạo hoá đơn đặt cọc | [routes/booking/proposal-agree.md](routes/booking/proposal-agree.md) |
| POST | `/api/proposals/{proposalId}/reject` | Bearer + `ACCOUNT_ACTIVE`; chủ tài nguyên | Từ chối đề xuất; lần thứ `max_rejection_count` huỷ đơn | [routes/booking/proposal-reject.md](routes/booking/proposal-reject.md) |
| GET | `/api/fm/rental-orders/{orderId}` | `FM`; `rental_order.read` + facility scope | Chi tiết đơn để đề xuất lại | [routes/booking/fm-rental-order-detail.md](routes/booking/fm-rental-order-detail.md) |
| POST | `/api/fm/rental-orders/{orderId}/cancel` | `FM`; `rental_order.cancel` + facility scope | FM huỷ đơn (khách không còn nhu cầu), chạy Cancellation Cascade | [routes/booking/fm-rental-order-cancel.md](routes/booking/fm-rental-order-cancel.md) |
| POST | `/api/rental-orders/{orderId}/proposals` | Bearer + `ACCOUNT_ACTIVE`; `proposal_feedback.repropose` (+ `.override`) + facility scope | Đề xuất lại khoang cho đơn | [routes/booking/rental-order-proposals-create.md](routes/booking/rental-order-proposals-create.md) |
| GET | `/api/payments/vnpay/ipn` | Public (chữ ký VNPay) | VNPay xác nhận thanh toán — nguồn xác nhận duy nhất | [routes/payment/vnpay-ipn.md](routes/payment/vnpay-ipn.md) |
| GET | `/api/payments/vnpay/return` | Public (redirect VNPay) | Trang kết quả cho khách — không ghi dữ liệu | [routes/payment/vnpay-return.md](routes/payment/vnpay-return.md) |
| POST | `/api/media/uploads` | Bearer + `ACCOUNT_ACTIVE` | Upload file (ảnh/PDF ≤ 10MB), trả URL dùng lại trong flow | [routes/media/upload.md](routes/media/upload.md) |

Ghi chú:

- Thêm route mới → bắt đầu từ template [templates/route.md](templates/route.md),
  thêm một dòng vào bảng này **và** tạo file chi tiết trong `routes/`.
- Quy ước chung (base URL, error body, 401 vs 403): xem
  [index.md › Quy ước data contract](index.md#quy-ước-data-contract).
