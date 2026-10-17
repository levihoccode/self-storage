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

## Notification

- Caller nghiệp vụ đầu tiên: booking B2 gửi `RENTAL_REQUEST_APPROVED` (có `orderId`) —
  [booking-conversion.md](booking-conversion.md). Handover/payment nối sau; khi nối flow, truyền
  `orderId` để FE deep-link mở đơn.
- `RENTAL_ORDER_CANCELED`: template dùng `{{outcome}}` — caller của Cancellation Cascade phải map
  `cancel_reason` → `"đã bị hủy"` / `"đã hết hạn"`; nên có một helper dùng chung khi làm cascade.
- Nhắc `RENTAL_ORDER_EXPIRING_SOON` mỗi đơn một lần: cron tương lai phải tự dedup qua liên kết
  `OrderNotification` (repository hiện chỉ có `findByOrderId`, chưa có query theo order + type).
- Phân trang list trả mảng không kèm `total` — FE tải thêm tới khi trang rỗng; nếu cần
  `total`/`hasMore` phải mở issue đổi contract.

## Audit

- AuditLog (A8) đi theo hướng service ghi audit gọi tường minh từ call site. Hướng AOP interceptor
  nêu ở #15 §7 tạm để sau: interceptor không tự biết `entity_id` / `old_value` / `new_value` khi
  một thao tác đổi nhiều entity — caller vẫn phải truyền đủ dữ liệu, thêm annotation lúc này chỉ
  che luồng chạy thật. Xem lại khi số call site đủ nhiều và pattern đã ổn định.

## Media

- **File mồ côi**: FE upload xong nhưng flow không submit (bỏ ngang) → asset không được entity nào
  trỏ tới. Đã truy vết được theo folder `uploads/acc-{id}`; chưa có job dọn — cân nhắc sweep định kỳ
  khi A7 (scheduler) xong, hoặc bảng `media_uploads` (pending/attached) nếu cần relink tự động.
- Không rate limit upload; trùng file tạo nhiều asset (không dedup) — chấp nhận ở MVP.
