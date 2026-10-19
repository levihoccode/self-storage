---
purpose: Đăng ký tài khoản khách hàng — tạo account CUSTOMER chưa xác minh và gửi mã xác minh qua email.
---
# POST /api/auth/register

Đăng ký tài khoản khách hàng: tạo `Account` role `CUSTOMER` (chưa xác minh) và gửi email chứa mã
xác minh 6 số — hoàn tất ở [POST /api/auth/verify-email](verify-email.md).

- **Actor / quyền:** public — không cần token.
- **Contract chi tiết:** annotation (`@Operation`/`@ApiResponses`/`@ExampleObject`) tại
  `identity/controller/AuthController.java`; Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

`RegistrationService.register` (một transaction):

1. Chuẩn hoá email (`trim` + lowercase — `Account.normalize`); email đã tồn tại → **409** (kể cả
   account chưa xác minh — lấy mã mới bằng [resend-verification](resend-verification.md)).
2. Băm mật khẩu BCrypt; tạo `Account` role `CUSTOMER`, `email_verified_at = null`, `status = ACTIVE`.
3. Ghi `AuditLog` action `ACCOUNT_REGISTERED`, entity `Account`, actor = chính account vừa tạo.
4. Phát mã xác minh (6 số, TTL 10 phút) — lưu Redis dạng BCRYPT hash, không lưu plaintext — rồi
   phát event `VerificationEmailRequested`; module notification gửi email **sau commit**:
   rollback thì không gửi, SMTP lỗi không rollback account (best-effort, chỉ log).

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` › Flow 1 §1.2; cơ chế OTP chốt tại spec-gap #139.
- Mật khẩu tối thiểu 8 ký tự (validate DTO → 400 `Dữ liệu không hợp lệ`).
- Account chưa xác minh **bị chặn đăng nhập** (403 `Tài khoản chưa xác minh email`) và mọi thao tác
  authenticated — phải hoàn tất [verify-email](verify-email.md) trước:
  [authentication.md › Đăng ký và xác minh email](../../authentication.md#đăng-ký-và-xác-minh-email).
- Chống spam gửi lại mã: [resend-verification](resend-verification.md); chống brute-force login:
  [login](login.md).

## Ví dụ

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"khach-moi@example.com","password":"matkhau123"}'
# {"message":"Đăng ký thành công. Kiểm tra email để lấy mã xác minh.","data":null}

# 409 — email đã đăng ký
# {"message":"Email đã được đăng ký","timestamp":"2026-10-15T10:07:56.911733683Z"}

# 400 — mật khẩu dưới 8 ký tự / email sai định dạng
# {"message":"Dữ liệu không hợp lệ","timestamp":"2026-10-15T10:07:56.947684468Z"}
```

Email xác minh (render qua `NotificationEmailLayout` — HTML chuẩn; xem tại MailHog dev
`http://localhost:8025` hoặc hộp thư thật theo `MAIL_*`):

```text
Subject: Mã xác minh email đăng ký Kho Mộc

Mã xác minh email của bạn là: 123456

Mã có hiệu lực trong 10 phút. Nếu bạn không đăng ký tài khoản, hãy bỏ qua email này.
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md) · [verify-email](verify-email.md)
