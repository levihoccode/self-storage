-- ============================================================================
-- V1 — Baseline schema (A2)
-- Nguồn sự thật: specs/db-table-draft.md. File này được mirror (byte-identical)
-- tại backend/sql/schema.sql — CI check đồng bộ; sửa file nào thì sửa cả hai.
--
-- Quy ước:
--   * Tên bảng snake_case số nhiều (accounts, storage_units…) khớp @Table entity.
--   * Tiền: NUMERIC(12,2). Thời gian: TIMESTAMPTZ. Enum: VARCHAR + CHECK.
--   * Không gồm CheckoutRecord (Flow 2.a chưa review — #30) và FacilityTask (TODO).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,   -- normalized: trim + lowercase
    password_hash     VARCHAR(255) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    role_id           BIGINT NOT NULL REFERENCES roles (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'BANNED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE facilities (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    address         TEXT,
    operating_hours TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'Active'
                    CHECK (status IN ('Active', 'Inactive')),
    fm_account_id   BIGINT UNIQUE REFERENCES accounts (id)   -- FM phụ trách (1–1); NULL = chưa gán
);

-- Chỉ dùng cho FS (Flow 5) — data-scope khi một cơ sở có nhiều FS; không dùng cho FM.
CREATE TABLE account_facility_assignments (
    account_id  BIGINT NOT NULL REFERENCES accounts (id),
    facility_id BIGINT NOT NULL REFERENCES facilities (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, facility_id)
);

-- RBAC data-driven (Flow 5): role — permission.
CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,   -- <resource>.<action>, ví dụ rental_request.approve
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------

CREATE TABLE unit_types (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,       -- Small / Medium / Large
    width         NUMERIC(6,2) NOT NULL,              -- mét
    depth         NUMERIC(6,2) NOT NULL,
    height        NUMERIC(6,2) NOT NULL,
    area          NUMERIC(6,2) NOT NULL,              -- m2
    description   TEXT,
    monthly_price NUMERIC(12,2) NOT NULL,             -- MVP: 1 giá toàn hệ thống
    updated_by    BIGINT REFERENCES accounts (id),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Policy dạng key-value (Flow 4 chuẩn hoá sau). Key dùng trong spec, ví dụ
-- request.pending_expiry_days; seed baseline nằm ở V2.
CREATE TABLE policies (
    id          BIGSERIAL PRIMARY KEY,
    key         VARCHAR(100) NOT NULL UNIQUE,
    value       VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Facility — khoang chứa
-- ---------------------------------------------------------------------------

CREATE TABLE storage_units (
    id                     BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(50) NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES facilities (id),
    unit_type_id           BIGINT NOT NULL REFERENCES unit_types (id),
    size                   VARCHAR(50),
    monthly_price          NUMERIC(12,2),             -- null = dùng giá của UnitType
    maintenance_started_at TIMESTAMPTZ,               -- chỉ Flow 2.a set khi bảo trì do trả kho
    status                 VARCHAR(20) NOT NULL DEFAULT 'Available'
                           CHECK (status IN ('Available', 'Reserved', 'Rented', 'Maintenance')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (facility_id, code)
);

CREATE INDEX idx_storage_units_facility_status ON storage_units (facility_id, status);

-- ---------------------------------------------------------------------------
-- Flow 1 — request → order → contract
-- ---------------------------------------------------------------------------

CREATE TABLE rental_requests (
    id                        BIGSERIAL PRIMARY KEY,
    facility_id               BIGINT NOT NULL REFERENCES facilities (id),
    customer_name             VARCHAR(255) NOT NULL,
    normalized_customer_email VARCHAR(255) NOT NULL,
    customer_phone            VARCHAR(20) NOT NULL,
    unit_type_id              BIGINT NOT NULL REFERENCES unit_types (id),
    start_date                DATE NOT NULL,
    period                    INTEGER NOT NULL CHECK (period > 0),
    unit_id                   BIGINT REFERENCES storage_units (id),   -- FM chỉ định lúc duyệt
    status                    VARCHAR(20) NOT NULL DEFAULT 'Pending'
                              CHECK (status IN ('Pending', 'Rejected', 'Approved', 'Converted', 'Expired')),
    reject_reason             VARCHAR(50)
                              CHECK (reject_reason IS NULL
                                     OR reject_reason IN ('UNIT_UNAVAILABLE', 'UNIT_MISMATCH', 'CUSTOMER_REQUEST', 'OTHER')),
    reject_note               TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at              TIMESTAMPTZ,
    expires_at                TIMESTAMPTZ,
    CHECK (status <> 'Rejected' OR reject_reason IS NOT NULL),
    CHECK (reject_reason IS DISTINCT FROM 'OTHER' OR reject_note IS NOT NULL)
);

CREATE INDEX idx_rental_requests_facility_status ON rental_requests (facility_id, status);
CREATE INDEX idx_rental_requests_email ON rental_requests (normalized_customer_email);

CREATE TABLE rental_orders (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL UNIQUE REFERENCES rental_requests (id),
    customer_id   BIGINT NOT NULL REFERENCES accounts (id),
    unit_id       BIGINT REFERENCES storage_units (id),
    cancel_reason TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'Pending'
                  CHECK (status IN ('Pending', 'Deposited', 'Scheduled', 'InProgress', 'Canceled', 'Done', 'Expired')),
    expires_at    TIMESTAMPTZ                          -- hạn giữ kho sau khi đặt cọc
);

CREATE TABLE rental_contracts (
    id                            BIGSERIAL PRIMARY KEY,
    order_id                      BIGINT NOT NULL REFERENCES rental_orders (id),
    customer_id                   BIGINT NOT NULL REFERENCES accounts (id),
    unit_id                       BIGINT NOT NULL REFERENCES storage_units (id),
    code                          VARCHAR(50) NOT NULL UNIQUE,
    terms_version                 VARCHAR(50) NOT NULL,   -- version điều khoản khách đã đồng ý (Flow 4)
    monthly_price                 NUMERIC(12,2) NOT NULL, -- giá chốt lúc ký
    deposit_amount                NUMERIC(12,2) NOT NULL, -- cọc đã thu ở Flow 1.3
    period                        INTEGER NOT NULL CHECK (period > 0),
    start_date                    DATE NOT NULL,
    start_date_override_requested DATE,
    start_date_override_reason    TEXT,
    start_date_override_status    VARCHAR(20)
                                  CHECK (start_date_override_status IS NULL
                                         OR start_date_override_status IN ('Pending', 'Approved', 'Rejected')),
    end_date                      DATE,
    signed_at                     TIMESTAMPTZ,
    signature                     TEXT,                   -- MVP ký giấy → để trống
    document_url                  TEXT,                   -- PDF render từ template (bản gốc)
    pdf_url                       TEXT,                   -- bản scan đã ký
    status                        VARCHAR(20) NOT NULL DEFAULT 'Draft'
                                  CHECK (status IN ('Draft', 'Signed', 'Active', 'Ended', 'Canceled'))
);

-- Một order chỉ có tối đa 1 contract đang xử lý (Draft/Signed/Active).
CREATE UNIQUE INDEX uq_rental_contracts_active_per_order
    ON rental_contracts (order_id)
    WHERE status IN ('Draft', 'Signed', 'Active');

-- ---------------------------------------------------------------------------
-- Flow 1 — invoice / thanh toán
-- ---------------------------------------------------------------------------

CREATE TABLE invoices (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT REFERENCES rental_orders (id),
    contract_id BIGINT REFERENCES rental_contracts (id),
    customer_id BIGINT NOT NULL REFERENCES accounts (id),
    code        VARCHAR(50) NOT NULL UNIQUE,              -- INV-{service}-{facility}-{YYMMDD}-{rand}
    title       VARCHAR(255) NOT NULL,
    type        VARCHAR(20) NOT NULL
                CHECK (type IN ('Deposit', 'Rental', 'Extension', 'Penalty', 'Service')),
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'Unpaid'
                CHECK (status IN ('Unpaid', 'Paid', 'Canceled', 'Expired')),
    amount      NUMERIC(12,2) NOT NULL,                   -- snapshot giá hiệu lực lúc tạo
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_date    TIMESTAMPTZ,
    -- Nguồn gốc: đúng một FK được set (draft). Nới khi bổ sung support_request_id (Flow 7).
    CHECK (num_nonnulls(order_id, contract_id) = 1)
);

CREATE INDEX idx_invoices_customer_status ON invoices (customer_id, status);
CREATE INDEX idx_invoices_due_date ON invoices (due_date);

-- ---------------------------------------------------------------------------
-- Flow 1 — proposal
-- ---------------------------------------------------------------------------

CREATE TABLE proposal_feedbacks (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES rental_orders (id),
    customer_id BIGINT NOT NULL REFERENCES accounts (id),
    unit_id     BIGINT NOT NULL REFERENCES storage_units (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'Pending'
                CHECK (status IN ('Pending', 'Agreed', 'Rejected', 'Expired')),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ
);

CREATE INDEX idx_proposal_feedbacks_order ON proposal_feedbacks (order_id);

-- ---------------------------------------------------------------------------
-- Flow 1/2 — lịch hẹn & bàn giao
-- ---------------------------------------------------------------------------

CREATE TABLE appointments (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT NOT NULL REFERENCES accounts (id),
    facility_id   BIGINT NOT NULL REFERENCES facilities (id),
    staff_id      BIGINT REFERENCES accounts (id),        -- null khi chưa phân công FS
    type          VARCHAR(20) NOT NULL
                  CHECK (type IN ('CHECKIN', 'HANDOVER', 'RETURN')),
    cancel_reason TEXT,
    date          DATE NOT NULL,
    started_at    TIMESTAMPTZ,
    end_at        TIMESTAMPTZ,
    arrived_at    TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL DEFAULT 'Pending'
                  CHECK (status IN ('Pending', 'Done', 'Canceled')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointments_facility_date ON appointments (facility_id, date);
CREATE INDEX idx_appointments_staff ON appointments (staff_id);

CREATE TABLE rental_appointments (
    appointment_id BIGINT PRIMARY KEY REFERENCES appointments (id),
    order_id       BIGINT NOT NULL REFERENCES rental_orders (id)
);

CREATE TABLE handover_records (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT NOT NULL REFERENCES rental_orders (id),
    appointment_id       BIGINT NOT NULL UNIQUE REFERENCES appointments (id),
    unit_id              BIGINT NOT NULL REFERENCES storage_units (id),
    identity_status      VARCHAR(20) NOT NULL DEFAULT 'Pending'
                         CHECK (identity_status IN ('Pending', 'Verified', 'Failed')),
    identity_verified_at TIMESTAMPTZ,
    inspection_status    VARCHAR(20) NOT NULL DEFAULT 'Pending'
                         CHECK (inspection_status IN ('Pending', 'Agreed', 'Rejected')),
    unit_inspected_at    TIMESTAMPTZ,
    inspection_notes     TEXT,
    inspection_photos    TEXT[],
    result               VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                         CHECK (result IN ('IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELED')),
    completed_at         TIMESTAMPTZ,
    reject_reason        TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at               TIMESTAMPTZ                       -- set khi ghi nhận khách đến; không đổi sau đó
);

-- Một order chỉ có tối đa 1 hồ sơ bàn giao đang mở.
CREATE UNIQUE INDEX uq_handover_records_open_per_order
    ON handover_records (order_id)
    WHERE result = 'IN_PROGRESS';

CREATE TABLE unit_access_keys (
    id          BIGSERIAL PRIMARY KEY,
    unit_id     BIGINT NOT NULL REFERENCES storage_units (id),
    contract_id BIGINT NOT NULL REFERENCES rental_contracts (id),
    access_type VARCHAR(20) NOT NULL CHECK (access_type IN ('PhysicalKey', 'AccessCode')),
    quantity    INTEGER CHECK (quantity IS NULL OR quantity > 0),  -- số chìa (PhysicalKey)
    code_hash   VARCHAR(255),                                      -- hash mã truy cập (AccessCode)
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL DEFAULT 'Active'
                CHECK (status IN ('Active', 'Revoked', 'Lost')),
    CHECK ((access_type = 'AccessCode') = (code_hash IS NOT NULL)),
    CHECK (status <> 'Lost' OR access_type = 'PhysicalKey')
);

-- Mỗi contract tối đa 1 dòng Active cho mỗi loại truy cập.
CREATE UNIQUE INDEX uq_unit_access_keys_active
    ON unit_access_keys (contract_id, access_type)
    WHERE status = 'Active';

-- ---------------------------------------------------------------------------
-- Payment
-- ---------------------------------------------------------------------------

CREATE TABLE payment_transactions (
    id                     BIGSERIAL PRIMARY KEY,
    invoice_id             BIGINT NOT NULL REFERENCES invoices (id),
    gateway_transaction_no VARCHAR(100) UNIQUE,             -- mã giao dịch từ cổng (đối soát)
    transaction_content    TEXT,
    response_payload       TEXT,                            -- raw webhook/IPN để đối soát
    vnp_txn_ref            VARCHAR(100) NOT NULL UNIQUE,    -- idempotency theo vnp_txn_ref
    amount                 NUMERIC(12,2) NOT NULL,
    direction              VARCHAR(10) NOT NULL CHECK (direction IN ('PAY', 'REFUND')),
    failure_reason         TEXT,
    paid_at                TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                 VARCHAR(20) NOT NULL DEFAULT 'Pending'
                           CHECK (status IN ('Pending', 'Failed', 'Success'))
);

CREATE INDEX idx_payment_transactions_invoice ON payment_transactions (invoice_id);

-- ---------------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------------

-- Append-only. action/entity_type phải khớp catalog trong specs/db-table-draft.md.
CREATE TABLE audit_logs (
    id               BIGSERIAL PRIMARY KEY,
    actor_account_id BIGINT REFERENCES accounts (id),      -- null = hệ thống/cron/IPN
    action           VARCHAR(60) NOT NULL
                     CHECK (action IN (
                         'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
                         'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED',
                         'INVOICE_CREATED', 'INVOICE_CANCELED',
                         'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'MANUAL_REFUND_RECORDED',
                         'RENTAL_ORDER_DEPOSITED', 'STORAGE_UNIT_RESERVED',
                         'APPOINTMENT_CREATED', 'FS_ASSIGNED', 'APPOINTMENT_RESCHEDULED',
                         'APPOINTMENT_CANCELED_NO_SHOW',
                         'IDENTITY_VERIFIED', 'UNIT_INSPECTED', 'HANDOVER_REJECTED',
                         'RENTAL_ORDER_CANCELED', 'HANDOVER_COMPLETED',
                         'START_DATE_OVERRIDE_REQUESTED', 'START_DATE_OVERRIDE_APPROVED',
                         'START_DATE_OVERRIDE_REJECTED',
                         'CONTRACT_SIGNED', 'CONTRACT_ACTIVATED', 'CONTRACT_CANCELED', 'CONTRACT_ENDED',
                         'HANDOVER_CANCELED', 'ACCESS_KEY_ISSUED', 'ACCESS_KEY_REVOKED',
                         'STORAGE_UNIT_RENTED', 'STORAGE_UNIT_MAINTENANCE', 'STORAGE_UNIT_AVAILABLE',
                         'RENTAL_ORDER_DONE', 'HANDOVER_RECORD_CREATED',
                         'CHECKOUT_INSPECTED', 'CHECKOUT_COMPLETED'
                     )),
    entity_type      VARCHAR(60) NOT NULL
                     CHECK (entity_type IN (
                         'RentalRequest', 'ProposalFeedback', 'Invoice', 'PaymentTransaction',
                         'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
                         'RentalContract', 'UnitAccessKey', 'CheckoutRecord'
                     )),
    entity_id        TEXT NOT NULL,                        -- luôn lưu dạng chuỗi
    old_value        JSONB,
    new_value        JSONB,
    reason           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);

-- ---------------------------------------------------------------------------
-- Notification
-- ---------------------------------------------------------------------------

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
CREATE INDEX idx_notifications_account_created ON notifications (account_id, created_at DESC);

-- Liên kết thông báo dành cho đơn hàng: mỗi notification tối đa một đơn.
CREATE TABLE order_notifications (
    order_id        BIGINT NOT NULL REFERENCES rental_orders (id),
    notification_id BIGINT NOT NULL UNIQUE REFERENCES notifications (id),
    PRIMARY KEY (order_id, notification_id)
);
