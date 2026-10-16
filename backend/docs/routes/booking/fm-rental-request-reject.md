---
auth-model: role-bound
purpose: FM từ chối yêu cầu đặt kho (B1 phần 4) — sang Rejected, gửi mail, hết nhóm B1.
---

# POST /api/fm/rental-requests/{id}/reject

FM từ chối `RentalRequest` đang `Pending` với lý do theo bộ chuẩn.

- **Actor / quyền:** role-bound `FM` (`/api/fm/**` trong `SecurityConfig`) + permission
  `rental_request.reject` theo facility qua `Access.can(permission, facilityId)`. Guard đặt trong
  service (giống approve) vì cần `facilityId` từ entity mới guard được theo cơ sở.
- **Contract chi tiết:** annotation tại
  `booking/controller/FmRentalRequestRejectController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi (đúng spec `specs/draft.md` §1.1, Rejected là trạng thái cuối)

1. Request không tồn tại → 404, không đổi gì.
2. Chéo cơ sở / thiếu permission → 403 (permission kiểm trước, facility 404 sau — theo `Access`).
3. Request không còn `Pending` (2 FM / double-click) → 409 "Yêu cầu đã được xử lý bởi FM khác",
   không đổi gì.
4. `rejectReason` null → 400; `OTHER` thiếu ghi chú → 400 "Ghi chú bắt buộc khi lý do là Khác"
   (khớp CHECK DB: Rejected bắt buộc reason, OTHER bắt buộc note).
5. OK → 1 transaction: conditional update `WHERE status='Pending'` (chống race double-click /
   approve-reject song song — chỉ 1 tx thắng) → `Rejected` + `responded_at=now`
   (`expires_at` giữ nguyên — request đã đóng).
6. Mail + noti (after-commit, lỗi mail chỉ log, không rollback): luôn gửi mail từ chối tới
   email khách; tạo thêm thông báo web `RENTAL_REQUEST_REJECTED` chỉ khi email đã có account
   (`notifications.account_id` NOT NULL nên khách vãng lai chỉ nhận mail).

Body: `{rejectReason: UNIT_UNAVAILABLE | UNIT_MISMATCH | CUSTOMER_REQUEST | OTHER,
rejectNote?}`. Trả envelope `{message, data: {id, status, rejectReason, respondedAt}}`.

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md` §1.1 + nhánh "không tìm thấy khoang" §1.1; bộ chuẩn
  `db-table-draft.md`; permission `rental_request.reject` (FACILITY) đã seed V3.
- Không tạo `ProposalFeedback` / `RentalOrder` / chuyển `Converted` (sang B2).

## Ví dụ

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"fm1@lemar.vn","password":"Test@1234"}' | jq -r .data.token)

# 400 — OTHER thiếu ghi chú
curl -s -X POST http://localhost:8080/api/fm/rental-requests/13/reject -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"rejectReason":"OTHER"}'
# {"message":"Ghi chú bắt buộc khi lý do là Khác","timestamp":"2026-10-16T14:51:52.512907909Z"}

# 200 — từ chối theo yêu cầu khách; request giữ Pending sau lần 400 ở trên
curl -s -X POST http://localhost:8080/api/fm/rental-requests/13/reject -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"rejectReason":"CUSTOMER_REQUEST"}'
# {"message":"Đã từ chối yêu cầu đặt kho","data":{"id":13,"status":"Rejected","rejectReason":"CUSTOMER_REQUEST","respondedAt":"2026-10-16T14:51:52.526854955Z"}}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) — [index.md](../index.md)
