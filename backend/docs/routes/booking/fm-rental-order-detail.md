---
auth-model: permission-bound
purpose: FM xem đơn thuê để đề xuất lại — lịch sử proposal, số lần từ chối, khoang trống cùng loại.
---
# GET /api/fm/rental-orders/{orderId}

Dữ liệu cho màn "Đề xuất lại khoang" (`/fm/rental-orders/:id/re-propose`).

- **Actor / quyền:** role-bound `FM` (`/api/fm/**`) + permission `rental_order.read` theo cơ sở của đơn
  (`Access.can`), FM cơ sở khác → 403 — route nằm trong `/api/fm/**` theo quy tắc ở `authorization.md`.
- **Contract chi tiết:** annotation tại `booking/controller/FmRentalOrderController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Đơn + thông tin khách và nhu cầu lấy từ `rental_requests` (tên, email, SĐT, loại khoang, ngày bắt đầu,
  số tháng).
- `proposals`: toàn bộ lịch sử proposal của đơn, mới nhất trước, kèm `note` lý do từ chối.
- `availableUnits`: khoang `Available` cùng cơ sở, cùng loại khách cần; `previouslyRejected = true` nghĩa là
  chọn lại cần `overrideReason`.
- Chỉ đọc, không ghi gì.

## Ví dụ

```bash
TOKEN='<token của fm1@lemar.vn>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/fm/rental-orders/3
# {"message":"Lấy chi tiết đơn thuê thành công","data":{"id":3,"status":"Pending","cancelReason":null,"expiresAt":"2026-11-12T09:32:41.202069Z","unitId":null,"facilityId":1,"customer":{"accountId":6,"name":"Khách mẫu don-3","email":"customer1@lemar.vn","phone":"0900000000"},"requested":{"unitTypeId":1,"unitTypeName":"Small","startDate":"2026-10-20","period":3},"rejectionCount":1,"maxRejectionCount":3,"proposals":[{"id":3,"unitId":2,"unitCode":"A-02","status":"Rejected","note":"Khoang xa cửa ra vào","createdAt":"2026-10-13T09:32:41.202069Z"}],"availableUnits":[{"id":1,"code":"A-01","size":null,"monthlyPrice":500000.00,"previouslyRejected":false},{"id":2,"code":"A-02","size":null,"monthlyPrice":500000.00,"previouslyRejected":true},{"id":3,"code":"A-03","size":null,"monthlyPrice":500000.00,"previouslyRejected":false},{"id":4,"code":"A-04","size":null,"monthlyPrice":500000.00,"previouslyRejected":false}]}}

# fm2@lemar.vn (cơ sở TD) gọi đơn của Q7
# {"message":"Bạn không được gán vào cơ sở này","timestamp":"2026-10-15T15:04:26.316557800Z"}
```

## Liên quan

- [rental-order-proposals-create.md](rental-order-proposals-create.md) · [routes.md](../../routes.md) · [index.md](../../index.md)
