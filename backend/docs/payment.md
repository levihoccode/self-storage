---
purpose: Module payment — luồng thanh toán VNPay: tạo giao dịch, IPN/return, đối soát phiên và xử lý hết hạn.
---
# Payment — luồng thanh toán VNPay

Module `payment` (`backend/src/main/java/vn/lemar/selfstorage/payment/`): cổng thanh toán VNPay
dùng chung cho Flow 1 (thu cọc), Flow 2 (thu tháng đầu), Flow 3 (thu gia hạn). Cả ba flow gọi cùng
một service, chỉ truyền hóa đơn vào.

## Luồng tổng thể

```text
Caller (flow, hoặc FE nút "Thanh toán")
   │  PaymentService.startPayment(invoiceId, request)
   │    → ghi PaymentTransaction(Pending) TRƯỚC (khách bỏ ngang vẫn còn dấu vết đối soát)
   ▼
VnPayGateway.buildPaymentUrl()
   │  ký HMAC-SHA512 (amount ×100, giờ GMT+7, tham số sắp alphabet)
   │  → trả URL để caller redirect khách sang cổng VNPay
   ▼
Khách trả tiền trên cổng VNPay
   │
   ├─► GET /api/payments/vnpay/ipn        (server VNPay gọi; NGUỒN XÁC NHẬN CHÍNH)
   │     verify chữ ký → tìm giao dịch theo vnp_TxnRef → check vnp_TmnCode
   │     → check vnp_Amount → guard còn Pending → ghi Success/Failed
   │     → bắn PaymentSucceeded / PaymentFailed → trả {"RspCode":"00"}
   │
   └─► GET /api/payments/vnpay/return     (redirect trình duyệt; CHỈ hiển thị)
         kiểm chữ ký để hiển thị validSignature — không ghi DB, không bắn event
   ▼
Module sở hữu Invoice nghe PaymentSucceeded → chuyển Invoice sang Paid
   (chưa nối — chờ các luồng đơn hàng tương ứng)
```

## Hành vi

- **Đồng hồ của hóa đơn: `Invoice.due_date`.** Quá hạn là mốc duy nhất của "quá hạn thanh toán" —
  Flow 6 xử lý (`Expired` + cascade hủy đơn).
  - **LƯU Ý:** đừng nhầm với cửa sổ phiên VNPay (`vnp_ExpireDate` = lúc tạo URL + `PAYMENT_WINDOW`,
    hiện 15') — cửa sổ chỉ là giới hạn kỹ thuật (hết cửa sổ là cổng không cho trả tiếp); khách bấm
    trả sát `due_date` vẫn có đủ 15'.
- **Đối soát phiên im lặng.** Khách bỏ ngang (đóng browser, IPN không về) để lại giao dịch `Pending`.
  Job định kỳ quét `Pending` quá ngưỡng và gọi `querydr`: cổng có giao dịch → chốt theo trạng thái
  thật; không có → đóng `Failed` + `failure_reason` ghi rõ phiên hết hạn.
- **Xác nhận muộn (lưới an toàn).** Hi hữu — khách đã trả trong cửa sổ hợp lệ nhưng hệ thống chỉ biết
  sau khi invoice/đơn đã hủy (chỉ khi job/server lỗi dài): vẫn ghi `Success` + đủ dấu vết, log ERROR
  để vận hành xử lý thủ công (hoàn tiền hoặc xếp chỗ khác). Không tự mở lại đơn.

## Quy tắc bất biến

- **IPN là nguồn xác nhận thanh toán chính.** Return URL nằm trên thanh địa chỉ, khách sửa được —
  không bao giờ dùng để ghi nhận đã trả tiền. Khi IPN không về, chỉ `querydr` (kênh server-to-server
  cùng chữ ký HMAC) được dùng làm nguồn đối soát dự phòng — không có nguồn nào khác.
- **Đóng phiên dựa trên kết quả `querydr`, không dựa trên đồng hồ.** Ngưỡng thời gian chỉ để chọn lúc
  hỏi cổng; `querydr` lỗi thì giữ `Pending`, lần chạy sau đối soát tiếp.
- **Idempotent theo `vnp_TxnRef`.** VNPay retry tối đa 10 lần (cách 5 phút) và có thể gọi song song;
  row bị khóa `PESSIMISTIC_WRITE` khi đọc, lần thứ hai trả `02` và không ghi gì. Unique constraint
  không thay được khóa vì cả hai cùng update một row.
- **Thứ tự kiểm trong IPN:** chữ ký (`97`) → tìm giao dịch (`01`) → `vnp_TmnCode` (`01`)
  → `vnp_Amount` (`04`) → guard trạng thái (`02`) → OK (`00`). Sai chữ ký thì không để lộ gì về
  giao dịch.
- **Số tiền:** cổng nhận `amount × 100`; đối chiếu khi IPN về. Nguồn số tiền tin cậy là **hóa đơn
  trong DB** — caller phải truyền đúng từ đó (chưa có entity invoice: TODO(invoice) trong
  `PaymentService`).
- **Ranh giới module:** `PaymentTransaction.invoiceId` là `Long`, không quan hệ JPA với bảng
  `invoices`; module khác giao tiếp qua event, không import chéo.
- **Listener event bắt buộc `AFTER_COMMIT`.** `PaymentSucceeded`/`PaymentFailed` được publish
  *trong* transaction IPN — module nghe phải dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`
  (hoặc `REQUIRES_NEW`). Listener đồng bộ mặc định sẽ thấy state chưa commit, và exception từ
  listener sẽ rollback giao dịch IPN khiến VNPay retry đủ 10 lần.
- **Không dùng `ApiEnvelope` cho IPN.** VNPay đọc phản hồi theo đúng hai key `RspCode`/`Message` ở
  mức gốc — đây là contract của bên thứ ba.

## Cấu hình

Bốn biến `VNPAY_*` fail-fast (thiếu là app không boot): `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`,
`VNPAY_PAY_URL`, `VNPAY_RETURN_URL`. Hướng dẫn sandbox: `docs/vnpay-sandbox.md`.

## Trạng thái hiện tại

- Gateway + IPN + Return + entity: có (VNPay).
- **Endpoint khởi tạo thanh toán `POST /api/invoices/{invoiceId}/pay`: chưa có** — FE nút thanh toán
  đang chạy chế độ demo khi chưa cấu hình `VITE_API_BASE_URL`; endpoint thật thuộc luồng đặt cọc.
- **Chưa có ai nghe `PaymentSucceeded`/`PaymentFailed`** — invoice/booking chưa có code (các luồng đơn hàng).
- **Job đối soát phiên (`querydr`) + xử lý xác nhận muộn: chưa có trong code** — thuộc luồng đặt cọc.
- **Chặn hủy đơn khi phiên thanh toán còn mở: chưa có trong code** — cần chốt spec trước.
