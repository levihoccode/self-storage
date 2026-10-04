# GET /api/auth/me

Xác minh session hiện tại là ai — FE dùng sau login để biết email + role.

- **Actor / quyền:** bearer token + authority `ACCOUNT_ACTIVE`.
- **Contract chi tiết:** annotation tại `identity/controller/AuthController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Route nằm dưới `/api/auth/**` nhưng được match **trước** rule `permitAll` trong `SecurityConfig`,
  nên vẫn yêu cầu token và status `ACTIVE`.
- Role đọc từ DB **mỗi request**: admin đổi role hoặc khóa account thì request kế tiếp thấy hiệu lực ngay.
- 401/403 trả body `{message, timestamp}`; 401 kèm header `WWW-Authenticate` — xem
  [authentication.md › Tín hiệu 401 cho client](../../authentication.md#tín-hiệu-401-cho-client).

## Ghi chú

- Đây là endpoint chính thức để FE lấy role, thay cho field `role` đã bỏ khỏi response login.

## Ví dụ

```bash
TOKEN='<token từ POST /api/auth/login>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/me
# {"message":"Lấy thông tin tài khoản thành công","data":{"email":"customer1@lemar.vn","role":"CUSTOMER"}}

curl -s http://localhost:8080/api/auth/me
# {"message":"Full authentication is required to access this resource","timestamp":"2026-10-04T08:14:32.831012049Z"}

curl -s -i -H 'Authorization: Bearer abc.def.ghi' http://localhost:8080/api/auth/me | grep -iE 'WWW-Authenticate|^\{'
# WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
# {"message":"An error occurred while attempting to decode the Jwt: Malformed token","timestamp":"2026-10-04T08:14:32.840856214Z"}
```
