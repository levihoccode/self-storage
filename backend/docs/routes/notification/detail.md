# GET /api/notifications/{notificationId}

Chi tiết một thông báo của account đang đăng nhập — gồm đầy đủ `body`.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`; chỉ chủ tài nguyên.
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- 404 khi notification không tồn tại **hoặc** thuộc account khác — không phân biệt hai case để tránh dò sự tồn tại.

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/2
# {"message":"Lấy chi tiết thông báo thành công","data":{"id":2,"type":"OTHER","title":"Bảo trì khẩn cấp","body":"Cơ sở tạm đóng để bảo trì ngày mai.","readAt":null,"createdAt":"2026-10-05T07:37:09.677410Z"}}

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/999999
# {"message":"Không tìm thấy thông báo","timestamp":"2026-10-05T07:37:09.909897330Z"}
```

## Liên quan

- [list.md](list.md) · [mark-read.md](mark-read.md) · [routes.md](../routes.md) · [index.md](../index.md)
