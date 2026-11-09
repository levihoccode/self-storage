-- Cửa sổ "sắp đến hạn" cho hợp đồng thuê (Flow 3.1): badge cảnh báo trước khi end_date tới.
-- Chốt 7 ngày; BOM đổi bằng UPDATE policies, không cần migration mới.
INSERT INTO policies (key, value, description) VALUES
    ('contract.expiring_soon_days', '7', 'Số ngày trước end_date được coi là sắp hết hạn hợp đồng (Flow 3.1)');
