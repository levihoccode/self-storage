---
purpose: Gửi lại mã xác minh email cho account chưa xác minh (cooldown 60 giây).
---
# POST /api/auth/resend-verification

Phát mã xác minh mới (ghi đè mã cũ) cho account chưa xác minh; mỗi lần cách nhau tối thiểu 60 giây.

- **Actor / quyền:** public — không cần token.
- **Contract chi tiết:** annotation tại `identity/controller/AuthController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

`RegistrationService.resendVerification`:

1. Chuẩn hoá email. Không có account và không có pending registration còn hạn → **400**
   `Email chưa được đăng ký`.
2. Account đã xác minh → **400** `Tài khoản đã được xác minh` (không phát mã mới).
3. Redis `auth:verify-cooldown:<email>` (SET NX, TTL 60 giây) — còn hiệu lực → **429** (áp cho cả
   register lẫn resend). Qua được cooldown → phát mã mới (giữ hash mật khẩu của pending), reset bộ
   đếm sai, gửi email sau commit như [register](register.md#luồng--hành-vi).

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` › `## Business workflow`; tham số cooldown chốt tại spec-gap #139.
- Cấu hình: `EMAIL_VERIFICATION_RESEND_COOLDOWN` — xem [index.md › Cấu hình](../index.md#cấu-hình).

## Ví dụ

```bash
curl -s -X POST http://localhost:8080/api/auth/resend-verification \
  -H 'Content-Type: application/json' \
  -d '{"email":"khach-moi@example.com"}'
# {"message":"Đã gửi lại mã xác minh","data":null}

# 429 — gửi lại trong vòng 60 giây
# {"message":"Vui lòng chờ trước khi yêu cầu gửi lại mã.","timestamp":"2026-10-15T10:08:37.700101223Z"}

# 400 — email chưa đăng ký
# {"message":"Email chưa được đăng ký","timestamp":"2026-10-15T10:09:07.393585034Z"}

# 400 — tài khoản đã xác minh
# {"message":"Tài khoản đã được xác minh","timestamp":"2026-10-15T10:19:54.662678736Z"}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [verify-email](verify-email.md)
