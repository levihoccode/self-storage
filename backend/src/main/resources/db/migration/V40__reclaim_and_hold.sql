-- Flow 6 — hợp đồng quá hạn kéo dài khách bỏ đi: thu hồi khoang → lưu giữ đồ → khách nhận lại hoặc thanh lý.
-- Mốc tính từ ngày quá hạn: cảnh báo cuối (abandon_days − abandon_notice_days) → FM thu hồi khoang (abandon_days) →
-- lưu giữ storage.hold_days ngày (thông báo trước storage.liquidation_notice_days ngày) → thanh lý.

INSERT INTO policies (key, value, description) VALUES
    ('overdue.abandon_notice_days', '7', 'Gửi cảnh báo cuối trước mốc thu hồi khoang N ngày'),
    ('overdue.abandon_days', '45', 'Quá hạn ≥ N ngày (đang tạm khoá, khách chưa đến trả kho) → FM được thu hồi khoang'),
    ('storage.hold_days', '60', 'Số ngày cơ sở lưu giữ đồ sau khi thu hồi khoang, quá hạn thì thanh lý'),
    ('storage.liquidation_notice_days', '7', 'Báo khách trước khi hết hạn lưu giữ / thanh lý N ngày'),
    ('fee.reclaim', '300000', 'Phí thu hồi khoang (mở khoang, kiểm kê, di dời đồ) — VND'),
    ('fee.hold_per_day', '10000', 'Phí lưu giữ đồ sau thu hồi mỗi ngày — VND')
ON CONFLICT (key) DO NOTHING;

-- Lô đồ thu hồi từ khoang: Holding → Returned (khách trả hết nợ + phí lưu giữ, FM trả đồ) | Liquidated (hết hạn lưu giữ).
CREATE TABLE reclaimed_goods (
    id                     BIGSERIAL PRIMARY KEY,
    contract_id            BIGINT        NOT NULL UNIQUE REFERENCES rental_contracts (id),
    facility_id            BIGINT        NOT NULL REFERENCES facilities (id),
    unit_code              VARCHAR(50)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'Holding'
                           CHECK (status IN ('Holding', 'Returned', 'Liquidated')),
    reclaimed_at           TIMESTAMPTZ   NOT NULL,
    reclaimed_by           BIGINT        NOT NULL REFERENCES accounts (id),
    witness_staff_id       BIGINT        NOT NULL REFERENCES accounts (id),
    inventory_note         VARCHAR(4000) NOT NULL,
    photos                 TEXT[]        NOT NULL DEFAULT '{}',
    storage_location       VARCHAR(200)  NOT NULL,
    hold_until             DATE          NOT NULL,
    liquidation_notice_at  TIMESTAMPTZ,
    return_requested_at    TIMESTAMPTZ,
    returned_at            TIMESTAMPTZ,
    returned_by            BIGINT REFERENCES accounts (id),
    liquidated_at          TIMESTAMPTZ,
    liquidated_by          BIGINT REFERENCES accounts (id),
    proceeds_amount        NUMERIC(14, 2) CHECK (proceeds_amount IS NULL OR proceeds_amount >= 0),
    liquidation_note       VARCHAR(4000),
    liquidation_photos     TEXT[]        NOT NULL DEFAULT '{}',
    -- Tiền thanh lý còn dư sau khi trừ nợ — giữ hộ khách, FM hoàn thủ công rồi ghi nhận.
    surplus_amount         NUMERIC(14, 2) CHECK (surplus_amount IS NULL OR surplus_amount >= 0),
    surplus_refunded_at    TIMESTAMPTZ,
    surplus_refunded_by    BIGINT REFERENCES accounts (id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_reclaimed_goods_facility_status ON reclaimed_goods (facility_id, status);

-- Audit: copy CHECK hiện tại (V39 action, V38 entity type) + thu hồi / lưu giữ / thanh lý.
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
        'OVERLOCK_REQUIRED', 'OVERLOCK_APPLIED', 'OVERLOCK_REMOVAL_REQUIRED', 'OVERLOCK_REMOVED', 'OVERLOCK_CANCELED',
        'OVERLOCK_ASSIGNED',
        'CONTRACT_RECLAIMED', 'GOODS_RETURN_REQUESTED', 'GOODS_RETURNED', 'GOODS_LIQUIDATED',
        'GOODS_SURPLUS_REFUNDED'
    ));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_entity_type_check;

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check
    CHECK (entity_type IN (
        'RentalRequest', 'ProposalFeedback', 'Invoice', 'PaymentTransaction',
        'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
        'RentalContract', 'UnitAccessKey', 'CheckoutRecord', 'Account', 'ReturnRequest',
        'SupportRequest', 'Facility', 'Policy', 'UnitType', 'Role',
        'StaffShift', 'StaffLeave', 'StaffProfile', 'AccountRequest', 'UnitTransfer',
        'UnitOverlock', 'ReclaimedGoods'
    ));
