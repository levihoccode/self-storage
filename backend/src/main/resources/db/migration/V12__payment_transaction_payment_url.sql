-- URL thanh toán của phiên đang mở, để trả lại cho khách khi cache Redis bị mất (bấm back, Redis
-- khởi động lại). Không dựng lại URL vì có cổng (ZaloPay) không cho tạo lại đơn với cùng mã.
-- Xoá về NULL khi giao dịch chốt Success/Failed hoặc phiên quá 15 phút; row và audit giữ nguyên.
ALTER TABLE payment_transactions ADD COLUMN payment_url TEXT;
