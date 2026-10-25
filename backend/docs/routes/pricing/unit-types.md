---
purpose: Danh mục loại khoang một cơ sở cung cấp — public cho form lọc dropdown.
---

# GET /api/unit-types?facilityId={id}

Trả các loại khoang một cơ sở cung cấp (bảng `facility_unit_type`, migration V4).

- **Actor / quyền:** public (`permitAll` đúng scope `GET /api/unit-types` trong
  `SecurityConfig`).
- **Contract chi tiết:** annotation tại `pricing/controller/UnitTypeController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Query: `facilityId` bắt buộc. Trả envelope `{message, data: [{id, name, width, depth,
  height, area, monthlyPrice}]}` sắp xếp theo `id`.
- Cơ sở không tồn tại → danh sách rỗng (không 404 — endpoint chỉ phục vụ dropdown).

## Ghi chú nghiệp vụ

- Spec: deviate `db-table-draft.md:130` được chấp nhận. Seed V4: Q7 đủ 3 loại,
  TD chỉ Small + Medium.
- `POST /api/rental-requests` dùng cùng bảng để chặn tổ hợp sai → 400.

## Ví dụ

```bash
# 200 — Q7 cung cấp đủ 3 loại
curl -s "http://localhost:8080/api/unit-types?facilityId=1"
# {"message":"Lấy danh sách loại khoang thành công","data":[{"id":1,"name":"Small","width":1.00,"depth":2.00,"height":2.20,"area":2.00,"monthlyPrice":500000.00},{"id":2,"name":"Medium","width":2.00,"depth":2.50,"height":2.20,"area":5.00,"monthlyPrice":900000.00},{"id":3,"name":"Large","width":2.50,"depth":3.00,"height":2.50,"area":7.50,"monthlyPrice":1400000.00}]}

# 200 — TD chỉ Small + Medium (thiếu Large)
curl -s "http://localhost:8080/api/unit-types?facilityId=2"
# {"message":"Lấy danh sách loại khoang thành công","data":[{"id":1,"name":"Small","width":1.00,"depth":2.00,"height":2.20,"area":2.00,"monthlyPrice":500000.00},{"id":2,"name":"Medium","width":2.00,"depth":2.50,"height":2.20,"area":5.00,"monthlyPrice":900000.00}]}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
