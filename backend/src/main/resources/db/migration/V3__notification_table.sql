-- ============================================================================
-- V3 — Notification (A5, spec-gap #55)
-- ============================================================================

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    type       VARCHAR(60) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_account_read ON notifications (account_id, read_at);