-- Tài khoản seed dev chuyển sang tên miền example.com (RFC 2606 — tên miền dành cho ví dụ, không nhận thư thật).
-- V2/V13 giữ nguyên để không đổi checksum của DB đã migrate; migration này đổi dữ liệu đã seed.
-- Chỉ đổi đúng 8 địa chỉ seed. Địa chỉ mới đã có account khác thì bỏ qua dòng đó (không vi phạm UNIQUE).

CREATE TEMPORARY TABLE seed_account_email (old_email VARCHAR(255) PRIMARY KEY, new_email VARCHAR(255) NOT NULL)
    ON COMMIT DROP;

INSERT INTO seed_account_email (old_email, new_email) VALUES
    ('admin@lemar.vn',      'admin@example.com'),
    ('bom1@lemar.vn',       'bom1@example.com'),
    ('fm1@lemar.vn',        'fm1@example.com'),
    ('fm2@lemar.vn',        'fm2@example.com'),
    ('fs1@lemar.vn',        'fs1@example.com'),
    ('customer1@lemar.vn',  'customer1@example.com'),
    ('unverified@lemar.vn', 'unverified@example.com'),
    ('banned@lemar.vn',     'banned@example.com');

UPDATE accounts a
SET email = s.new_email
FROM seed_account_email s
WHERE a.email = s.old_email
  AND NOT EXISTS (SELECT 1 FROM accounts b WHERE b.email = s.new_email);

-- Yêu cầu thuê khớp account theo email đã chuẩn hoá: đổi theo để claim/duyệt vẫn tìm đúng account seed.
UPDATE rental_requests r
SET normalized_customer_email = s.new_email
FROM seed_account_email s
WHERE r.normalized_customer_email = s.old_email
  AND EXISTS (SELECT 1 FROM accounts a WHERE a.email = s.new_email)
  AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.email = s.old_email);
