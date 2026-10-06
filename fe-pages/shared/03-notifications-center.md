# Trung tâm thông báo

- **Route:** `/notifications` (dropdown chuông + trang danh sách đầy đủ), dùng chung mọi role
- **Actor:** Mọi role
- **Flow tham chiếu:** `Notification` entity (db-table-draft.md), tổng hợp từ các event nêu trong "Events phát ra từ Flow X" của Flow 1/2/2.5/3

## Mục đích
Hiển thị thông báo web cho user — duyệt/từ chối yêu cầu, hóa đơn cần thanh toán, hợp đồng sắp hết hạn, phân công công việc (FM/FS), v.v.

## Navigation

- **Vào từ:** notification bell trong shell hoặc deep link `/notifications`.
- **Đi tới:** theo `orderId` khi có (mở trang đơn tương ứng theo role), còn lại theo `type`; giữ filter khi mark read nếu có thể.

## Dữ liệu hiển thị
- Danh sách `Notification WHERE account_id = current_user`, sort `created_at desc` — mọi bản ghi đều là thông báo web (email chỉ gửi đi, không lưu)
- Mỗi item: `type`, `title`, `readAt`, `createdAt`, `orderId` (nullable — có khi thông báo gắn đơn hàng); `body` chỉ có ở API chi tiết
- Badge số lượng chưa đọc trên icon chuông (`GET /api/notifications/unread-count`)

## Actions
- Bấm vào 1 thông báo → `PATCH /api/notifications/{id}/read`, điều hướng theo `orderId` khi có (mở trang đơn), còn lại dựa vào `type`/role
- [Đánh dấu đã đọc tất cả]

## States / UI trạng thái
- Dropdown rút gọn (5-10 item gần nhất) trên header + trang đầy đủ có phân trang
- Phân trang bằng `page`/`size` (mặc định 20, tối đa 100); API trả mảng không kèm `total` — FE tải thêm tới khi trang rỗng
- Empty state: chưa có thông báo nào
- Realtime: MVP dùng polling `GET /api/notifications/unread-count` cho badge (chưa có WebSocket)

## API liên quan
- `GET /api/notifications?is_read=&page=&size=`
- `GET /api/notifications/{id}`
- `GET /api/notifications/unread-count`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read-all`

## Edge case / Lưu ý UX
- `Notification.account_id` NOT NULL — khách chưa có tài khoản (Flow 1.1) chỉ nhận **email**, không có bản ghi Notification để hiển thị (xem `db-table-draft.md › Notification` Notes)
- Danh sách `type` thông báo rất đa dạng theo từng role (FM nhận `PROPOSAL_REJECTED`, Customer nhận `RENTAL_REQUEST_APPROVED`...) — nên thiết kế icon/màu theo nhóm `type` (Duyệt/Từ chối, Hóa đơn, Phân công, Cảnh báo hết hạn) thay vì icon riêng cho từng `type` cụ thể
