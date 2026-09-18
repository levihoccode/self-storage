# Checklist trả kho on-site (kiểm tra → xử lý phí → hoàn cọc)

- **Route:** `/staff/appointments/{id}/return`
- **Actor:** Facility Staff
- **Flow tham chiếu:** 2.5.2 – 2.5.3 (branch `specs/flow-2-2.5`)

## Mục đích
Một trang duy nhất dẫn dắt FS qua tiến trình kiểm tra và bàn giao lại khoang, ghi nhận trên `CheckoutRecord`, tính phí phát sinh và đối trừ cọc.

## Dữ liệu hiển thị
- Hiện trạng khoang **lúc bàn giao** (đọc từ `HandoverRecord` của Flow 2 — `inspection_notes`, `inspection_photos`) để đối chiếu, hiển thị song song với form nhập hiện trạng lúc trả
- Checklist: đã dọn trống (`is_empty`), tình trạng vệ sinh (`cleanliness`), hư hỏng (`damages[]`, `photos[]`), số chìa khóa trả lại (`returned_key_quantity`)
- Danh sách phí dự kiến tính theo cấu hình Flow 4 (vệ sinh/hư hỏng/mất chìa/trả trễ) trước khi chốt biên bản
- Tiền cọc đã thu (`Invoice type=Deposit, status=Paid`) để đối trừ

## Actions
- [Ghi nhận khách đến] (nếu chưa `arrive`)
- [Nhập kiểm tra] → hệ thống tính danh sách phí dự kiến, hiển thị cho FS xem trước (chưa tạo `Invoice`)
- [Chốt biên bản trả kho] → tạo các `Invoice(type=Penalty)` chính thức, thu hồi `UnitAccessKey`
- Trường hợp còn tài sản trong khoang: [Đánh dấu còn đồ] (`result = PENDING_ITEMS`) — tạo `Appointment(RETURN)` mới cho lần hẹn quay lại, **không** thu hồi khóa

## States / UI trạng thái
- 3 nhánh kết quả rõ ràng trên UI: **Đạt yêu cầu** (không phí) / **Còn tài sản** (`PENDING_ITEMS`, chưa hoàn tất) / **Bẩn/hư hỏng** (phát sinh phí)
- Bảng đối trừ cọc: tổng phí phát sinh + hóa đơn tồn đọng so với tiền cọc — hiển thị rõ "Dư (cần hoàn X)" hoặc "Thiếu (khách cần đóng thêm X)"
- Trạng thái chặn: nếu thiếu tiền, [Chuyển Maintenance] bị khóa cho tới khi hóa đơn bổ sung được thanh toán

## API liên quan
- `GET /api/staff/appointments/{id}/handover-record` (lấy hiện trạng lúc nhận)
- `POST /api/staff/appointments/{id}/inspection` (body: `is_empty`, `cleanliness`, `damages[]`, `photos[]`, `returned_key_quantity`, `note?`)
- `POST /api/staff/appointments/{id}/finalize-return` (chốt biên bản, tạo Invoice, thu hồi key)

## Edge case / Lưu ý UX
- **Còn tài sản trong khoang:** không thu hồi `UnitAccessKey`, khoang giữ `Rented`, hợp đồng giữ `Active` — nếu vượt hạn hợp đồng phát sinh phí trả trễ; hệ thống **không tự động tính phí lưu giữ** (ngoài MVP), FS/FM gửi hóa đơn thủ công riêng — cần 1 nút [Tạo hóa đơn thủ công] link sang trang phù hợp
- Đối trừ cọc dư → MVP chỉ **ghi nhận số tiền cần hoàn**, FM xử lý hoàn tiền thủ công ngoài hệ thống (không có luồng refund tự động qua cổng thanh toán)
- Cơ chế khách phản đối đánh giá hư hỏng của FS **không thuộc MVP** — đánh giá của FS là kết quả cuối cùng, không thiết kế nút "khách phản hồi" ở bước này
- Sau khi chốt xong và không còn khoản phải thu: `StorageUnit → Maintenance`, `RentalContract → Ended` — thời gian bảo trì và việc tự mở lại `Available` là cron của Flow 2.5, FS không thao tác gì thêm
