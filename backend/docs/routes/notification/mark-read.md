# PATCH /api/notifications/{notificationId}/read

Đánh dấu một thông báo là đã đọc — FE gọi khi user bấm vào item.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`; chỉ chủ tài nguyên.
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Idempotent: đã đọc rồi thì trả nguyên trạng, không ghi lại `read_at`.
- 404 khi notification không tồn tại hoặc thuộc account khác.

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -X PATCH -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/2/read
# {"message":"Đã đánh dấu thông báo là đã đọc","data":{"id":2,"type":"OTHER","title":"Bảo trì khẩn cấp","body":"Cơ sở tạm đóng để bảo trì ngày mai.","readAt":"2026-10-05T07:37:09.819877134Z","createdAt":"2026-10-05T07:37:09.677410Z"}}
```

## Liên quan

- [unread-count.md](unread-count.md) · [read-all.md](read-all.md) · [routes.md](../routes.md) · [index.md](../index.md)
