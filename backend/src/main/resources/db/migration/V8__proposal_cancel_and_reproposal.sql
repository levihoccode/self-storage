-- Flow 1.3 (đề xuất khoang): phần schema còn thiếu sau V1–V7.
-- V1/V2 đã áp dụng nên đóng băng; thay đổi đi bằng migration mới giống cách V5–V7 đã làm.
-- V6 đã bỏ 'Expired' khỏi rental_orders.status, V7 đã viết lại audit_logs.action (chưa có
-- PROPOSAL_CANCELED), nên V8 chỉ bổ sung cột cờ, CHECK proposal và seed tiền cọc.

ALTER TABLE rental_orders
    ADD COLUMN needs_reproposal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE proposal_feedbacks DROP CONSTRAINT proposal_feedbacks_status_check;

ALTER TABLE proposal_feedbacks ADD CONSTRAINT proposal_feedbacks_status_check
    CHECK (status IN ('Pending', 'Agreed', 'Rejected', 'Canceled'));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
        'RENTAL_REQUEST_CONVERTED', 'RENTAL_REQUEST_EXPIRED',
        'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED', 'PROPOSAL_CREATED',
        'PROPOSAL_CANCELED',
        'INVOICE_CREATED', 'INVOICE_CANCELED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'MANUAL_REFUND_RECORDED',
        'RENTAL_ORDER_DEPOSITED', 'RENTAL_ORDER_CREATED', 'STORAGE_UNIT_RESERVED',
        'APPOINTMENT_CREATED', 'FS_ASSIGNED', 'APPOINTMENT_RESCHEDULED',
        'APPOINTMENT_CANCELED_NO_SHOW', 'APPOINTMENT_CANCELED',
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

INSERT INTO policies (key, value, description) VALUES
    ('invoice.deposit_months', '1', 'Số tháng giá thuê thu làm tiền cọc (Flow 1.3)')
ON CONFLICT (key) DO NOTHING;
