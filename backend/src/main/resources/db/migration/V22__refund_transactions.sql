-- Tiền ra có ledger riêng: payment_transactions chỉ ghi PAY.
CREATE TABLE refund_transactions (
    id                     BIGSERIAL PRIMARY KEY,
    invoice_id             BIGINT NOT NULL REFERENCES invoices (id),
    source_transaction_id  BIGINT NOT NULL REFERENCES payment_transactions (id),
    amount                 NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    method                 VARCHAR(20) NOT NULL
                           CHECK (method IN ('CASH', 'BANK_TRANSFER', 'VNPAY')),
    status                 VARCHAR(20) NOT NULL
                           CHECK (status IN ('Pending', 'Success', 'Failed', 'Canceled')),
    reason_code            VARCHAR(30) NOT NULL
                           CHECK (reason_code IN (
                               'UNIT_UNAVAILABLE', 'DUPLICATE_PAYMENT',
                               'DEPOSIT_SURPLUS', 'OTHER'
                           )),
    reason_note            TEXT,
    gateway_ref            VARCHAR(100),
    evidence_url           TEXT,
    note                   TEXT,
    handled_by_account_id  BIGINT REFERENCES accounts (id),
    disbursed_at           TIMESTAMPTZ,
    customer_confirmed_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'Success' OR disbursed_at IS NOT NULL),
    CHECK (reason_code <> 'OTHER' OR nullif(btrim(reason_note), '') IS NOT NULL)
);

CREATE INDEX idx_refund_transactions_invoice
    ON refund_transactions (invoice_id, created_at DESC);
CREATE INDEX idx_refund_transactions_status
    ON refund_transactions (status, created_at);
CREATE UNIQUE INDEX uq_refund_transactions_success_per_source
    ON refund_transactions (source_transaction_id)
    WHERE status = 'Success';

ALTER TABLE invoices
    DROP CONSTRAINT invoices_refund_status_check;
ALTER TABLE invoices
    ADD CONSTRAINT invoices_refund_status_check
    CHECK (refund_status IN ('None', 'Required', 'Processing', 'AwaitingConfirmation', 'Refunded'));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
        'RENTAL_REQUEST_CONVERTED', 'RENTAL_REQUEST_EXPIRED',
        'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED', 'PROPOSAL_CREATED',
        'PROPOSAL_CANCELED',
        'INVOICE_CREATED', 'INVOICE_CANCELED', 'INVOICE_REFUND_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PAYMENT_DUPLICATE', 'MANUAL_REFUND_RECORDED',
        'REFUND_CUSTOMER_CONFIRMED', 'REFUND_CUSTOMER_DISPUTED',
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
        'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
        'RentalContract', 'UnitAccessKey', 'CheckoutRecord', 'Account'
    ));

INSERT INTO permissions (code, description)
VALUES ('refund_transaction.handover', 'Xác nhận đã đưa tiền hoàn cho khách');

INSERT INTO role_permissions (role_id, permission_id, scope)
SELECT r.id, p.id, 'FACILITY'
FROM roles r
JOIN permissions p ON p.code = 'refund_transaction.handover'
WHERE r.name IN ('FS', 'ADMIN');
