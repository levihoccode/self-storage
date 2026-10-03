# POST /api/auth/login

Đăng nhập bằng email + mật khẩu, trả về access token (JWT).

> Chạy backend rồi mở Swagger UI — `http://localhost:8080/swagger-ui/index.html` — để gọi thử
> trực tiếp.

## Request

`Content-Type: application/json`

| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `email` | string | ✔ | `@NotBlank` + `@Email`; chuẩn hoá `trim` + lowercase trước khi so |
| `password` | string | ✔ | `@NotBlank` |

```json
{ "email": "customer1@lemar.vn", "password": "Test@1234" }
```

## Response 200

| Field | Kiểu | Ghi chú |
|---|---|---|
| `accountId` | number | ID account |
| `email` | string | email đã chuẩn hoá |
| `token` | string | JWT HS256, TTL mặc định 15 phút |

```json
{
  "accountId": 6,
  "email": "customer1@lemar.vn",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

Token chỉ chứa claim `email`; role/status được đọc từ DB ở mỗi request tiếp theo
(xem [authentication.md › Xác thực mỗi request](../../authentication.md#xác-thực-mỗi-request)).
FE lấy role qua [GET /api/auth/me](me.md) — role **không** còn trong response login.

## Lỗi

| Status | Điều kiện | Body |
|---|---|---|
| 400 | Thiếu field / email sai định dạng (`MethodArgumentNotValidException`) | Format lỗi mặc định của Spring Boot |
| 401 | Email không tồn tại **hoặc** sai mật khẩu — cùng một message để không lộ tài khoản nào tồn tại | `{"message":"Email hoặc mật khẩu không đúng","timestamp":"..."}` |
| 403 | Status không cho login | Message theo status: `UNVERIFIED` → `"Tài khoản chưa xác thực email"` · `LOCKED` → `"Tài khoản đang bị khóa"` · `BANNED` → `"Tài khoản đã bị cấm"` |

## Ví dụ

```bash
# 200 — account seed
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@lemar.vn","password":"Test@1234"}'
# {"accountId":6,"email":"customer1@lemar.vn","token":"eyJ..."}

# 401 — sai mật khẩu
# {"message":"Email hoặc mật khẩu không đúng","timestamp":"..."}

# 403 — chưa verify email (account seed: unverified@lemar.vn)
# {"message":"Tài khoản chưa xác thực email","timestamp":"..."}

# 400 — email sai định dạng
# {"timestamp":"...","status":400,"error":"Bad Request","path":"/api/auth/login"}
```

## Luồng xử lý

`AuthService.login`:

1. Chuẩn hoá email (`trim` + lowercase — `Account.normalize`).
2. `findByEmail`; không thấy → `InvalidCredentialsException` (401).
3. `passwordEncoder.matches` (BCrypt); sai → 401 **cùng message** (không lộ tài khoản nào tồn tại).
4. `account.isLoginAllowed()` (status `ACTIVE`); không → `AccountNotAllowedException` (403,
   message theo status).
5. `JwtService.issueAccessToken` → response `{accountId, email, token}`.

## Ghi chú

- Mật khẩu lưu bằng BCrypt; cost cấu hình qua `BCRYPT_STRENGTH` (mặc định 10).
- Account seed để test: `customer1@lemar.vn` / `Test@1234`; `unverified@lemar.vn` và
  `locked@lemar.vn` để test nhánh 403.
