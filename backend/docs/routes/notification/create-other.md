---
auth-model: permission-bound
purpose: Tạo thông báo OTHER thủ công cho một account (ADMIN/BOM/FM).
---
# POST /api/notifications

Tạo thông báo `OTHER` thủ công (khẩn cấp) cho một account theo email.

- **Actor / quyền:** bearer token + `ACCOUNT_ACTIVE`; `Access.can("notification.create_other")` kiểm
  tra permission qua `role_permissions` (ADMIN, BOM, FM) — [catalog](../../permission-catalog.md).
- **Contract chi tiết:** annotation tại `notification/controller/NotificationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Body: `recipientEmail` (bắt buộc, phải thuộc account tồn tại — 404 nếu không), `title`, `body`.
- Type cố định `OTHER` — chỉ dùng khi chưa có type nghiệp vụ phù hợp.
- Ghi bản ghi web + gửi email (layout HTML chung, gửi sau commit) cho account nhận; email lỗi
  không rollback bản ghi web — xem `index.md › Gửi email (module notification)`.
- `OTHER` không gắn đơn nên `orderId` trong response luôn null.

## Ví dụ

```bash
FM_TOKEN='<token của fm1@lemar.vn>'
CUST_TOKEN='<token của customer1@lemar.vn>'

# 200 — FM tạo cho khách theo email
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $FM_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"hungthanh1412hz@gmail.com","title":"Thử gửi từ FM","body":"FM gửi thông báo thủ công cho khách qua email."}'
# {"message":"Tạo thông báo thành công","data":{"id":4,"type":"OTHER","title":"Thử gửi từ FM","body":"FM gửi thông báo thủ công cho khách qua email.","readAt":null,"createdAt":"2026-10-05T14:26:00.965787173Z","orderId":null}}

# 404 — email không thuộc account nào
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $FM_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"khong-ton-tai@example.com","title":"x","body":"y"}'
# {"message":"Không tìm thấy tài khoản","timestamp":"2026-10-05T14:26:25.055008592Z"}

# 403 — customer không có quyền tạo
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"hungthanh1412hz@gmail.com","title":"x","body":"y"}'
# {"message":"Bạn không có quyền thực hiện thao tác này", ...}
```

## Liên quan

- [list.md](list.md) · [routes.md](../routes.md) · [index.md](../index.md)
