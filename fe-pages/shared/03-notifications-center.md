# Trung tâm thông báo

- **Route:** `/notifications` (dropdown chuông + trang danh sách đầy đủ), dùng chung mọi role
- **Actor:** Mọi role
- **Flow tham chiếu:** `Notification` entity (db-table-draft.md), tổng hợp từ các event nêu trong "Events phát ra từ Flow X" của Flow 1/2/2.5/3

## Mục đích
Hiển thị thông báo web (channel = Web) cho user — duyệt/từ chối yêu cầu, hóa đơn cần thanh toán, hợp đồng sắp hết hạn, phân công công việc (FM/FS), v.v.

## Dữ liệu hiển thị
- Danh sách `Notification WHERE account_id = current_user AND channel = Web`, sort `sent_at desc`
- Mỗi item: `type`, `title`, `content`, `is_read`, thời gian
- Badge số lượng chưa đọc trên icon chuông

## Actions
- Bấm vào 1 thông báo → set `is_read = true`, điều hướng theo `entity_type`/`entity_id` (vd `type=RentalRequest.Approved` → mở trang chi tiết request tương ứng theo role)
- [Đánh dấu đã đọc tất cả]

## States / UI trạng thái
- Dropdown rút gọn (5-10 item gần nhất) trên header + trang đầy đủ có phân trang
- Empty state: chưa có thông báo nào
- Realtime/polling: MVP có thể polling định kỳ thay vì WebSocket (không thấy chốt trong tài liệu — cần xác nhận với BE)

## API liên quan
- `GET /api/notifications?is_read=`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read-all`

## Edge case / Lưu ý UX
- Bảng `Notification.account_id` nullable — dùng khi gửi email cho người **chưa có tài khoản** (Flow 1.1, request được duyệt nhưng chưa đăng ký); trường hợp này không xuất hiện trong trung tâm thông báo web (vì chưa có account để hiển thị), chỉ tồn tại dưới dạng email
- Danh sách `type` thông báo rất đa dạng theo từng role (FM nhận `RentalRequest.Created`, Customer nhận `ExtendRequest.Approved`...) — nên thiết kế icon/màu theo nhóm `type` (Duyệt/Từ chối, Hóa đơn, Phân công, Cảnh báo hết hạn) thay vì icon riêng cho từng `type` cụ thể
