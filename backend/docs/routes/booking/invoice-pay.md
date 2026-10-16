---
purpose: Mở phiên thanh toán cho hóa đơn cọc — năm nhánh kiểm của Flow 1.4 và nhánh chốt khi IPN về.
---
# POST /api/invoices/{invoiceId}/pay

Khách bấm "Tiến hành thanh toán" trên hóa đơn cọc. Endpoint kiểm điều kiện, mở một phiên thanh
toán và trả URL VNPay để FE chuyển hướng.

- **Actor / quyền:** khách đã đăng nhập, **đã xác minh email**, và phải là chủ hóa đơn.
- **Contract chi tiết:** annotation tại `InvoicePaymentController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Năm nhánh kiểm — đúng thứ tự spec §1.4

| # | Điều kiện | Kết quả |
|---|---|---|
| 1 | `RentalOrder.expires_at < now` | 409 "Đơn thuê kho đã hết hạn…" — không đổi gì |
| 2 | khoang `Reserved`/`Rented` | 409 + `Invoice=Canceled` + notify FM `PROPOSAL_REPROPOSAL_REQUIRED` |
| 3 | khoang `Maintenance` | 409 + `Invoice=Canceled` + notify FM `PROPOSAL_REPROPOSAL_REQUIRED` |
| 4 | khoang đang có phiên thanh toán khác | 409 "…đang trong quá trình xử lý thanh toán…" — không đổi gì |
| 5 | không vướng gì | `PaymentTransaction(Pending)` + trả `paymentUrl` |

Thứ tự quan trọng: đơn quá hạn chặn **trước** khi đụng tới khoang (không vô hiệu hóa đơn của một
khoang vẫn còn tốt), còn khóa phiên kiểm **sau cùng** (không báo "đang xử lý" cho khoang thật ra
đã `Reserved`).

## Khóa giao dịch ngắn hạn

Không có bảng khóa và không dùng Redis. Một khoang coi là đang bận khi tồn tại
`PaymentTransaction` còn `Pending` thuộc đơn của khoang đó, tạo chưa quá `PaymentSession.WINDOW`
(15 phút) — đúng bằng `vnp_ExpireDate` mà gateway đặt, nên khóa và URL hết hạn cùng lúc.

Kiểm **theo khoang, không theo hóa đơn**: hai khách khác nhau trả cho hai hóa đơn khác nhau vẫn
tranh cùng một khoang.

Thanh toán thất bại không cần gỡ khóa: giao dịch chuyển `Failed` nên tự hết `Pending`.

## Nhánh thành công — chốt khi IPN về, không chốt ở đây

Endpoint này **không bao giờ** ghi nhận đã thu tiền. `DepositPaymentListener` nghe
`PaymentSucceeded` rồi trong một transaction: `Invoice=Paid` · `RentalOrder=Deposited` với
`expires_at = now + order.deposit_expiry_days` · `StorageUnit=Reserved` · notify khách
`DEPOSIT_PAYMENT_SUCCEEDED` · ghi 2 bản ghi audit (`RENTAL_ORDER_DEPOSITED`,
`STORAGE_UNIT_RESERVED`).

Listener bắt buộc `@TransactionalEventListener(AFTER_COMMIT)` **và**
`@Transactional(REQUIRES_NEW)` — xem `payment.md` › Quy tắc bất biến. `AuditRecorder` dùng
propagation `MANDATORY` nên không có transaction mới là lỗi wiring.

## Ghi chú nghiệp vụ

- **Số tiền đọc từ `Invoice` trong DB**, không nhận từ client. Đây là chỗ đóng `TODO(invoice)`
  mà A6 để lại.
- `vnp_TxnRef` dạng `INV{invoiceId}T{yyyyMMddHHmmss}` GMT+7 — chữ và số, không trùng trong ngày.
- Hóa đơn không tồn tại và hóa đơn của người khác trả **cùng một** 404.
- Cơ sở chưa gán FM thì vẫn hủy hóa đơn, chỉ bỏ qua thông báo — thiếu người nhận không được chặn
  nghiệp vụ.
- `PAYMENT_SUCCEEDED` audit thuộc module `payment` (entity `PaymentTransaction`), không ghi ở đây.

## Chưa có trong PR này

- Cron hủy đơn quá hạn thanh toán + đối soát phiên bằng `querydr` — chờ spec-gap #117.
- Hóa đơn cọc do B3 tạo; hiện test bằng invoice seed tay.

## Liên quan

- [payment.md](../../payment.md) · [vnpay-ipn.md](../payment/vnpay-ipn.md) · [routes.md](../../routes.md)
