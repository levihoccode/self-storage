---
purpose: ZaloPay gọi server-to-server khi khách thanh toán thành công — ghi nhận giao dịch, idempotent theo app_trans_id.
---

# POST /api/payments/zalopay/callback

ZaloPay gọi khi khách thanh toán thành công một đơn. Đây là nguồn xác nhận chính khi cổng đang hoạt
động là ZaloPay; khi callback không về, job đối soát hỏi `/v2/query` (xem [payment.md](../../payment.md)).

- **Actor / quyền:** public — ZaloPay gọi không có JWT. Thay cho token là `mac` = HMAC-SHA256 bằng
  **key2** trên chuỗi `data`. Route chỉ tồn tại khi `PAYMENT_PROVIDER=zalopay`.
- **Contract chi tiết:** annotation tại `ZaloPayCallbackController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

Body `{data, mac, type}`; `data` là chuỗi JSON (`app_id`, `app_trans_id`, `amount`, `zp_trans_id`,
`server_time`, …) — **không có field trạng thái**: callback hợp lệ nghĩa là đã thanh toán thành công.

Thứ tự kiểm, dừng ở bước đầu tiên không đạt:

1. Có `data` + `mac`, `mac` đúng (key2), `app_id` khớp, `data` đọc được và có `app_trans_id` + `amount`
   — sai → `return_code = 2`, không ghi gì.
2. Tìm giao dịch theo `app_trans_id` (khoá row `PESSIMISTIC_WRITE`) — không có → `0`, ZaloPay gọi lại.
3. `amount` khớp `PaymentTransaction.amount` — lệch → `2`, không ghi gì.
4. Giao dịch đã chốt trước đó (callback lặp, hoặc job đối soát chốt trước) → `1`, không audit, không
   bắn event lần hai.
5. Ghi `Success` (`gateway_transaction_no` = `zp_trans_id`, `paid_at` = `server_time`), xoá
   `payment_url` + cache Redis của hoá đơn, audit `PAYMENT_SUCCEEDED`, bắn `PaymentSucceeded` → `1`.

Lỗi phía hệ thống khi ghi (DB…) trả `0` để ZaloPay gọi lại; transaction rollback.

## Ghi chú nghiệp vụ

- **Không dùng `ApiEnvelope`.** ZaloPay đọc `return_code`/`return_message` ở mức gốc — contract của
  bên thứ ba.
- `return_code`: `1` đã ghi nhận / đã xử lý trước đó; `0` chưa tìm thấy giao dịch (để ZaloPay gọi lại);
  `2` callback không hợp lệ. ZaloPay chỉ gọi lại khi không nhận `1`.
- `PaymentSucceeded` bắn trong transaction callback — listener booking chạy `AFTER_COMMIT` và chốt hoá
  đơn/đơn/khoang (`routes/booking/invoice-pay.md` › Nhánh thành công).
- Callback URL gửi kèm lúc tạo đơn (`ZALOPAY_CALLBACK_URL`); dev local cần URL public (ngrok) trỏ về
  route này.

## Ví dụ

Chạy local với `PAYMENT_PROVIDER=zalopay`, key2 = `local-key2`, `data` = callback mẫu đúng định dạng
ZaloPay cho một `app_trans_id` chưa có trong DB:

```bash
# mac sai
curl -s -X POST http://localhost:8082/api/payments/zalopay/callback \
  -H 'Content-Type: application/json' -d '{"data":"<data>","mac":"deadbeef","type":1}'
# {"return_code":2,"return_message":"invalid callback"}

# mac đúng (HMAC-SHA256 key2), app_trans_id không có trong DB
# {"return_code":0,"return_message":"retry"}

# mac ký bằng key1 thay vì key2
# {"return_code":2,"return_message":"invalid callback"}

# thiếu data
curl -s -X POST http://localhost:8082/api/payments/zalopay/callback \
  -H 'Content-Type: application/json' -d '{"mac":"x","type":1}'
# {"return_code":2,"return_message":"invalid callback"}
```

## Liên quan

- [payment.md](../../payment.md) · [zalopay-return.md](zalopay-return.md) ·
  [invoice-pay.md](../booking/invoice-pay.md) · [routes.md](../../routes.md)
