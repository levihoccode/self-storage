---
auth-model: permission-bound
purpose: FS/FM ghi nhận đã đưa tiền hoàn; tạo refund ledger và chuyển invoice sang AwaitingConfirmation.
---
# POST /api/refunds/invoices/{invoiceId}/handover

FS/FM xác nhận đã đưa tiền hoàn (tiền mặt/chuyển khoản). Route tạo
`refund_transactions(Success)`, ghi audit và chuyển invoice sang `AwaitingConfirmation`.
Không có bước FM duyệt sau đó.

- **Actor / quyền:**
  - FS: `refund_transaction.handover` + facility scope.
  - FM: `payment_transaction.refund` + facility scope.
  - ADMIN: `refund_transaction.handover` + facility scope.
- **Contract chi tiết:** annotation tại `InvoiceRefundController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Request

```json
{
  "method": "CASH",
  "evidenceUrl": "https://res.cloudinary.com/.../receipt.jpg",
  "gatewayRef": null,
  "note": "Đã đưa tiền tại quầy",
  "sourceTransactionId": 123,
  "reasonNote": null
}
```

- `method`: `CASH`, `BANK_TRANSFER` hoặc `VNPAY`.
- `evidenceUrl`: bắt buộc; URL file đã upload qua `POST /api/media/uploads`.
- `gatewayRef`: optional, mã chuyển khoản/VNPay.
- `sourceTransactionId`: optional; bỏ trống thì lấy giao dịch `PAY` gần nhất có thể hoàn
  (`Success` hoặc `Duplicate`).
- `reasonNote`: bắt buộc khi `invoices.refund_reason` là `OTHER`.

Số tiền **không** có trong request; service lấy `invoice.amount` và kiểm ≤ `source_transaction.amount`.

## Phạm vi

Route này là **xác nhận đã chi tiền cho nghĩa vụ hoàn tiền của invoice**, không phải route tạo
nghĩa vụ hoàn tiền và không phải route riêng cho từng lý do. Nghĩa vụ phải có trước với
`invoice.refund_status = Required`/`Processing`.

Các loại hoàn tiền sau này (trả cọc khi kết thúc thuê, dư phí, chi phí sửa chữa) phải tạo nghĩa vụ
trước rồi dùng lại route này. Nếu cần tính toán hoặc duyệt riêng, thêm endpoint `settle`/`approve`
trước bước handover; không thêm một route handover thứ hai.

`payment_transaction.refund` là permission cũ của FM cho action này, không phải quyền wildcard cho
mọi chức năng refund tương lai. Mỗi endpoint refund mới phải tự khai báo guard/permission.

## Hành vi

1. Kiểm permission facility của actor.
2. Khoá invoice; chỉ nhận `Required`/`Processing`, còn lại `409`.
3. Khoá giao dịch gốc; kiểm cùng invoice, `direction=PAY`, status `Success`/`Duplicate`.
4. Chặn nếu giao dịch đã có refund `Success` (partial unique index V22 là chốt cuối).
5. Kiểm `invoice.amount ≤ source.amount`; ghi `refund_transactions(Success)` với `reason_code`
   snapshot từ invoice, `disbursed_at=now()`.
6. Audit `MANUAL_REFUND_RECORDED` trên `RefundTransaction`.
7. Invoice → `AwaitingConfirmation`.
8. Sau commit, gửi notification `REFUND_AWAITING_CONFIRMATION` cho khách.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | thiếu/sai `method`, thiếu `evidenceUrl`, `reasonCode=OTHER` thiếu ghi chú, không có giao dịch gốc |
| 403 | sai role hoặc actor không thuộc facility |
| 404 | không thấy invoice |
| 409 | invoice không còn `Required`/`Processing`, hoặc source đã có refund `Success` |

## Liên quan

- [refund-confirm.md](refund-confirm.md)
- [admin-refund-required.md](admin-refund-required.md)
- [payment.md](../../payment.md)
