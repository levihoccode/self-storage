# GET /api/facility-access/ping/{facilityId}

Endpoint demo để kiểm tra RBAC và facility scope; không thực hiện nghiệp vụ lưu trữ.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`; `Access` chỉ cho phép ADMIN, BOM, FM hoặc FS và kiểm tra facility scope tương ứng.
- **Contract chi tiết:** annotation tại `identity/controller/PingFacilityController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Security yêu cầu request đã xác thực và account đang hoạt động.
- `Access.can()` kiểm tra role; `Access.canAccessFacility()` kiểm tra quyền với `facilityId`.
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
