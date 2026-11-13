-- Flow 7 — Support request & issue handling.
-- Trạng thái "đang chờ xử lý" mà demo-seed.sql không có: 3 phiếu, đủ 3 nhánh cho FS và FM.
-- Mốc thời gian tính bằng now() nên reset lúc nào cũng đúng.
BEGIN;

WITH ctx AS (
    SELECT (SELECT id FROM accounts WHERE email = 'customer1@lemar.vn') AS customer_id,
           (SELECT id FROM accounts WHERE email = 'fs1@lemar.vn')       AS fs_id,
           (SELECT id FROM facilities WHERE code = 'Q7')                AS facility_id,
           (SELECT id FROM rental_contracts WHERE customer_id =
                (SELECT id FROM accounts WHERE email = 'customer1@lemar.vn')
            ORDER BY id LIMIT 1)                                        AS contract_id
),
seed (category, title, description, status, assigned, ago_minutes) AS (
    VALUES ('LOST_KEY',      'Mất chìa khoá cơ',        'Khách báo mất chìa khoá cơ A-01, cần làm lại.',        'Open',     false, 35),
           ('ACCESS_CODE',   'Mã khoá số không mở được',  'Nhập mã trên app nhưng cửa không mở, đã thử 3 lần.',     'Open',     false, 20),
           ('BILLING',       'Hoá đơn thuê tháng 11 sai', 'Bị tính phí quá hạn trong khi đã chuyển khoản trước hạn.', 'Assigned', true,  10)
)
INSERT INTO support_requests
    (customer_id, facility_id, contract_id, category, title, description, status,
     assigned_staff_id, assigned_at, created_at, updated_at)
SELECT ctx.customer_id, ctx.facility_id, ctx.contract_id, s.category, s.title, s.description, s.status,
       CASE WHEN s.assigned THEN ctx.fs_id END,
       CASE WHEN s.assigned THEN now() - interval '5 minutes' END,
       now() - (s.ago_minutes || ' minutes')::interval,
       now() - (s.ago_minutes || ' minutes')::interval
FROM ctx, seed s;

COMMIT;

SELECT r.id, r.category, r.status, a.name AS assigned_fs, r.title
FROM support_requests r LEFT JOIN accounts a ON a.id = r.assigned_staff_id
ORDER BY r.id;
