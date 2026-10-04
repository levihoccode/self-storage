# Error Handling

Cách backend xử lý lỗi và format response lỗi — trạng thái hiện tại (as-built).
Quy ước envelope chung nằm ở [index.md › Quy ước data contract](index.md#quy-ước-data-contract).

## Envelope

- Success (mọi 2xx): `{message, data}` — `ApiEnvelope`; message riêng từng route.
- Error (mọi 4xx/5xx): `{message, timestamp}` — `ApiError`; message tiếng Việt hiển thị trực tiếp,
  timestamp ISO-8601 UTC.

## Các tầng bắt lỗi — theo đường request đi qua

```text
Request
  │
  ▼ Filter chain (Spring Security + filter tương lai)
  │    401/403 → JsonAuthenticationEntryPoint / JsonAccessDeniedHandler
  │    exception khác trong filter → container → /error (chưa theo envelope, xem cuối trang)
  ▼
DispatcherServlet
  │    exception từ controller / service / interceptor
  ▼
@RestControllerAdvice (theo @Order)
  │    AuthExceptionHandler (HIGHEST_PRECEDENCE)
  │      InvalidCredentialsException  → 401 "Email hoặc mật khẩu không đúng"
  │      AccountNotAllowedException   → 403 "Tài khoản đã bị chặn"
  │    ApiExceptionHandler (LOWEST_PRECEDENCE)
  │      validate / JSON hỏng         → 400 "Dữ liệu không hợp lệ"
  │      lỗi khung (ErrorResponse)    → giữ status + message tiếng Việt (404/405/415…)
  │      exception không lường trước   → 500 "Internal server error" (chi tiết chỉ vào log)
```

**Thứ tự advice:** `ExceptionHandlerExceptionResolver` chọn advice **match đầu tiên**, không phải
handler cụ thể nhất. Vì vậy `AuthExceptionHandler` đặt `@Order(HIGHEST_PRECEDENCE)`,
`ApiExceptionHandler` đặt `@Order(LOWEST_PRECEDENCE)`; advice mới phải set `@Order` tường minh —
nếu không, lỗi nghiệp vụ sẽ bị catch-all 500 đè.

## Bảng status

| Status | Nguồn | `message` |
|---|---|---|
| 200 | controller | riêng từng route (envelope `{message, data}`) |
| 400 | `ApiExceptionHandler` | `Dữ liệu không hợp lệ` |
| 401 | `AuthExceptionHandler` | `Email hoặc mật khẩu không đúng` (sai email/mật khẩu) |
| 401 | `JsonAuthenticationEntryPoint` | mô tả lỗi từ Spring (vd `… Jwt expired at …`) + header `WWW-Authenticate` |
| 403 | `AuthExceptionHandler` | `Tài khoản đã bị chặn` (login với account `BANNED`) |
| 403 | `JsonAccessDeniedHandler` | `Tài khoản đã bị chặn` (`BANNED`) hoặc `Bạn không có quyền truy cập` (sai role) |
| 404 | `ApiExceptionHandler` | `Không tìm thấy tài nguyên` |
| 405 | `ApiExceptionHandler` | `Phương thức không được hỗ trợ` |
| 415 | `ApiExceptionHandler` | `Định dạng nội dung không được hỗ trợ` |
| 4xx khác | `ApiExceptionHandler` | `Yêu cầu không hợp lệ` |
| 500 | `ApiExceptionHandler` | `Internal server error` — chi tiết exception chỉ vào log |

404/405/415/500 là status nền, áp dụng cho mọi route — Swagger từng route không lặp lại.

## Ví dụ (output thật)

```text
# 401 token — kèm header chuẩn RFC 6750
$ curl -s -i -H 'Authorization: Bearer abc.def.ghi' http://localhost:8080/api/auth/me
WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
{"message":"An error occurred while attempting to decode the Jwt: Malformed token","timestamp":"2026-10-04T08:14:32.840856214Z"}

# 403 — account BANNED
{"message":"Tài khoản đã bị chặn","timestamp":"2026-10-04T08:14:28.027349693Z"}

# 400 — validate
{"message":"Dữ liệu không hợp lệ","timestamp":"2026-10-04T08:14:28.048550698Z"}

# 405 — sai method
{"message":"Phương thức không được hỗ trợ","timestamp":"2026-10-04T08:46:02.634342450Z"}

# 404 — sai path (đã đăng nhập; ẩn danh vào /api/** bị 401 chặn trước)
{"message":"Không tìm thấy tài nguyên","timestamp":"2026-10-04T08:46:24.796283083Z"}
```

500 chưa có route thật nào ném; test `ApiExceptionHandlerTest` assert
`jsonPath("$.message").value("Internal server error")` và body không chứa chi tiết nội bộ.

## Chưa theo envelope

Exception ném từ **filter** (không phải 401/403 của security — đã có handler riêng) bay ra servlet
container → error-dispatch `/error` → `BasicErrorController` của Spring Boot trả
`{timestamp,status,error,path}` — không có `message`. Hiện repo chưa có filter tự viết nên chưa xảy
ra; khi thêm filter đầu tiên cần `ErrorController` tùy biến theo envelope (issue riêng).

## Test

- `ApiExceptionHandlerTest` — 500 generic không lộ chi tiết; 405 giữ status + message
- `AuthControllerTest` — login 200 envelope; 400/401/403 message
- `SecurityConfigTest` — 401 thiếu token, 403 sai role/`BANNED` có body
- `ExpiredTokenTest` — token hết hạn 401 + header `WWW-Authenticate`
- `RequiredClaimsTest` — token thiếu claim bắt buộc 401
