---
auth-model: permission-bound
purpose: Khách xác nhận đã nhận tiền hoàn; invoice chỉ chuyển Refunded sau bước này.
---
# POST /api/invoices/{invoiceId}/refund-confirm

Khách sở hữu invoice xác nhận đã nhận tiền. Chỉ hợp lệ sau khi FS/FM đã handover — trước đó trả
`409`. Gọi lặp lại trả kết quả cũ, không ghi audit trùng.

- **Actor / quyền:** Bearer + `ACCOUNT_ACTIVE`; chủ invoice.
- **Contract chi tiết:** annotation tại `InvoiceRefundController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Request

```json
{
  "refundTransactionId": 123,
  "note": "Đã nhận đủ"
}
```

- `refundTransactionId`: optional; bỏ trống thì lấy refund `Success` gần nhất của invoice.
- `note`: optional, hiện chỉ lưu ở request/response context, không ghi DB.

## Hành vi

1. Khoá invoice; invoice không thuộc khách → `404`.
2. Nếu invoice đã `Refunded`: trả refund cũ (idempotent).
3. Chỉ nhận `AwaitingConfirmation`; `Required`/`Processing` → `409` “cơ sở chưa xác nhận đã đưa tiền”.
4. Refund phải `Success` và có `disbursed_at`; nếu không → `409`.
5. Set `customer_confirmed_at`, audit `REFUND_CUSTOMER_CONFIRMED`.
6. Invoice → `Refunded`, set `refunded_at`.

## Lỗi

| Status | Khi nào |
|---|---|
| 404 | invoice không tồn tại hoặc không thuộc khách |
| 409 | chưa handover, refund không `Success`, hoặc trạng thái invoice không hợp lệ |

## Liên quan

- [refund-handover.md](refund-handover.md)
- [refund-dispute.md](refund-dispute.md)
