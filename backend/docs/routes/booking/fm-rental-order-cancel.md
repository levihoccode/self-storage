---
auth-model: permission-bound
purpose: FM huỷ đơn thuê với lý do do FM nhập.
---
# POST /api/fm/rental-orders/{orderId}/cancel

Body `{"reason": "...", "note": "..."}`:

- `reason` — bắt buộc, tối đa 255 ký tự; hiển thị cho khách trong notification.
- `note` — tuỳ chọn, tối đa 500 ký tự; **ghi chú nội bộ**, chỉ lưu `audit_logs.new_value.note`, không gửi khách.

Service `trim()` cả hai; `note` rỗng được bỏ.

- **Actor / quyền:** role FM (namespace `/api/fm/**`) + permission `rental_order.cancel` theo cơ sở của đơn
  (seed V3, scope `FACILITY`). FM cơ sở khác → 403.
- **Contract chi tiết:** annotation tại `booking/controller/FmRentalOrderController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Thiếu/blank `reason` → 400 (qua `@Valid`); quá 255 ký tự → 400; `note` quá 500 ký tự → 400.
- Kiểm quyền theo cơ sở của đơn, rồi khoá row đơn. Đơn `Canceled`/`Done` → 409.
- Lý do FM nhập được ghi vào `rental_orders.cancel_reason`, `audit_logs.reason` và body notification
  `RENTAL_ORDER_CANCELED` gửi khách + FM. `note` chỉ vào `audit_logs.new_value.note` của `RentalOrder`.
- Chạy Cancellation Cascade dùng chung với lần từ chối thứ 3 (`RentalOrderCancellation`): đơn `Canceled` +
  `cancel_reason`, proposal `Pending`/`Agreed` → `Canceled`, hoá đơn `Unpaid` → `Canceled`, khoang `Reserved` →
  `Available`, AuditLog từng entity (actor = FM), khách + FM nhận `RENTAL_ORDER_CANCELED`.

## Ghi chú nghiệp vụ

- Spec `draft.md` › 1.3 nhánh "Khách không phản hồi proposal": FM liên hệ khách ngoài hệ thống, không được thì
  huỷ đơn. Lý do do FM nhập (proposal #268) — không còn cố định "Khách không còn nhu cầu".
- Cascade chưa xử lý `RentalContract`, `HandoverRecord`, `Appointment` và hoàn cọc (Flow 2/4 chưa có code).

## Ví dụ

```bash
TOKEN='<token của fm1@lemar.vn>'

curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"Khách không còn nhu cầu thuê khoang","note":"Đã gọi 3 lần, không liên hệ được"}' \
  http://localhost:8080/api/fm/rental-orders/1/cancel
# 200 {"message":"Đã huỷ đơn thuê","data":{"id":1,"customerId":6,"unitId":1,"status":"Canceled","cancelReason":"Khách không còn nhu cầu thuê khoang"}}

# Thiếu reason
# 400 {"message":"Dữ liệu không hợp lệ","timestamp":"..."}

# Gọi lại
# 409 {"message":"Đơn thuê đã đóng, không thể huỷ","timestamp":"2026-10-15T15:16:46.193969400Z"}

# fm2@lemar.vn (cơ sở khác)
# 403 {"message":"Bạn không được gán vào cơ sở này","timestamp":"2026-10-15T15:16:45.341504600Z"}

# customer1@lemar.vn
# 403 {"message":"Bạn không có quyền truy cập","timestamp":"2026-10-15T15:16:45.466905900Z"}

# Đơn không tồn tại
# 404 {"message":"Không tìm thấy đơn thuê","timestamp":"2026-10-15T15:16:45.601282500Z"}
```

DB trước/sau lần gọi 200 (đơn 1; khoang A-01 được đặt `Reserved` bằng SQL trước khi gọi để thấy bước nhả khoang):

| Bảng | Trước | Sau |
|---|---|---|
| `rental_orders` 1 | `Pending` | `Canceled`, lý do FM nhập |
| `proposal_feedbacks` 1 / 5 | `Agreed` / `Pending` | `Canceled` / `Canceled` |
| `storage_units` A-01 | `Reserved` | `Available` |
| `audit_logs` | — | `RENTAL_ORDER_CANCELED` × 4 (RentalOrder 1, ProposalFeedback 1, 5, StorageUnit 1), actor 3 |
| `notifications` | — | `RENTAL_ORDER_CANCELED` cho fm1 và customer1: "Đơn 1 đã bị hủy. Lý do: Khách không còn nhu cầu." |

Hoá đơn duy nhất của đơn đã `Canceled` từ trước nên không có `INVOICE_CANCELED`; nhánh hoá đơn `Unpaid` có
trong `ProposalServiceIntegrationTest.fmCancelRunsFullCascadeWithAuditAndNotifications`.

## Liên quan

- [fm-rental-order-detail.md](fm-rental-order-detail.md) · [proposal-reject.md](proposal-reject.md) ·
  [routes.md](../../routes.md) · [authorization.md](../../authorization.md)
