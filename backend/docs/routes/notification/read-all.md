# PATCH /api/notifications/read-all

Đánh dấu tất cả thông báo chưa đọc của account hiện tại là đã đọc — nút [Đánh dấu đã đọc tất cả].

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`.
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Cập nhật mọi bản ghi `read_at IS NULL` của account; trả `unreadCount` sau cùng (luôn 0).

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -X PATCH -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/read-all
# {"message":"Đã đánh dấu tất cả thông báo là đã đọc","data":{"unreadCount":0}}
```

## Liên quan

- [mark-read.md](mark-read.md) · [unread-count.md](unread-count.md) · [routes.md](../routes.md) · [index.md](../index.md)
