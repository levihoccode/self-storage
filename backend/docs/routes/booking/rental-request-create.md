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
  (404 "Không tìm thấy loại khoang") + được cơ sở cung cấp (bảng `facility_unit_type`, V4 —
  sai tổ hợp → 400 "Loại khoang không được cung cấp tại cơ sở này"); `start_date >= hôm nay
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

- Chưa chạy thật trên app (Docker/Postgres chưa có ở máy dev) — sẽ bổ sung output curl thật
  khi chạy E2E. Xem contract trong Swagger ở mục trên.

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
