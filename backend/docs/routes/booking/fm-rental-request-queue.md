---
auth-model: role-bound
purpose: Hàng đợi yêu cầu đặt kho cho FM — chỉ thấy cơ sở của mình, kèm bước tiếp theo của đơn.
---

# GET /api/fm/rental-requests

Hàng đợi `RentalRequest` cho FM xử lý: lọc theo facility + status, phân trang, mới nhất trước.
Mỗi dòng kèm trạng thái đơn/proposal để FM biết bước tiếp theo.

- **Actor / quyền:** role-bound `FM` (`/api/fm/**` trong `SecurityConfig`) + permission-bound
  `rental_request.read` theo facility qua `Access.can(permission, facilityId)`.
- **Contract chi tiết:** annotation tại `booking/controller/FmRentalRequestController.java`;
  Swagger UI — `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Query: `facilityId` bắt buộc; `status` tùy chọn (`Pending/Rejected/Approved/Converted/Expired`);
  `page` mặc định 0, `size` mặc định 20 tối đa 100; sắp xếp `createdAt` giảm dần.
- Guard: thiếu permission → 403 "Bạn không có quyền thực hiện thao tác này"; ngoài facility
  → 403 "Bạn không được gán vào cơ sở này"; facility không tồn tại → 404.
- Trả envelope `{message, data: {items, page, size, total}}`; từng entry không có field facility
  (spec draft.md NOTES §1.1).
- Mỗi entry có thêm: `orderId` (null nếu chưa convert), `orderStatus` (null nếu chưa convert),
  `needsRepropose`, `lastProposal` (proposal gần nhất), `rejectionCount`, `maxRejectionCount`.

## `needsRepropose`

`needsRepropose = true` khi **đủ tất cả**:

- đã có order và order chưa `Canceled`/`Done`;
- order chưa quá `expires_at` — endpoint `POST /api/rental-orders/{id}/proposals` trả 409 khi đơn
  hết hạn, nên queue không mời FM đề xuất lại ở trạng thái đó;
- không có proposal `Pending`;
- proposal gần nhất không phải `Agreed`;
- `(order.status = Pending || order.needs_reproposal)`;
- proposal gần nhất là `null`/`Rejected`/`Canceled`.

Flag này phủ cả hai ca: order còn `Pending` sau khi khách từ chối (không set
`needs_reproposal`) và order đã cọc có `needs_reproposal = true`.

FE dùng flag này, không tự suy luận:

| Điều kiện | Nhãn |
|---|---|
| request `Pending` | Chờ xử lý |
| `needsRepropose = true` | Cần đề xuất lại |
| request `Converted`, flag `false` | Đã được xử lý — theo dõi ở Đơn thuê kho |
| request `Rejected`/`Expired` | Đã từ chối / Hết hạn |

Queue chỉ trả request; trạng thái/action còn lại của đơn (chờ khách phản hồi, chờ thanh toán, lịch)
thuộc màn Đơn thuê kho.

## Ghi chú nghiệp vụ

- Spec: `specs/draft.md §1.1`; permission `rental_request.read` (FACILITY) đã seed V3.
- Queue đọc policy `proposal.max_rejection_count` cho trang khác rỗng, thiếu row đó là 500 —
  cùng dependency với luồng từ chối proposal.
- `lastProposal` là proposal mới nhất của order, kèm `unitCode`, `status`, `note`, `createdAt`;
  `rejectionCount` đếm proposal `Rejected` của order; `maxRejectionCount` lấy từ policy
  `proposal.max_rejection_count`.
- Request `Converted` vẫn nằm trong hàng đợi; trạng thái request không đổi khi khách từ chối
  proposal. `needsRepropose` là tín hiệu duy nhất queue cần cho action đề xuất lại.

## Ví dụ

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"fm1@example.com","password":"Test@1234"}' | jq -r .data.token)

# 200 — request Converted, order Pending, proposal gần nhất Rejected → cần đề xuất lại
curl -s "http://localhost:8080/api/fm/rental-requests?facilityId=1&status=Converted&page=0&size=1" \
  -H "Authorization: Bearer $TOKEN"
# {"message":"Lấy danh sách yêu cầu đặt kho thành công","data":{"items":[{"id":4,"customerName":"phamquochung170505","customerEmail":"phamquochung170505@gmail.com","customerPhone":"0123467891","unitTypeId":2,"startDate":"2026-11-24","period":2,"status":"Converted","createdAt":"2026-11-02T13:21:13.352906Z","expiresAt":"2026-11-09T13:21:46.842322Z","orderId":3,"orderStatus":"Pending","needsRepropose":true,"lastProposal":{"id":3,"unitCode":"B-02","status":"Rejected","note":"Không thích khoang này","createdAt":"2026-11-02T13:21:46.853749Z"},"rejectionCount":1,"maxRejectionCount":3}],"page":0,"size":1,"total":3}}

# 403 — FM Q7 hỏi hàng đợi của cơ sở TD
curl -s "http://localhost:8080/api/fm/rental-requests?facilityId=2" -H "Authorization: Bearer $TOKEN"
# {"message":"Bạn không được gán vào cơ sở này","timestamp":"2026-10-16T14:51:43.435261665Z"}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md) · [proposal-flow.md](../../booking/proposal-flow.md)
