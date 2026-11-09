-- Nghĩa vụ hoàn tiền được tạo trước khi chi tiền. Tiền thực tế vẫn đi qua refund_transactions.
CREATE TABLE refund_requests (
    id                       BIGSERIAL PRIMARY KEY,
    invoice_id               BIGINT REFERENCES invoices (id),
    order_id                 BIGINT REFERENCES rental_orders (id),
    amount                   NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason_code              VARCHAR(30) NOT NULL
                             CHECK (reason_code IN (
                                 'UNIT_UNAVAILABLE', 'DUPLICATE_PAYMENT',
                                 'DEPOSIT_SURPLUS', 'OTHER'
                             )),
    reason_note              TEXT,
    status                   VARCHAR(20) NOT NULL DEFAULT 'Requested'
                             CHECK (status IN ('Requested', 'Approved', 'Disbursed', 'Canceled')),
    calculation_version      VARCHAR(50) NOT NULL,
    calculation_input        JSONB,
    formula_text             TEXT NOT NULL,
    requested_by_account_id  BIGINT REFERENCES accounts (id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (invoice_id IS NOT NULL OR order_id IS NOT NULL),
    CHECK (reason_code <> 'OTHER' OR nullif(btrim(reason_note), '') IS NOT NULL)
);

CREATE INDEX idx_refund_requests_invoice
    ON refund_requests (invoice_id, status, created_at DESC);
CREATE INDEX idx_refund_requests_order
    ON refund_requests (order_id, status, created_at DESC);

CREATE TABLE refund_request_lines (
    id                 BIGSERIAL PRIMARY KEY,
    refund_request_id  BIGINT NOT NULL REFERENCES refund_requests (id) ON DELETE CASCADE,
    component_code     VARCHAR(40) NOT NULL
                       CHECK (component_code IN (
                           'DEPOSIT_PAID', 'DEPOSIT_REQUIRED', 'UNIT_CHANGE_FEE',
                           'UNPAID_RENT', 'REPAIR_FEE', 'CLEANING_FEE',
                           'PRIOR_REFUND', 'GOODWILL', 'ADJUSTMENT', 'OTHER'
                       )),
    direction          VARCHAR(10) NOT NULL CHECK (direction IN ('ADD', 'SUBTRACT')),
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    label              VARCHAR(200) NOT NULL,
    source_type        VARCHAR(30),
    source_id          BIGINT,
    note               TEXT,
    sort_order         INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_refund_request_lines_request
    ON refund_request_lines (refund_request_id, sort_order, id);

-- Một request active cho mỗi invoice; request order-only không chặn request theo invoice khác.
CREATE UNIQUE INDEX uq_refund_requests_active_invoice
    ON refund_requests (invoice_id)
    WHERE invoice_id IS NOT NULL AND status IN ('Requested', 'Approved');
CREATE UNIQUE INDEX uq_refund_requests_active_order_only
    ON refund_requests (order_id)
    WHERE invoice_id IS NULL AND status IN ('Requested', 'Approved');

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
        'RENTAL_REQUEST_CONVERTED', 'RENTAL_REQUEST_EXPIRED',
        'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED', 'PROPOSAL_CREATED',
        'PROPOSAL_CANCELED',
        'INVOICE_CREATED', 'INVOICE_CANCELED', 'INVOICE_REFUND_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PAYMENT_DUPLICATE', 'MANUAL_REFUND_RECORDED',
        'REFUND_CUSTOMER_CONFIRMED', 'REFUND_CUSTOMER_DISPUTED', 'REFUND_REQUESTED',
        'DEPOSIT_REFUND_REQUIRED',
        'UNIT_CHANGE_RELEASED',
        'RENTAL_ORDER_DEPOSITED', 'RENTAL_ORDER_CREATED', 'STORAGE_UNIT_RESERVED',
        'APPOINTMENT_CREATED', 'FS_ASSIGNED', 'APPOINTMENT_RESCHEDULED',
        'APPOINTMENT_CANCELED_NO_SHOW', 'APPOINTMENT_CANCELED', 'APPOINTMENT_ARRIVED',
        'IDENTITY_VERIFIED', 'UNIT_INSPECTED', 'HANDOVER_REJECTED',
        'RENTAL_ORDER_CANCELED', 'HANDOVER_COMPLETED',
        'START_DATE_OVERRIDE_REQUESTED', 'START_DATE_OVERRIDE_APPROVED',
        'START_DATE_OVERRIDE_REJECTED',
        'CONTRACT_SIGNED', 'CONTRACT_ACTIVATED', 'CONTRACT_CANCELED', 'CONTRACT_ENDED',
        'HANDOVER_CANCELED', 'ACCESS_KEY_ISSUED', 'ACCESS_KEY_REVOKED',
        'STORAGE_UNIT_RENTED', 'STORAGE_UNIT_MAINTENANCE', 'STORAGE_UNIT_AVAILABLE',
        'RENTAL_ORDER_DONE', 'HANDOVER_RECORD_CREATED',
        'CHECKOUT_INSPECTED', 'CHECKOUT_COMPLETED',
        'ACCOUNT_REGISTERED'
    ));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_entity_type_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check
    CHECK (entity_type IN (
        'RentalRequest', 'ProposalFeedback', 'Invoice', 'PaymentTransaction', 'RefundTransaction',
        'RefundRequest',
        'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
        'RentalContract', 'UnitAccessKey', 'CheckoutRecord', 'Account'
    ));

INSERT INTO permissions (code, description)
VALUES ('refund_transaction.request', 'Tạo nghĩa vụ hoàn tiền thủ công');

INSERT INTO role_permissions (role_id, permission_id, scope)
SELECT r.id, p.id, 'FACILITY'
FROM roles r
JOIN permissions p ON p.code = 'refund_transaction.request'
WHERE r.name IN ('FM', 'ADMIN');
