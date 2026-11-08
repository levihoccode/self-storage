# Hàng chờ yêu cầu đặt kho

- **Route:** `/fm/rental-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.1 / 1.3

## Mục đích

FM xử lý `RentalRequest` gửi tới cơ sở mình: duyệt/từ chối request mới; request đã convert
sang đơn thì theo dõi tiếp ở màn **Đơn thuê kho** (`/fm/rental-orders`).

## Dữ liệu hiển thị

- Nguồn: `GET /api/fm/rental-requests?facilityId=&page=0&size=100`.
- Mỗi dòng có `status` (trạng thái request), `orderId`/`orderStatus` (nếu đã convert),
  `needsRepropose`, `lastProposal`, `rejectionCount`, `maxRejectionCount`.
- Tab **Chờ xử lý**: request `Pending` hoặc `needsRepropose = true`.
- Tab **Đã xử lý**: request `Approved`/`Converted` và `needsRepropose = false`.
- Tab **Từ chối**, **Hết hạn**: theo `status` request.
- Không hiển thị cột `facility`; FM chỉ thấy cơ sở của mình.

## Nhãn hiển thị

FE hiển thị nhãn theo request/flag, không hiển thị trạng thái kỹ thuật `Converted`:

| Điều kiện | Nhãn |
|---|---|
| request `Pending` | Chờ xử lý |
| `needsRepropose = true` | Cần đề xuất lại |
| request `Converted`, flag `false` | Đã được xử lý |
| request `Rejected` | Đã từ chối |
| request `Expired` | Hết hạn |
| request `Approved` | Đã duyệt |

`Converted` là trạng thái của `RentalRequest`, không phải `RentalOrder`; detail có thể hiện
trạng thái request, nhưng row dùng nhãn ở trên.

## Actions

- **Đề xuất** (`status = Pending`): chọn khoang `Available` cùng cơ sở/loại khoang →
  `POST /api/fm/rental-requests/{id}/approve` body `{unitId}`.
- **Từ chối**: nhập lý do → `POST /api/fm/rental-requests/{id}/reject`
  body `{rejectReason, rejectNote?}`.
- **Đề xuất lại** (`needsRepropose = true`): điều hướng `/fm/rental-orders/{orderId}/re-propose`.
- **Đã được xử lý**: chỉ mở chi tiết; nút **Mở Đơn thuê kho** → `/fm/rental-orders`.
- Panel chi tiết hiện proposal gần nhất, `rejectionCount`/`maxRejectionCount`.

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
- `needsRepropose` do BE tính từ order/proposal; FE không tự suy luận.
- Trạng thái/action còn lại của đơn (chờ khách phản hồi/thanh toán, lịch) thuộc màn Đơn thuê kho.
- Không có tab `Wishlisted` — API không trả trạng thái này.
