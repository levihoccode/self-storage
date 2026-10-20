---
purpose: Luồng nội bộ convert RentalRequest đã duyệt thành RentalOrder + ProposalFeedback, claim sau xác minh email.
---
# Convert yêu cầu thuê → đơn + proposal

Hành vi theo spec: `specs/draft.md` › 1.1 Yêu cầu đặt kho (nhánh "email đã có tài khoản"),
› 1.2 Khách tạo tài khoản › Case A. Trang này mô tả **cách code đang chạy** và cách module khác
nối vào — không thay spec.

Luồng convert **không có endpoint HTTP riêng**. Chạy bên trong hai endpoint có sẵn:
`POST /api/fm/rental-requests/{id}/approve` và `POST /api/auth/verify-email`.

## Khi nào convert

| Trigger | Điểm nối | Hàm | Transaction |
|---|---|---|---|
| FM duyệt request, email đã thuộc account **đã xác minh** | `RentalRequestService.approve` — khoá email trước pre-check trùng, rồi conditional update `Approved` + audit | `RentalRequestConversionService.convertOnApproval(requestId)` | Join transaction duyệt — convert lỗi thì duyệt rollback theo |
| Customer vừa xác minh email | `RegistrationService.verifyEmail` phát `EmailVerified(accountId)` → `booking/application/EmailVerifiedClaimListener` (`@TransactionalEventListener(AFTER_COMMIT)`) | `RentalRequestConversionService.claimForVerifiedAccount(accountId)` | Transaction **riêng** (`REQUIRES_NEW`) sau khi xác minh đã commit — claim lỗi chỉ log, xác minh vẫn thành công |

Identity không gọi sang booking (identity là module nền) — nên trigger 2 đi qua event.
Claim lỗi ở trigger 2 (ví dụ thiếu policy `order.expiry_days`) không tự thử lại; request vẫn `Approved`.

Trigger 1 khoá advisory theo email **trước pre-check trùng** và giữ tới commit; nếu `convertOnApproval`
vẫn phát hiện trùng (request khác vừa convert), B1 trả **409** `RentalRequestDuplicateUnitException`
và rollback cả transaction duyệt — request giữ `Pending`, không có ca "duyệt xong nhưng không có đơn".

`convertOnApproval` trả `ConversionResult`:

| `outcome` | Ý nghĩa | Caller (luồng duyệt) làm gì |
|---|---|---|
| `CONVERTED` | Đã tạo đơn + proposal, request → `Converted`, đã gửi notification + email | — |
| `NO_VERIFIED_ACCOUNT` | Email chưa có account (kể cả đang pending registration) hoặc account legacy chưa xác minh. Request giữ `Approved` | Luồng duyệt gửi email "chưa có tài khoản" (Flow 1.1); khách đăng ký/xác minh xong → claim |
| `NOT_CONVERTIBLE` | Request không ở `Approved` hoặc đã quá `expires_at` | — |
| `DUPLICATE_UNIT` | Account đã có đơn/proposal đang hoạt động cho khoang này. Request giữ `Approved` | — (luồng duyệt đã chặn gán trùng; đây là guard cho race hiếm) |

`claimForVerifiedAccount` trả danh sách `ConversionResult` cho từng request `Approved` còn hạn khớp
email (rỗng = Case B, hoặc email chưa có account — pending/không tồn tại).

## Convert làm gì

Trong một transaction:

1. `RentalOrder(status = Pending, unit_id = null, expires_at = now + order.expiry_days)`.
2. `ProposalFeedback(status = Pending, unit_id = RentalRequest.unit_id)` — khoang FM đã chỉ định.
   Không có auto-matching hay đề xuất nhiều khoang.
3. `RentalRequest.status = Converted` — conditional update `WHERE status = 'Approved' AND expires_at > now`
   (`RentalRequestRepository.convertIfApproved`, cùng kiểu `approveIfPending` của luồng duyệt); 0 row → `NOT_CONVERTIBLE`,
   không tạo gì. Kiểm trùng chạy trước bước này để request trùng giữ nguyên `Approved`.
4. Notification `RENTAL_REQUEST_APPROVED` cho customer, gắn `OrderNotification(order_id)`; email
   cùng nội dung gửi **sau commit**. Lỗi SMTP chỉ log, không rollback (xem
   [index.md › Gửi email](index.md#gửi-email-module-notification)). Link trong body:
   `<FRONTEND_URL>/proposals`.
5. Audit: chỉ trigger claim ghi `RENTAL_REQUEST_CLAIMED` (entity `RentalRequest`, actor = customer).
   Trigger duyệt và tạo đơn/proposal **chưa ghi audit**.

`order.expiry_days` đọc từ bảng `policies` qua `pricing::application` `PolicyQueryService`, không fallback.
Key này seed ở `V5__order_expiry_days_seed.sql` — thiếu key là lỗi cấu hình: convert ném
`IllegalStateException` và rollback.

## Chống tạo trùng (account + khoang)

- "Đang hoạt động" = có `ProposalFeedback` `Pending`/`Agreed` cho khoang đó, trên một đơn của
  account chưa `Canceled`/`Done`/`Expired` (`ConversionGuardRepository.hasActiveConversion`).
  Đơn cũ đã đóng thì convert lại cùng khoang được.
- Hai trigger cùng khoá `pg_advisory_xact_lock` theo **email khách đã chuẩn hoá**, giữ tới hết
  transaction, nên duyệt và claim của cùng email chạy tuần tự (email unique theo account, claim↔claim
  vẫn tuần tự).
  - Khoá row request là chưa đủ: request r1 đang được duyệt trong transaction duyệt chưa commit thì
    với transaction khác r1 vẫn `Pending`, nên claim r2 cùng khoang không đụng row nào của r1.
    Test `claimDuringUncommittedApprovalWaitsAndSkipsSameUnit`.
  - Không khoá theo account id: lúc FM duyệt, khách có thể đang pending đăng ký (chưa có account). Trigger duyệt khoá
    email **trước** khi tra account và giữ khoá cả ở nhánh `NO_VERIFIED_ACCOUNT`, nên claim của lần
    xác minh chạy song song phải chờ duyệt commit rồi mới select — không bỏ sót request.
    Test `approvalBeforeVerificationCommitIsStillClaimed`.
  - Hai test trên chỉ commit transaction duyệt khi thấy claim thật sự bị chặn trên advisory lock
    (`pg_locks`), không dựa vào sleep; bỏ khoá hoặc khoá sau khi tra account thì test fail.
- Thứ tự khoá ở cả hai trigger: **email trước, row request sau** — trigger duyệt khoá email trước
  pre-check và update row; claim khoá email trước select.
- Request trùng giữ nguyên `Approved`, không auto-reject. Khi quá hạn, request được chuyển
  `Expired` bởi cron Flow 1 — không thuộc luồng convert. Claim luôn bỏ qua request đã quá
  `expires_at`, kể cả khi cron chưa chạy.

## Code map

| Thành phần | File |
|---|---|
| Convert + claim | `booking/application/RentalRequestConversionService.java`, `ConversionResult.java` |
| Nối trigger | `booking/application/RentalRequestService.java` (approve), `booking/application/EmailVerifiedClaimListener.java`, `identity/application/EmailVerified.java`, `identity/application/RegistrationService.java` |
| Khoá + kiểm trùng | `booking/repository/ConversionGuardRepository.java` |
| Request | `booking/domain/RentalRequest.java`, `booking/repository/RentalRequestRepository.java` (`convertIfApproved`, `findClaimableForUpdate`) |
| Đọc policy | `pricing/application/PolicyQueryService.java` |
| Account đã xác minh | `identity/application/AccountQueryService.java` (`findVerifiedByEmail`, `findVerifiedById`) |
| Test | `src/test/.../booking/RentalRequestConversionIntegrationTest.java` (Testcontainers) |

## Việc còn mở

- Nội dung email/notification chưa có loại + kích thước khoang (mới có mã khoang + link).
- Audit cho trigger duyệt / tạo đơn / proposal chưa được ghi.
- Cron `RentalRequest` → `Expired` chưa có (thuộc cron Flow 1).
