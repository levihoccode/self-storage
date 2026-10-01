-- ============================================================================
-- V4 — Fix mock password hash sai trong V2 + bo sung account fm2 (A3)
-- Mirror: append noi dung nay vao cuoi backend/sql/seed.sql.
--
-- Ly do: V2 da duoc apply voi 1 hash BCrypt SAI (khong khop "Test@1234"),
-- khong duoc sua truc tiep V2 nua vi se gay Flyway checksum mismatch cho
-- bat ky ai da tung chay V2 cu. Fix bang UPDATE trong migration moi thay vi.
-- ============================================================================

-- Hash dung cho "Test@1234" (BCrypt strength 10), da verify khop.
UPDATE accounts
SET password_hash = '$2b$10$P/R7KFCo7.aafYaJlDll5u2UIq19QeITMwAM1dQbvQW5cDfJMjdRS'
WHERE password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i';

-- Them account fm2 de co 2 FM test facility-scope (A3b)
INSERT INTO accounts (email, password_hash, email_verified_at, role_id, status)
VALUES
    ('fm2@lemar.vn',
     '$2b$10$P/R7KFCo7.aafYaJlDll5u2UIq19QeITMwAM1dQbvQW5cDfJMjdRS',
     now(),
     (SELECT id FROM roles WHERE name = 'FM'),
     'ACTIVE')
    ON CONFLICT (email) DO NOTHING;