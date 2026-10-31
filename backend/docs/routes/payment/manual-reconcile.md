# POST /api/admin/payments/{txnRef}/reconcile

Lớp đối soát thủ công thay cho `querydr`: ADMIN tra portal VNPay rồi nhập kết quả cho một giao dịch
còn `Pending`. Endpoint không sửa DB tay; nó khoá row, ghi audit và phát event như IPN.

- **Actor / quyền:** Bearer + role `ADMIN`.
- **Contract chi tiết:** annotation tại `AdminPaymentReconciliationController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Request

```json
{
  "outcome": "SUCCESS",
  "note": "portal VNPay: vnp_TransactionNo 14777001",
  "paidAt": "2026-10-30T10:00:00Z",
  "gatewayTransactionNo": "14777001"
}
```

- `outcome`: `SUCCESS` hoặc `FAILED`.
- `note`: bằng chứng/ghi chú, bắt buộc.
- `paidAt`: thời điểm trả theo portal; bỏ trống thì lấy thời điểm nhập.
- `gatewayTransactionNo`: mã giao dịch VNPay nếu có.

## Hành vi

1. Tìm giao dịch theo `txnRef`; không có → `404`.
2. Khoá row; chỉ nhận `Pending`; đã `Success`/`Failed` → `409`, không ghi thêm.
3. `SUCCESS` → `markSucceeded`, audit `PAYMENT_SUCCEEDED`, phát `PaymentSucceeded`.
4. `FAILED` → `markFailed`, audit `PAYMENT_FAILED`, phát `PaymentFailed`.
5. Listener booking cập nhật invoice/order/unit như IPN; late success vẫn đi nhánh invoice
   `Canceled`/`Expired` để xử lý tay/hoàn tiền.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | thiếu/sai `outcome`, `note`, JSON sai |
| 403 | không phải ADMIN |
| 404 | không có giao dịch với `txnRef` |
| 409 | giao dịch đã chốt trước đó |

## Quy trình ops

1. Lọc `PaymentTransaction(Pending)` quá 15 phút.
2. Tra portal VNPay theo `vnp_TxnRef`.
3. Đã trả → gọi `SUCCESS` kèm `paidAt` + `gatewayTransactionNo`; chưa trả → gọi `FAILED` kèm note.
4. Kiểm tra invoice/order/unit + audit; không sửa DB tay.

## Giới hạn

- Không tự retry, không tự phát hiện giao dịch; đây là thay thế có chủ ý cho querydr.
- Giao dịch `Pending` không có ai tra vẫn nằm nguyên — vận hành phải có lịch kiểm tra.

## Liên quan

- [payment.md](../../payment.md) · [invoice-pay.md](../booking/invoice-pay.md) ·
  [routes.md](../../routes.md)
