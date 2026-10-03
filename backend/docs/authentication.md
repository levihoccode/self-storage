# Authentication Model

Mô hình xác thực của backend: stateless JWT + đọc DB mỗi request.
Tài liệu này trả lời **"bạn là ai"** (authentication); phần **"bạn được làm gì"** (authorization)
nằm ở [index.md › Policy route](index.md#policy-route-securityconfig).

## Tổng quan

- Access token: JWT **HS256**, ký bằng `JWT_SECRET`.
- Token **chỉ mang claim `email`** — không có `role`, không có `sub`.
- Không lưu session phía server; mỗi request tự xác thực lại.
- Role + status đọc từ DB mỗi request (qua converter) → khóa tài khoản / đổi role có hiệu lực ngay.

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

- `iss` cố định `self-storage`, được validate khi decode (`JwtValidators.createDefaultWithIssuer`).
- `exp` = `iat` + `security.jwt.access-token-ttl` (mặc định `PT15M` = 900 giây).
- Không có claim phân quyền — có chủ ý, xem "Vì sao token không mang role".

## Token được phát thế nào

Việc kiểm mật khẩu/status và phát token là hành vi của endpoint — luồng xử lý chi tiết và
contract nằm ở [routes/auth/login.md](routes/auth/login.md).

## Xác thực mỗi request

```text
Authorization: Bearer <JWT>
  │
  ▼ BearerTokenAuthenticationFilter (OAuth2 Resource Server)
  │    JwtDecoder verify: chữ ký HS256 + exp + iss        (JwtConfiguration)
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
  ▼ authorizeHttpRequests đối chiếu rule (bảng policy)
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

## Vì sao token không mang role

Token mang role thì nhanh hơn (không cần query DB mỗi request), nhưng khi admin khóa tài khoản
hoặc đổi role, token cũ vẫn dùng được tới lúc hết hạn. Mô hình này chọn ngược lại: token nói
"tôi là account có email X", còn **quyền hiện tại của X do DB trả lời ở mỗi request**.
Đánh đổi: thêm 1 query mỗi request, bù lại thu hồi quyền tức thời.

## 401 vs 403

401 là lỗi xác thực, 403 là lỗi phân quyền — bảng đối chiếu, policy route và điều kiện nghiệp vụ:
[authorization.md](authorization.md).

## Cấu hình liên quan

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `JWT_SECRET` | Khóa ký JWT, Base64, ≥ 32 byte sau decode | — (bắt buộc) |
| `JWT_ACCESS_TOKEN_TTL` | TTL access token (ISO-8601 duration) | `PT15M` |
| `BCRYPT_STRENGTH` | Bcrypt cost (log2 rounds) | `10` |

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
- `AuthServiceTest` — login 401/403, chuẩn hoá email, `/me`.
