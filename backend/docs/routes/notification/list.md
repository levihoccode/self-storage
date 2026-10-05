# GET /api/notifications

Danh sách thông báo web của account đang đăng nhập — nguồn cho notification center (chuông + trang đầy đủ).

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`; chỉ account hiện tại (accountId lấy từ principal, không nhận từ caller).
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Trả summary **không gồm `body`**, sắp xếp `created_at DESC`.
- Phân trang `page` (mặc định 0) / `size` (mặc định 20, tối đa 100).
- Lọc trạng thái đọc: `is_read=true|false`; bỏ trống = tất cả.
- `orderId` trả kèm khi thông báo gắn đơn (`OrderNotification`), null nếu không — FE dùng để mở trang đơn.

## Ghi chú nghiệp vụ

- Chỉ đọc được thông báo của account hiện tại — không có route xem hộ account khác.
- `RENTAL_ORDER_EXPIRING_SOON` chỉ gửi một lần cho mỗi đơn (dedup qua liên kết `OrderNotification`).

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications
# {"message":"Lấy danh sách thông báo thành công","data":[{"id":6,"type":"OTHER","title":"Hello","readAt":null,"createdAt":"2026-10-05T12:20:45.673829Z","orderId":null},{"id":5,"type":"OTHER","title":"Bảo trì khẩn cấp","readAt":null,"createdAt":"2026-10-05T12:20:39.012401Z","orderId":null}]}

curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/notifications?is_read=true'
# {"message":"Lấy danh sách thông báo thành công","data":[{"id":6,"type":"OTHER","title":"Hello","readAt":"2026-10-05T12:20:50.937434Z","createdAt":"2026-10-05T12:20:45.673829Z","orderId":null}]}
```

## Liên quan

- [detail.md](detail.md) · [unread-count.md](unread-count.md) · [routes.md](../routes.md) · [index.md](../index.md)
