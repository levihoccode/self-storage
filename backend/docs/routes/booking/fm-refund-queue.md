---
auth-model: permission-bound
purpose: FM xem các invoice đang có nghĩa vụ hoàn tiền thuộc cơ sở mình; ADMIN dùng cùng route cross-facility.
---
# GET /api/refunds/queues/invoices?facilityId=&page=&size=

Queue refund read-only cho FM theo facility. Hiện trả các invoice có `refund_status='Required'`;
khi refund ledger mở rộng sẽ thêm active/history.

- **Actor / quyền:** Bearer + role `FM`; `payment_transaction.refund` scope `FACILITY`.
- **Contract chi tiết:** annotation tại `RefundQueueController.java`.

## Hành vi

- `facilityId` bắt buộc; FM phải thuộc facility đó. ADMIN không cần `facilityId`.
- `view` mặc định `active`; FM chỉ hỗ trợ `active`, `history` trả `400`.
- Chỉ invoice có `refund_status='Required'` và thuộc facility.
- Queue chỉ invoice; order-only obligation không xuất hiện ở đây.
- Mỗi dòng: `invoiceId`, `invoiceCode`, `orderId`, `amount`, `refundStatus`, `refundReason`,
  `refundRequestedAt`, `vnpTxnRef`, `gatewayTransactionNo`, `paidAt`.
- Read-only; handover/confirm dùng route refund riêng.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | page/size không hợp lệ |
| 403 | Không phải FM hoặc facility ngoài phạm vi |
| 404 | Facility không tồn tại |

## Liên quan

- [fm-invoice-list.md](fm-invoice-list.md)
- [admin-refund-required.md](../payment/admin-refund-required.md)
