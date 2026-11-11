---
auth-model: ownership
purpose: Hợp đồng đang hiệu lực của khách — nguồn cho màn "Kho của tôi" và tab "Đang sử dụng".
---

# GET /api/contracts

Trả các `RentalContract` đang `Active` của account gọi, kèm khoang + cơ sở + loại khoang.

- **Actor / quyền:** bearer token + `ACCOUNT_ACTIVE` (route trong `/api/**`) + ownership: chỉ hợp đồng
  có `customer_id` = account đang đăng nhập. Không dùng prefix `/api/customer/**` — prefix đó chỉ là
  rule bảo mật, không có controller.
- **Contract chi tiết:** annotation tại `handover/controller/ContractController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Không tham số. Lọc cứng `rental_contracts.status = 'Active'`; hợp đồng `Draft`/`Ended`/`Canceled`
  không xuất hiện.
- Sắp xếp `end_date` tăng dần (`null` cuối), rồi `id` — hợp đồng gần hết hạn lên trước.
- Mỗi dòng: `id`, `code`, `status`, `orderId`, `unitId`, `unitCode`, `unitSize`, `unitTypeName`,
  `facilityId`, `facilityName`, `facilityAddress`, `monthlyPrice`, `depositAmount`, `period`,
  `startDate`, `endDate`, `signedAt`, `overdue`, `expiringSoon`.
- `overdue` BE tính sẵn: `endDate` khác null và **nhỏ hơn ngày hôm nay** theo `Asia/Ho_Chi_Minh`.
  So sánh theo ngày, không theo timestamp — ngày cuối hợp đồng chưa bị coi là quá hạn.
- `expiringSoon` = chưa `overdue` và `endDate <= hôm nay + contract.expiring_soon_days` (seed V22 = 7).
  Hai cờ loại trừ nhau.
- `monthlyPrice` là giá chốt lúc ký trong hợp đồng, không lấy lại từ `storage_units`.
- Không có hợp đồng → `200` với `data: []`.

## Ghi chú nghiệp vụ

- Ngưỡng "sắp hết hạn" là policy `contract.expiring_soon_days`, seed V22 = 7 ngày; BOM đổi bằng
  `UPDATE policies`, không cần migration mới.
- Nhóm theo cơ sở là việc của FE: endpoint trả danh sách phẳng kèm `facilityId`/`facilityName`.
- Trạng thái thanh toán (`paymentLabel` trong mock cũ) chưa có trong response — cần join hoá đơn,
  tách issue riêng khi làm phần nhắc thanh toán.

## Ví dụ

```bash
curl -s -H "Authorization: Bearer $CUSTOMER_TOKEN" http://localhost:8080/api/contracts
# {"message":"Lấy danh sách hợp đồng thành công","data":[{"id":1,"code":"HD-Q7-0001","status":"Active",
#   "orderId":3,"unitId":1,"unitCode":"A-01","unitSize":null,"unitTypeName":"Small",
#   "facilityId":1,"facilityName":"Self Storage Quận 7",
#   "facilityAddress":"123 Nguyễn Văn Linh, Quận 7, TP.HCM",
#   "monthlyPrice":500000.00,"depositAmount":500000.00,"period":6,
#   "startDate":"2026-11-01","endDate":"2027-05-01","signedAt":"2026-11-01T02:00:00Z",
#   "overdue":false,"expiringSoon":false}]}
```

## Liên quan

- [routes.md](../../routes.md) · [index.md](../../index.md) ·
  [proposal-agree.md](proposal-agree.md)
