---
purpose: Module payment — luồng thanh toán VNPay: tạo giao dịch, IPN/return, local repair và đối soát thủ công khi IPN mất.
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
Module sở hữu Invoice (booking) nghe PaymentSucceeded → chuyển Invoice sang Paid,
chốt đơn và khoang theo loại hoá đơn (routes/booking/invoice-pay.md › Nhánh thành công)
```

## Ba lớp xác nhận & đối soát

Không dùng `querydr` tự động. Ba lớp không thay thế nhau; mỗi lớp chỉ xử lý state mà lớp trước bỏ lại.

| Lớp | Khi nào chạy | Chặn được | Không chặn được |
|---|---|---|---|
| **1. IPN** | VNPay gọi `GET /api/payments/vnpay/ipn` | IPN giả/sai chữ ký (`97`), sai merchant/amount (`01`/`04`), IPN lặp (`02`); ghi `Success`/`Failed` | IPN không về; listener booking lỗi sau khi IPN đã commit |
| **2. Local repair** | `DepositStateRepairJob` mỗi 5 phút | `PaymentTransaction(PAY/Success)` + invoice `Unpaid` (`Deposit`/`DepositSupplement`) do listener lỗi; replay listener idempotent | IPN mất hẳn (transaction còn `Pending`); không tự gọi cổng/tạo giao dịch |
| **3. Đối soát thủ công** | `PendingPaymentWatchdogJob` tạo case; ADMIN tra portal rồi reconcile | `Pending` do IPN mất; chốt `Success`/`Failed`; Success thứ hai thành `Duplicate`, không ghi Success lần hai, notify FM | Không tự retry; không tự hoàn tiền; job chỉ alert, không tự chốt |

**Nhớ nhanh:** IPN về → lớp 1. Success đã ghi nhưng invoice còn `Unpaid` → lớp 2. IPN không về,
transaction còn `Pending` → lớp 3.

Lớp 3 dùng **watchdog + case**: `PendingPaymentWatchdogJob` mỗi 5 phút tạo case cho
`PAY/Pending` quá 15 phút, gửi notification cho ADMIN; ADMIN xem danh sách case rồi gọi
`POST /api/admin/payments/{txnRef}/reconcile`. Job không đổi trạng thái và không gọi cổng.
Case/evidence chi tiết ở [routes/payment/admin-pending-payments.md](routes/payment/admin-pending-payments.md).
Không lớp nào tự hoàn tiền.

## Hành vi

- **Đồng hồ của hóa đơn: `Invoice.due_date`.** Quá hạn là mốc duy nhất của "quá hạn thanh toán" —
  Flow 6 xử lý (`Expired` + cascade hủy đơn).
  - **LƯU Ý:** đừng nhầm với cửa sổ phiên VNPay (`vnp_ExpireDate` = lúc tạo URL + `PAYMENT_WINDOW`,
    hiện 15') — cửa sổ chỉ là giới hạn kỹ thuật (hết cửa sổ là cổng không cho trả tiếp); khách bấm
    trả sát `due_date` vẫn có đủ 15'.
- **Xác nhận muộn / unit đã mất.** Tiền đã vào thì invoice vẫn `Paid`. Nếu khoang không còn giữ
  được, không reserve unit, order giữ trạng thái cũ, invoice có `refund_status=Required` +
  `refund_reason=UNIT_UNAVAILABLE`; ghi audit `INVOICE_REFUND_REQUIRED`, notify FM/customer và xử lý
  hoàn tiền thủ công.
- **Huỷ đơn có cọc (FM).** `POST /api/fm/rental-orders/{id}/cancel` với `refundDeposit=true` chuyển
  mỗi hoá đơn `Paid` của đơn sang `refund_status=Required` + `refund_reason=ORDER_CANCELED`; ghi audit
  `INVOICE_REFUND_REQUIRED`, notify FM `ORDER_CANCELED_REFUND_REQUIRED` và khách
  `ORDER_CANCELED_REFUND_PENDING`. Không tạo `refund_transactions`; tiền ra vẫn qua handover.
  `refundDeposit=false` (mặc định) là khách mất cọc.
- **Sửa state khi listener IPN lỗi.** `PaymentTransaction(PAY/Success)` là bản ghi bền.
  `DepositStateRepairJob` quét invoice `Unpaid` (`Deposit`/`DepositSupplement`) có giao dịch
  `PAY/Success`, rồi gọi lại `DepositPaymentListener` (idempotent). Không cần querydr, không thêm
  bảng outbox. Job không cứu được IPN mất hẳn (giao dịch còn `Pending`) — ca đó vẫn cần nguồn đối
  soát ngoài (báo cáo portal).
- **URL phiên cache Redis.** Key `payment:url:{invoiceId}`, TTL 15 phút, khớp `vnp_ExpireDate`.
  `/pay` lần nữa trên cùng hoá đơn trả lại URL cũ; cache mất thì `resumePayment` dựng lại URL từ
  transaction Pending — cùng `vnp_TxnRef`, `vnp_CreateDate`/`vnp_ExpireDate` gốc, không tạo giao dịch
  mới. Không còn Pending trong 15 phút thì caller trả 409. Cache không phải khóa — khóa phiên vẫn
  suy từ `PaymentTransaction(Pending)`.
- **`vnp_OrderInfo` bỏ dấu trước khi ký.** VNPay chỉ hiển thị được ASCII; chuỗi có dấu bị encode
  thành `?`. `VnPayGateway` chuẩn hoá về không dấu trước khi ghép tham số, nên chuỗi ký và chuỗi
  khách thấy khớp nhau: `Đặt cọc khoang chứa A-01` → `Dat coc khoang chua A-01`.

## Quy tắc bất biến

- **IPN là nguồn xác nhận thanh toán chính.** Return URL nằm trên thanh địa chỉ, khách sửa được —
  không bao giờ dùng để ghi nhận đã trả tiền. Khi IPN không về, nguồn dự phòng là **đối soát thủ
  công qua portal VNPay**; không có querydr tự động trong hệ thống.
- **Không tự đóng `Pending` theo đồng hồ.** Giao dịch Pending không tự chuyển trạng thái; ops là
  người quyết định sau khi tra portal, qua endpoint ADMIN, có audit + event như IPN.
- **Idempotent theo `vnp_TxnRef`.** VNPay retry tối đa 10 lần (cách 5 phút) và có thể gọi song song;
  row bị khóa `PESSIMISTIC_WRITE` khi đọc, lần thứ hai trả `02` và không ghi gì. Unique constraint
  không thay được khóa vì cả hai cùng update một row.
- **Thứ tự kiểm trong IPN:** chữ ký (`97`) → tìm giao dịch (`01`) → `vnp_TmnCode` (`01`)
  → `vnp_Amount` (`04`) → guard trạng thái (`02`) → OK (`00`). Sai chữ ký thì không để lộ gì về
  giao dịch.
- **Số tiền:** cổng nhận `amount × 100`; đối chiếu khi IPN về. Nguồn số tiền tin cậy là **hóa đơn
  trong DB** — caller đọc `Invoice` rồi truyền amount vào `startPayment`; `payment` không import
  `booking` nên không tự đọc lại.
- **Không mở phiên mới khi đã có giao dịch `PAY/Success`.** IPN ghi giao dịch trước khi listener
  booking chạy; nếu listener chưa chạy/lỗi, invoice vẫn `Unpaid`. `/pay` kiểm giao dịch thành công
  và trả 409, không thu lần hai; local repair hoặc đối soát thủ công chốt phần còn lại.
- **Một invoice tối đa một `PAY/Success`.** Advisory lock theo invoice là lớp chính; partial unique
  index `(invoice_id) WHERE direction = 'PAY' AND status = 'Success'` (V18) là chốt cuối ở DB.
  Nếu một nguồn xác nhận thành công thứ hai cho cùng invoice, transaction thứ hai chuyển `Duplicate`
  — không phát `PaymentSucceeded`, không đụng invoice/order/unit. Ghi audit `PAYMENT_DUPLICATE`,
  notify FM để hoàn tiền thủ công. IPN trả `02`; manual trả `200` với status `DUPLICATE`.
- **Ranh giới module:** `PaymentTransaction.invoiceId` là `Long`, không quan hệ JPA với bảng
  `invoices`; module khác giao tiếp qua event, không import chéo.
- **Listener event bắt buộc `AFTER_COMMIT`.** `PaymentSucceeded`/`PaymentFailed` được publish
  *trong* transaction IPN — module nghe phải dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`
  (hoặc `REQUIRES_NEW`). Listener đồng bộ mặc định sẽ thấy state chưa commit, và exception từ
  listener sẽ rollback giao dịch IPN khiến VNPay retry đủ 10 lần.
- **Không dùng `ApiEnvelope` cho IPN.** VNPay đọc phản hồi theo đúng hai key `RspCode`/`Message` ở
  mức gốc — đây là contract của bên thứ ba.


## Hoàn tiền thủ công

Tiền ra có ledger riêng `refund_transactions`; `payment_transactions` chỉ ghi tiền vào (`PAY`).
Không có bước FM duyệt giữa FS đưa tiền và khách xác nhận.

```text
Required/Processing
  → FS/FM ghi nhận đã đưa tiền (refund_transactions Success)
  → AwaitingConfirmation
  → khách xác nhận đã nhận
  → Refunded
```

- **FS/FM handover:** `POST /api/refunds/invoices/{invoiceId}/handover` với `method`, `evidenceUrl`,
  `gatewayRef?`, `note?`, `sourceTransactionId?`, `reasonNote?`. Số tiền lấy từ `invoice.amount`
  và phải ≤ giao dịch PAY gốc (`Success` hoặc `Duplicate`), không nhận amount từ client. FS dùng permission
  `refund_transaction.handover`; FM dùng `payment_transaction.refund`; ADMIN override; tất cả kiểm
  facility scope. Evidence bắt buộc.
- **Khách xác nhận:** `POST /api/refunds/invoices/{invoiceId}/confirm`; chỉ nhận khi refund ledger
  đã `Success` và invoice đang `AwaitingConfirmation`, nếu chưa trả `409`. Xác nhận lặp lại trả kết
  quả cũ, không ghi audit trùng. Invoice chuyển `Refunded`, set `refunded_at`.
- **Khách báo chưa nhận:** `POST /api/refunds/invoices/{invoiceId}/dispute`; lần đầu ghi
  `disputed_at/dispute_note`, audit `REFUND_CUSTOMER_DISPUTED` + notification, giữ invoice
  `AwaitingConfirmation`, không tạo refund lần hai. Gọi lặp trả kết quả cũ, không ghi audit/notification
  trùng. Support request/Flow 7 xử lý tiếp.
- **Bất biến:** một `refund_transactions` `Success` cho mỗi `source_transaction_id` (partial unique
  index V22); `status='Success'` bắt buộc có `disbursed_at`; `reason_code=OTHER` bắt buộc
  `reason_note`. `reason_code` snapshot từ `invoices.refund_reason` tại lúc handover. Partial refund
  từ cùng một source chưa hỗ trợ — cần đổi unique index và contract trước khi dùng cho hoàn cọc
  một phần.
- **Queue:** `GET /api/refunds/queues/invoices`; ADMIN dùng `view=active|history` (active gồm
  `Required`, `Processing`, `AwaitingConfirmation`; history gồm `Refunded`). FM dùng
  `facilityId=...&view=active`, chỉ thấy invoice `Required`/`Processing` của cơ sở.
- **Notification:** sau khi handover commit, listener gửi `REFUND_AWAITING_CONFIRMATION` cho khách
  (in-app + email). Gửi lỗi không rollback nghiệp vụ.

### Refund obligation — nghĩa vụ trước khi chi tiền

`POST /api/refunds/obligations` tạo **refund obligation** thủ công, chưa chi tiền và chưa tạo
`refund_transactions`. Dùng cho case đặc biệt không có obligation tự động hoặc amount khác invoice.

- FM theo facility; ADMIN override; permission `refund_obligation.create`.
- Obligation có thể gắn `invoiceId`, `orderId`, hoặc cả hai; ít nhất một cái.
- Server tính final amount từ `lines`, không nhận amount từ client:
  `final = sum(ADD) - sum(SUBTRACT)`.
- `calculationVersion` + `calculationInput` là snapshot để giải thích/recompute.
- `formulaText` chỉ để hiển thị/audit, không dùng để tính tiền.
- Obligation lưu `amount`, `formulaText`, lines; audit `REFUND_OBLIGATION_CREATED` trên `RefundObligation`.
- Obligation ở trạng thái `Open`; handover hiện tại chưa nối với obligation này — cần chuyển
  handover sang `refundObligationId` trước khi dùng trong vận hành.
- Route `POST /api/refunds/obligations` đang gate sau profile `refund-obligation`, mặc định không
  expose; bật khi có luồng chi tiền cho obligation.

### Phạm vi route handover

`POST /api/refunds/invoices/{invoiceId}/handover` là **action chi tiền cho một nghĩa vụ hoàn tiền đã tồn tại**
trên invoice. Route không tạo nghĩa vụ hoàn tiền, không quyết định invoice có được hoàn hay không,
và không phải route riêng cho từng lý do.

```text
Flow nghiệp vụ tạo nghĩa vụ
  (unit mất khi cọc, hoặc FM huỷ đơn với refundDeposit=true)
  → invoice.refund_status = Required
  → POST /refunds/invoices/{id}/handover (FS/FM xác nhận đã đưa tiền)
  → khách confirm
```

- Điều kiện vào route: invoice đang `Required`/`Processing`; source PAY cùng invoice; amount ≤ source.
- `reason_code` chỉ là snapshot lý do tại lúc handover; không thay đổi permission hay endpoint.
- Các loại hoàn tiền tương lai như trả cọc khi kết thúc thuê, dư phí hoặc chi phí sửa chữa phải
  tạo nghĩa vụ trước (set `refund_status=Required` hoặc tạo invoice/obligation tương ứng), rồi dùng
  lại route handover này.
- Nếu nghiệp vụ cần bước tính toán/duyệt riêng, thêm endpoint `settle`/`approve` trước handover;
  không thêm endpoint handover thứ hai trùng chức năng.

`payment_transaction.refund` là permission cũ của FM cho action handover hiện tại; nó **không** phải
quyền wildcard cho mọi endpoint refund tương lai. Mỗi endpoint refund mới phải tự khai báo guard và
permission tương ứng.

## Known limitations

- **Không có đối soát phiên tự động (`querydr`).** IPN không về → `PaymentTransaction(Pending)` nằm
  nguyên; invoice/order/unit giữ state cũ; ops phải tra portal rồi chốt qua endpoint ADMIN. Không có
  job tự đóng; `Pending` còn hạn 15' có thể chặn mở phiên mới.
- **Listener lỗi sau IPN** được local repair xử lý tối đa mỗi 5 phút; nếu job không cứu được thì vẫn
  cần đối soát thủ công.
- **Chưa có cron hủy đơn quá hạn thanh toán** (Flow 6); invoice/order không tự `Expired`/`Canceled`,
  khoang không tự release theo hạn.
- **Chưa có hoàn tiền tự động qua VNPay** (Flow 4). Handover/confirm thủ công đã có; VNPay
  `vnp_Command=refund` và querydr vẫn tách issue riêng.
- **FS chưa có màn hoàn tiền.** FS có `refund_transaction.handover` nhưng
  `GET /api/refunds/queues/invoices` chỉ mở cho ADMIN/FM, và FS shell chưa có trang refund. Khi có
  module task, mỗi refund task mang sẵn invoice/amount/reason và mở thẳng form handover — FS không
  cần màn danh sách refund riêng; xem [follow-up.md](follow-up.md) › FS tasks.
- **Refund obligation chưa nối chi tiền.** `POST /api/refunds/obligations` chỉ ghi obligation/lines;
  order-only obligation không có disbursement/queue/confirm. Invoice-bound vẫn dùng handover invoice
  với `invoice.amount`, chưa dùng `obligation.amount`; `refund_transactions` chưa có
  `refund_obligation_id`. Route đang gate sau profile `refund-obligation`, mặc định tắt.
- **Obligation state mới `Open`.** `Approved`/`Disbursed`/`Canceled` chưa có endpoint; invoice
  `Processing` không code nào set. Không có idempotency/cancel cho order-only.
- **Một invoice chỉ handover một lần.** Mỗi `source_transaction_id` tối đa một `Success`; duplicate
  khác cần obligation/transaction riêng. Lịch sử ledger của cơ sở đọc qua
  `GET /api/fm/transactions` (invoice/fee + payment + refund); khách và ADMIN chưa có route tương ứng.
- **Chưa có support request/Flow 7** để phân công FS và theo dõi dispute; hiện dispute chỉ ghi audit
  + notification một lần.
- **Chỉ VNPay.** Chưa provider-neutral; `PaymentTransaction` chưa có cột provider; chưa có ZaloPay/payOS.
- **Giả định một instance.** `DepositStateRepairJob` và `PendingPaymentWatchdogJob` chạy mỗi 5 phút;
  chưa cấu hình ShedLock vì deployment hiện tại chỉ một instance. Nếu scale ngang phải bổ sung khóa job.

## Cấu hình

Bốn biến `VNPAY_*` fail-fast (thiếu là app không boot): `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`,
`VNPAY_PAY_URL`, `VNPAY_RETURN_URL`. Hướng dẫn sandbox: `docs/vnpay-sandbox.md`.


## Đối soát thủ công — thay cho querydr

- **Endpoint:** `POST /api/admin/payments/{txnRef}/reconcile`; quyền `ADMIN`; contract chi tiết ở
  [routes/payment/manual-reconcile.md](routes/payment/manual-reconcile.md).
- **Case Pending:** `PendingPaymentWatchdogJob` tạo case `Open`; ADMIN xem
  `GET /api/admin/payments/reconciliation?view=open|history`; reconcile đóng case và lưu
  note/evidence. `/api/admin/payments/pending` là alias cũ của `view=open`.
  Khoản cần hoàn tiền có queue riêng `GET /api/refunds/queues/invoices` (ADMIN cross-facility,
  FM theo `facilityId`; xem mục **Hoàn tiền thủ công** bên dưới).
  Evidence là URL file Admin upload qua `POST /api/media/uploads`, optional.
- **Chỉ giao dịch `Pending`.** Row bị khoá `PESSIMISTIC_WRITE`; invoice được khoá thêm bằng
  advisory lock để hai manual request cùng invoice serialize. Giao dịch đã `Success`/`Failed`/
  `Duplicate`, hoặc `gatewayTransactionNo` trùng giao dịch khác trả `409`, không ghi thêm.
  Invoice đã có `PAY/Success` khác trả `200` với status `DUPLICATE`, không phải `409`.
  `SUCCESS` bắt buộc `paidAt` (không tương lai, không trước `createdAt` — so sánh tới giây) và
  `gatewayTransactionNo`; thiếu/sai trả `400`. Amount lấy từ `PaymentTransaction`, không nhận từ
  request.
- **Ghi như IPN:** `markSucceeded`/`markFailed`, audit `PAYMENT_SUCCEEDED`/`PAYMENT_FAILED`
  (`newValue` của Success gồm `gatewayTransactionNo` + `paidAt`; reason của manual ghi email
  admin), phát `PaymentSucceeded`/`PaymentFailed`; listener booking cập nhật invoice/order/unit.
- **FM xem danh sách:** `GET /api/fm/invoices?facilityId=...` trả hoá đơn cơ sở kèm
  `paymentStatus`, `pendingAgeMinutes`, `needsReconciliation`; đây là tín hiệu để FE highlight,
  không tự đối soát.
- **Quy trình ops:** lọc `PaymentTransaction(Pending)` quá 15'; tra portal VNPay theo `vnp_TxnRef`;
  nếu đã trả → gọi endpoint với `outcome=SUCCESS`, `paidAt`, `gatewayTransactionNo`; nếu không →
  `outcome=FAILED` + note. Không sửa DB tay.
- **Giới hạn:** không tự retry reconcile; watchdog chỉ tạo case/alert, không tự chốt.
  Giao dịch `Pending` không có ai tra vẫn nằm nguyên — case giúp phát hiện, không thay ops.
  Evidence chỉ là bằng chứng hỗ trợ; nguồn sự thật vẫn là transaction/audit.

## Trạng thái hiện tại

- Gateway + IPN + Return + entity: có (VNPay).
- **Endpoint khởi tạo thanh toán `POST /api/invoices/{invoiceId}/pay`: có** — mở phiên cho hoá đơn
  `Deposit`/`DepositSupplement`; contract ở `routes/booking/invoice-pay.md`.
- **`PaymentSucceeded`/`PaymentFailed`: `booking` đã nghe** — `DepositPaymentListener` chốt hoá đơn,
  đơn và khoang khi `PaymentSucceeded`; `PaymentFailed` chỉ ghi log vì khoá phiên tự hết hạn. Chi
  tiết ở `routes/booking/invoice-pay.md`.
- **Sửa state booking từ giao dịch Success: có** — `DepositStateRepairJob`, xem “Sửa state khi
  listener IPN lỗi”.
- **Đối soát thủ công: có** — `POST /api/admin/payments/{txnRef}/reconcile` (ADMIN), xem
  [routes/payment/manual-reconcile.md](routes/payment/manual-reconcile.md).
- **Querydr tự động: không dùng** — thay bằng đối soát thủ công; chấp nhận `Pending` không tự đóng.
- **Hoàn tiền thủ công: có** — `refund_transactions`, handover/confirm/dispute, queue active/history;
  xem mục **Hoàn tiền thủ công**.
- **Chặn hủy đơn khi phiên thanh toán còn mở: chưa có trong code** — cần chốt và ghi vào docs trước.
