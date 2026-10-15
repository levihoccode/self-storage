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
| POST | `/api/rental-requests` | Public | Gửi yêu cầu đặt kho (form public, B1) | [routes/booking/rental-request-create.md](routes/booking/rental-request-create.md) |
| GET | `/api/fm/rental-requests` | Bearer + role `FM`; `rental_request.read` + facility scope | Hàng đợi yêu cầu đặt kho (B1) | [routes/booking/fm-rental-request-queue.md](routes/booking/fm-rental-request-queue.md) |
| GET | `/api/unit-types` | Public | Loại khoang cơ sở cung cấp cho form (B1) | [routes/pricing/unit-types.md](routes/pricing/unit-types.md) |
| POST | `/api/fm/rental-requests/{id}/approve` | Bearer + role `FM`; `rental_request.approve` + facility scope | Duyệt yêu cầu đặt kho, gán khoang (B1, dừng ở Approved) | [routes/booking/fm-rental-request-approve.md](routes/booking/fm-rental-request-approve.md) |
| POST | `/api/fm/rental-requests/{id}/reject` | Bearer + role `FM`; `rental_request.reject` + facility scope | Từ chối yêu cầu đặt kho + mail (B1, dừng ở Rejected) | [routes/booking/fm-rental-request-reject.md](routes/booking/fm-rental-request-reject.md) |
| PATCH | `/api/notifications/{notificationId}/read` | Chủ tài nguyên | Đánh dấu một thông báo đã đọc | [routes/notification/mark-read.md](routes/notification/mark-read.md) |
| PATCH | `/api/notifications/read-all` | Bearer + `ACCOUNT_ACTIVE` | Đánh dấu tất cả đã đọc | [routes/notification/read-all.md](routes/notification/read-all.md) |

Ghi chú:

- Thêm route mới → bắt đầu từ template [templates/route.md](templates/route.md),
  thêm một dòng vào bảng này **và** tạo file chi tiết trong `routes/`.
- Quy ước chung (base URL, error body, 401 vs 403): xem
  [index.md › Quy ước data contract](index.md#quy-ước-data-contract).
