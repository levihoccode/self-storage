---
purpose: Mô hình xác thực — JWT, vòng đời token, resolve account mỗi request.
---
# Authentication Model

Mô hình xác thực của backend: stateless JWT + đọc DB mỗi request.
Tài liệu này trả lời **"bạn là ai"** (authentication); phần **"bạn được làm gì"** (authorization)
nằm ở [authorization.md](authorization.md).

## Tổng quan

- Access token: JWT **HS256**, ký bằng `JWT_SECRET`.
- Token **chỉ mang claim `email`** — không có `role`, không có `sub`.
- Không lưu session phía server; mỗi request tự xác thực lại.
- Role + status đọc từ DB mỗi request (qua converter) → khóa tài khoản / đổi role có hiệu lực ngay.
- Mọi response theo envelope chung — xem [index.md › Quy ước data contract](index.md#quy-ước-data-contract).

## Token

Payload thật của token phát bởi `POST /api/auth/login`:

```json
{
  "iss": "self-storage",
  "iat": 1791012361,
  "exp": 1791013261,
  "email": "customer1@lemar.vn"
}
```

- `iss` cố định `self-storage` — bắt buộc, validate khi decode (`JwtIssuerValidator`).
- `exp` = `iat` + `security.jwt.access-token-ttl` (mặc định `PT15M` = 900 giây) — bắt buộc;
  kiểm hạn khi decode (`JwtTimestampValidator`, cho phép lệch đồng hồ 60 giây).
- `email` — bắt buộc, dùng để tra account (`AccountJwtAuthenticationConverter`).
- `iat` — metadata, không bắt buộc (không nơi nào đọc giá trị).
- Token thiếu `exp` hoặc `email` bị từ chối ngay khi decode (`RequiredJwtClaimsValidator` → 401) —
  mọi producer phải phát token đủ claim, một shape duy nhất.
- Không có claim phân quyền — có chủ ý, xem "Vì sao token không mang role".

## Token được phát thế nào

Việc kiểm mật khẩu/status và phát token là hành vi của endpoint — luồng xử lý chi tiết và
contract nằm ở [routes/auth/login.md](routes/auth/login.md).

## Xác thực mỗi request

```text
Authorization: Bearer <JWT>
  │
  ▼ BearerTokenAuthenticationFilter (OAuth2 Resource Server)
  │    JwtDecoder verify: chữ ký HS256 + iss + claim bắt buộc (exp, email),
  │    và exp còn hạn                                      (JwtConfiguration)
  ▼
Jwt (claims: email, iss, exp…)
  │
  ▼ AccountJwtAuthenticationConverter.convert(jwt)        (đọc DB mỗi request)
  │    email claim rỗng                     → InvalidBearerTokenException (401)
  │    findWithRoleByEmail(email)
  │      không tồn tại                      → 401
  │    isLoginAllowed == true:
  │      authorities = [ACCOUNT_ACTIVE, ROLE_<role>]
  │    isLoginAllowed == false:
  │      authorities = []                   → route yêu cầu quyền đều 403
  ▼
JwtAuthenticationToken → SecurityContext
  │
  ▼ authorizeHttpRequests đối chiếu rule (policy khai báo trong `SecurityConfig`;
       quyền từng route xem [routes.md](routes.md))
```

Principal name = email của account trong DB. Snippet rút gọn:

```java
// AccountJwtAuthenticationConverter.java
String email = jwt.getClaimAsString("email");
Account account = accountRepository.findWithRoleByEmail(Account.normalize(email))
        .orElseThrow(() -> new InvalidBearerTokenException("Account for JWT was not found"));

List<GrantedAuthority> authorities = new ArrayList<>();
if (account.isLoginAllowed()) {
    authorities.add(new SimpleGrantedAuthority("ACCOUNT_ACTIVE"));
    authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getRole().getName()));
}
return new JwtAuthenticationToken(jwt, authorities, account.getEmail());
```

## Trạng thái account

- `status` (`AccountStatus`): `ACTIVE` (mặc định khi tạo) | `BANNED` — chỉ dùng để chặn truy cập.
- `email_verified_at = null` (chưa xác minh) **không chặn login**; chỉ chặn các thao tác nghiệp vụ
  yêu cầu xác minh — xem
  [authorization.md › Điều kiện nghiệp vụ](authorization.md#điều-kiện-nghiệp-vụ-xác-minh-email).

## Đăng ký và xác minh email

Đăng ký → email mã 6 số → xác minh bằng mã (spec-gap #139):

- `POST /api/auth/register`: tạo `Account` role `CUSTOMER`, `email_verified_at = null`, ghi audit
  `ACCOUNT_REGISTERED` (actor = chính account vừa tạo), gửi email mã xác minh.
- `POST /api/auth/verify-email`: mã đúng → set `email_verified_at`; account đã xác minh trước đó →
  200 với message `Email đã được xác minh trước đó` (no-op, không kiểm mã — thao tác lặp của user
  không thành lỗi). Xác minh email **không** ghi `AuditLog` (spec-gap #75 — bản ghi vòng đời là
  `email_verified_at`).
- Mã: 6 số, TTL 10 phút, tối đa 5 lần nhập sai (429), gửi lại tối đa mỗi 60 giây; lưu Redis dạng
  BCRYPT hash (`auth:verify-code:<email>` — không plaintext), dùng một lần. Redis lỗi thì
  register/verify/resend trả lỗi (không fail-open).
- Mã gắn theo email: mã của account này **không** verify được account khác (tra key theo email đích).
  Mặt trái: đoán sai 5 lần khoá mã của account — kẻ xấu biết email có thể cố tình khoá để buộc nạn
  nhân gửi lại mã (DoS nhẹ, khôi phục bằng resend; verify chưa có rate limit theo IP — bổ sung khi cần).
- Gửi email đi qua event `VerificationEmailRequested` (identity không phụ thuộc module
  `notification` — cycle); listener `VerificationEmailListener` gửi after-commit như mọi email.
- Route chi tiết: [register](routes/auth/register.md) · [verify-email](routes/auth/verify-email.md)
  · [resend-verification](routes/auth/resend-verification.md).

## Chống brute-force đăng nhập

Sai 5 lần/15 phút theo email hoặc 20 lần/15 phút theo IP → **429** (spec-gap #140):

- Đếm trên Redis (`auth:login-fail:email:<email>`, `auth:login-fail:ip:<ip>`), fixed window 15 phút
  (không sliding — chấp nhận cho MVP); đăng nhập thành công xoá bộ đếm của email.
- Sai email không tồn tại cũng tính là một lần sai; account `BANNED` (403) không tính.
- **Fail-open**: Redis lỗi thì chỉ log và cho đăng nhập đi tiếp — đổi lấy availability, chấp nhận
  tạm mất lớp chống brute-force. Ngưỡng cấu hình qua `LOGIN_RATE_LIMIT_*` (bảng dưới).
- IP lấy từ `HttpServletRequest.getRemoteAddr()` — chạy sau reverse proxy (ngrok/cloudflared) sẽ
  thấy IP của proxy; **chưa** đọc `X-Forwarded-For` (tránh giả mạo khi chưa cấu hình trusted proxy).
  Cần xử lý khi triển khai thật sau proxy.

## Vì sao token không mang role

Token mang role thì nhanh hơn (không cần query DB mỗi request), nhưng khi admin khóa tài khoản
hoặc đổi role, token cũ vẫn dùng được tới lúc hết hạn. Mô hình này chọn ngược lại: token nói
"tôi là account có email X", còn **quyền hiện tại của X do DB trả lời ở mỗi request**.
Đánh đổi: thêm 1 query mỗi request, bù lại thu hồi quyền tức thời.

## 401 vs 403

401 là lỗi xác thực, 403 là lỗi phân quyền — bảng đối chiếu, policy route và điều kiện nghiệp vụ:
[authorization.md](authorization.md).

## Tín hiệu 401 cho client

Token sai / hết hạn / thiếu claim bắt buộc → 401 body chung `{message, timestamp}` **và** header
`WWW-Authenticate` (RFC 6750, không kèm `error_description`) — output thật:

```text
$ curl -s -i -H 'Authorization: Bearer abc.def.ghi' http://localhost:8080/api/auth/me
WWW-Authenticate: Bearer error="invalid_token"
{"message":"Bạn cần đăng nhập để tiếp tục.","timestamp":"2026-10-05T06:20:49.329273914Z"}
```

Chi tiết lỗi token chỉ ghi log server (debug), không trả cho client; thiếu token hoàn toàn thì
header là `Bearer` trần.

Client xử lý theo `status = 401` (không parse message để làm logic); `message` để hiển thị/log;
refresh token (#74) sẽ bám vào tín hiệu này. Toàn bộ các tầng bắt lỗi:
[error-handling.md](error-handling.md).

## Cấu hình liên quan

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `JWT_SECRET` | Khóa ký JWT, Base64, ≥ 32 byte sau decode | — (bắt buộc) |
| `JWT_ACCESS_TOKEN_TTL` | TTL access token (ISO-8601 duration) | `PT15M` |
| `BCRYPT_STRENGTH` | Bcrypt cost (log2 rounds) | `10` |
| `EMAIL_VERIFICATION_TTL` | TTL mã xác minh email (ISO-8601 duration) | `PT10M` |
| `EMAIL_VERIFICATION_MAX_ATTEMPTS` | Số lần nhập sai tối đa cho mỗi mã | `5` |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN` | Cooldown giữa hai lần gửi lại mã | `PT60S` |
| `LOGIN_RATE_LIMIT_EMAIL_MAX` | Số lần đăng nhập sai tối đa theo email / `window` | `5` |
| `LOGIN_RATE_LIMIT_IP_MAX` | Số lần đăng nhập sai tối đa theo IP / `window` | `20` |
| `LOGIN_RATE_LIMIT_WINDOW` | Cửa sổ đếm lần đăng nhập sai | `PT15M` |

## Mô hình này có gì / chưa có

Có:

- Thu hồi quyền tức thời qua status/role trong DB.
- Không lưu session phía server (stateless).
- Swagger khai báo bearer scheme để thử API.

Chưa có (theo dõi ở issue #74):

- Refresh token + rotation → hết hạn 15 phút phải login lại.
- Logout endpoint (revoke) — access token không thu hồi được trước hạn, nhưng khóa account
  thì có hiệu lực ngay.
- `kid` / key rotation — xoay `JWT_SECRET` làm toàn bộ token cũ hết hiệu lực.

## Test

- `JwtServiceTest` — claims email-only, issuer, expiry.
- `SecurityConfigTest` — route gate theo role/`ACCOUNT_ACTIVE`, `/me` 401/200/403, account bị xóa.
- `AuthServiceTest` — login 401/403, chuẩn hoá email, `/me`, rate limit (429/đếm/reset).
- `RegistrationServiceTest` — đăng ký (409, audit, phát mã), verify (idempotent), resend (cooldown).
- `EmailVerificationCodesTest`, `LoginAttemptLimiterTest` — kho mã Redis + bộ đếm rate limit (unit).
- `AuthControllerTest` — contract HTTP register/verify/resend (400/409/429), Swagger examples.
- `RegistrationIntegrationTest` — end-to-end Postgres + Redis: register → verify → login, khoá sau
  5 lần sai, cooldown resend, audit `ACCOUNT_REGISTERED`.
- `ExpiredTokenTest` — token hết hạn → 401 + header `WWW-Authenticate`.
- `RequiredClaimsTest` — token thiếu `exp` / `email` → 401.
