---
auth-model: ownership
purpose: Số thông báo chưa đọc của account đang đăng nhập (badge chuông).
---
# GET /api/notifications/unread-count

Số thông báo chưa đọc của account đang đăng nhập — cho badge trên chuông.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`.
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Đếm `read_at IS NULL`; dùng index `(account_id, read_at)`.

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/unread-count
# {"message":"Lấy số thông báo chưa đọc thành công","data":{"unreadCount":0}}
```

## Liên quan

- [mark-read.md](mark-read.md) · [read-all.md](read-all.md) · [routes.md](../routes.md) · [index.md](../index.md)
