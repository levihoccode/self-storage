-- ============================================================================
-- V3 — RBAC permission catalog + mapping mặc định cho 5 role (A3b, #57)
--
-- Catalog: backend/docs/permission-catalog.md — sửa catalog thì sửa cả hai nơi.
-- V3+ không mirror trong backend/sql/ (chỉ baseline V1/V2 có mirror).
--
-- Quy ước: code = <resource>.<action>, resource = tên bảng snake số ít.
-- Ô `own` trong catalog không seed vào role_permissions (guard ownership ở service).
-- ============================================================================

INSERT INTO permissions (code, description) VALUES
    ('appointment.assign',                          'Phân công FS cho lịch hẹn'),
    ('appointment.checkin',                         'Xác nhận khách đã đến (Done)'),
    ('appointment.create',                          'Chọn lịch check-in'),
    ('appointment.read',                            'Xem lịch hẹn theo cơ sở / được gán'),
    ('facility.access',                             'Truy cập dữ liệu cơ sở (permission tạm cho endpoint demo A3b)'),
    ('handover_record.inspect',                     'Ghi/xác nhận hiện trạng khoang'),
    ('handover_record.reject',                      'Từ chối khoang tại chỗ'),
    ('handover_record.verify_identity',             'Xác minh danh tính khi bàn giao'),
    ('invoice.pay',                                 'Thanh toán hóa đơn'),
    ('invoice.read',                                'Xem hóa đơn'),
    ('notification.create_other',                   'Tạo thông báo OTHER thủ công'),
    ('payment_transaction.refund',                  'Ghi nhận hoàn tiền thủ công'),
    ('proposal_feedback.override',                  'Override khoang đã bị từ chối (audit)'),
    ('proposal_feedback.repropose',                 'Đề xuất lại khoang'),
    ('proposal_feedback.respond',                   'Đồng ý/từ chối đề xuất khoang'),
    ('rental_contract.sign',                        'Ký/upload hợp đồng'),
    ('rental_contract.start_date_override.request', 'Đề nghị đổi mốc tính tiền'),
    ('rental_contract.start_date_override.review',  'Duyệt/từ chối đổi mốc tính tiền'),
    ('rental_order.cancel',                         'Hủy đơn'),
    ('rental_order.read',                           'Xem đơn thuê'),
    ('rental_request.approve',                      'Duyệt yêu cầu đặt kho + gán unit'),
    ('rental_request.read',                         'Xem yêu cầu đặt kho'),
    ('rental_request.reject',                       'Từ chối yêu cầu đặt kho'),
    ('unit_access_key.issue',                       'Bàn giao khóa/mã truy cập');

-- Mapping role → permission (mỗi ô G/F trong catalog = 1 dòng).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('ADMIN', 'appointment.read'),
    ('ADMIN', 'facility.access'),
    ('ADMIN', 'notification.create_other'),
    ('ADMIN', 'rental_order.read'),
    ('ADMIN', 'rental_request.read'),
    ('BOM',   'appointment.read'),
    ('BOM',   'facility.access'),
    ('BOM',   'notification.create_other'),
    ('BOM',   'rental_order.read'),
    ('BOM',   'rental_request.read'),
    ('FM',    'appointment.assign'),
    ('FM',    'appointment.read'),
    ('FM',    'facility.access'),
    ('FM',    'payment_transaction.refund'),
    ('FM',    'proposal_feedback.override'),
    ('FM',    'proposal_feedback.repropose'),
    ('FM',    'rental_contract.start_date_override.review'),
    ('FM',    'rental_order.cancel'),
    ('FM',    'rental_order.read'),
    ('FM',    'rental_request.approve'),
    ('FM',    'rental_request.read'),
    ('FM',    'rental_request.reject'),
    ('FS',    'appointment.checkin'),
    ('FS',    'appointment.read'),
    ('FS',    'facility.access'),
    ('FS',    'handover_record.inspect'),
    ('FS',    'handover_record.verify_identity'),
    ('FS',    'rental_contract.sign'),
    ('FS',    'rental_contract.start_date_override.request'),
    ('FS',    'rental_order.read'),
    ('FS',    'unit_access_key.issue')
) AS mapping (role_name, permission_code)
JOIN roles r ON r.name = mapping.role_name
JOIN permissions p ON p.code = mapping.permission_code;
