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

1. Chuẩn hoá email; không tìm thấy account → **400** cùng message với mã sai (không lộ email nào
   tồn tại).
2. Account **đã xác minh** → trả 200 với message `Email đã được xác minh trước đó` (idempotent,
   không kiểm mã, không đốt lượt nhập sai).
3. Đối chiếu mã trong Redis (`auth:verify-code:<email>` — so với BCRYPT hash):
   - mã sai → tăng `attempts`, trả **400**; đủ 5 lần sai → **429** (khoá tới khi resend / mã hết hạn);
   - mã hết hạn / chưa từng phát → **400**;
   - mã đúng → xoá mã (dùng một lần), set `email_verified_at`.
4. Xác minh email **không** ghi `AuditLog` — bản ghi vòng đời là `Account.email_verified_at`
   (spec-gap #75).

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` › `## Business workflow` + Flow 1 §1.2; tham số (6 số / 10 phút / 5 lần)
  chốt tại spec-gap #139.
- Cấu hình: `EMAIL_VERIFICATION_TTL`, `EMAIL_VERIFICATION_MAX_ATTEMPTS` — xem
  [index.md › Cấu hình](../index.md#cấu-hình).
- Sau khi xác minh, account được liên kết `RentalRequest` đã duyệt (Flow 1 §1.2 — thuộc package B,
  chưa triển khai tại đây).

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
