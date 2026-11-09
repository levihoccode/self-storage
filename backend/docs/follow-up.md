---
purpose: Việc còn lại đã biết nhưng chưa có issue riêng — ghi để không quên.
---
# Follow-up

Việc còn lại đã biết, chưa có issue riêng — ghi để không quên. Việc nào thành task chính thức
thì mở issue theo workflow; file này không thay thế issue.

## RBAC

- Guard hiện gọi trực tiếp `Access.can(...)` ở controller. Cân nhắc chuyển sang annotation
  (`@PreAuthorize` gọi bean, hoặc annotation riêng + interceptor) khi số endpoint tăng — lý do chưa
  làm ghi ở `TODO(flow-5)` trong `identity/application/Access.java`.
- Quản trị hệ thống (`/api/admin/**`) chưa có endpoint nào; permission catalog mở rộng dần theo
  Flow 3–7 — xem [permission-catalog.md](permission-catalog.md).

## Booking

- No-show `RentalOrder`/`HandoverRecord` chưa xử lý — issue #222 (ngoài scope PR cron `Appointment`).

## Notification

- Caller nghiệp vụ đầu tiên: luồng convert gửi `RENTAL_REQUEST_APPROVED` (có `orderId`) —
  [booking/conversion.md](booking/conversion.md). Handover/payment nối sau; khi nối flow, truyền
  `orderId` để FE deep-link mở đơn.
- `RENTAL_ORDER_CANCELED`: template dùng `{{outcome}}` — caller của Cancellation Cascade phải map
  `cancel_reason` → `"đã bị hủy"` / `"đã hết hạn"`; nên có một helper dùng chung khi làm cascade.
- Nhắc `RENTAL_ORDER_EXPIRING_SOON` mỗi đơn một lần: cron tương lai phải tự dedup qua liên kết
  `OrderNotification` (repository hiện chỉ có `findByOrderId`, chưa có query theo order + type).
- Phân trang list trả mảng không kèm `total` — FE tải thêm tới khi trang rỗng; nếu cần
  `total`/`hasMore` phải mở issue đổi contract.

## Refund

- **Queue vận hành:** `GET /api/refunds/queues/invoices` — ADMIN cross-facility; FM theo
  `facilityId`, chỉ `view=active`. Admin chỉ nên giữ cross-facility oversight.
- **Refund obligation:** `POST /api/refunds/obligations` đang gate sau profile `refund-obligation`
  (mặc định tắt) và chỉ tạo obligation/lines/formula snapshot. Cần nối obligation vào disbursement
  (`refund_obligation_id`), thêm route transaction/confirm/queue, chuyển `Open → Disbursed`; order-only
  chưa có đường chi tiền.
- **Hoàn cọc khi kết thúc thuê:** flow quyết toán tạo obligation với lines cọc/khấu trừ; sau đó dùng
  handover theo obligation. Nếu cần duyệt, thêm `approve` trước handover, không thêm endpoint handover
  trùng.
- **Partial refund:** hiện một `Success` cho mỗi `source_transaction_id`; hoàn cọc một phần từ cùng
  giao dịch cọc cần thiết kế lại unique index và contract amount trước khi implement.
- **Per-reason policy:** `payment_transaction.refund`/`refund_transaction.handover` hiện cho phép
  mọi `reason_code` trong facility. Nếu FS/FM chỉ được xử lý một số lý do, thêm policy theo
  role/task/reason ở service; không tạo permission cho từng `reason_code`.
- **VNPay auto refund:** `vnp_Command=refund` vẫn tách riêng; không dùng permission thủ công.

## FS tasks

- Chưa có bảng task/assignment cho FS. Refund queue FM hiện read-only; chưa biết FS nào xử lý
  invoice nào. Cần bảng `fs_tasks` dùng chung cho refund, handover, return, support…; tách issue riêng.
- FS **không đọc** `GET /api/refunds/queues/invoices` (chỉ ADMIN/FM). Refund task mang sẵn
  `invoiceId`, amount, reason, requestedAt; bấm task mở thẳng form handover, không cần màn danh sách
  refund riêng. Task phải được tạo khi invoice vào `Required`/`Processing` — nếu chỉ dựa vào
  assignment thủ công, khoản hoàn có thể mồ côi.
- Khi có assignment, handover refund phải kiểm `assigned_fs_id`, không chỉ facility permission.

## Audit

- AuditLog đi theo hướng service ghi audit gọi tường minh từ call site. Hướng AOP interceptor
  tạm gác lại: interceptor không tự biết `entity_id` / `old_value` / `new_value` khi
  một thao tác đổi nhiều entity — caller vẫn phải truyền đủ dữ liệu, thêm annotation lúc này chỉ
  che luồng chạy thật. Xem lại khi số call site đủ nhiều và pattern đã ổn định.

## Media

- **File mồ côi**: batch delete/attach đã có (`media_uploads`); FE chỉ xoá khi người dùng bấm X
  trên file trong phase hiện tại. Batch Pending bị bỏ ngang (rời trang/Hủy) vẫn mồ côi — chưa có
  job sweep `Uploading`/`Pending` quá hạn; cân nhắc khi hạ tầng scheduler xong.
- Không rate limit upload; trùng file tạo nhiều asset (không dedup) — chấp nhận ở MVP.
- Batch delete không xoá asset `Attached`; nếu phase đã submit rồi mới phát hiện sai thì cần flow xử
  lý riêng (hiện chưa có route delete theo `publicId`).
- Xoá media khi order bị **hard-delete** hoặc theo retention policy chưa có; chưa nên tự xoá khi
  đơn chỉ bị Canceled vì media là bằng chứng/audit.
