---
auth-model: ownership
purpose: Danh sách đề xuất khoang của khách đang đăng nhập (trang "Kiểm tra kho của tôi").
---
# GET /api/customer/proposals

Trả các `ProposalFeedback` của chính khách, mới nhất trước — FE dùng cho tab "Chờ duyệt".

- **Actor / quyền:** role-bound `CUSTOMER` (`/api/customer/**`) + ownership: chỉ proposal có
  `customer_id` = account đang đăng nhập.
- **Contract chi tiết:** annotation tại `booking/controller/CustomerProposalController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Query `status` tuỳ chọn: `Pending` / `Agreed` / `Rejected` / `Canceled`; giá trị khác → 400.
- Mỗi dòng kèm thông tin khoang, cơ sở, giá thuê hiệu lực (`storage_units.monthly_price`, trống thì giá
  loại khoang), `rejectionCount` của đơn và `maxRejectionCount` (`policies.proposal.max_rejection_count`) —
  FE hiển thị cảnh báo từ số này, không tự suy ra ngưỡng.

## Ghi chú nghiệp vụ

- Spec `draft.md` › 1.3: thông tin hiển thị lấy từ `ProposalFeedback`. Proposal dùng `Canceled` thay
  `Expired` (chốt #113).

## Ví dụ

```bash
TOKEN='<token của customer1@lemar.vn>'

curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/customer/proposals?status=Pending"
# {"message":"Lấy danh sách đề xuất khoang thành công","data":[{"id":2,"orderId":2,"status":"Pending","note":null,"createdAt":"2026-10-13T09:32:41.202069Z","unitId":8,"unitCode":"C-01","unitSize":null,"unitTypeName":"Large","facilityId":1,"facilityName":"Self Storage Quận 7","facilityAddress":"123 Nguyễn Văn Linh, Quận 7, TP.HCM","monthlyPrice":1400000.00,"rejectionCount":0,"maxRejectionCount":3},{"id":1,"orderId":1,"status":"Pending","note":null,"createdAt":"2026-10-13T09:32:41.202069Z","unitId":1,"unitCode":"A-01","unitSize":null,"unitTypeName":"Small","facilityId":1,"facilityName":"Self Storage Quận 7","facilityAddress":"123 Nguyễn Văn Linh, Quận 7, TP.HCM","monthlyPrice":500000.00,"rejectionCount":0,"maxRejectionCount":3}]}

curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/customer/proposals?status=Expired"
# {"message":"Trạng thái không hợp lệ: Expired","timestamp":"2026-10-13T09:33:22.757636Z"}
```

Dữ liệu ví dụ tạo bằng `backend/sql/dev/proposal-sample.sql`.

## Liên quan

- [customer-proposal-agree.md](customer-proposal-agree.md) · [customer-proposal-reject.md](customer-proposal-reject.md) ·
  [routes.md](../../routes.md) · [index.md](../../index.md)
