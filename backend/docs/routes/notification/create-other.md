---
auth-model: permission-bound
purpose: Tạo thông báo OTHER thủ công cho một account (ADMIN/BOM).
---
# POST /api/notifications

Tạo thông báo `OTHER` thủ công (khẩn cấp) cho một account theo email.

- **Actor / quyền:** bearer token + `ACCOUNT_ACTIVE`; `Access.can("notification.create_other")` kiểm
  tra permission qua `role_permissions` (ADMIN, BOM) — [catalog](../../permission-catalog.md).
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
ADMIN_TOKEN='<token của admin@lemar.vn>'
CUST_TOKEN='<token của customer1@lemar.vn>'

# 200 — admin tạo cho khách theo email
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"customer1@lemar.vn","title":"Bảo trì khẩn cấp","body":"Kho tạm đóng để bảo trì ngày mai."}'
# {"message":"Tạo thông báo thành công","data":{"id":1,"type":"OTHER","title":"Bảo trì khẩn cấp","body":"Kho tạm đóng để bảo trì ngày mai.","readAt":null,"createdAt":"2026-10-08T14:33:41.699472688Z","orderId":null}}

# 404 — email không thuộc account nào
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"khong-ton-tai@example.com","title":"x","body":"y"}'
# {"message":"Không tìm thấy tài khoản","timestamp":"2026-10-08T14:33:48.819687103Z"}

# 403 — customer không có quyền tạo
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"customer1@lemar.vn","title":"x","body":"y"}'
# {"message":"Bạn không có quyền thực hiện thao tác này","timestamp":"2026-10-08T14:33:48.836112910Z"}
```

## Liên quan

- [list.md](list.md) · [routes.md](../routes.md) · [index.md](../index.md)
