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

- Chưa chạy thật trên app (Docker/Postgres chưa có ở máy dev) — sẽ bổ sung output curl thật
  khi chạy E2E.

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
