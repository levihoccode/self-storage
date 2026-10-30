---
purpose: payOS gọi server-to-server khi có giao dịch chuyển khoản — ghi nhận thanh toán, idempotent theo orderCode.
---

# POST /api/payments/payos/webhook

payOS gọi khi khách chuyển khoản cho một link thanh toán. Đây là nguồn xác nhận chính khi cổng đang
hoạt động là payOS; webhook không về thì job đối soát tra cứu link (xem [payment.md](../../payment.md)).

- **Actor / quyền:** public — payOS gọi không có JWT. Thay cho token là `signature` = HMAC-SHA256
  bằng **checksum key** trên object `data`. Route chỉ tồn tại khi `PAYMENT_PROVIDER=payos`.
- **Contract chi tiết:** annotation tại `PayOsWebhookController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

Body `{code, desc, success, data, signature}`; `data` gồm `orderCode`, `amount`, `reference`,
`transactionDateTime` (`yyyy-MM-dd HH:mm:ss` giờ Việt Nam), `code`, `desc`, …

Thứ tự kiểm, dừng ở bước đầu tiên không đạt:

1. Có `data` + `signature`, chữ ký đúng (các key của `data` theo alphabet, `key=value` nối bằng `&`,
   null thành chuỗi rỗng), có `orderCode` + `amount` — sai → `400`, không ghi gì.
2. Tìm giao dịch theo `orderCode` (khoá row `PESSIMISTIC_WRITE`) — không có → `200`.
3. `amount` khớp `PaymentTransaction.amount` — lệch → `200`, không ghi gì, log WARN.
4. Giao dịch đã chốt trước đó (webhook lặp, hoặc job đối soát chốt trước) → `200`, không audit, không
   bắn event lần hai.
5. `data.code` khác `00` → `200`, giữ `Pending`.
6. Ghi `Success` (`gateway_transaction_no` = `reference`, `paid_at` = `transactionDateTime`), xoá
   `payment_url` + cache Redis của hoá đơn, audit `PAYMENT_SUCCEEDED`, bắn `PaymentSucceeded` → `200`.

Lỗi phía hệ thống khi ghi (DB…) trả `500`; transaction rollback.

## Ghi chú nghiệp vụ

- **Không dùng `ApiEnvelope`.** payOS chỉ đọc HTTP status (2xx = đã giao). Body
  `{success, result}` chỉ để log/debug; `result` là kết quả xử lý (`ACCEPTED`, `ALREADY_SETTLED`,
  `NOT_FOUND`, `INVALID_AMOUNT`, `IGNORED`).
- **Webhook đúng chữ ký luôn `200`.** Khi đăng ký URL trên trang payOS (`confirm-webhook`), payOS gửi
  giao dịch mẫu `orderCode = 123` không có trong DB và chỉ lưu URL khi nhận 2xx. Giao dịch lỡ
  webhook do job đối soát bắt lại.
- `PaymentSucceeded` bắn trong transaction webhook — listener booking chạy `AFTER_COMMIT` và chốt hoá
  đơn/đơn/khoang (`routes/booking/invoice-pay.md` › Nhánh thành công).
- URL webhook đăng ký trên trang kênh thanh toán của payOS; dev local cần URL public (ngrok) trỏ về
  route này.

## Ví dụ

Chạy local với `PAYMENT_PROVIDER=payos`, checksum key = `local-checksum`, `data` = webhook mẫu trong
docs payOS (`orderCode = 123`, chưa có trong DB):

```bash
# chữ ký đúng (checksum key), orderCode không có trong DB — giống giao dịch mẫu của confirm-webhook
curl -s -X POST http://localhost:8082/api/payments/payos/webhook \
  -H 'Content-Type: application/json' --data-binary @ok.json
# {"success":true,"result":"NOT_FOUND"}   HTTP 200

# chữ ký bằng key khác
# {"success":false,"result":"invalid webhook"}   HTTP 400

# thiếu data
curl -s -X POST http://localhost:8082/api/payments/payos/webhook \
  -H 'Content-Type: application/json' -d '{"signature":"x"}'
# {"success":false,"result":"invalid webhook"}   HTTP 400
```

## Liên quan

- [payment.md](../../payment.md) · [payos-return.md](payos-return.md) ·
  [invoice-pay.md](../booking/invoice-pay.md) · [routes.md](../../routes.md)
