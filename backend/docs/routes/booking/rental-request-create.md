---
purpose: Form public gửi yêu cầu đặt kho (B1) — không cần đăng nhập.
---

# POST /api/rental-requests

Form public cho khách điền nhu cầu thuê kho mà không cần chỉ định khoang cụ thể.

- **Actor / quyền:** public (`permitAll` đúng scope `POST /api/rental-requests` trong
  `SecurityConfig`, đặt trước rule vét `/api/**` — #126).
- **Contract chi tiết:** annotation tại `booking/controller/RentalRequestController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Validate Bean Validation: `facilityId`/`unitTypeId`/`startDate`/`period` bắt buộc; `customerName`
  non-blank; `customerEmail` email hợp lệ; `customerPhone` theo pattern hỗ trợ; `period >= 1`.
- Service: `facility` phải tồn tại (404 "Không tìm thấy cơ sở"); `unit_type` phải tồn tại
  ("Loại khoang không tồn tại" — 400) + được cơ sở cung cấp (bảng `facility_unit_type`, V4 —
  "Loại khoang không được cung cấp tại cơ sở này" — 400); `start_date >= hôm nay
  theo Asia/Ho_Chi_Minh` (400); phone 7-15 digit (400). Email normalize trim + lowercase
  trước khi lưu. Dropdown loại khoang lấy từ `GET /api/unit-types?facilityId=`.
- Tạo `RentalRequest(status=Pending)`, `expires_at = created_at + request.pending_expiry_days`
  (đọc từ bảng `policies`, fallback 7 ngày). Trả 201 + envelope `{message, data}`.
- NOTE(B1): chưa ghi AuditLog cho bản ghi mới — A8 chưa có code, sẽ bổ sung khi A8 xong.

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md §1.1`; schema `db-table-draft.md › RentalRequest`; #126 (ràng buộc
  facility–unit_type, deviate db-table-draft.md:130 được chấp nhận).
- Chưa có rate-limit/captcha chống spam (Advanced Features, không phải MVP).

## Ví dụ

```bash
# 201 — gửi yêu cầu (form public)
curl -s -X POST http://localhost:8080/api/rental-requests -H 'Content-Type: application/json' \
  -d '{"facilityId":1,"customerName":"Nguyen Van A","customerEmail":"khach.moi@example.com","customerPhone":"0901234567","unitTypeId":1,"startDate":"2026-12-01","period":6}'
# {"message":"Đã gửi yêu cầu đặt kho","data":{"id":12,"status":"Pending","startDate":"2026-12-01","expiresAt":"2026-10-23T14:51:43.260979171Z"}}

# 400 — unit_type không được cơ sở cung cấp (TD thiếu Large)
curl -s -X POST http://localhost:8080/api/rental-requests -H 'Content-Type: application/json' \
  -d '{"facilityId":2,"customerName":"Nguyen Van A","customerEmail":"khach.moi2@example.com","customerPhone":"0901234567","unitTypeId":3,"startDate":"2026-12-01","period":6}'
# {"message":"Loại khoang không được cung cấp tại cơ sở này","timestamp":"2026-10-16T14:51:43.279826788Z"}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
