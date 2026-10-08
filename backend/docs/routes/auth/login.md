---
purpose: Đăng nhập bằng email + mật khẩu, trả access token (JWT).
---
# POST /api/auth/login

Đăng nhập bằng email + mật khẩu, trả access token (JWT).

- **Actor / quyền:** public — không cần token.
- **Contract chi tiết:** annotation (`@Operation`/`@ApiResponses`/`@ExampleObject`) tại
  `identity/controller/AuthController.java`; Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng xử lý

`AuthService.login`:

1. Chuẩn hoá email (`trim` + lowercase — `Account.normalize`).
2. `findByEmail`; không thấy → 401.
3. `passwordEncoder.matches` (BCrypt); sai → 401 **cùng message** (không lộ tài khoản nào tồn tại).
4. `account.isLoginAllowed()` (status `ACTIVE`); `BANNED` → 403 "Tài khoản đã bị chặn".
5. `JwtService.issueAccessToken` → envelope `{message: "Đăng nhập thành công", data: {accountId, email, token}}`.

## Ghi chú

- Mật khẩu lưu bằng BCrypt; cost cấu hình qua `BCRYPT_STRENGTH` (mặc định 10).
- Token chỉ chứa claim `email`; role/status đọc từ DB ở mỗi request — xem
  [authentication.md › Xác thực mỗi request](../../authentication.md#xác-thực-mỗi-request).
- FE lấy role qua [GET /api/auth/me](me.md) — role **không** có trong response login.
- Account seed để test: `customer1@lemar.vn` / `Test@1234`; `unverified@lemar.vn` (login được
  nhưng chưa xác minh email) và `banned@lemar.vn` (403).

## Ví dụ

```bash
# 200 — account seed
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@lemar.vn","password":"Test@1234"}'
# {"message":"Đăng nhập thành công","data":{"accountId":6,"email":"customer1@lemar.vn","token":"eyJhbGciOiJIUzI1NiJ9..."}}

# 401 — sai mật khẩu
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@lemar.vn","password":"sai-mat-khau"}'
# {"message":"Email hoặc mật khẩu không đúng","timestamp":"2026-10-04T08:14:27.947381536Z"}

# 403 — account bị chặn (account seed: banned@lemar.vn)
# {"message":"Tài khoản đã bị chặn","timestamp":"2026-10-04T08:14:28.027349693Z"}

# 400 — email sai định dạng
# {"message":"Dữ liệu không hợp lệ","timestamp":"2026-10-04T08:14:28.048550698Z"}
```
