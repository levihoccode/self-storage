---
auth-model: permission-bound
purpose: Endpoint demo kiểm tra RBAC + facility scope qua Access.can().
---
# GET /api/facility-access/ping/{facilityId}

Endpoint demo để kiểm tra RBAC và facility scope; không thực hiện nghiệp vụ lưu trữ.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`; `Access.can("facility.access", facilityId)`
  kiểm tra permission + facility scope ([catalog](../../permission-catalog.md)).
- **Contract chi tiết:** annotation tại `identity/controller/PingFacilityController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Security yêu cầu request đã xác thực và account đang hoạt động.
- `Access.can(permission, facilityId)` kiểm tra role có `facility.access` không, rồi kiểm facility
  scope (ADMIN/BOM toàn cục; FM theo `fm_account_id`; FS theo assignment).
- Facility không tồn tại → `404` (kiểm sau bước permission; role không có quyền vẫn `403` nên
  không thấy được facility nào tồn tại).
- Request hợp lệ trả success envelope `{message, data}`, trong đó `message` là `pong` và
  `data.facilityId` là ID cơ sở đã kiểm tra; role không được phép hoặc truy cập chéo cơ sở bị từ chối.

## Ghi chú nghiệp vụ

- Đây là endpoint demo phục vụ kiểm tra quyền A3b, không thay thế policy guard của các API nghiệp vụ.

## Ví dụ

```bash
TOKEN='<JWT từ POST /api/auth/login>'

curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/facility-access/ping/1
```

## Liên quan

- Xem thêm: [routes.md](../../routes.md) · [index.md](../../index.md)
