# VNPay sandbox — lấy credential và dựng IPN URL

Thuộc mục **A1b** của gói nền tảng #33. Tài liệu này ghi lại cách lấy credential sandbox và
những ràng buộc của VNPay mà người làm **A6** (tích hợp thanh toán) phải tuân theo, để khỏi
phải dò lại tài liệu gốc.

Credential **không nằm trong repo**. Chỉ `.env` giữ giá trị thật, và `.env` đã bị `.gitignore` chặn.

## 1. Đăng ký tài khoản sandbox

Đăng ký tại <https://sandbox.vnpayment.vn/devreg/>. Sau khi điền form, VNPay gửi qua email hai
giá trị:

| Giá trị | Là gì |
|---|---|
| `vnp_TmnCode` | mã website đã đăng ký trên hệ thống VNPay |
| `vnp_HashSecret` | chuỗi bí mật dùng ký và kiểm checksum giữa hai hệ thống |

Quản lý tài khoản sau đó ở <https://sandbox.vnpayment.vn/merchantv2/>.

## 2. Điền vào `.env`

Copy `.env.example` thành `.env` rồi điền hai giá trị vừa nhận:

```bash
VNPAY_TMN_CODE=<mã VNPay gửi>
VNPAY_HASH_SECRET=<chuỗi bí mật VNPay gửi>
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8080/api/payments/vnpay/return
VNPAY_IPN_URL=<URL https công khai, xem mục 3>
```

`application.yml` **chưa đọc** mấy biến này — việc wire vào app thuộc A6.

## 3. IPN URL công khai

Tài liệu VNPay ghi rõ **IPN URL cần có SSL**, nên `http://localhost:8080` không dùng được.
Dev local phải mở tunnel HTTPS.

```bash
# cloudflared — không cần tài khoản
cloudflared tunnel --url http://localhost:8080

# hoặc ngrok — cần đăng ký lấy authtoken trước
ngrok http 8080
```

Lấy URL https mà lệnh in ra, ghép đường dẫn IPN vào rồi dùng làm `VNPAY_IPN_URL`, ví dụ
`https://<random>.trycloudflare.com/api/payments/vnpay/ipn`.

**Hai điểm dễ vướng:**

- IPN URL khai trong **cấu hình tài khoản merchant** trên portal, không truyền kèm lúc tạo URL
  thanh toán. Đổi URL tunnel thì phải vào portal khai lại.
- Quick tunnel của cloudflared đổi domain mỗi lần chạy lại. Nếu phải test nhiều ngày thì dùng
  named tunnel (cloudflared có domain riêng) hoặc ngrok có reserved domain, đỡ phải khai lại liên tục.

## 4. Ràng buộc VNPay áp lên A6

- **Checksum: HMAC-SHA512.** Sắp tham số theo thứ tự alphabet, nối thành query string rồi ký.
- **`vnp_ReturnUrl` chỉ để hiển thị kết quả cho khách**, kiểm checksum nhưng **không** dùng để cập
  nhật dữ liệu giao dịch. Nguồn xác nhận thanh toán duy nhất là IPN.
- **IPN retry tối đa 10 lần, cách nhau 5 phút** khi merchant trả `01`, `04`, `97`, `99` hoặc khi
  timeout. Nghĩa là **handler phải idempotent trước khi test cọc**, nếu không một giao dịch có thể
  được ghi nhận nhiều lần.
- RspCode phải trả về đúng bộ sau:

  | RspCode | Nghĩa | Có retry |
  |---|---|---|
  | `00` | Xác nhận thành công | không |
  | `02` | Đơn đã được xác nhận trước đó | không |
  | `01` | Không tìm thấy đơn | có |
  | `04` | Số tiền không hợp lệ | có |
  | `97` | Sai chữ ký | có |
  | `99` | Lỗi không xác định | có |

## 5. Thẻ test của sandbox

Môi trường sandbox chỉ chấp nhận thẻ trong danh sách của VNPay. Chủ thẻ dùng chung
`NGUYEN VAN A`, địa chỉ `22 Lang Ha, Ha Noi`.

| Ngân hàng | Số thẻ | Ngày phát hành | OTP | Kết quả mô phỏng |
|---|---|---|---|---|
| NCB | `9704198526191432198` | 07/15 | `123456` | thành công |
| NCB | `9704195798459170488` | 07/15 | | không đủ số dư |
| NCB | `9704192181368742` | 07/15 | | thẻ chưa kích hoạt |
| NCB | `9704193370791314` | 07/15 | | thẻ bị khoá |
| NCB | `9704194841945513` | 07/15 | | thẻ hết hạn |

Thẻ quốc tế (CVV `123`, hết hạn `12/26`): VISA `4456530000001005` (không 3DS) ·
VISA `4456530000001096` (3DS) · MasterCard `5200000000001005` / `5200000000001096` ·
JCB `3337000000000008` / `3337000000200004`.

Bộ thẻ đủ và mới nhất xem tại <https://sandbox.vnpayment.vn/apis/vnpay-demo/>.

## 6. Trạng thái hiện tại

- [x] Biến `VNPAY_*` đã có sẵn trong `.env.example` (làm ở A1a)
- [ ] `vnp_TmnCode` + `vnp_HashSecret` — chờ đăng ký ở mục 1
- [ ] IPN URL công khai — dựng khi A6 đã có endpoint để nhận; hiện backend chưa có
      `/api/payments/vnpay/ipn` nên mở tunnel lúc này chưa có tác dụng

## Nguồn

- [Hướng dẫn tích hợp thanh toán (pay)](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [Đăng ký merchant môi trường test](https://sandbox.vnpayment.vn/devreg/)
- [Demo và danh sách thẻ test](https://sandbox.vnpayment.vn/apis/vnpay-demo/)
