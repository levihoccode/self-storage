-- Flow 1.3 (khách đồng ý / từ chối đề xuất khoang, FM đề xuất lại / huỷ đơn).
-- Migration mới thay vì sửa V1/V2 (checksum): DB dev đang chạy cũng được vá, không phải reset.

-- 1. Proposal bị huỷ dùng `Canceled` thay `Expired`: proposal không có TTL, chỉ bị huỷ khi khoang hết
--    Available lúc khách đồng ý hoặc khi đơn chạy Cancellation Cascade.
--    Bỏ CHECK cũ trước khi UPDATE: CHECK đang chặn `Canceled` nên DB có row `Expired` sẽ fail.
ALTER TABLE proposal_feedbacks DROP CONSTRAINT proposal_feedbacks_status_check;

UPDATE proposal_feedbacks SET status = 'Canceled' WHERE status = 'Expired';

ALTER TABLE proposal_feedbacks ADD CONSTRAINT proposal_feedbacks_status_check
    CHECK (status IN ('Pending', 'Agreed', 'Rejected', 'Canceled'));

-- 2. FM cần đề xuất lại khoang dù đơn đã qua Pending (vd khách từ chối khoang tại check-in).
--    Flow 2 bật/tắt cờ; Flow 1.3 chỉ đọc trong điều kiện đề xuất lại.
ALTER TABLE rental_orders ADD COLUMN needs_reproposal BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Audit khi proposal bị huỷ vì khoang hết Available lúc khách đồng ý.
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

-- 4. Tiền cọc = giá thuê hiệu lực × số tháng này (`sql/seed.sql` giữ mirror theo V2; key này chỉ có qua Flyway).
INSERT INTO policies (key, value, description) VALUES
    ('invoice.deposit_months', '1', 'Số tháng giá thuê thu làm tiền cọc (Flow 1.3)')
ON CONFLICT (key) DO NOTHING;
