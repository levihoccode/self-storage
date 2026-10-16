---
purpose: Hàng đợi yêu cầu đặt kho cho FM (B1) — chỉ thấy cơ sở của mình.
---

# GET /api/fm/rental-requests

Hàng đợi `RentalRequest` cho FM xử lý: lọc theo facility + status, phân trang, mới nhất trước.

- **Actor / quyền:** role-bound `FM` (`/api/fm/**` trong `SecurityConfig`) + permission-bound
  `rental_request.read` theo facility qua `Access.can(permission, facilityId)`.
- **Contract chi tiết:** annotation tại `booking/controller/FmRentalRequestController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Query: `facilityId` bắt buộc; `status` tùy chọn (`Pending/Rejected/Approved/Converted/Expired`);
  `page` mặc định 0, `size` mặc định 20 tối đa 100; sắp xếp `createdAt` giảm dần.
- Guard: thiếu permission → 403 "Bạn không có quyền thực hiện thao tác này"; ngoài facility
  → 403 "Bạn không được gán vào cơ sở này"; facility không tồn tại → 404.
- Trả envelope `{message, data: {items, page, size, total}}`; từng entry không có field facility
  (spec draft.md NOTES §1.1).

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md §1.1`; permission `rental_request.read` (FACILITY) đã seed V3.

## Ví dụ

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"fm1@lemar.vn","password":"Test@1234"}' | jq -r .data.token)

# 200 — hàng đợi Q7, chỉ Pending, size=2
curl -s "http://localhost:8080/api/fm/rental-requests?facilityId=1&status=Pending&size=2" \
  -H "Authorization: Bearer $TOKEN"
# {"message":"Lấy danh sách yêu cầu đặt kho thành công","data":{"items":[{"id":12,"customerName":"Nguyen Van A","customerEmail":"khach.moi@example.com","customerPhone":"0901234567","unitTypeId":1,"startDate":"2026-12-01","period":6,"status":"Pending","createdAt":"2026-10-16T14:51:43.262953Z","expiresAt":"2026-10-23T14:51:43.260979Z"},{"id":10,"customerName":"Exp Block","customerEmail":"expblock@example.com","customerPhone":"0901234567","unitTypeId":1,"startDate":"2026-12-01","period":1,"status":"Pending","createdAt":"2026-10-16T14:16:41.533184Z","expiresAt":"2026-10-15T14:16:41.621670Z"}],"page":0,"size":2,"total":5}}

# 403 — FM Q7 hỏi hàng đợi của cơ sở TD
curl -s "http://localhost:8080/api/fm/rental-requests?facilityId=2" -H "Authorization: Bearer $TOKEN"
# {"message":"Bạn không được gán vào cơ sở này","timestamp":"2026-10-16T14:51:43.435261665Z"}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
