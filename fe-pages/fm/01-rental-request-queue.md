# Hàng chờ yêu cầu đặt kho

- **Route:** `/fm/rental-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.1 / 1.3

## Mục đích

FM xử lý `RentalRequest` gửi tới cơ sở mình: duyệt/từ chối request mới, và đề xuất lại khoang cho
request đã convert khi khách từ chối đề xuất trước (`needsRepropose`).

## Dữ liệu hiển thị

- Nguồn: `GET /api/fm/rental-requests?facilityId=&page=0&size=100`.
- Mỗi dòng có `status` (trạng thái request), `orderId`/`orderStatus` (nếu đã convert),
  `needsRepropose`, `lastProposal`, `rejectionCount`, `maxRejectionCount`.
- Tab **Chờ xử lý**: request `Pending`, hoặc `needsRepropose = true`.
- Tab **Đã duyệt**: request `Approved`/`Converted` với `needsRepropose = false`.
- Tab **Từ chối**, **Hết hạn**: theo `status` request.
- Không hiển thị cột `facility`; FM chỉ thấy cơ sở của mình.

## Nhãn hiển thị

`needsRepropose` do BE tính từ trạng thái đơn + proposal gần nhất. FE chỉ render nhãn theo bảng
dưới, không suy ra từ `orderStatus`.

| Điều kiện | Nhãn |
|---|---|
| `needsRepropose = true` | Cần đề xuất lại |
| request `Pending` | Chờ xử lý |
| request `Converted`, `needsRepropose = false` | Đã được xử lý — theo dõi ở Đơn thuê kho |
| request `Rejected` / `Expired` | Đã từ chối / Hết hạn |

Hàng đợi chỉ trả request. Các bước còn lại của đơn (chờ khách phản hồi, chờ thanh toán, chờ đặt
lịch) thuộc màn Đơn thuê kho — hàng đợi không hiển thị nhãn cho các bước đó.

`Converted` là trạng thái của `RentalRequest`, không phải `RentalOrder`; detail có thể hiện
trạng thái request, nhưng row trong hàng đợi dùng nhãn ở bảng trên.

## Actions

- **Đề xuất** (request `Pending`): mở form chọn khoang `Available` cùng cơ sở/loại khoang →
  `POST /api/fm/rental-requests/{id}/approve` body `{unitId}`.
- **Từ chối** (request `Pending`): nhập lý do → `POST /api/fm/rental-requests/{id}/reject`
  body `{rejectReason, rejectNote?}`.
- **Đề xuất lại** (`needsRepropose = true`): điều hướng `/fm/rental-orders/{orderId}/re-propose`.
- **Xem**: chỉ mở panel chi tiết; panel hiện proposal gần nhất,
  `rejectionCount`/`maxRejectionCount`.

## States / UI

- Loading: "Đang tải hàng đợi…".
- Error: SurfaceState lỗi + "Thử lại".
- 409 khi duyệt: khoang vừa bị chiếm hoặc request đã được FM khác xử lý; hiện message BE và
  tải lại danh sách.
- 403/404: forbidden — không có quyền/cơ sở.

## API liên quan

- `GET /api/fm/facility`
- `GET /api/fm/storage-units?facilityId=`
- `GET /api/fm/rental-requests?facilityId=&page=&size=`
- `POST /api/fm/rental-requests/{id}/approve`
- `POST /api/fm/rental-requests/{id}/reject`

## Edge case / Lưu ý UX

- Toàn bộ API kiểm tra facility scope ở BE, không chỉ ẩn ở FE.
- Duyệt chạy trong một transaction, re-check khoang; FE disable nút khi đang gửi và vẫn xử lý 409.
- Request `Converted` vẫn nằm trong hàng đợi; khi `needsRepropose = false`, FM không còn thao tác
  trên request — theo dõi đơn ở màn Đơn thuê kho.
- Không có tab `Wishlisted` — API không trả trạng thái này.
