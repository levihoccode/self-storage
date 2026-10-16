-- ============================================================================
-- Dữ liệu mẫu Flow 1.3 (B3 — đồng ý / từ chối / đề xuất lại) — CHẠY TAY trên DB local.
--
-- KHÔNG phải migration: Flyway không đọc thư mục này. Seed chung vẫn là V2__baseline_seed.sql.
-- Đơn + proposal bình thường do luồng convert (B2) tạo; file này chỉ để thử B3 khi chưa có B2.
--
-- Chạy (sau khi backend đã chạy Flyway V1–V3 trên DB self_storage):
--   Get-Content backend\sql\dev\proposal-sample.sql -Raw |
--     docker compose exec -T postgres psql -U self_storage -d self_storage
--
-- Tạo ra (cơ sở Q7, FM = fm1@lemar.vn, khách = customer1@lemar.vn):
--   Đơn 1: proposal A-01 Pending           → thử khách Đồng ý / Từ chối
--   Đơn 2: proposal C-01 Pending (khoang Maintenance) → thử nhánh khoang hết Available
--   Đơn 3: proposal A-02 Rejected          → thử FM đề xuất lại (chọn lại A-02 cần lý do override)
-- Chạy lại nhiều lần sẽ tạo thêm đơn mới; muốn sạch thì docker compose down -v rồi up lại.
-- ============================================================================

BEGIN;

WITH ctx AS (
    SELECT (SELECT id FROM facilities WHERE code = 'Q7')                  AS facility_id,
           (SELECT id FROM unit_types WHERE name = 'Small')               AS small_type,
           (SELECT id FROM accounts WHERE email = 'customer1@lemar.vn')   AS customer_id
),
sample (label, unit_code, proposal_status, note) AS (
    VALUES ('don-1', 'A-01', 'Pending',  NULL),
           ('don-2', 'C-01', 'Pending',  NULL),
           ('don-3', 'A-02', 'Rejected', 'Khoang xa cửa ra vào')
),
req AS (
    INSERT INTO rental_requests (facility_id, customer_name, normalized_customer_email, customer_phone,
                                 unit_type_id, start_date, period, unit_id, status)
    SELECT ctx.facility_id, 'Khách mẫu ' || s.label, 'customer1@lemar.vn', '0900000000', ctx.small_type,
           current_date + 7, 3, (SELECT id FROM storage_units WHERE code = s.unit_code), 'Converted'
    FROM ctx CROSS JOIN sample s
    RETURNING id, customer_name
),
ord AS (
    INSERT INTO rental_orders (request_id, customer_id, status, expires_at)
    SELECT req.id, ctx.customer_id, 'Pending', now() + interval '30 days'
    FROM req CROSS JOIN ctx
    RETURNING id, request_id
)
INSERT INTO proposal_feedbacks (order_id, customer_id, unit_id, status, note)
SELECT ord.id, ctx.customer_id, (SELECT id FROM storage_units WHERE code = s.unit_code), s.proposal_status, s.note
FROM ord
JOIN req ON req.id = ord.request_id
JOIN sample s ON req.customer_name = 'Khách mẫu ' || s.label
CROSS JOIN ctx;

COMMIT;

-- Xem lại:
SELECT p.id AS proposal_id, p.order_id, u.code AS unit, u.status AS unit_status, p.status, p.note
FROM proposal_feedbacks p JOIN storage_units u ON u.id = p.unit_id
ORDER BY p.id DESC LIMIT 3;
