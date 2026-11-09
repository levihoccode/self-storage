---
auth-model: permission-bound
purpose: Lịch sử dòng tiền của cơ sở FM — invoice/fee, payment và refund trong một danh sách.
---
# GET /api/fm/transactions?facilityId=&kind=&direction=&status=&refundStatus=&needsReconciliation=&from=&to=&page=&size=

Lịch sử giao dịch của cơ sở FM đang phụ trách, mới nhất trước. Route read-only: union ba bảng
nguồn, không đổi invoice/payment/refund, không gọi cổng thanh toán.

- **Actor / quyền:** role `FM`; `invoice.read` scope `FACILITY` (403 nếu ngoài cơ sở).
- **Contract chi tiết:** annotation tại `FmTransactionController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Nguồn dữ liệu

Một dòng thuộc đúng một nguồn; `kind` cho biết đọc từ đâu:

| `kind` | Nguồn | `direction` | `occurredAt` | Ý nghĩa |
|---|---|---|---|---|
| `INVOICE` | `invoices` type `Deposit`/`DepositSupplement`/`Rental` | `IN` | `created_at` | Chứng từ phát sinh |
| `FEE` | `invoices` type `Extension`/`Penalty`/`Service` | `IN` | `created_at` | Phí phát sinh |
| `PAYMENT` | `payment_transactions` (`direction='PAY'`) | `IN` | `paid_at` khi `Success`, ngược lại `created_at` | Tiền khách đã trả |
| `REFUND` | `refund_transactions` | `OUT` | `coalesce(disbursed_at, created_at)` | Tiền cơ sở đã hoàn |

Invoice và payment là hai dòng khác nhau cho cùng một khoản: invoice là chứng từ, payment là tiền
vào thực tế. Route không cộng dồn, không sinh bút toán mới.

## Luồng / hành vi

- `facilityId` bắt buộc; invoice thuộc cơ sở qua khoang của đơn hoặc hợp đồng.
- `kind` optional: `INVOICE` / `FEE` / `PAYMENT` / `REFUND`; sai → `400`.
- `direction` optional: `IN` / `OUT`; sai → `400`.
- `status` optional: `Unpaid` / `Paid` / `Canceled` / `Expired` / `Pending` / `Failed` /
  `Success` / `Duplicate`; lọc theo trạng thái của từng nguồn; sai → `400`.
- `refundStatus` optional, chỉ áp cho dòng invoice/fee: `None` / `Required` / `Processing` /
  `AwaitingConfirmation` / `Refunded`; sai → `400`. Dùng để tìm hoá đơn đang chờ hoàn tiền.
- `needsReconciliation` optional (mặc định `false`): `true` chỉ giữ `PAYMENT` `Pending` đã quá cửa
  sổ `PaymentSession.WINDOW` (15 phút).
- `from` / `to` optional, ISO-8601 UTC (ví dụ `2026-11-01T00:00:00Z`); `from` bao gồm, `to` loại
  trừ; sai định dạng hoặc `from` sau `to` → `400`.
- `page` mặc định `0`, `size` mặc định `20`, tối đa `100`; sai → `400`.
- Sort `occurredAt` giảm dần; cùng thời điểm thì theo id nguồn giảm dần.
- Response là một trang: `data.items`, `data.page`, `data.size`, `data.totalElements`,
  `data.totalPages`.
- Mỗi dòng gồm `id`, `kind`, `direction`, `amount`, `status`, `occurredAt`, `orderId`,
  `invoiceId`, `invoiceCode`, `paymentTransactionId`, `refundTransactionId`, `code`, `method`,
  `description`, thông tin khách/cơ sở/khoang.
- Dòng `PAYMENT` `Pending` có `pendingAgeMinutes` và `needsReconciliation` (quá 15 phút). Các dòng
  khác trả `pendingAgeMinutes: 0`, `needsReconciliation: false`.
- Dòng `REFUND` thêm `refundConfirmedAt`, `refundDisputedAt`, `refundDisputeNote`.
- Dòng `INVOICE`/`FEE` thêm `invoiceRefundStatus`, `invoiceRefundReason`.
- Không có giao dịch → `200` với `data.items: []`, `totalElements: 0`.
- FE `/fm/transactions` dùng route này cho tab **Giao dịch**; tab **Chờ hoàn tiền** gọi
  [fm-refund-queue.md](fm-refund-queue.md) — queue là nghĩa vụ, không phải ledger.

## Ghi chú nghiệp vụ

- `refund_obligations` (nghĩa vụ hoàn tiền, chưa chi) **không** nằm trong lịch sử: chưa phải tiền
  ra. Hoá đơn đang chờ hoàn vẫn hiện ở dòng `INVOICE`/`FEE` qua `invoiceRefundStatus`.
- Queue invoice chờ hoàn vẫn là [admin-refund-required.md](../payment/admin-refund-required.md);
  route này không thay thế handover/confirm/dispute.
- Payment `Pending`/`Failed`/`Duplicate` và refund `Pending`/`Failed`/`Canceled` đều hiện — FM cần
  cho đối soát. Cờ `needsReconciliation` chỉ để FM nhắc khách/escalate; chốt trạng thái vẫn là
  thao tác ADMIN qua `POST /api/admin/payments/{txnRef}/reconcile`.
- Invoice không gắn được khoang/cơ sở (không có unit qua order/contract) không thuộc lịch sử của
  cơ sở nào và không xuất hiện.
- Route này thay màn **Hoá đơn** cũ của FM. `GET /api/fm/invoices` vẫn tồn tại cho tích hợp cũ,
  nhưng không còn là màn chính.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | `kind`, `direction`, `status`, `refundStatus`, `from`/`to`, `page`, `size` không hợp lệ |
| 401 | Thiếu / sai / hết hạn token |
| 403 | Không phải role FM, hoặc facility ngoài phạm vi |
| 404 | `facilityId` không tồn tại |

## Ví dụ

```bash
curl -s -H "Authorization: Bearer $FM_TOKEN" \
  "http://localhost:8080/api/fm/transactions?facilityId=1&kind=REFUND&direction=OUT&page=0&size=20"
# {"message":"Lấy lịch sử giao dịch cơ sở thành công","data":{"items":[{"id":1,"kind":"REFUND",
# "direction":"OUT","amount":500000.00,"status":"Success","occurredAt":"2026-11-09T10:00:00Z",
# "orderId":1,"invoiceId":1,"invoiceCode":"INV-DEMO-001","paymentTransactionId":null,
# "refundTransactionId":1,"code":"GW-REFUND-1","method":"CASH","description":"Hoàn tiền INV-DEMO-001",
# "customerId":6,"customerName":"Khách demo","customerEmail":"customer1@lemar.vn","facilityId":1,
# "facilityName":"Self Storage Quận 7","unitCode":"A-01","invoiceRefundStatus":null,
# "invoiceRefundReason":null,"refundConfirmedAt":null,"refundDisputedAt":null,
# "refundDisputeNote":null}],"page":0,"size":20,"totalElements":1,"totalPages":1}}
```

## Liên quan

- Hoá đơn FM (route cũ): [fm-invoice-list.md](fm-invoice-list.md)
- Queue chờ hoàn: [admin-refund-required.md](../payment/admin-refund-required.md)
- Xem thêm: [routes.md](../../routes.md) · [index.md](../../index.md)
