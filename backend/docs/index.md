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

Hiện tại `identity` xử lý xác thực/RBAC; `facility` cung cấp public API kiểm tra facility manager.

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
| RBAC + facility scope | `identity/application/Access.java`, `identity/application/CurrentAccountProvider.java` |
| Kiểm tra FM phụ trách facility | `facility/application/FacilityAccess.java` |
| Khai báo route policy | `SecurityConfig.java` |
| Envelope + lỗi chung | `ApiEnvelope.java`, `ApiError.java`, `ApiExceptionHandler.java`, `JsonAuthenticationEntryPoint.java`, `JsonAccessDeniedHandler.java` |

## Xác thực & phân quyền

Mô hình stateless JWT: access token chỉ mang claim `email`; role + status được đọc lại từ DB
mỗi request. Chi tiết:

- [authentication.md](authentication.md) — danh tính, token, luồng xác thực mỗi request, giới hạn.
- [authorization.md](authorization.md) — authorities, policy route, 401 vs 403, điều kiện xác minh email.

Policy bổ sung ở method cho endpoint demo A3b:

| Route | Yêu cầu |
|---|---|
| `GET /api/facility-access/ping/{facilityId}` | `ACCOUNT_ACTIVE`; `Access` chỉ cho ADMIN/BOM/FM/FS và kiểm tra facility scope trước khi trả pong. |

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

Seed dev có 8 account `@lemar.vn`: 6 account phủ đủ 5 role (FM có 2) + 2 ca đặc biệt `unverified`
(chưa xác minh, vẫn login được) và `banned` (403), mật khẩu `Test@1234`.
Chi tiết: `backend/sql/README.md`.

> ⚠️ Sửa migration đã áp dụng → DB dev cũ lệch checksum Flyway, phải `docker compose down -v`
> rồi `docker compose up -d` trước khi chạy lại.

## Chạy & kiểm thử

```bash
docker compose up -d                  # Postgres + Redis + MailHog
cd backend && ./mvnw spring-boot:run  # chạy app

./mvnw -B verify                      # checkstyle + test + module boundary + coverage ≥80%
```

> Lưu ý: `MigrationTest` và `SchemaValidationTest` tự skip khi máy không có Docker/Testcontainers,
> nên `verify` local có thể xanh mà chưa validate Flyway/schema — CI mới chạy đủ hai test này.

Coverage: `verify` chạy **cổng LINE và BRANCH ≥ 80%** toàn codebase (JaCoCo check) — dưới ngưỡng là đỏ.
CI chặn thêm **dòng thay đổi ≥ 80%** (diff-cover) và lưu artifact `backend-coverage`
(HTML + XML + `diff-cover.html`). Báo cáo local: `backend/target/site/jacoco/index.html`.

Swagger UI: `http://localhost:8080/swagger-ui/index.html` — đã khai báo bearer scheme;
bấm **Authorize** và dán token (không cần prefix `Bearer `) để gọi API cần đăng nhập.

## Quy ước data contract

- Base URL: `/api`; body JSON, `Content-Type: application/json`
- API cần đăng nhập: `Authorization: Bearer <JWT>`
- **Mọi response 2xx** — envelope `{message, data}`; `message` là câu tiếng Việt để FE hiển thị
  trực tiếp, không hardcode phía FE:

```json
{ "message": "Đăng nhập thành công", "data": { "accountId": 6, "email": "customer1@lemar.vn", "token": "eyJ..." } }
```

- **Mọi lỗi 4xx/5xx** — `{message, timestamp}`; message set trong exception/handler tương ứng:

```json
{ "message": "Email hoặc mật khẩu không đúng", "timestamp": "2026-10-04T08:14:27.947381536Z" }
```

- 400 validate/JSON hỏng dùng message chung `"Dữ liệu không hợp lệ"`; 401/403 tầng security cũng
  trả đúng shape trên (401 giữ header `WWW-Authenticate`, không còn body rỗng).
- Lỗi không lường trước (bug) → 500 `{"message":"Internal server error","timestamp":"..."}` —
  chi tiết exception chỉ vào log, không lộ ra response.
- Lỗi khung (404 sai path, 405 sai method, 415 sai content-type…) giữ đúng status, message tiếng
  Việt tương ứng. Đây là các status **nền, áp dụng cho mọi route** — Swagger từng route không lặp lại.
- Message cố định tiếng Việt phía BE; i18n (nếu cần) xử lý phía FE sau.
- Chi tiết các tầng bắt lỗi và bảng status: [error-handling.md](error-handling.md).

## Mục lục

| Tài liệu | Nội dung |
|---|---|
| [authentication.md](authentication.md) | Mô hình xác thực: token, luồng request, giới hạn |
| [authorization.md](authorization.md) | Mô hình phân quyền: authorities, policy route, điều kiện xác minh |
| [error-handling.md](error-handling.md) | Xử lý lỗi: envelope, các tầng bắt lỗi, bảng status nền |
| [routes.md](routes.md) | Index toàn bộ route hiện có |
| [routes/health.md](routes/health.md) | `GET /api/health` |
| [routes/auth/login.md](routes/auth/login.md) | `POST /api/auth/login` |
| [routes/auth/me.md](routes/auth/me.md) | `GET /api/auth/me` |
