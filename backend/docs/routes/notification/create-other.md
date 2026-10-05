# POST /api/notifications

Tạo thông báo `OTHER` thủ công (khẩn cấp) cho một account.

- **Actor / quyền:** bearer token + role `ADMIN` hoặc `BOM` (chặn tại `SecurityConfig`).
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Body: `recipientAccountId` (bắt buộc, account phải tồn tại — 404 nếu không), `title`, `body`.
- Type cố định `OTHER` — chỉ dùng khi chưa có type nghiệp vụ phù hợp.
- Ghi bản ghi web + gửi email (layout HTML chung, gửi sau commit) cho account nhận; email lỗi
  không rollback bản ghi web — xem `index.md › Gửi email (module notification)`.
- `OTHER` không gắn đơn nên `orderId` trong response luôn null.

## Ví dụ

```bash
ADMIN_TOKEN='<token của admin@lemar.vn>'
CUST_TOKEN='<token của customer1@lemar.vn>'

# 200 — admin tạo cho customer1 (accountId = 6)
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientAccountId":6,"title":"Bảo trì khẩn cấp","body":"Cơ sở tạm đóng để bảo trì ngày mai."}'
# {"message":"Tạo thông báo thành công","data":{"id":5,"type":"OTHER","title":"Bảo trì khẩn cấp","body":"Cơ sở tạm đóng để bảo trì ngày mai.","readAt":null,"createdAt":"2026-10-05T12:20:39.012401376Z","orderId":null}}

# 403 — customer không có quyền tạo
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientAccountId":1,"title":"x","body":"y"}'
# {"message":"Bạn không có quyền truy cập","timestamp":"2026-10-05T07:37:09.923125581Z"}
```

## Liên quan

- [list.md](list.md) · [routes.md](../routes.md) · [index.md](../index.md)
