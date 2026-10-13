---
auth-model: ownership
purpose: Khách đồng ý đề xuất khoang — gán khoang vào đơn và tạo hoá đơn đặt cọc.
---
# POST /api/customer/proposals/{proposalId}/agree

Khách bấm "Đồng ý" trên một proposal `Pending`.

- **Actor / quyền:** role-bound `CUSTOMER` + ownership (proposal phải của khách đang đăng nhập, sai → 403).
- **Contract chi tiết:** annotation tại `booking/controller/CustomerProposalController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Một transaction: khoá row proposal (bấm đúp / 2 tab → request sau nhận 409), khoá row khoang theo
  `unit_id`.
- Khoang còn `Available`: proposal `Agreed`, `RentalOrder.unit_id` = khoang đó, tạo `Invoice`
  `Deposit`/`Unpaid`, AuditLog `PROPOSAL_AGREED` + `INVOICE_CREATED`.
- Khoang `Reserved`/`Maintenance`: proposal `Canceled`, FM nhận `PROPOSAL_REPROPOSAL_REQUIRED`, response 409 —
  hai thay đổi này **vẫn được commit** (service trả kết quả, không ném exception).

## Ghi chú nghiệp vụ

- Hoá đơn: code `INV-DEP-{facility_code}-{YYMMDD}-{rand 6 ký tự}`, title "Đặt cọc khoang chứa {mã}",
  amount = giá thuê hiệu lực × `invoice.deposit_months` (chốt #113, seed 1), `due_date` =
  min(now + `invoice.deposit_due_days`, `RentalOrder.expires_at`).
- `Canceled` thay `Expired` của spec (chốt #113); spec sẽ cập nhật sau review.

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customer/proposals/1/agree
# {"message":"Đã đồng ý khoang. Vui lòng thanh toán hoá đơn đặt cọc","data":{"proposal":{"id":1,"orderId":1,"customerId":6,"unitId":1,"status":"Agreed","note":null,"createdAt":"2026-10-13T09:32:41.202069Z"},"invoice":{"id":1,"orderId":1,"code":"INV-DEP-Q7-261013-GD6UVE","title":"Đặt cọc khoang chứa A-01","description":"Thanh toán đặt cọc khoang chứa A-01 để đảm bảo giữ chỗ.","type":"Deposit","status":"Unpaid","amount":500000.00,"dueDate":"2026-10-16T09:32:51.078127500Z"}}}

# Gọi lại lần 2
# {"message":"Đề xuất này đã được xử lý","timestamp":"2026-10-13T09:32:51.222739500Z"}

# Proposal 2 trỏ khoang C-01 đang Maintenance
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customer/proposals/2/agree
# {"message":"Khoang đã có người đặt cọc / không khả dụng","timestamp":"2026-10-13T09:32:50.961085100Z"}
```

## Liên quan

- [customer-proposals-list.md](customer-proposals-list.md) · [routes.md](../../routes.md) · [index.md](../../index.md)
