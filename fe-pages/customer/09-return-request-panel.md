# Panel yêu cầu trả kho

- **Route:** modal/panel trên `/customer/contracts/{id}` (không phải route riêng)
- **Actor:** Customer (chủ hợp đồng)
- **Flow tham chiếu:** 3.4 (branch `specs/flow-3`)

## Mục đích
Cho khách gửi yêu cầu trả kho, chọn ngày mong muốn, theo dõi tiến trình FM phân công nhân viên xử lý on-site (chi tiết on-site thuộc Flow 2.5, không xử lý ở panel này).

## Navigation

- **Vào từ:** panel trên `/my-storage/:id`.
- **Đi tới:** quay lại `/my-storage/:id` sau khi gửi/hủy; `/appointments` khi FM tạo lịch trả kho; `/notifications` để theo dõi phân công/trạng thái.

## Dữ liệu hiển thị
- Form nhập `preferred_date` (validate `>= today`), `reason` (tuỳ chọn)
- Sau khi tạo: trạng thái `ReturnRequest.status` (Pending / Assigned / Canceled / Completed)

## Actions
- [Gửi yêu cầu trả kho] → tạo `ReturnRequest(status=Pending)`
- [Hủy yêu cầu] (chỉ khi `status = Pending`) → nhập `cancel_reason` (tuỳ chọn)

## States / UI trạng thái
- Pending: "Đang chờ FM phân công nhân viên" + nút Hủy
- Assigned: "Đã phân công nhân viên, ngày hẹn dự kiến {preferred_date}", **không cho hủy qua web** (ghi chú liên hệ FM/FS trực tiếp)
- Completed: chuyển tiếp sang trạng thái hợp đồng `Ended` (dashboard tự cập nhật, hợp đồng biến mất khỏi danh sách Active)

## API liên quan
- `POST /api/customer/contracts/{id}/return-requests`
- `DELETE /api/customer/return-requests/{id}` (body: `cancel_reason`)

## Edge case / Lưu ý UX
- **Được phép gửi ngay cả khi hợp đồng đã quá hạn** — đây là trường hợp cần trả kho nhất, không được ẩn nút này khi quá hạn (khác với nút Gia hạn)
- Không cho tạo khi đang có `ExtendRequest` mở (`PendingApproval`/`ApprovedPendingPayment`) — nếu khách muốn trả kho, cần hủy yêu cầu gia hạn `PendingApproval` trước
- Giờ/ca hẹn cụ thể do Flow 2.5 chốt khi tạo `Appointment` — panel này chỉ thu `preferred_date`, không có chọn khung giờ chi tiết
