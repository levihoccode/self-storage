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
`PaymentSucceeded` rồi trong một transaction: `Invoice=Paid` · `RentalOrder=Deposited` ·
`StorageUnit=Reserved` · notify khách `DEPOSIT_PAYMENT_SUCCEEDED` · ghi 2 bản ghi audit
(`RENTAL_ORDER_DEPOSITED`, `STORAGE_UNIT_RESERVED`).

Hạn đơn **không đổi**: `db-table-draft.md` › RentalOrder ghi `expires_at` "set một lần khi tạo
dơn", và hạn giữ kho là phần còn lại của chính hạn đó.

Khoang chỉ chuyển `Reserved` khi vẫn còn `Available` tại thời điểm xác nhận — cổng có thể gọi lại
tới 10 lần trong 50 phút, đủ lâu để khách khác cọc trước. Khoang đã đổi chủ thì đơn giữ nguyên
trạng thái và tiền phải hoàn thủ công.

Listener bắt buộc `@TransactionalEventListener(AFTER_COMMIT)` **và**
`@Transactional(REQUIRES_NEW)` — xem `payment.md` › Quy tắc bất biến. `AuditRecorder` dùng
propagation `MANDATORY` nên không có transaction mới là lỗi wiring.

## Ghi chú nghiệp vụ

- **Số tiền đọc từ `Invoice` trong DB**, không nhận từ client — cổng đối chiếu `vnp_Amount` với
  chính con số đó khi xác nhận.
- `vnp_TxnRef` dạng `INV{invoiceId}T{yyyyMMddHHmmss}` GMT+7 kèm hậu tố ngẫu nhiên: mốc thời gian
  chỉ tới giây nên hai lần bấm trong cùng một giây sẽ trùng mã và vỡ unique constraint.
- Phiên đang mở **của chính hóa đơn này** không chặn khách: bấm back rồi bấm lại không phải
  "khách hàng khác". Chỉ phiên của hóa đơn khác trên cùng khoang mới chặn.
- Hóa đơn không tồn tại và hóa đơn của người khác trả **cùng một** 404.
- Cơ sở chưa gán FM thì vẫn hủy hóa đơn, chỉ bỏ qua thông báo — thiếu người nhận không được chặn
  nghiệp vụ.
- `PAYMENT_SUCCEEDED` audit thuộc module `payment` (entity `PaymentTransaction`), không ghi ở đây.

## Chưa có trong luồng này

- Cron hủy đơn quá hạn thanh toán và đối soát phiên bằng `querydr`.

## Liên quan

- [payment.md](../../payment.md) · [vnpay-ipn.md](../payment/vnpay-ipn.md) · [routes.md](../../routes.md)
