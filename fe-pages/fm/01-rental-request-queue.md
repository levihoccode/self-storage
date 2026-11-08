# Hàng chờ yêu cầu đặt kho

- **Route:** `/fm/rental-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.1 / 1.3

## Mục đích

FM xử lý `RentalRequest` gửi tới cơ sở mình: duyệt/từ chối request mới, và tiếp tục xử lý
request đã convert thành đơn (đề xuất lại, chờ khách phản hồi/thanh toán).

## Dữ liệu hiển thị

- Nguồn: `GET /api/fm/rental-requests?facilityId=&page=0&size=100`.
- Mỗi dòng có `status` (trạng thái request), `orderId`/`orderStatus` (nếu đã convert),
  `nextAction`, `lastProposal`, `rejectionCount`, `maxRejectionCount`.
- Tab **Chờ xử lý**: `nextAction = APPROVE_REQUEST` hoặc `REPROPOSE_UNIT`.
- Tab **Đã duyệt**: request `Approved`/`Converted` và `nextAction != REPROPOSE_UNIT`
  (`WAIT_CUSTOMER`/`VIEW`).
- Tab **Từ chối**, **Hết hạn**: theo `status` request.
- Không hiển thị cột `facility`; FM chỉ thấy cơ sở của mình.

## Nhãn hiển thị

FE hiển thị nhãn theo `nextAction`, không hiển thị trạng thái kỹ thuật `Converted`:

| Điều kiện | Nhãn |
|---|---|
| `APPROVE_REQUEST` | Chờ xử lý |
| `REPROPOSE_UNIT` | Cần đề xuất lại |
| `WAIT_CUSTOMER` | Chờ khách phản hồi |
| `VIEW` + order `Pending` + proposal `Agreed` | Chờ khách thanh toán |
| `VIEW` + order `Deposited` | Chờ khách đặt lịch |
| `VIEW` + order `Scheduled`/`InProgress` | Đã có lịch |
| `VIEW` + order `Canceled`/`Done` | Đã đóng |
| `VIEW` + request `Approved` chưa có order | Chờ khách xác minh email |
| còn lại | Nhãn theo trạng thái request |

`Converted` là trạng thái của `RentalRequest`, không phải `RentalOrder`; detail có thể hiện
trạng thái request, nhưng row trong hàng đợi dùng nhãn hành động ở trên.

## Actions

- **Đề xuất** (`APPROVE_REQUEST`): mở form chọn khoang `Available` cùng cơ sở/loại khoang →
  `POST /api/fm/rental-requests/{id}/approve` body `{unitId}`.
- **Từ chối**: nhập lý do → `POST /api/fm/rental-requests/{id}/reject`
  body `{rejectReason, rejectNote?}`.
- **Đề xuất lại** (`REPROPOSE_UNIT`): điều hướng `/fm/rental-orders/{orderId}/re-propose`.
- **Xem** (`WAIT_CUSTOMER`/`VIEW`): chỉ mở panel chi tiết; panel hiện proposal gần nhất,
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
- Request `Converted` vẫn nằm trong hàng đợi để FM xử lý bước tiếp theo của đơn.
- Không có tab `Wishlisted` — API không trả trạng thái này.
