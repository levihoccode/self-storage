# GET /api/payments/vnpay/ipn

Server VNPay gọi vào để xác nhận kết quả thanh toán. Đây là **nguồn xác nhận thanh toán duy nhất**
— Return URL chỉ để hiển thị cho khách.

- **Actor / quyền:** public. VNPay gọi từ server của họ và không có JWT, nên route này không thể
  yêu cầu token. Thứ thay cho token là chữ ký HMAC-SHA512 trong `vnp_SecureHash`.
- **Contract chi tiết:** annotation tại `VnPayCallbackController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

Thứ tự kiểm, dừng ngay ở bước đầu tiên không đạt:

1. Chữ ký HMAC-SHA512 — sai → `97`
2. Tìm giao dịch theo `vnp_TxnRef` — không có → `01`
3. `vnp_TmnCode` khớp merchant — không khớp → `01`
4. `vnp_Amount` khớp số tiền (VNPay gửi đã nhân 100) — lệch → `04`
5. Giao dịch còn `Pending` — không còn → `02`, **không ghi gì thêm**
6. Ghi `Success`/`Failed`, bắn `PaymentSucceeded`/`PaymentFailed` → `00`

**Idempotent theo `vnp_TxnRef`.** Bước 5 là chốt chặn. Row được khoá `PESSIMISTIC_WRITE` khi đọc,
nên hai IPN đến song song cũng chỉ một lần được ghi nhận — cái còn lại đợi, đọc lại thấy `Success`
và trả `02`. Unique constraint không thay thế được khoá này vì cả hai cùng update một row.

Lỗi ngoài dự kiến trả `99` thay vì HTTP 500, bắt ở controller nên transaction vẫn rollback.

## Ghi chú nghiệp vụ

- **Không dùng `ApiEnvelope`.** Mọi route khác trả `{message, data}`, route này thì không: VNPay đọc
  phản hồi theo đúng hai key `RspCode`/`Message` ở mức gốc. Bọc thêm một lớp là VNPay không parse
  được, coi như merchant chưa xác nhận và retry đủ 10 lần. Đây là contract của bên thứ ba.
- **VNPay retry tối đa 10 lần, cách nhau 5 phút** khi nhận `01`, `04`, `97`, `99` hoặc khi timeout.
  `00` và `02` là chốt.
- IPN URL khai trong **cấu hình tài khoản merchant** trên portal VNPay, không gửi kèm lúc tạo URL
  thanh toán. Dev local cần tunnel HTTPS — xem [docs/vnpay-sandbox.md](../../../../docs/vnpay-sandbox.md).
- `vnp_Amount` hiện đối chiếu với `PaymentTransaction.amount`, mà giá trị đó do caller truyền vào
  lúc `startPayment`. Khi module invoice có entity thật thì phải đọc amount từ invoice trong DB —
  đã ghi `TODO(invoice)` tại chỗ so sánh.

## Ví dụ

Body dưới đây lấy từ test chạy thật, không phải chép tay:

```text
# IpnResponseTest — serialize bằng Jackson thật
{"RspCode":"00","Message":"Confirm Success"}

# SecurityConfigTest — gọi qua cả tầng MVC, không kèm token
GET /api/payments/vnpay/ipn?vnp_TxnRef=TXN1&vnp_SecureHash=deadbeef
200 {"RspCode":"00","Message":"Confirm Success"}
```

Gọi tay trên trình duyệt (không có `vnp_SecureHash` hợp lệ) trả `97` — đúng, vì không có chữ ký
thì không chứng minh được request đến từ VNPay.

## Liên quan

- [vnpay-return.md](vnpay-return.md) · [routes.md](../../routes.md) · [index.md](../../index.md)
- Hướng dẫn lấy credential sandbox + dựng IPN URL: [docs/vnpay-sandbox.md](../../../../docs/vnpay-sandbox.md)
