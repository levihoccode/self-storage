-- Đổi tên entity theo đúng nghĩa: FM/Admin tạo nghĩa vụ hoàn tiền, không phải khách gửi request.
ALTER TABLE refund_requests RENAME TO refund_obligations;
ALTER TABLE refund_request_lines RENAME TO refund_obligation_lines;
ALTER TABLE refund_obligation_lines RENAME COLUMN refund_request_id TO refund_obligation_id;
ALTER TABLE refund_obligations RENAME COLUMN requested_by_account_id TO created_by_account_id;

ALTER INDEX idx_refund_requests_invoice RENAME TO idx_refund_obligations_invoice;
ALTER INDEX idx_refund_requests_order RENAME TO idx_refund_obligations_order;
ALTER INDEX uq_refund_requests_active_invoice RENAME TO uq_refund_obligations_active_invoice;
ALTER INDEX idx_refund_request_lines_request RENAME TO idx_refund_obligation_lines_obligation;

-- Requested -> Open: nghĩa vụ vừa được tạo, chưa phải "khách yêu cầu".
ALTER TABLE refund_obligations DROP CONSTRAINT refund_requests_status_check;
UPDATE refund_obligations SET status = 'Open' WHERE status = 'Requested';
ALTER TABLE refund_obligations ALTER COLUMN status SET DEFAULT 'Open';
ALTER TABLE refund_obligations ADD CONSTRAINT refund_obligations_status_check
    CHECK (status IN ('Open', 'Approved', 'Disbursed', 'Canceled'));

-- Permission code theo resource mới.
UPDATE permissions
SET code = 'refund_obligation.create',
    description = 'Tạo nghĩa vụ hoàn tiền thủ công'
WHERE code = 'refund_transaction.request';

-- Bỏ CHECK cũ trước khi đổi dữ liệu audit, rồi mới siết lại ở cuối.
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_entity_type_check;

-- Audit cũ đổi theo tên mới trước khi siết CHECK.
UPDATE audit_logs SET action = 'REFUND_OBLIGATION_CREATED' WHERE action = 'REFUND_REQUESTED';
UPDATE audit_logs SET entity_type = 'RefundObligation' WHERE entity_type = 'RefundRequest';

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN (
        'RENTAL_REQUEST_APPROVED', 'RENTAL_REQUEST_REJECTED', 'RENTAL_REQUEST_CLAIMED',
        'RENTAL_REQUEST_CONVERTED', 'RENTAL_REQUEST_EXPIRED',
        'PROPOSAL_AGREED', 'PROPOSAL_REJECTED', 'PROPOSAL_REPROPOSED', 'PROPOSAL_CREATED',
        'PROPOSAL_CANCELED',
        'INVOICE_CREATED', 'INVOICE_CANCELED', 'INVOICE_REFUND_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PAYMENT_DUPLICATE', 'MANUAL_REFUND_RECORDED',
        'REFUND_CUSTOMER_CONFIRMED', 'REFUND_CUSTOMER_DISPUTED', 'REFUND_OBLIGATION_CREATED',
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

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check
    CHECK (entity_type IN (
        'RentalRequest', 'ProposalFeedback', 'Invoice', 'PaymentTransaction', 'RefundTransaction',
        'RefundObligation',
        'RentalOrder', 'StorageUnit', 'Appointment', 'HandoverRecord',
        'RentalContract', 'UnitAccessKey', 'CheckoutRecord', 'Account'
    ));
