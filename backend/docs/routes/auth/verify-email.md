---
purpose: Xác minh email bằng mã 6 số nhận qua email — mở khoá account đã xác minh.
---
# POST /api/auth/verify-email

Nhập mã 6 số nhận qua email để xác minh account (set `Account.email_verified_at`).

- **Actor / quyền:** public — không cần token.
- **Contract chi tiết:** annotation tại `identity/controller/AuthController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

`RegistrationService.verifyEmail`:

1. Chuẩn hoá email. Account đã tồn tại:
   - **đã xác minh** → 200 với message `Email đã được xác minh trước đó` (idempotent, không kiểm mã);
   - chưa xác minh (legacy/seed) → kiểm mã rồi set `email_verified_at`.
2. Chưa có account → đối chiếu mã trong Redis (`auth:verify-code:<email>` — so với BCRYPT hash):
   - mã sai → tăng `attempts`, trả **400**; đủ 5 lần sai → **429** (khoá tới khi resend / mã hết hạn);
   - mã hết hạn / chưa từng phát → **400** (không lộ email nào tồn tại);
   - mã đúng → xoá mã (dùng một lần), **tạo `Account` role `CUSTOMER` với `email_verified_at` set
     ngay** + ghi `AuditLog` `ACCOUNT_REGISTERED` (actor = account vừa tạo).
3. Việc xác minh tự nó **không** ghi `AuditLog` riêng — bản ghi vòng đời là `Account.email_verified_at`
   (spec-gap #75); audit duy nhất ở bước này là `ACCOUNT_REGISTERED` khi tạo account.

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` › `## Business workflow` + Flow 1 §1.2; tham số (6 số / 10 phút / 5 lần)
  chốt tại spec-gap #139.
- Cấu hình: `EMAIL_VERIFICATION_TTL`, `EMAIL_VERIFICATION_MAX_ATTEMPTS` — xem
  [index.md › Cấu hình](../index.md#cấu-hình).
- **Xác minh tạo account (verify-first)**: trước khi nhập đúng mã, email chỉ có pending registration
  (không có `Account`); sau khi xác minh mới đăng nhập được. Account legacy chưa xác minh (seed) được
  mark verified.
- Sau khi xác minh: phát event `EmailVerified`; sau commit, booking (B2) claim các `RentalRequest`
  `Approved` còn hạn khớp email trong transaction riêng — claim lỗi chỉ log, không ảnh hưởng kết quả
  xác minh ([booking-conversion.md](../../booking-conversion.md)).

## Ví dụ

```bash
curl -s -X POST http://localhost:8080/api/auth/verify-email \
  -H 'Content-Type: application/json' \
  -d '{"email":"khach-moi@example.com","code":"123456"}'
# {"message":"Xác minh email thành công","data":null}

# 200 — account đã xác minh trước đó (idempotent, không kiểm mã)
# {"message":"Email đã được xác minh trước đó","data":null}

# 400 — mã sai hoặc hết hạn
# {"message":"Mã xác minh không đúng hoặc đã hết hạn","timestamp":"2026-10-15T10:08:37.807650115Z"}

# 429 — nhập sai quá 5 lần
# {"message":"Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.","timestamp":"2026-10-15T10:08:38.184864391Z"}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [resend-verification](resend-verification.md)
