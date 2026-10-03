-- ============================================================================
-- V2 — Baseline seed (A2)
-- Mirror byte-identical tại backend/sql/seed.sql — CI check đồng bộ.
--
-- Mật khẩu plain-text của mọi account: "Test@1234" (BCrypt strength 10).
-- Đây là dữ liệu dev; A3 sẽ thay bằng luồng auth thật.
-- ============================================================================

-- Roles (5 role theo actors của spec)
INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('BOM'),
    ('FM'),
    ('FS'),
    ('CUSTOMER');

-- Accounts dev — đủ 5 role + 2 ca đặc biệt để test login/chặn login
INSERT INTO accounts (email, password_hash, email_verified_at, role_id, status) VALUES
    ('admin@lemar.vn',    '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'ADMIN'),    'ACTIVE'),
    ('bom1@lemar.vn',     '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'BOM'),      'ACTIVE'),
    ('fm1@lemar.vn',      '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'FM'),       'ACTIVE'),
    ('fm2@lemar.vn',      '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'FM'),       'ACTIVE'),
    ('fs1@lemar.vn',      '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'FS'),       'ACTIVE'),
    ('customer1@lemar.vn','$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'CUSTOMER'), 'ACTIVE'),
    ('unverified@lemar.vn','$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', NULL, (SELECT id FROM roles WHERE name = 'CUSTOMER'), 'UNVERIFIED'),
    ('locked@lemar.vn',   '$2a$10$RSb.3SX6jl/QwQ.O2Lbt1uXgErlw5Gy9o7XHMkxLTiUoDAyuYMpJW', now(), (SELECT id FROM roles WHERE name = 'CUSTOMER'), 'LOCKED');

-- Facility dev: Q7 (fm1) + TD (fm2) — đủ để test cross-facility 403 (A3b, #59)
INSERT INTO facilities (code, name, address, operating_hours, status, fm_account_id) VALUES
    ('Q7', 'Self Storage Quận 7', '123 Nguyễn Văn Linh, Quận 7, TP.HCM', '08:00–20:00 hằng ngày', 'Active',
     (SELECT id FROM accounts WHERE email = 'fm1@lemar.vn')),
    ('TD', 'Self Storage Thủ Đức', '456 Võ Văn Ngân, Thủ Đức, TP.HCM', '08:00–20:00 hằng ngày', 'Active',
     (SELECT id FROM accounts WHERE email = 'fm2@lemar.vn'));

-- Unit types — MVP: một mức giá áp dụng toàn hệ thống
INSERT INTO unit_types (name, width, depth, height, area, description, monthly_price, updated_by) VALUES
    ('Small',  1.00, 2.00, 2.20, 2.00, 'Kho nhỏ — cá nhân, thùng nhỏ',   500000,  (SELECT id FROM accounts WHERE email = 'bom1@lemar.vn')),
    ('Medium', 2.00, 2.50, 2.20, 5.00, 'Kho vừa — gia đình nhỏ',         900000,  (SELECT id FROM accounts WHERE email = 'bom1@lemar.vn')),
    ('Large',  2.50, 3.00, 2.50, 7.50, 'Kho lớn — văn phòng, nhiều đồ', 1400000, (SELECT id FROM accounts WHERE email = 'bom1@lemar.vn'));

-- Storage units tại Q7: 7 Available + 1 Maintenance (để test filter/trạng thái)
-- monthly_price để NULL theo MVP — giá hiệu lực lấy từ UnitType.
INSERT INTO storage_units (code, facility_id, unit_type_id, status) VALUES
    ('A-01', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Small'),  'Available'),
    ('A-02', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Small'),  'Available'),
    ('A-03', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Small'),  'Available'),
    ('A-04', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Small'),  'Available'),
    ('B-01', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Medium'), 'Available'),
    ('B-02', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Medium'), 'Available'),
    ('B-03', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Medium'), 'Available'),
    ('C-01', (SELECT id FROM facilities WHERE code = 'Q7'), (SELECT id FROM unit_types WHERE name = 'Large'),  'Maintenance');

-- FS được gán về Q7 (facility scope của A3)
INSERT INTO account_facility_assignments (account_id, facility_id) VALUES
    ((SELECT id FROM accounts WHERE email = 'fs1@lemar.vn'), (SELECT id FROM facilities WHERE code = 'Q7'));

-- Policies — chỉ seed key CÓ giá trị chốt trong spec (flows 1–2 dùng thật)
INSERT INTO policies (key, value, description) VALUES
    ('request.pending_expiry_days',  '7',  'Request Pending quá số ngày này → Expired (Flow 1.1)'),
    ('account.claim_ttl_days',       '7',  'Hạn khách claim request Approved sau khi duyệt (Flow 1.2)'),
    ('proposal.max_rejection_count', '3',  'Số lần khách từ chối proposal tối đa trước khi hủy đơn (Flow 1.3)'),
    ('invoice.deposit_due_days',     '3',  'Hạn thanh toán hóa đơn cọc (Flow 1.3)'),
    ('order.deposit_expiry_days',    '30', 'Hạn giữ kho sau khi đặt cọc (Flow 1.4)'),
    ('handover.max_rejection_count', '2',  'Số lần khách từ chối khoang tại chỗ tối đa (Flow 2)');

-- KHÔNG seed (spec có nhắc nhưng chưa có giá trị chốt / chưa dùng):
--   handover.payment_grace_hours — Flow 2 dùng, giá trị "chờ BOM" → seed khi Flow 4 chốt
--   handover.due_days            — Flow 2 dùng, "chờ BOM (gợi ý 1–2 ngày)"
--   unit.maintenance_days        — thuộc Flow 2.5/2.a (chưa triển khai)
--   fee.unit_change              — thuộc Flow 4 (chưa triển khai)
