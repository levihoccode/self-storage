---
auth-model: permission-bound
purpose: FM đề xuất khoang khác cho đơn sau khi khách từ chối hoặc khoang hết khả dụng.
---
# POST /api/fm/rental-orders/{orderId}/re-propose

Body `{ "unitId": 3, "overrideReason": "..." }` — tạo `ProposalFeedback` `Pending` mới cho đơn.

- **Actor / quyền:** role-bound `FM` + permission `proposal_feedback.repropose` theo cơ sở của đơn; chọn lại
  khoang khách đã từ chối cần thêm `proposal_feedback.override`.
- **Contract chi tiết:** annotation tại `booking/controller/FmRentalOrderController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Thiếu `unitId` → 400. Đơn đã `Canceled`/`Done` → 409. Đơn đang có proposal `Pending` → 409 (spec 1.3 chưa
  nói proposal cũ xử lý ra sao — đang chặn, chưa hỏi).
- Khoá đơn, khoá khoang; khoang khác cơ sở → 404, khoang không `Available` → 409.
- Khoang khách đã từ chối trong đơn: thiếu `overrideReason` → 400; có thì lý do ghi vào AuditLog
  `PROPOSAL_REPROPOSED`.
- Proposal mới `Pending`, bản cũ giữ làm lịch sử; khách nhận `PROPOSAL_REPROPOSED` kèm link
  `<FRONTEND_URL>/proposals`.

## Ví dụ

```bash
TOKEN='<token của fm1@lemar.vn>'

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":2}' http://localhost:8080/api/fm/rental-orders/3/re-propose
# {"message":"Khách đã từ chối khoang A-02, cần nhập lý do để đề xuất lại","timestamp":"2026-10-13T09:33:02.810066900Z"}

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":3}' http://localhost:8080/api/fm/rental-orders/3/re-propose
# {"message":"Đã gửi đề xuất khoang mới cho khách","data":{"id":4,"orderId":3,"customerId":6,"unitId":3,"status":"Pending","note":null,"createdAt":"2026-10-13T09:33:02.913901400Z"}}

# Gọi tiếp khi proposal 4 còn Pending
# {"message":"Đơn đang có đề xuất chờ khách phản hồi","timestamp":"2026-10-13T09:33:03.044071700Z"}

# Override (body UTF-8 từ file): {"unitId":2,"overrideReason":"Khách gọi lại muốn lấy khoang A-02"}
# {"message":"Đã gửi đề xuất khoang mới cho khách","data":{"id":5,"orderId":3,"customerId":6,"unitId":2,"status":"Pending","note":null,"createdAt":"2026-10-13T09:33:22.115929800Z"}}

# Sau khi đơn bị huỷ
# {"message":"Đơn thuê đã đóng, không thể đề xuất khoang","timestamp":"2026-10-13T09:33:22.687253300Z"}
```

## Liên quan

- [fm-rental-order-detail.md](fm-rental-order-detail.md) · [routes.md](../../routes.md) · [index.md](../../index.md)
