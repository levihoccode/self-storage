---
auth-model: permission-bound
purpose: Khách báo chưa nhận tiền hoàn; ghi nhận để đối soát, không tạo refund lần hai.
---
# POST /api/refunds/invoices/{invoiceId}/dispute

Khách sở hữu invoice báo chưa nhận tiền sau khi FS/FM đã handover. Lần đầu route ghi audit +
notification; các lần sau trả kết quả cũ, không tạo audit/notification trùng. Invoice giữ
`AwaitingConfirmation`; không tạo thêm refund transaction.

- **Actor / quyền:** Bearer + `ACCOUNT_ACTIVE`; chủ invoice.
- **Contract chi tiết:** annotation tại `InvoiceRefundController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Request

```json
{
  "refundTransactionId": 123,
  "note": "Tài khoản chưa nhận được tiền"
}
```

- `note`: bắt buộc.
- `refundTransactionId`: optional; bỏ trống thì lấy refund `Success` gần nhất.

## Hành vi

1. Khoá invoice; invoice không thuộc khách → `404`.
2. Chỉ nhận `AwaitingConfirmation`; trạng thái khác → `409`.
3. Refund phải `Success` + có `disbursed_at`; nếu không → `409`.
4. Nếu là lần dispute đầu: set `disputed_at/dispute_note`, audit `REFUND_CUSTOMER_DISPUTED`,
   gửi notification `REFUND_DISPUTE_RECORDED`. Lần lặp trả bản ghi cũ, không ghi thêm.
5. Giữ nguyên `AwaitingConfirmation`; support request/Flow 7 xử lý đối soát.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | thiếu `note` |
| 404 | invoice/refund không thuộc khách |
| 409 | chưa handover hoặc invoice không ở `AwaitingConfirmation` |

## Liên quan

- [refund-handover.md](refund-handover.md)
- [refund-confirm.md](refund-confirm.md)
