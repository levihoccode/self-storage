---
purpose: FM duyệt yêu cầu đặt kho (B1) — gán khoang Available, dừng ở Approved.
---

# POST /api/fm/rental-requests/{id}/approve

FM duyệt `RentalRequest` đang `Pending` bằng cách gán một khoang cụ thể.

- **Actor / quyền:** role-bound `FM` (`/api/fm/**` trong `SecurityConfig`) + permission
  `rental_request.approve` theo facility qua `Access.can(permission, facilityId)` (#126).
  Guard đặt trong service (thay vì controller) vì cần `facilityId` từ entity mới guard được
  theo cơ sở.
- **Contract chi tiết:** annotation tại
  `booking/controller/FmRentalRequestApproveController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi (đúng spec `specs/draft.md` §1.1, dừng ở Approved — không sang B2)

1. Request không tồn tại → 404, không đổi gì.
2. Chéo cơ sở / thiếu permission → 403 (permission kiểm trước, facility 404 sau — theo `Access`).
3. Request không còn `Pending` (2 FM / double-click) → 409 "Yêu cầu đã được xử lý bởi FM khác",
   không đổi gì.
4. Khoang mất `Available` → 409 "Khoang đã không còn khả dụng, vui lòng chọn khoang khác",
   request giữ `Pending`.
5. Khoang đã gán cho request khác của cùng email đang `Approved`/`Converted` (#126) →
   409 "Khoang đã được gán cho yêu cầu khác của cùng khách hàng...", request giữ `Pending`,
   không noti. Khác email + cùng khoang vẫn cho (cọc trước giữ trước).
6. OK → 1 transaction: `approve(unitId)` → `Approved` + `responded_at=now` +
   `expires_at=responded_at + account.claim_ttl_days` (fallback 7 ngày).
7. Mail duyệt nhánh chưa-có-account theo template `draft.md:156` (after-commit, lỗi chỉ log);
   nhánh đã-có-account để B2 thông báo kèm proposal link.

Body: `{unitId}` bắt buộc. Trả envelope `{message, data: {id, status, unitId, respondedAt,
expiresAt}}`.

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` §1.1; permission `rental_request.approve` (FACILITY) đã seed V3;
  #126 (claim_ttl, chặn gán trùng khoang, mail nhánh chưa-có-account).
- Không tạo `ProposalFeedback` / `RentalOrder` / chuyển `Converted` (sang B2).

## Ví dụ

- Chưa chạy thật trên app (Docker/Postgres chưa có ở máy dev) — sẽ bổ sung output curl thật
  khi chạy E2E.

## Liên quan

- Xem thêm: [routes.md](../routes.md) — [index.md](../index.md)
