---
purpose: Danh mục loại khoang một cơ sở cung cấp (B1) — public cho form lọc dropdown.
---

# GET /api/unit-types?facilityId={id}

Trả các loại khoang một cơ sở cung cấp (bảng `facility_unit_type`, migration V4).

- **Actor / quyền:** public (`permitAll` đúng scope `GET /api/unit-types` trong
  `SecurityConfig` — #126).
- **Contract chi tiết:** annotation tại `pricing/controller/UnitTypeController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Query: `facilityId` bắt buộc. Trả envelope `{message, data: [{id, name, width, depth,
  height, area, monthlyPrice}]}` sắp xếp theo `id`.
- Cơ sở không tồn tại → danh sách rỗng (không 404 — endpoint chỉ phục vụ dropdown).

## Ghi chú nghiệp vụ

- Spec: #126 (deviate `db-table-draft.md:130` được chấp nhận). Seed V4: Q7 đủ 3 loại,
  TD chỉ Small + Medium.
- `POST /api/rental-requests` dùng cùng bảng để chặn tổ hợp sai → 400.

## Ví dụ

- Chưa chạy thật trên app (Docker/Postgres chưa có ở máy dev) — sẽ bổ sung output curl thật
  khi chạy E2E.

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
