---
auth-model: permission-bound
purpose: FM/ADMIN tạo nghĩa vụ hoàn tiền thủ công với breakdown và snapshot cách tính; chưa chi tiền.
---
# POST /api/refunds/obligations

Tạo **refund obligation** — nghĩa vụ hoàn tiền có final amount do server tính từ các dòng. Route
không tạo `refund_transactions`, không đổi invoice state và chưa phải bước handover.

- **Actor / quyền:** FM `refund_obligation.create` + facility scope; ADMIN override.
- **Contract chi tiết:** annotation tại `RefundObligationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Request

```json
{
  "invoiceId": 123,
  "orderId": 456,
  "reasonCode": "OTHER",
  "reasonNote": "Khách báo lỗi vận hành",
  "calculationVersion": "DEPOSIT_SETTLEMENT_V1",
  "calculationInput": {
    "paidDeposit": 1000000,
    "unpaidRent": 200000
  },
  "lines": [
    {"componentCode": "DEPOSIT_PAID", "direction": "ADD", "amount": 1000000,
     "label": "Cọc đã thu"},
    {"componentCode": "UNPAID_RENT", "direction": "SUBTRACT", "amount": 200000,
     "label": "Tiền thuê còn thiếu"}
  ]
}
```

- Ít nhất một trong `invoiceId`/`orderId`.
- `calculationVersion`, `reasonCode`, `lines` bắt buộc.
- Mỗi line: `componentCode`, `direction` (`ADD`/`SUBTRACT`), `amount > 0`, `label`.
- `sourceType`/`sourceId` optional để trace line về invoice/payment/contract.
- `calculationInput` optional; server lưu nguyên snapshot JSONB.
- Client **không** gửi final amount.

## Cách tính

```text
final_amount = sum(ADD) - sum(SUBTRACT)
```

Server tính, lưu `amount`, và render `formulaText`; `formulaText` chỉ để hiển thị/audit, không
được dùng để tính lại.

## Hành vi

1. Xác định facility từ invoice/order; kiểm permission facility.
2. Chặn obligation active thứ hai cho cùng invoice; nhiều obligation order-only cho cùng đơn được phép.
3. Validate component code, direction, amount, reason; `OTHER` phải có note.
4. Tạo `refund_obligations` + `refund_obligation_lines`, tính final amount.
5. Audit `REFUND_OBLIGATION_CREATED` trên `RefundObligation`.
6. Obligation ở `Open`; chưa chi tiền.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | thiếu invoice/order, line/reason không hợp lệ, final amount ≤ 0 |
| 403 | không có permission tại facility |
| 404 | không thấy invoice/đơn |
| 409 | invoice đã có obligation đang mở |

## Liên quan

- [payment.md](../../payment.md) › Refund obligation
- [refund-handover.md](refund-handover.md)
- [follow-up.md](../../follow-up.md) › Refund
