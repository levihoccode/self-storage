-- ============================================================
-- Mock DB cho self-storage: bảng roles + accounts (test login)
-- Chạy: docker exec -i self-storage-postgres-1 psql -U self_storage -d self_storage < mock-data.sql
-- ============================================================

-- 1. Bảng roles
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Bảng accounts
CREATE TABLE IF NOT EXISTS accounts (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,          -- normalized (lowercase, trim)
    password_hash       VARCHAR(255) NOT NULL,                 -- BCrypt hash
    email_verified_at   TIMESTAMPTZ NULL,
    role_id             BIGINT NOT NULL REFERENCES roles(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | UNVERIFIED | LOCKED | BANNED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);
CREATE INDEX IF NOT EXISTS idx_accounts_role_id ON accounts(role_id);

-- 3. Seed roles
INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('OWNER'),        -- chủ kho/self-storage
    ('CUSTOMER')
ON CONFLICT (name) DO NOTHING;

-- 4. Seed accounts (mock)
-- Mật khẩu plain-text cho tất cả account bên dưới: "Test@1234"
-- Hash BCrypt tương ứng (strength 10): $2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i
-- (bạn có thể generate hash khác bằng BCryptPasswordEncoder trong code nếu cần)

INSERT INTO accounts (email, password_hash, email_verified_at, role_id, status)
VALUES
    ('admin@lemar.vn',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i',
     now(),
     (SELECT id FROM roles WHERE name = 'ADMIN'),
     'ACTIVE'),

    ('owner1@lemar.vn',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i',
     now(),
     (SELECT id FROM roles WHERE name = 'OWNER'),
     'ACTIVE'),

    ('customer1@lemar.vn',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i',
     now(),
     (SELECT id FROM roles WHERE name = 'CUSTOMER'),
     'ACTIVE'),

    -- Account chưa verify email -> dùng để test luồng chặn login khi chưa xác thực
    ('unverified@lemar.vn',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i',
     NULL,
     (SELECT id FROM roles WHERE name = 'CUSTOMER'),
     'UNVERIFIED'),

    -- Account bị khóa -> test luồng chặn login do status
    ('locked@lemar.vn',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Cvzoy9zAxUpN1L8O0.z7VZgLdz9i',
     now(),
     (SELECT id FROM roles WHERE name = 'CUSTOMER'),
     'LOCKED')
ON CONFLICT (email) DO NOTHING;
