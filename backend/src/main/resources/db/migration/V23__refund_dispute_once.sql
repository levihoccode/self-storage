-- Khách chỉ ghi nhận một lần báo chưa nhận cho mỗi refund; lần sau là idempotent.
ALTER TABLE refund_transactions
    ADD COLUMN disputed_at TIMESTAMPTZ,
    ADD COLUMN dispute_note TEXT;
