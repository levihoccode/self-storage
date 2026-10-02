-- ============================================================================
-- V4 — Fix mock password hash sai trong V2 (A3)
-- Migration độc lập: KHÔNG mirror vào backend/sql/seed.sql (seed.sql chỉ
-- mirror V2).
--
-- Lý do: V2 đã được apply với 1 hash BCrypt SAI (không khớp "Test@1234").
-- Không sửa trực tiếp V2 vì sẽ gây Flyway checksum mismatch cho bất kỳ ai đã
-- chạy V2 cũ. Fix bằng UPDATE trong migration mới.
-- ============================================================================

-- Hash đúng cho "Test@1234" (BCrypt strength 10), đã verify khớp.
UPDATE accounts
SET password_hash = '$2b$10$P/R7KFCo7.aafYaJlDll5u2UIq19QeITMwAM1dQbvQW5cDfJMjdRS'
WHERE password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i';

-- Đảm bảo có account fm2 (2 FM để test facility-scope A3b). Với V2 hiện tại
-- fm2 đã tồn tại nên câu này không làm gì; giữ lại cho DB cũ chưa có fm2.
INSERT INTO accounts (email, password_hash, email_verified_at, role_id, status)
VALUES
    ('fm2@lemar.vn',
     '$2b$10$P/R7KFCo7.aafYaJlDll5u2UIq19QeITMwAM1dQbvQW5cDfJMjdRS',
     now(),
     (SELECT id FROM roles WHERE name = 'FM'),
     'ACTIVE')
    ON CONFLICT (email) DO NOTHING;