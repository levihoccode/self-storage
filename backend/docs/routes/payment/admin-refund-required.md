---
auth-model: permission-bound
purpose: ADMIN xem queue hoàn tiền đang mở và lịch sử; read-only.
---
# GET /api/admin/payments/refunds?page=&size=&view=

Trả các invoice trong queue hoàn tiền, kèm giao dịch `PAY` gần nhất (`Success` hoặc `Duplicate`).
Route read-only; thao tác handover/confirm nằm ở route khách/FS.

- **Actor / quyền:** role `ADMIN`; đây là queue oversight. FM dùng
  `GET /api/fm/invoices/refunds` theo facility.
- **Contract chi tiết:** annotation tại `AdminRefundPaymentController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- `page` bắt đầu từ 0; `size` mặc định 20, tối đa 100; sai → `400`.
- `view=active` (mặc định): `Required`, `Processing`, `AwaitingConfirmation`.
- `view=history`: `Refunded`. Giá trị khác → `400`.
- Query đọc từ `invoices`, không đọc từ case: khoản cần hoàn vẫn hiện dù chưa từng có case đối soát.
- Mỗi dòng: `invoiceId`, `invoiceCode`, `orderId`, `amount`, `refundStatus`, `refundReason`,
  `refundRequestedAt`, `vnpTxnRef`, `gatewayTransactionNo`, `paidAt`.
- `vnpTxnRef`/`gatewayTransactionNo`/`paidAt` lấy từ giao dịch `PAY` gần nhất (`Success` hoặc
  `Duplicate`); có thể null nếu chưa có giao dịch.
- Không đổi trạng thái invoice/transaction; không tạo refund transaction.

## Ghi chú nghiệp vụ

- `refund_status=Required` được đặt khi tiền đã vào nhưng khoang không còn giữ được
  (`INVOICE_REFUND_REQUIRED`).
- Queue active/history là query trên cùng `invoices` + `refund_transactions`; không có bảng history
  riêng.
- Trạng thái: `Required → AwaitingConfirmation → Refunded` sau handover/confirm.

## Liên quan

- Case Pending: [admin-pending-payments.md](admin-pending-payments.md)
- Nhánh thanh toán unit đã mất: [invoice-pay.md](../booking/invoice-pay.md)
- Handover/confirm/dispute: [refund-handover.md](refund-handover.md), [refund-confirm.md](refund-confirm.md), [refund-dispute.md](refund-dispute.md)
- Xem thêm: [routes.md](../../routes.md) · [index.md](../../index.md)
