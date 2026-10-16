---
purpose: Luồng nội bộ B2 — convert RentalRequest đã duyệt thành RentalOrder + ProposalFeedback, claim sau xác minh email; điểm nối cho B1/A3a.
---
# Convert yêu cầu thuê → đơn + proposal (B2)

Hành vi theo spec: `specs/draft.md` › 1.1 Yêu cầu đặt kho (nhánh "email đã có tài khoản"),
› 1.2 Khách tạo tài khoản › Case A, › Scheduled Jobs. Trang này mô tả **cách code đang chạy** và
cách module khác nối vào — không thay spec.

B2 **không có endpoint HTTP**. Mọi thứ đi qua public API của `booking::application`.

## Khi nào convert

| Trigger | Ai gọi | Hàm | Transaction |
|---|---|---|---|
| FM duyệt request, email đã thuộc account **đã xác minh** | B1 (#126), ngay sau khi set `Approved` | `RentalRequestConversionService.convertOnApproval(requestId)` | Join transaction duyệt của B1 — convert lỗi thì duyệt rollback theo |
| Customer vừa xác minh email | A3a, sau khi set `email_verified_at` | `RentalRequestConversionService.claimForVerifiedAccount(accountId)` | Join transaction xác minh (hoặc tự mở nếu gọi riêng) |

Hiện **chưa nối** cả hai trigger: B1 (#145) chưa merge; A3a đã merge nhưng `verifyEmail` chưa phát
event cho booking — nối sau cùng B1. Test gọi thẳng service.

`convertOnApproval` trả `ConversionResult`:

| `outcome` | Ý nghĩa | Caller (B1) làm gì |
|---|---|---|
| `CONVERTED` | Đã tạo đơn + proposal, request → `Converted`, đã gửi notification + email | — |
| `NO_VERIFIED_ACCOUNT` | Email chưa có account, hoặc account chưa xác minh. Request giữ `Approved` | Gửi email "chưa có tài khoản" của Flow 1.1 (thuộc B1) |
| `NOT_CONVERTIBLE` | Request không ở `Approved` hoặc đã quá `expires_at` | — |
| `DUPLICATE_UNIT` | Account đã có đơn/proposal đang hoạt động cho khoang này. Request giữ `Approved` | — (B1 đã chặn gán trùng lúc duyệt; đây là guard cho race hiếm) |

`claimForVerifiedAccount` trả danh sách `ConversionResult` cho từng request `Approved` còn hạn khớp
email (rỗng = Case B, hoặc account chưa xác minh / không tồn tại).

## Convert làm gì

Trong một transaction:

1. `RentalOrder(status = Pending, unit_id = null, expires_at = now + order.expiry_days)`.
2. `ProposalFeedback(status = Pending, unit_id = RentalRequest.unit_id)` — khoang FM đã chỉ định.
   Không có auto-matching hay đề xuất nhiều khoang.
3. `RentalRequest.status = Converted`.
4. Notification `RENTAL_REQUEST_APPROVED` cho customer, gắn `OrderNotification(order_id)`; email
   cùng nội dung gửi **sau commit**. Lỗi SMTP chỉ log, không rollback (xem
   [index.md › Gửi email](index.md#gửi-email-module-notification)). Link trong body:
   `<FRONTEND_URL>/proposals`.
5. Audit: chỉ trigger claim ghi `RENTAL_REQUEST_CLAIMED` (entity `RentalRequest`, actor = customer).
   Trigger duyệt và tạo đơn/proposal **chưa ghi audit**, chờ spec-gap #132.

`order.expiry_days` đọc từ bảng `policies` qua `pricing::application` `PolicyQuery`. Seed V2 hiện
**chưa có key này** (bug #130) — thiếu key thì convert ném `IllegalStateException` và rollback.
Chạy local thì tự thêm:

```sql
insert into policies (key, value) values ('order.expiry_days', '30') on conflict (key) do nothing;
```

## Chống tạo trùng (account + khoang)

- "Đang hoạt động" = có `ProposalFeedback` `Pending`/`Agreed` cho khoang đó, trên một đơn của
  account chưa `Canceled`/`Done`/`Expired` (`ConversionGuardRepository.hasActiveConversion`).
  Đơn cũ đã đóng thì convert lại cùng khoang được.
- Hai trigger cùng khoá `pg_advisory_xact_lock` theo account trước khi kiểm trùng, nên chạy
  tuần tự theo account. Khoá row request là chưa đủ: request r1 đang được duyệt trong transaction
  B1 chưa commit thì với transaction khác r1 vẫn `Pending`, nên claim r2 cùng khoang không đụng row
  nào của r1. Test `claimDuringUncommittedApprovalWaitsAndSkipsSameUnit` tái hiện đúng ca này:
  bỏ khoá thì test fail.
- Thứ tự khoá ở cả hai trigger: account trước, row request sau.
- Request trùng giữ nguyên `Approved`, không auto-reject. Khi quá hạn, request được chuyển
  `Expired` bởi cron Flow 1 (#143) — không thuộc B2. Claim luôn bỏ qua request đã quá
  `expires_at`, kể cả khi cron chưa chạy.

## Code map

| Thành phần | File |
|---|---|
| Convert + claim | `booking/application/RentalRequestConversionService.java`, `ConversionResult.java` |
| Khoá + kiểm trùng | `booking/repository/ConversionGuardRepository.java` |
| Request | `booking/domain/RentalRequest.java` (chỉ map cột B2 cần — B1 bổ sung), `booking/repository/RentalRequestRepository.java` |
| Đọc policy | `pricing/application/PolicyQuery.java` |
| Account đã xác minh | `identity/application/AccountQueryService.java` (`findVerifiedByEmail`, `findVerifiedById`) |
| Test | `src/test/.../booking/RentalRequestConversionIntegrationTest.java` (Testcontainers) |

## Việc còn mở

- Nối trigger 1 vào B1 (#126), trigger 2 vào luồng xác minh A3a.
- Seed `order.expiry_days`: bug #130.
- Nội dung email/notification thiếu loại + kích thước khoang so với email Flow 1.1: spec-gap #131.
- Audit cho trigger duyệt / tạo đơn / proposal: spec-gap #132.
- Cron `RentalRequest` → `Expired`: #143 (cron Flow 1), không thuộc B2.
