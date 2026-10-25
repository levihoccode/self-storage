-- Flow 1.3: tách hoá đơn bổ sung khỏi Deposit và chặn 2 hoá đơn cọc active trên một đơn.
-- Nếu DB đang có 2 hoá đơn cọc active (dữ liệu bất thường/race), migration sẽ fail —
-- phải xử lý dữ liệu trước khi chạy.

ALTER TABLE invoices DROP CONSTRAINT invoices_type_check;

ALTER TABLE invoices ADD CONSTRAINT invoices_type_check
    CHECK (type IN ('Deposit', 'DepositSupplement', 'Rental', 'Extension', 'Penalty', 'Service'));

-- Một đơn chỉ có tối đa một hoá đơn cọc đầy đủ còn hiệu lực (Unpaid/Paid).
CREATE UNIQUE INDEX uq_invoices_active_full_deposit
    ON invoices (order_id)
    WHERE type = 'Deposit' AND status IN ('Unpaid', 'Paid');

-- Một đơn chỉ có tối đa một hoá đơn bổ sung chờ thanh toán.
CREATE UNIQUE INDEX uq_invoices_unpaid_deposit_supplement
    ON invoices (order_id)
    WHERE type = 'DepositSupplement' AND status = 'Unpaid';
