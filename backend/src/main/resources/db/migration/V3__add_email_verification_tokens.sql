-- ============================================================================
-- V3 — Email verification tokens (A3a-BE)
-- Mirror byte-identical tại backend/sql/schema.sql (append cuối file) — nhớ
-- cập nhật cả 2 nơi + specs/db-table-draft.md.
-- ============================================================================

CREATE TABLE email_verification_tokens (
                                           id          BIGSERIAL PRIMARY KEY,
                                           account_id  BIGINT NOT NULL REFERENCES accounts (id),
                                           token_hash  VARCHAR(255) NOT NULL,          -- SHA-256 hex của token gửi qua email (không lưu plain)
                                           expires_at  TIMESTAMPTZ NOT NULL,
                                           consumed_at TIMESTAMPTZ,
                                           created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_tokens_account ON email_verification_tokens (account_id);

-- Mỗi account chỉ có tối đa 1 token đang hiệu lực (chưa dùng) tại một thời điểm.
CREATE UNIQUE INDEX uq_email_verification_tokens_active
    ON email_verification_tokens (account_id)
    WHERE consumed_at IS NULL;