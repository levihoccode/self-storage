---
auth-model: permission-bound
purpose: FM đề xuất khoang khác cho đơn sau khi khách từ chối hoặc khoang hết khả dụng.
---
# POST /api/rental-orders/{orderId}/proposals

Body `{ "unitId": 3, "overrideReason": "..." }` — tạo `ProposalFeedback` `Pending` mới cho đơn.

- **Actor / quyền:** bearer token + `ACCOUNT_ACTIVE` (route trong `/api/**`, contract #113) + permission
  `proposal_feedback.repropose` theo cơ sở của đơn; chọn lại khoang khách đã từ chối cần thêm
  `proposal_feedback.override`. Role không có permission (vd khách) → 403.
- **Contract chi tiết:** annotation tại `booking/controller/RentalOrderProposalController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Thiếu `unitId` → 400.
- Điều kiện được đề xuất lại (chốt PR #136): đơn chưa đóng **và** không có proposal `Pending` **và** đơn đang
  `Pending` hoặc `needs_reproposal = true`. Sai một trong ba → 409 với câu tương ứng.
- Khoá đơn, khoá khoang; khoang khác cơ sở → 404, khoang không `Available` → 409.
- Khoang khách đã từ chối trong đơn: thiếu `overrideReason` → 400; có thì lý do ghi vào AuditLog
  `PROPOSAL_REPROPOSED`.
- Hoá đơn `Deposit` `Unpaid` còn treo của đơn (khách đã đồng ý khoang cũ nhưng chưa trả) → `Canceled` + AuditLog
  `INVOICE_CANCELED`. Proposal cũ giữ nguyên làm lịch sử.
- Proposal mới `Pending`; khách nhận `PROPOSAL_REPROPOSED` kèm link `<FRONTEND_URL>/proposals`.

## Ghi chú nghiệp vụ

- `needs_reproposal` do Flow 2 bật/tắt (vd khách từ chối khoang tại check-in); B3 chỉ đọc cờ này.
- Đơn còn proposal `Pending` (khách chưa phản hồi): giữ 409 — FM liên hệ khách ngoài hệ thống, không được thì
  huỷ đơn (#142).

## Ví dụ

```bash
TOKEN='<token của fm1@lemar.vn>'

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":2}' http://localhost:8080/api/rental-orders/3/proposals
# {"message":"Khách đã từ chối khoang A-02, cần nhập lý do để đề xuất lại","timestamp":"2026-10-15T15:04:26.454383700Z"}

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":3}' http://localhost:8080/api/rental-orders/3/proposals
# {"message":"Đã gửi đề xuất khoang mới cho khách","data":{"id":4,"orderId":3,"customerId":6,"unitId":3,"status":"Pending","note":null,"createdAt":"2026-10-15T15:04:26.551841800Z"}}

# Gọi tiếp khi proposal 4 còn Pending
# {"message":"Đơn đang có đề xuất chờ khách phản hồi","timestamp":"2026-10-15T15:04:26.673177400Z"}

# customer1@lemar.vn gọi (không có permission proposal_feedback.repropose)
# {"message":"Bạn không có quyền thực hiện thao tác này","timestamp":"2026-10-15T15:04:26.829600200Z"}

# Đơn 1: khách đã đồng ý A-01, hoá đơn cọc INV-DEP-Q7-261015-FCGZ4F còn Unpaid → FM đề xuất A-04
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":4}' http://localhost:8080/api/rental-orders/1/proposals
# {"message":"Đã gửi đề xuất khoang mới cho khách","data":{"id":5,"orderId":1,"customerId":6,"unitId":4,"status":"Pending","note":null,"createdAt":"2026-10-15T15:04:27.059277100Z"}}
# Sau đó: invoices.status của INV-DEP-Q7-261015-FCGZ4F = Canceled; audit_logs có INVOICE_CANCELED "FM đề xuất lại khoang khác".
```

## Liên quan

- [fm-rental-order-detail.md](fm-rental-order-detail.md) · [routes.md](../../routes.md) · [index.md](../../index.md)
