---
purpose: Việc còn lại đã biết nhưng chưa có issue riêng — ghi để không quên.
---
# Follow-up

Việc còn lại đã biết, chưa có issue riêng — ghi để không quên. Việc nào thành task chính thức
thì mở issue theo workflow; file này không thay thế issue.

## Notification

- Chưa có caller nghiệp vụ gọi `NotificationService.notify()` (booking/handover/payment nối sau);
  hiện chỉ có API tạo `OTHER` thủ công. Khi nối flow, truyền `orderId` để FE deep-link mở đơn.
- `RENTAL_ORDER_CANCELED`: template dùng `{{outcome}}` — caller của Cancellation Cascade phải map
  `cancel_reason` → `"đã bị hủy"` / `"đã hết hạn"`; nên có một helper dùng chung khi làm cascade.
- Nhắc `RENTAL_ORDER_EXPIRING_SOON` mỗi đơn một lần: cron tương lai phải tự dedup qua liên kết
  `OrderNotification` (repository hiện chỉ có `findByOrderId`, chưa có query theo order + type).
- Phân trang list trả mảng không kèm `total` — FE tải thêm tới khi trang rỗng; nếu cần
  `total`/`hasMore` phải mở issue đổi contract.
