-- Nghĩa vụ hoàn tiền gắn với invoice hoặc từng quyết toán riêng, không gắn với order.
-- Một order có thể có nhiều refund request active; chỉ chặn trùng theo invoice (V24).
DROP INDEX IF EXISTS uq_refund_requests_active_order_only;
