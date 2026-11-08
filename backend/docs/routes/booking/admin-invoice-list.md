---
auth-model: role-bound
purpose: ADMIN xem danh sách hoá đơn toàn hệ thống; read-only.
---
# GET /api/admin/invoices?page=&size=

Trả một trang hoá đơn mới nhất trước. Route chỉ đọc; không đổi trạng thái invoice/payment.

- **Actor / quyền:** Bearer + role `ADMIN` (`/api/admin/**`).
- **Contract chi tiết:** annotation tại `AdminInvoiceController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Response

`data` gồm `items`, `page`, `size`, `totalElements`, `totalPages`.

Mỗi item:

- `id`, `orderId`, `code`, `title`, `description`
- `type`, `status`, `amount`, `createdAt`, `dueDate`
- `unitId`, `unitCode`, `facilityId`, `facilityName`

Không trả thông tin giao dịch/thẻ/ngân hàng.

## Lỗi

| Status | Khi nào |
|---|---|
| 400 | `page < 0` hoặc `size` ngoài 1..100 |
| 403 | Không phải ADMIN |

## Ghi chú

- Dùng cho tab `Hoá đơn` của `/admin/invoices`; tab `Hoàn tiền` dùng
  `GET /api/admin/payments/refunds`.
- Route read-only; muốn handover/confirm dùng route refund riêng.

## Liên quan

- [admin-refund-required.md](../payment/admin-refund-required.md)
- [routes.md](../../routes.md)
