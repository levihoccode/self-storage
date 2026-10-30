---
purpose: Trang payOS chuyển trình duyệt khách về sau khi trả hoặc huỷ — chỉ hiển thị, không ghi dữ liệu.
---

# GET /api/payments/payos/return

payOS chuyển trình duyệt khách về URL này sau khi thanh toán (`PAYOS_RETURN_URL`) hoặc khi khách bấm
huỷ trên trang thanh toán (`PAYOS_CANCEL_URL`; mặc định cả hai trỏ cùng route). Route chỉ trả lại
trạng thái để hiển thị.

- **Actor / quyền:** public — redirect trình duyệt, không mang `Authorization`. Route chỉ tồn tại khi
  `PAYMENT_PROVIDER=payos`.
- **Contract chi tiết:** annotation tại `PayOsWebhookController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Đọc `status`, `cancel`, `orderCode` từ query string rồi trả lại nguyên văn; không kiểm chữ ký,
  không đọc hay ghi DB, không bắn event.

## Ghi chú nghiệp vụ

- **Không dùng để ghi nhận đã trả tiền hay đã huỷ.** Query string nằm trên thanh địa chỉ, khách sửa
  được; trạng thái thật chỉ đến từ webhook hoặc job đối soát. Khách huỷ trên trang payOS không đóng
  giao dịch ngay — giao dịch giữ `Pending` tới khi job đối soát thấy link `CANCELLED`.

## Ví dụ

```bash
curl -s "http://localhost:8082/api/payments/payos/return?code=00&id=abc&cancel=false&status=PAID&orderCode=179335305819136"
# {"provider":"payos","status":"PAID","cancel":"false","orderCode":"179335305819136"}

curl -s "http://localhost:8082/api/payments/payos/return?code=00&id=abc&cancel=true&status=CANCELLED&orderCode=179335305819136"
# {"provider":"payos","status":"CANCELLED","cancel":"true","orderCode":"179335305819136"}
```

## Liên quan

- [payos-webhook.md](payos-webhook.md) · [payment.md](../../payment.md) · [routes.md](../../routes.md)
