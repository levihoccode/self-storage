# Tài liệu Backend — Self Storage

Tài liệu này mô tả backend đang có gì và nó hoạt động thế nào, ở mức **medium-level**:
giải thích high-level, kèm snippet code minh họa khi cần. Data contract đầy đủ của từng
endpoint nằm trong thư mục [`routes/`](routes.md) — file này chỉ ghi quy ước chung.

- Ngôn ngữ: tiếng Việt, thuật ngữ kỹ thuật giữ English.
- Phạm vi: những gì **đang có trong code**, không mô tả kế hoạch.

## Backend có gì

Spring Boot 3.3.5 · Java 17 · Spring Modulith · PostgreSQL 16 · Redis · MailHog (mail dev).

Kiến trúc **modular monolith** theo issue #15: `vn.lemar.selfstorage` là package gốc,
mỗi package con trực tiếp là một module độc lập (`identity`, `booking`, `facility`, ...).
`ModuleStructureTest` chặn việc gọi sai chiều giữa các module ngay lúc build.

Hiện tại chỉ module `identity` có code chạy; các module khác mới là khung.

Layout trong một module:

```text
<module>/
  domain/       entity + invariant
  repository/   truy cập dữ liệu
  application/  service + transaction
  controller/   HTTP API
```

Code map (identity):

| Thành phần | File |
|---|---|
| Login + `/me` | `identity/application/AuthService.java`, `identity/controller/AuthController.java` |
| Phát JWT | `identity/application/JwtService.java` |
| Verify + cấp quyền theo DB | `identity/application/AccountJwtAuthenticationConverter.java`, `identity/config/JwtConfiguration.java` |
| Khai báo route policy | `SecurityConfig.java` |

## Xác thực & phân quyền

Mô hình stateless JWT: access token chỉ mang claim `email`; role + status được đọc lại từ DB
mỗi request. Luồng đăng nhập, cấu trúc token, 401 vs 403 và các giới hạn hiện tại:
[authentication.md](authentication.md).

### Policy route (SecurityConfig)

| Route | Yêu cầu |
|---|---|
| `/api/health`, `/api/auth/**` | public — **trừ** `/api/auth/me` |
| `/api/auth/me` | Bearer token + `ACCOUNT_ACTIVE` (match trước rule public) |
| `/api/customer/**` | `ROLE_CUSTOMER` |
| `/api/staff/**` | `ROLE_FS` |
| `/api/fm/**` | `ROLE_FM` |
| `/api/bom/**` | `ROLE_BOM` |
| `/api/admin/**` | `ROLE_ADMIN` |
| `/api/**` (còn lại) | `ACCOUNT_ACTIVE` |

**401 vs 403**: chưa xác thực được (thiếu/sai/hết hạn token, account đã bị xóa) → `401`;
đã xác thực nhưng thiếu quyền (sai role, status không ACTIVE) → `403`.

## Cấu hình

Nguồn: `backend/src/main/resources/application.yml`, override qua biến môi trường:

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `JWT_SECRET` | Khóa ký JWT, Base64, ≥ 32 byte sau decode | — (bắt buộc) |
| `JWT_ACCESS_TOKEN_TTL` | TTL access token (ISO-8601 duration) | `PT15M` |
| `BCRYPT_STRENGTH` | Bcrypt cost (log2 rounds), OWASP khuyến nghị ≥ 10 | `10` |

Chạy local không cần export tay: `application.yml` có
`spring.config.import: optional:file:../.env[.properties]` → copy `.env.example` thành `.env`.
Đường dẫn `../.env` tính theo working directory (đúng khi chạy từ `backend/`).

## Database & seed

Flyway chạy migration lúc khởi động: `V1__baseline_schema.sql`, `V2__baseline_seed.sql`.
Bản đọc được mirror ở `backend/sql/` — sửa SQL thì sửa cả hai nơi (CI check đồng bộ).

Seed dev có 8 account `@lemar.vn` (đủ 5 role + 2 ca đặc biệt), mật khẩu `Test@1234`.
Chi tiết: `backend/sql/README.md`.

> ⚠️ Sửa migration đã áp dụng → DB dev cũ lệch checksum Flyway, phải `docker compose down -v`
> rồi `docker compose up -d` trước khi chạy lại.

## Chạy & kiểm thử

```bash
docker compose up -d                  # Postgres + Redis + MailHog
cd backend && ./mvnw spring-boot:run  # chạy app

./mvnw -B verify                      # checkstyle + test + module boundary
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html` — đã khai báo bearer scheme;
bấm **Authorize** và dán token (không cần prefix `Bearer `) để gọi API cần đăng nhập.

## Quy ước data contract

- Base URL: `/api`; body JSON, `Content-Type: application/json`
- API cần đăng nhập: `Authorization: Bearer <JWT>`
- Lỗi nghiệp vụ auth (401/403 từ `AuthExceptionHandler`):

```json
{ "message": "Email hoặc mật khẩu không đúng", "timestamp": "2026-10-03T07:26:01.508529762Z" }
```

- Lỗi validate request (400) dùng format mặc định của Spring Boot:

```json
{ "timestamp": "2026-10-03T07:26:01.620+00:00", "status": 400, "error": "Bad Request", "path": "/api/auth/login" }
```

- 401 do thiếu token có thể **không có body** (resource server trả trước khi vào controller).

## Mục lục

| Tài liệu | Nội dung |
|---|---|
| [authentication.md](authentication.md) | Mô hình xác thực: token, luồng request, giới hạn |
| [routes.md](routes.md) | Index toàn bộ route hiện có |
| [routes/health.md](routes/health.md) | `GET /api/health` |
| [routes/auth/login.md](routes/auth/login.md) | `POST /api/auth/login` |
| [routes/auth/me.md](routes/auth/me.md) | `GET /api/auth/me` |
