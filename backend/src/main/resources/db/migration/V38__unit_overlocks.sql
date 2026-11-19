-- Flow 6 — khoá chặn của cơ sở (overlock) khi hợp đồng quá hạn kéo dài bị tạm khoá truy cập. Mã khoá số thu hồi từ xa
-- được, chìa cơ thì không: khoang có chìa cơ đang giao được gắn thêm một khoá chặn của cơ sở (khách không có chìa).
-- Mở lại truy cập (trả phí + gia hạn, hoặc khách đến buổi trả kho) → FS gỡ khoá chặn tại khoang.
--
-- Required        : vừa tạm khoá — chờ FS gắn khoá chặn tại khoang
-- Applied         : FS đã gắn
-- RemovalRequired : truy cập đã mở lại — chờ FS gỡ
-- Removed         : FS đã gỡ
-- Canceled        : mở lại trước khi FS kịp gắn (không cần gỡ)

CREATE TABLE unit_overlocks (
    id                   BIGSERIAL PRIMARY KEY,
    contract_id          BIGINT       NOT NULL REFERENCES rental_contracts (id),
    unit_id              BIGINT       NOT NULL REFERENCES storage_units (id),
    status               VARCHAR(20)  NOT NULL DEFAULT 'Required'
                         CHECK (status IN ('Required', 'Applied', 'RemovalRequired', 'Removed', 'Canceled')),
    required_at          TIMESTAMPTZ  NOT NULL,
    applied_by           BIGINT REFERENCES accounts (id),
    applied_at           TIMESTAMPTZ,
    removal_requested_at TIMESTAMPTZ,
    removed_by           BIGINT REFERENCES accounts (id),
    removed_at           TIMESTAMPTZ,
    canceled_at          TIMESTAMPTZ,
    note                 VARCHAR(500),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Mỗi hợp đồng tối đa một khoá chặn đang mở.
CREATE UNIQUE INDEX uq_unit_overlocks_open_per_contract ON unit_overlocks (contract_id)
    WHERE status IN ('Required', 'Applied', 'RemovalRequired');
CREATE INDEX idx_unit_overlocks_unit_status ON unit_overlocks (unit_id, status);

-- Audit: copy CHECK hiện tại (V37) + action / entity type của khoá chặn.
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
        'RENTAL_REQUEST_CONVERTED', 'RENTAL_REQUEST_EXPIRED',
        'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED', 'PROPOSAL_CREATED',
        'PROPOSAL_CANCELED',
        'INVOICE_CREATED', 'INVOICE_CANCELED', 'INVOICE_PAID', 'INVOICE_EXPIRED', 'INVOICE_REFUND_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PAYMENT_DUPLICATE', 'MANUAL_REFUND_RECORDED',
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
        'CONTRACT_EXTENDED',
        'HANDOVER_CANCELED', 'ACCESS_KEY_ISSUED', 'ACCESS_KEY_REVOKED',
        'STORAGE_UNIT_RENTED', 'STORAGE_UNIT_MAINTENANCE', 'STORAGE_UNIT_AVAILABLE',
        'RENTAL_ORDER_DONE', 'HANDOVER_RECORD_CREATED',
        'RETURN_REQUESTED', 'RETURN_ASSIGNED', 'RETURN_CANCELED', 'RETURN_COMPLETED',
        'CHECKOUT_INSPECTED', 'CHECKOUT_COMPLETED',
        'OVERDUE_FEE_ACCRUED',
        'SUPPORT_REQUEST_CREATED', 'SUPPORT_REQUEST_ASSIGNED', 'SUPPORT_REQUEST_RESOLVED',
        'SUPPORT_REQUEST_CANCELED',
        'FACILITY_CREATED', 'FACILITY_UPDATED', 'STORAGE_UNIT_CREATED', 'STORAGE_UNIT_UPDATED',
        'POLICY_UPDATED', 'UNIT_TYPE_UPDATED',
        'ACCOUNT_CREATED', 'ACCOUNT_UPDATED', 'ROLE_PERMISSIONS_UPDATED',
        'ACCOUNT_REGISTERED',
        'INSPECTION_CORRECTED', 'CONTRACT_ACCESS_SUSPENDED', 'CONTRACT_ACCESS_RESTORED',
        'RETURN_NO_SHOW',
        'STAFF_SHIFT_CREATED', 'STAFF_SHIFT_UPDATED', 'STAFF_SHIFT_DELETED',
        'STAFF_LEAVE_REQUESTED', 'STAFF_LEAVE_APPROVED', 'STAFF_LEAVE_REJECTED', 'STAFF_LEAVE_CANCELED',
        'STAFF_PROFILE_UPDATED',
        'ACCOUNT_REQUEST_CREATED', 'ACCOUNT_REQUEST_APPROVED', 'ACCOUNT_REQUEST_REJECTED', 'ACCOUNT_REQUEST_CANCELED',
        'UNIT_TRANSFER_REQUESTED', 'UNIT_TRANSFER_ALLOCATED', 'UNIT_TRANSFER_COMPLETED', 'UNIT_TRANSFER_CANCELED',
        'CONTRACT_UNIT_CHANGED',
        'OVERLOCK_REQUIRED', 'OVERLOCK_APPLIED', 'OVERLOCK_REMOVAL_REQUIRED', 'OVERLOCK_REMOVED', 'OVERLOCK_CANCELED'
    ));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_entity_type_check;

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check
    CHECK (entity_type IN (
        'RentalRequest', 'ProposalFeedback', 'Invoice', 'PaymentTransaction',
        'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
        'RentalContract', 'UnitAccessKey', 'CheckoutRecord', 'Account', 'ReturnRequest',
        'SupportRequest', 'Facility', 'Policy', 'UnitType', 'Role',
        'StaffShift', 'StaffLeave', 'StaffProfile', 'AccountRequest', 'UnitTransfer',
        'UnitOverlock'
    ));
