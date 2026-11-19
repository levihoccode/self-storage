---
purpose: Kho đang thuê sau bàn giao (Flow 3) và quá hạn (Flow 6) as-built — tiền thuê hằng tháng, gia hạn, yêu cầu trả kho, phí quá hạn, tạm khoá truy cập.
---
# Kho đang thuê, tiền thuê, gia hạn, quá hạn

Spec Flow 3 và Flow 6 chỉ có tiêu đề; trang này là hành vi as-built của hai flow. Muốn đổi thì mở `behavior-gap`.
Trang này cũng là contract của các route bên dưới (không có file riêng trong `routes/`); field chi tiết nằm ở
Swagger trên `tenancy/controller/TenancyController.java` và `overdue/controller/OverdueController.java`.

Code:
- `tenancy/application/TenancyService.java` — kho đang thuê, gia hạn, yêu cầu trả kho, FM phân công;
- `overdue/application/OverdueService.java` + `OverdueJob.java` — nhắc sắp hết hạn, phí quá hạn, tạm khoá / mở lại
  truy cập;
- `overdue/application/RentBillingService.java` — hoá đơn tiền thuê hằng tháng.

Hoá đơn đi qua `booking/application/ContractInvoiceService.java`, hợp đồng qua
`handover/application/RentalContractService.java`.

## Route

Mọi route cần bearer token + `ACCOUNT_ACTIVE`; prefix quyết role (`/api/customer/**` → `CUSTOMER`,
`/api/fm/**` → `FM`, `/api/staff/**` → `FS`, sai role → 403). Lỗi nghiệp vụ trả `{message, timestamp}` qua
`TenancyExceptionHandler`: 400 dữ liệu sai, 404 không thấy (kể cả tài nguyên của khách khác), 409 xung đột trạng thái.

| Method | Path | Ai gọi | Lỗi chính |
|---|---|---|---|
| GET | `/api/customer/rentals` | khách — hợp đồng `Active` của chính mình | — (không có thì `data: []`) |
| GET | `/api/customer/rentals/ended` | khách — hợp đồng `Ended` (đã trả kho) của chính mình, kết thúc gần nhất trước | — (không có thì `data: []`) |
| GET | `/api/customer/rentals/{contractId}` | khách chủ hợp đồng (mọi trạng thái hợp đồng) | 404 "Không tìm thấy hợp đồng thuê" (không có / của khách khác) |
| POST | `/api/customer/rentals/{contractId}/extensions` body `{months}` | khách chủ hợp đồng | 400 thiếu `months` / ngoài 1–`extension.max_months`; 404; 409 hợp đồng không `Active`, đang có yêu cầu trả kho mở, đã có hoá đơn gia hạn `Unpaid`, còn phí quá hạn `INV-OVD` chưa trả (trả phí trước) |
| POST | `/api/customer/rentals/{contractId}/return-request` body `{preferredDate, note?}` | khách chủ hợp đồng | 400 `preferredDate` trước hôm nay / quá 30 ngày tới, `note` > 1000 ký tự; 404; 409 hợp đồng không `Active`, đã có yêu cầu đang xử lý, còn hoá đơn gia hạn `Unpaid` |
| GET | `/api/customer/return-requests` | khách — mọi yêu cầu trả kho của mình, mới nhất trước | — |
| POST | `/api/customer/return-requests/{id}/cancel` | khách chủ yêu cầu | 404 "Không tìm thấy yêu cầu trả kho"; 409 yêu cầu đã đóng, FS đã xác nhận khách đến |
| GET | `/api/fm/return-requests?facilityId=` | FM có `rental_order.read` tại cơ sở — yêu cầu `Pending`/`Assigned`, cũ nhất trước | 403 thiếu quyền / ngoài phạm vi cơ sở |
| POST | `/api/fm/return-requests/{id}/assign` body `{staffId}` | FM có `appointment.assign` tại cơ sở của yêu cầu | 400 FS không thuộc cơ sở hoặc không hoạt động; 403; 404; 409 yêu cầu đã đóng, buổi trả kho đã bắt đầu, không còn khung trống trong 14 ngày, FS nghỉ phép ngày hẹn / không có ca tại cơ sở bao trọn khung giờ vừa đặt ([backoffice/hr.md](../backoffice/hr.md#luật-phân-công-theo-ca--nghỉ-phép)) |
| GET | `/api/staff/return-requests` | FS — yêu cầu `Assigned` phân cho chính mình | — |
| GET | `/api/fm/overdue-contracts?facilityId=` | FM có `rental_order.read` tại cơ sở | 403 thiếu quyền / ngoài phạm vi cơ sở |

Route trả kho tại cơ sở (`…/checkout`) thuộc [checkout/return.md](../checkout/return.md).

## Kho đang thuê

`GET /api/customer/rentals` trả mọi hợp đồng `Active` của khách, gồm:
- hoá đơn của hợp đồng, quyền truy cập đã cấp, yêu cầu trả kho đang mở;
- `daysLeft`, `overdue` (hợp đồng `Active` đã qua `end_date`; hợp đồng đã kết thúc luôn `false`);
- yêu cầu trả kho (`ReturnRequestView`) có `appointmentStart` và `appointmentArrivedAt` (mốc FS xác nhận khách đến
  buổi trả kho; `null` = chưa đến / chưa có lịch);
- `canExtend`: hợp đồng `Active`, không có yêu cầu trả kho mở, không có hoá đơn gia hạn chờ trả, không còn phí quá
  hạn `INV-OVD` chưa trả;
- `canRequestReturn`: hợp đồng `Active`, không có yêu cầu trả kho mở, không có hoá đơn gia hạn chờ trả;
- `accessSuspended`: hợp đồng `Active` đang tạm khoá truy cập do quá hạn kéo dài
  ([bên dưới](#quá-hạn-kéo-dài--tạm-khoá-truy-cập)).

`GET /api/customer/rentals/ended` trả hợp đồng `Ended` cùng dạng `RentalView` (`overdue`, `canExtend`,
`canRequestReturn`, `accessSuspended` luôn `false`) để khách xem lại hợp đồng, hoá đơn và biên bản trả kho sau khi
trả kho xong — hợp đồng không biến mất khỏi tài khoản khi rời danh sách `Active`.

## Gia hạn

1. Khách chọn `months` (1 – `extension.max_months` = 12). Bị chặn (409) khi đã có yêu cầu trả kho mở, còn hoá đơn
   gia hạn `Unpaid`, hoặc **còn phí quá hạn `INV-OVD` chưa trả** — "Vui lòng thanh toán phí quá hạn 140.000 đ (hoá đơn
   INV-OVD-…) trước khi gia hạn". Khách trả phí trước rồi mới gia hạn (hai hoá đơn riêng, không gộp); trả kho thì
   không bị chặn vì phí gộp vào quyết toán, trừ cọc.
2. Hệ thống tạo hoá đơn `Extension`:
   - mã `INV-EXT-{facility}-{YYMMDD}-{rand}`;
   - `amount = monthly_price × months`, giá chốt lúc ký nên không đổi;
   - hạn `now + extension.invoice_due_days` (3 ngày);
   - gắn `contract_id`.
3. Khách thanh toán qua `/api/invoices/{id}/pay`. Hoá đơn gắn hợp đồng của đơn đã `Done` bỏ qua các kiểm tra trạng
   thái / hạn của đơn (xem [deposit-payment.md](../booking/deposit-payment.md)).
4. Cổng xác nhận → `ContractInvoicePaid(type = Extension)` → `ExtensionPaidListener`, cùng transaction chốt thanh
   toán:
   - `months = amount / monthly_price`;
   - `period += months`, `end_date = start_date + period tháng − 1 ngày` (tính lại từ `start_date`, không cộng tháng
     vào `end_date` cũ — end_date cuối tháng cộng dồn bị cắt ngày: 31/1 → 28/2 → 28/3);
   - audit `CONTRACT_EXTENDED`; khách nhận `CONTRACT_EXTENDED`.
5. Hoá đơn gia hạn quá hạn → cron booking chuyển `Expired`; hợp đồng không đổi, khách tạo lại.
6. Gia hạn và trả kho loại trừ nhau theo cả hai chiều: đang có yêu cầu trả kho mở thì không tạo được hoá đơn gia
   hạn, còn hoá đơn gia hạn `Unpaid` thì không gửi được yêu cầu trả kho (409 "Đang có hoá đơn gia hạn chờ thanh
   toán…"). Khách thanh toán hoá đơn hoặc đợi nó `Expired` rồi mới gửi yêu cầu trả kho, nên hợp đồng không bị cộng kỳ
   trong lúc đang trả kho.

## Yêu cầu trả kho

| Bước | Ai | Kết quả |
|---|---|---|
| Gửi yêu cầu | Khách, hợp đồng `Active` | `ReturnRequest` `Pending`, `preferred_date` trong 30 ngày tới; FM nhận `RETURN_REQUESTED`; audit `RETURN_REQUESTED` |
| Phân công | FM (`appointment.assign` + facility scope), FS đang hoạt động của cơ sở | lịch `RETURN` (`Pending`, có `staff_id`) ở khung trống đầu tiên từ `preferred_date` mà FS có ca bao trọn và không nghỉ phép (không khung trống nào thoả thì lấy khung trống đầu tiên và báo 409 bên dưới), tìm tối đa 14 ngày; `Assigned`; khách + FS nhận `RETURN_SCHEDULED`; ngày khách chọn đã kín và lịch bị dời sang ngày khác → khách nhận thêm `RETURN_DATE_SHIFTED` (ngày giờ mới); audit `RETURN_ASSIGNED`. Khung được chọn vẫn kiểm lại: FS phải không nghỉ phép ngày đó và (policy `staff.shift_required` bật) có ca tại cơ sở bao trọn khung giờ — vi phạm → 409, rollback cả lịch vừa đặt |
| Phân công lại | FM | lịch cũ chưa có khách đến → `Canceled`, tạo lịch mới |
| Huỷ | Khách | chỉ khi FS chưa xác nhận khách đến; lịch `Pending` → `Canceled`; audit `RETURN_CANCELED` |
| Khách không đến | cron `ReturnNoShowJob` | [bên dưới](#khách-không-đến-buổi-trả-kho) |
| Hoàn tất | checkout | `Completed` + `completed_at`; audit `RETURN_COMPLETED` |

- Lịch `RETURN` dùng chung ba khung cố định với `CHECKIN`: một lịch mỗi khung mỗi cơ sở, tính cả hai loại.
- Khách đến trễ sau `end_at` nhưng trong `return.no_show_grace_minutes` (120) FS vẫn xác nhận đến được; quá mốc đó cron
  no-show huỷ lịch. FS không huỷ được lịch `RETURN` qua route huỷ lịch (409) — FM phân công lại hoặc khách huỷ yêu cầu
  ([staff-appointment-arrive.md](../routes/booking/staff-appointment-arrive.md)).
- Mỗi hợp đồng chỉ có một yêu cầu `Pending`/`Assigned` (partial unique index V25).
- Thứ tự khoá: đơn → hợp đồng → yêu cầu trả kho.

### Khách không đến buổi trả kho

`ReturnNoShowJob` chạy mỗi 10 phút (`tenancy.return-no-show.fixed-delay-ms`), mỗi lịch một transaction, khoá đơn → hợp
đồng → yêu cầu → lịch. Lịch `RETURN` còn `Pending` (FS chưa xác nhận khách đến) đã quá
`end_at + return.no_show_grace_minutes` và đang gắn với yêu cầu `Assigned`:
- lịch → `Canceled` lý do "Khách không đến buổi trả kho", audit `APPOINTMENT_CANCELED_NO_SHOW` (actor = System);
- `no_show_count += 1`, bỏ `appointment_id` / `assigned_staff_id`;
- chưa chạm `return.max_no_shows` (2) → yêu cầu về `Pending` — FM phân công lại như lần đầu;
- chạm ngưỡng → yêu cầu `Canceled` (`cancel_reason` "Khách không đến buổi trả kho 2 lần"); hợp đồng không còn yêu cầu
  trả kho mở nên quá hạn kéo dài lại bị tạm khoá theo điều kiện thường;
- audit `RETURN_NO_SHOW` (một bản ghi cho yêu cầu); khách + FM nhận `RETURN_NO_SHOW` (nội dung nêu rõ chờ phân công
  lại hay đã huỷ).

Phí quá hạn không cần xử lý thêm: khách không đến thì ngày dừng vẫn là hôm nay. Idempotent — lịch đã huỷ / đã có khách
đến thì bỏ qua.

Trả kho tại cơ sở: xem [checkout/return.md](../checkout/return.md).

## Thu tiền thuê hằng tháng

Hoá đơn tháng đầu (`contract.prepaid_months` tháng) thu lúc bàn giao
([handover/records.md](../handover/records.md)). Các tháng sau của kỳ thuê do `RentBillingService.bill` lập, chạy
trong `OverdueJob` (mỗi giờ) trên mọi hợp đồng `Active`, mỗi hợp đồng một transaction, khoá đơn → hợp đồng:

- **Tháng cần lập** = `period` − số tháng của hoá đơn `Extension` đã `Paid` (gia hạn trả trước và nối vào cuối kỳ).
- **Tháng đã lập** = tổng số tháng của mọi hoá đơn `Rental` **không** `Canceled` của hợp đồng — kể cả tháng đầu và
  hoá đơn `Expired` (nợ vẫn tính). Số tháng một hoá đơn = `amount / monthly_price` làm tròn, tối thiểu 1.
- Còn tháng chưa lập (tháng thứ `k = đã lập + 1`, bắt đầu `monthStart = start_date + (k − 1) tháng`) và hôm nay ≥
  `monthStart − rent.invoice_lead_days` (5) → lập **một** hoá đơn mỗi lượt:
  - `type = Rental`, mã `INV-RNT-{facility}-{YYMMDD}-{rand}`, `amount = monthly_price`;
  - title "Tiền thuê tháng k/period — hợp đồng …", mô tả kỳ `monthStart` – `monthStart + 1 tháng − 1 ngày`;
  - `due_date` = `monthStart`; cron lập muộn (tháng đã bắt đầu) thì tối thiểu hôm nay + 3 ngày;
  - audit `INVOICE_CREATED`; khách nhận `RENT_INVOICE_ISSUED` (kỳ, số tiền, hạn, link `/invoices`).
- Không lập tháng mới khi hợp đồng đang có yêu cầu trả kho mở.
- Hoá đơn `INV-RNT` quá hạn (cron booking `Expired`, `ContractInvoiceExpired` loại `Rental`) trên hợp đồng `Active` →
  FM nhận `RENT_UNPAID` để liên hệ khách; nợ giữ nguyên trên hoá đơn `Expired`, không lập lại — lúc FS chốt trả kho
  hoá đơn này được gộp vào quyết toán (trừ vào cọc) như hoá đơn `Unpaid`. Hoá đơn tháng đầu quá
  hạn xảy ra khi hợp đồng còn `Signed` nên không vào nhánh này (handover đóng phiên bàn giao).
- Hoá đơn tiền thuê còn `Unpaid` hoặc đã `Expired` lúc FS chốt trả kho được gộp vào quyết toán.

## Quá hạn (Flow 6)

`OverdueJob` chạy mỗi giờ (`overdue.job.fixed-delay-ms`), mỗi hợp đồng một transaction, khoá đơn → hợp đồng → hoá
đơn. Mỗi lượt chạy lần lượt: nhắc sắp hết hạn → đồng bộ phí quá hạn (kèm tạm khoá truy cập) → mở lại truy cập →
lập hoá đơn tiền thuê ([trên](#thu-tiền-thuê-hằng-tháng)).

| Điều kiện | Kết quả |
|---|---|
| `end_date` trong `contract.expiry_reminder_days` (7) ngày tới | khách nhận `CONTRACT_EXPIRING_SOON`, một lần mỗi kỳ |
| Hợp đồng `Active`, `end_date < hôm nay` | lần đầu: khách + FM nhận `CONTRACT_OVERDUE` (một lần mỗi kỳ) |
| Như trên, sau `overdue.grace_days` (3) ngày | phí = `ngày tính phí × overdue.fee_per_day` (20.000đ), trừ phần phí quá hạn đã `Paid` **của đợt quá hạn này** (hoá đơn tạo từ `end_date + 1` trở đi) |
| Quá hạn ≥ `overdue.escalation_days` (30) ngày, chưa có yêu cầu trả kho | tạm khoá truy cập — [bên dưới](#quá-hạn-kéo-dài--tạm-khoá-truy-cập) |

`CONTRACT_EXPIRING_SOON` và `CONTRACT_OVERDUE` gửi **một lần mỗi kỳ** — kỳ xác định bằng `end_date` hiện tại (dedup
qua `OrderNotification` theo đơn + type, chỉ tính notification tạo từ đầu cửa sổ của kỳ):
- `CONTRACT_EXPIRING_SOON`: đã có nhắc tạo từ ngày `end_date − contract.expiry_reminder_days` thì bỏ qua;
- `CONTRACT_OVERDUE`: đã có báo tạo từ ngày `end_date + 1` thì bỏ qua.

Gia hạn dời `end_date` nên kỳ mới có cửa sổ mới: khách gia hạn rồi lại sắp hết hạn / lại quá hạn thì nhận nhắc lại một
lần. Gia hạn không phủ hết kỳ quá hạn (`end_date` mới vẫn trong quá khứ) cũng là kỳ mới → báo quá hạn lại một lần.

Phí cộng dồn nằm trên đúng một hoá đơn `Penalty` mã `INV-OVD-…` còn `Unpaid`:
- **Tạo / cập nhật:** cron tạo mới hoặc cập nhật số tiền (audit `OVERDUE_FEE_ACCRUED`), hạn `now + 7 ngày`.
- **Ngày tính phí** = `(ngày dừng − end_date) − grace_days`. Ngày dừng:
  - mặc định là hôm nay — kể cả khi khách đã gửi yêu cầu trả kho (gửi yêu cầu không dừng phí);
  - yêu cầu trả kho đang mở có lịch `RETURN` mà FS **đã xác nhận khách đến** (`appointmentArrivedAt`) → ngày bắt đầu
    của buổi đó (`appointmentStart`), kể cả khi biên bản còn chờ (khoang còn đồ / chờ trả phần thiếu);
  - lịch chưa tới hoặc khách không đến → vẫn hôm nay; ngày dừng không bao giờ vượt hôm nay.
- **Gia hạn khi còn nợ phí:** không được — phải trả `INV-OVD` trước (xem [Gia hạn](#gia-hạn), bước 1). Gia hạn phủ kỳ
  quá hạn (`end_date` mới ≥ hôm nay) thì phí **ngừng cộng thêm**. Hoá đơn phí đã phát sinh không bao giờ bị huỷ vì gia
  hạn: huỷ sẽ cho phép khách chậm rồi gia hạn để xoá sạch phí.
- **Gộp nợ trước khi dọn đồ:** hợp đồng đang tạm khoá, khách đến buổi trả kho → phí chốt tới ngày buổi đó rồi gộp vào
  `INV-DBT` cùng mọi khoản nợ khác ([bên dưới](#quá-hạn-kéo-dài--tạm-khoá-truy-cập)).
- **`INV-OVD` không tự hết hạn:** khi phí ngừng cộng (vd. dừng ở ngày buổi trả kho khách đến) thì `due_date` (now + 7
  ngày lúc đổi số tiền gần nhất) không còn được dời, nên cron hết hạn hoá đơn bỏ qua `INV-OVD` và trang thanh toán cho
  trả cả sau `due_date`. Nếu để `Expired`, quyết toán trả kho (chỉ gộp hoá đơn `Unpaid` + tiền thuê `Expired`) sẽ
  không gộp và khoản nợ bị mất. Còn `INV-OVD` chưa trả thì truy cập bị khoá vì quá hạn kéo dài chưa mở lại. Cron vẫn
  quét hợp đồng đang có `INV-OVD` mở để đồng bộ số tiền khi còn quá hạn.
- **Còn giao dịch `Pending`:** không đổi số tiền, vì cổng đã nhận số cũ — kể cả giao dịch đã quá cửa sổ 15' (không
  còn tính là phiên mở) nhưng chưa được chốt; xác nhận muộn sẽ chốt `Paid` đúng số tiền đã thu.
- **Khách trả phí quá hạn rồi vẫn tiếp tục quá hạn:** hoá đơn mới chỉ thu phần chênh.
- **Gia hạn rồi lại quá hạn (đợt mới):** phí đã trả của đợt trước không bù cho đợt mới — đợt mới tính từ `end_date`
  mới.
- **Khi trả kho:** hoá đơn quá hạn còn `Unpaid` được gộp vào quyết toán. Đã có hoá đơn quyết toán (`INV-CLN` /
  `INV-DMG` / `INV-KEY` / `INV-PEN` chưa `Canceled`) thì cron không phát hành lại `INV-OVD` cho hợp đồng.

### Quá hạn kéo dài — tạm khoá truy cập

Trong bước đồng bộ phí (`OverdueService.accrue` → `escalate`): hợp đồng `Active`, số ngày quá hạn (tính từ
`end_date` tới hôm nay) ≥ `overdue.escalation_days` (30) và **chưa** có yêu cầu trả kho mở →
`RentalContractService.suspendAccess`:
- mã khoá số `Active` → `Revoked` (audit `ACCESS_KEY_REVOKED`, `reason` "Tạm khoá do quá hạn");
- ghi `rental_contracts.access_suspended_at`; audit `CONTRACT_ACCESS_SUSPENDED` (`accessSuspendedAt`, `daysOverdue`);
- khách + FM nhận `CONTRACT_ACCESS_SUSPENDED`; FM được nhắc liên hệ khách, không phản hồi thì xử lý thu hồi theo quy
  trình của cơ sở;
- idempotent: hợp đồng đã khoá thì không làm lại, không gửi lại.

Trong lúc khoá, khách lấy mã mới (`POST /api/customer/contracts/{id}/access-code`) bị 409.

#### Khoá chặn của cơ sở (chìa cơ)

Chìa cơ (`PhysicalKey`) không thu hồi từ xa được như mã số, nên khoang có chìa cơ `Active` được gắn thêm một khoá chặn
của cơ sở (overlock) — khách giữ chìa nhưng không mở được. Bảng `unit_overlocks` (V38), `UnitOverlockService`; mỗi hợp
đồng tối đa một khoá chặn đang mở.

| Trạng thái | Khi nào | Ai |
|---|---|---|
| `Required` | `suspendAccess` thấy hợp đồng còn chìa cơ `Active`; FS được gán cơ sở + FM nhận `OVERLOCK_REQUIRED` | System |
| `Applied` | FS gắn tại khoang rồi xác nhận (`POST /api/staff/overlocks/{id}/apply`) | FS |
| `RemovalRequired` | truy cập được mở lại (một trong hai đường ở bảng dưới) khi khoá chặn đã gắn; FS + FM nhận `OVERLOCK_REMOVAL_REQUIRED` | System |
| `Removed` | FS gỡ tại khoang rồi xác nhận (`POST /api/staff/overlocks/{id}/remove`); khách nhận `OVERLOCK_REMOVED` | FS |
| `Canceled` | mở lại truy cập hoặc chốt trả kho khi yêu cầu còn `Required` (chưa gắn) — không cần gỡ | System / FS |

- FS xem việc ở trang **Khoá chặn** (`GET /api/staff/overlocks`): khoá chặn đang mở ở các cơ sở mình được gán, chờ gỡ
  trước, rồi chờ gắn, rồi đang gắn. Mọi thao tác cần `unit_access_key.issue` tại cơ sở của khoang (cùng quyền giao /
  thu hồi chìa); gắn khi không còn `Required` hoặc gỡ khi chưa `RemovalRequired` (hợp đồng còn khoá) → 409.
- **FM giao FS** (V39 — `assigned_staff_id`, `assigned_at`): trang Hợp đồng quá hạn của FM có mục "Khoá chặn của cơ
  sở" (`GET /api/fm/overlocks?facilityId=`, `rental_order.read`) liệt kê khoá chặn đang mở kèm FS phụ trách; FM chọn
  một FS đang hoạt động của cơ sở và giao gắn / gỡ (`POST /api/fm/overlocks/{id}/assign` `{staffId}`,
  `appointment.assign`). Chỉ giao khi `Required` hoặc `RemovalRequired` (đang `Applied` → 409), giao lại được; FS
  không thuộc cơ sở / không hoạt động → 400. FS được giao nhận `OVERLOCK_ASSIGNED`; audit `OVERLOCK_ASSIGNED`.
  - Chưa giao: mọi FS của cơ sở nhận thông báo và làm được. Đã giao: chỉ FS đó xác nhận gắn / gỡ (FS khác → 403), và
    thông báo `OVERLOCK_REMOVAL_REQUIRED` khi mở lại chỉ gửi FS đó (+ FM). FM giao lại người khác cho bước gỡ nếu cần.
- Mở lại bằng trả phí + gia hạn: thông báo `CONTRACT_ACCESS_RESTORED` của khách nói thêm khoá chặn sẽ được nhân viên
  gỡ; khách nhận `OVERLOCK_REMOVED` khi FS gỡ xong.
- Mở lại khi khách đến buổi trả kho: biên bản trả kho của FS hiện khoá chặn "chờ gỡ" kèm nút xác nhận đã gỡ; còn
  `Applied` / `RemovalRequired` thì **không chốt trả kho được** (409) — xem [checkout/return.md](../checkout/return.md).
- Hợp đồng chỉ dùng mã số thì không có khoá chặn — thu hồi mã là đủ.
- FM xem trạng thái tạm khoá và khoá chặn trong danh sách hợp đồng quá hạn (`accessSuspended`, `overlockStatus`).

**Mở lại** (`OverdueService.restoreIfCleared`). Hợp đồng đang khoá chỉ có hai đường lấy lại quyền vào khoang:

| Đường | Điều kiện | Ghi chú |
|---|---|---|
| Trả nợ + gia hạn | không còn quá hạn (`end_date` ≥ hôm nay — gia hạn đã phủ kỳ quá hạn) **và** không còn `INV-OVD` `Unpaid` | chỉ trả phí mà chưa gia hạn, hoặc gia hạn chưa phủ hết kỳ quá hạn → vẫn khoá |
| Trả kho | còn quá hạn, có yêu cầu trả kho mở, FS **đã xác nhận khách đến** buổi `RETURN` (`appointmentArrivedAt`) **và khách đã trả hết nợ** (không còn hoá đơn `Unpaid` nào của hợp đồng, không còn tiền thuê `Expired`) | còn nợ thì giữ khoá — tránh khách dọn hết đồ rồi bỏ đi không trả; tiền cọc **không** dùng để bù nợ ở bước này |

- **Nợ gộp trước khi dọn đồ** (`OverdueService.prepareMoveOut`): hợp đồng đang khoá, FS vừa xác nhận khách đến buổi
  trả kho → chốt phí quá hạn tới ngày buổi đó (`accrue`, ngày dừng = `appointmentStart`), rồi
  `ContractInvoiceService.foldDebtForMoveOut` huỷ mọi hoá đơn `Unpaid` của hợp đồng (phí `INV-OVD`, tiền thuê, dịch
  vụ…) và gộp tiền thuê `Expired` (nợ cũ, không trả online được) thành **một** hoá đơn `Penalty`
  `INV-DBT-{facility}-{YYMMDD}-{rand}` (hạn now + 7 ngày); khách nhận `MOVE_OUT_DEBT_DUE` (số tiền + mã hoá đơn). Khách
  trả hoá đơn này (thường ngay tại buổi hẹn) → `ContractInvoicePaid` → mở khoang để dọn đồ.
  - Chạy ngay sau khi khách đến, sau mỗi lần hoá đơn của hợp đồng được trả, và trong cron mỗi giờ; idempotent — chỉ còn
    đúng một `INV-DBT` chưa trả thì không gộp lại; khách đang có phiên thanh toán mở thì đợi phiên đó chốt.
  - Không còn nợ lúc khách đến (vd. đã trả phí trong ngày) → không lập hoá đơn, mở ngay.
  - `INV-DBT` **không tự hết hạn** (như `INV-OVD`) và trả được cả sau `due_date`; đã có `INV-DBT` chưa huỷ thì cron
    không phát hành lại `INV-OVD` (phí đã chốt trong hoá đơn đó).
  - Tiền cọc giữ nguyên: khi chốt trả kho chỉ trừ phí vệ sinh / hư hỏng / mất chìa rồi hoàn phần dư. Khách không trả
    mà FS vẫn chốt trả kho (khoang thực tế đã trống) thì `INV-DBT` còn `Unpaid` được gộp vào quyết toán như mọi hoá
    đơn chưa trả.
  - Chỉ áp dụng cho hợp đồng đang tạm khoá; hợp đồng quá hạn chưa bị khoá vẫn vào khoang bình thường, nợ gộp vào
    quyết toán, trừ cọc.
- Gửi yêu cầu trả kho hay được phân lịch **không** mở lại — khoang chỉ mở khi khách thật sự có mặt ở buổi trả kho.
  `escalate` bỏ qua hợp đồng có yêu cầu trả kho mở nên sau khi mở không bị khoá lại; khách huỷ yêu cầu / no-show đủ
  ngưỡng thì lượt cron sau khoá lại theo điều kiện thường.
- Đã chốt quyết toán trả kho (có hoá đơn `INV-CLN/DMG/KEY/PEN` khác `Canceled` — FS đã thu hồi chìa / mã lúc chốt) thì
  không mở lại.
- **Thời điểm xét:** ngay sau khi hoá đơn của hợp đồng được trả (`ContractInvoicePaid` — gia hạn đã cộng kỳ trong cùng
  transaction chốt thanh toán) và ngay sau khi FS xác nhận khách đến một buổi hẹn (`AppointmentArrived`), qua
  `OverdueAccessListener` (`AFTER_COMMIT`, mỗi hợp đồng một transaction `REQUIRES_NEW`, lỗi chỉ log); cron mỗi giờ quét
  lại hợp đồng `Active` đang khoá làm đường dự phòng.

Kết quả: xoá `access_suspended_at`, audit `CONTRACT_ACCESS_RESTORED`, khách nhận `CONTRACT_ACCESS_RESTORED` (nội dung
nêu rõ "không còn quá hạn" hay "mở lại để dọn đồ tại buổi trả kho — bạn đã thanh toán hết nợ"). Mã cũ đã thu hồi nên
khách tự lấy mã mới trong trang kho đang thuê.

Trang hợp đồng của khách (hợp đồng `Active` quá hạn) gom hai đường trên vào một khung: số ngày quá hạn, phí
`INV-OVD` hiện tại (nút thanh toán), hoá đơn khác chưa trả, tiền cọc đang giữ; "Cách 1 — trả phí và gia hạn" (form gia
hạn hiện khi `canExtend`) và "Cách 2 — trả kho" (form yêu cầu trả kho; hợp đồng đang khoá thì nêu số tiền cần trả
trước khi dọn đồ và nút thanh toán `INV-DBT` khi đã có; chưa khoá thì tạm tính cọc − nợ: được hoàn / phải trả thêm,
chưa gồm phí vệ sinh / hư hỏng / mất chìa). Nút lấy mã khoá số bị tắt khi `accessSuspended`. Trang biên bản trả kho
của FS báo khi hợp đồng đang khoá: trước khi khách đến — xác nhận đến thì hệ thống gộp nợ; sau đó — khách còn nợ bao
nhiêu, trả xong khoang tự mở.

FM xem danh sách hợp đồng quá hạn của cơ sở (`GET /api/fm/overdue-contracts?facilityId=`), gồm:
- số ngày quá hạn và số ngày tính phí;
- tổng phí đã cộng dồn và hoá đơn đang mở;
- hợp đồng đã có yêu cầu trả kho chưa;
- đang tạm khoá truy cập không (`accessSuspended`) và trạng thái khoá chặn đang mở (`overlockStatus`, `null` = không
  có).

### Thu hồi khoang, lưu giữ và thanh lý

Hợp đồng quá hạn kéo dài mà khách bỏ đi (`checkout/application/ReclaimService.java`, bảng `reclaimed_goods` — V40).
Đồ trong khoang **không** mặc nhiên thuộc về cơ sở: cơ sở thu hồi khoang, lưu giữ đồ, rồi mới thanh lý theo Điều 6 hợp
đồng mẫu; tiền thanh lý chỉ dùng trừ nợ, phần dư là của khách.

| Mốc (ngày quá hạn) | Việc |
|---|---|
| `abandon_days − abandon_notice_days` (38) | `OverdueService.remindReclaim` (cron `OverdueJob`): hợp đồng đang tạm khoá, khách chưa đến buổi trả kho → khách + FM nhận `RECLAIM_FINAL_NOTICE` (mốc thu hồi, thời hạn lưu giữ, phí) — một lần mỗi kỳ |
| `overdue.abandon_days` (45) | FM nhận `RECLAIM_DUE`; hợp đồng hiện "thu hồi được" ở trang **Thu hồi & lưu giữ** (`GET /api/fm/reclaims?facilityId=`) |

Trước khi FM thu hồi, khách vẫn tự xử lý được (trả phí + gia hạn, hoặc đến trả kho và trả hết nợ) — hợp đồng rời danh
sách. `RentalView.reclaimDate` (= `end_date + abandon_days` khi quá hạn) để trang hợp đồng của khách cảnh báo trước.

**Thu hồi** (`POST /api/fm/contracts/{contractId}/reclaim` `{witnessStaffId, inventoryNote, photos, storageLocation}`,
`facility.access`). Điều kiện: hợp đồng `Active`, quá hạn ≥ `abandon_days`, đang tạm khoá, không có yêu cầu trả kho mở
(có → 409, xử lý theo buổi trả kho), chưa thu hồi; FS chứng kiến đang hoạt động và thuộc cơ sở (400), biên bản kiểm
kê (≤ 4000 ký tự), 1–20 ảnh, vị trí lưu giữ. Một transaction, khoá đơn → hợp đồng:
- khoá chặn đang mở đóng lại (`Required → Canceled`, đã gắn → `Removed`) — khoá khoang được thay khi bảo trì;
- `revokeAll(returnedKeys = 0)`: chìa cơ → `Lost`, mã số → `Revoked`;
- quyết toán như trả kho: mọi hoá đơn `Unpaid` (phí quá hạn, `INV-DBT`…) + tiền thuê `Expired` gộp vào `INV-PEN`, cộng
  phí thu hồi `fee.reclaim` (`INV-RCL`) và phí mất chìa (`INV-KEY`) → trừ cọc theo thứ tự RCL → KEY → PEN. Thiếu → một
  hoá đơn `INV-DBT` (công nợ, không tự hết hạn); dư → hàng đợi hoàn cọc như trả kho;
- hợp đồng `Ended`; khoang `Maintenance` thủ công (không `maintenance_started_at` — FM thay khoá rồi mở lại tay);
- lô đồ `Holding`, `hold_until = hôm nay + storage.hold_days` (60); ảnh attach media; audit `CONTRACT_RECLAIMED`; khách
  nhận `GOODS_RECLAIMED` (hạn lưu giữ, công nợ, phí lưu giữ).

**Khách nhận lại đồ** — trang hợp đồng (đã kết thúc) hiện khung "Khoang đã bị thu hồi" (biên bản, ảnh, công nợ):
- `POST /api/customer/reclaimed-goods/{id}/return-request`: lập `INV-HLD` (`Service`) = số ngày đã giữ (tối thiểu 1) ×
  `fee.hold_per_day` − phí lưu giữ đã trả, nếu chưa có `INV-HLD` chưa trả; FM nhận `GOODS_RETURN_REQUESTED` (lần đầu);
  audit `GOODS_RETURN_REQUESTED`. `GET /api/customer/reclaimed-goods` trả đồ lưu giữ của khách.
- Khách trả `INV-DBT` + `INV-HLD` → FM `POST /api/fm/reclaimed-goods/{id}/return` (chưa yêu cầu / còn nợ → 409) →
  `Returned`, khách nhận `GOODS_RETURNED`.

**Thanh lý** (`POST /api/fm/reclaimed-goods/{id}/liquidate` `{proceeds, note, photos}` — chỉ sau `hold_until`, 409 trước):
- 7 ngày trước hạn (`storage.liquidation_notice_days`) cron `ReclaimJobs` (mỗi giờ) báo khách + FM
  `LIQUIDATION_NOTICE`, một lần mỗi lô;
- phí lưu giữ tới hôm nay (trừ phần đã trả) + `INV-DBT` / `INV-HLD` chưa trả (huỷ, gộp) = tổng nợ; tiền thanh lý (VND
  nguyên ≥ 0) trừ nợ → hoá đơn `INV-LIQ` `Paid`; thiếu → `INV-DBT` mới; dư → `surplus_amount` giữ hộ khách, FM nhận
  `GOODS_SURPLUS_REFUND_REQUIRED`, hoàn ngoài hệ thống rồi ghi nhận
  `POST /api/fm/reclaimed-goods/{id}/surplus-refunded`;
- lô `Liquidated`, audit `GOODS_LIQUIDATED`; khách nhận `GOODS_LIQUIDATED` (thu bao nhiêu, trừ bao nhiêu, dư / thiếu).
  Biên bản ghi rõ món bán / giấy tờ gửi trả hoặc bàn giao cơ quan chức năng / huỷ.

**Công nợ:** khách (theo email) còn `INV-DBT` chưa trả thì không gửi được yêu cầu thuê mới — 409 "Bạn còn công nợ của
hợp đồng trước…" (`RentalRequestService.create`).

## Policy

| Key | Mặc định | Migration |
|---|---:|---|
| `extension.max_months` | 12 | V25 |
| `extension.invoice_due_days` | 3 | V25 |
| `contract.expiry_reminder_days` | 7 | V25 |
| `overdue.grace_days` | 3 | V25 |
| `overdue.fee_per_day` | 20000 | V25 |
| `rent.invoice_lead_days` | 5 | V27 |
| `overdue.escalation_days` | 30 | V27 |
| `return.no_show_grace_minutes` | 120 | V28 |
| `return.max_no_shows` | 2 | V28 |
| `overdue.abandon_notice_days` | 7 | V40 |
| `overdue.abandon_days` | 45 | V40 |
| `storage.hold_days` | 60 | V40 |
| `storage.liquidation_notice_days` | 7 | V40 |
| `fee.reclaim` | 300000 | V40 |
| `fee.hold_per_day` | 10000 | V40 |

## Test

`tenancy/PostRentalIntegrationTest.java` (Postgres) và `tenancy/controller/PostRentalControllerTest.java` (HTTP).
