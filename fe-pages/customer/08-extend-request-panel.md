# Panel yêu cầu gia hạn hợp đồng

- **Route:** modal/panel trên `/customer/contracts/{id}` (không phải route riêng)
- **Actor:** Customer (chủ hợp đồng)
- **Flow tham chiếu:** 3.3 (branch `specs/flow-3`)

## Mục đích
Cho khách gửi yêu cầu gia hạn thêm N tháng, theo dõi trạng thái duyệt của FM và thanh toán hóa đơn gia hạn khi được duyệt.

## Dữ liệu hiển thị
- Form nhập `extra_months`
- Sau khi tạo: trạng thái `ExtendRequest.status` (PendingApproval / ApprovedPendingPayment / Rejected / Expired / Completed)
- Khi `ApprovedPendingPayment`: số tiền hóa đơn gia hạn (= `extra_months × RentalContract.monthly_price`), `due_date`
- Khi `Rejected`: hiển thị `reject_reason`

## Actions
- [Gửi yêu cầu gia hạn] → `POST` tạo `ExtendRequest(status=PendingApproval)`
- [Hủy yêu cầu] (chỉ khi `status = PendingApproval`) → nhập `cancel_reason` (tuỳ chọn)
- [Thanh toán gia hạn] (khi `ApprovedPendingPayment`) → điều hướng sang hóa đơn Extension ở trang Hóa đơn

## States / UI trạng thái
- PendingApproval: "Đang chờ FM duyệt gia hạn" + nút Hủy
- ApprovedPendingPayment: hiện số tiền + `due_date` + nút Thanh toán, **không cho hủy qua web** (ẩn nút Hủy, ghi chú "liên hệ FM trực tiếp nếu cần hủy")
- Rejected: hiện lý do từ chối, cho phép gửi yêu cầu mới
- Expired: hóa đơn quá hạn thanh toán, tự động hủy — hiện thông báo và cho gửi lại yêu cầu mới
- Completed: hiện `end_date` mới của hợp đồng

## API liên quan
- `POST /api/customer/contracts/{id}/extend-requests`
- `DELETE /api/customer/extend-requests/{id}` (body: `cancel_reason`)

## Edge case / Lưu ý UX
- Không cho tạo khi hợp đồng đã quá hạn (`today > end_date`) — panel này chỉ mở được khi `available_actions` cho phép (xem `07-contract-detail.md`)
- Không cho tạo khi đang có `ReturnRequest` mở — BE trả 409, FE hiển thị message rõ ràng: "Bạn đang có yêu cầu trả kho, vui lòng hủy trước khi gia hạn"
- Cộng tháng theo lịch vào `end_date` hiện tại; nếu ngày không tồn tại ở tháng đích thì lấy ngày cuối tháng (vd 31/01 + 1 tháng = 28/02) — chỉ cần hiển thị `end_date` mới do BE trả về, không tự tính ở FE
