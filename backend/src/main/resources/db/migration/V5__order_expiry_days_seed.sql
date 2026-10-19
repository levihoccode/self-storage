-- Bug #130: V2 seed thiếu policy key `order.expiry_days` mà Flow 1 (B2) đọc khi tạo RentalOrder.
-- Giá trị chốt: `specs/draft.md` › Các tham số sử dụng trong Flow 1 — 30 ngày.
-- Dùng migration mới thay vì sửa V2 (checksum): DB dev đang chạy cũng được vá, không phải reset.
-- `sql/seed.sql` giữ mirror theo V2 (CI diff V2 ↔ seed.sql); key này chỉ có qua Flyway.
INSERT INTO policies (key, value, description) VALUES
    ('order.expiry_days', '30', 'Hạn hoàn thành của đơn, set một lần khi tạo (Flow 1)')
ON CONFLICT (key) DO NOTHING;
