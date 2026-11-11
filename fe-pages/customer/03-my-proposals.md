# Kiểm tra kho của tôi (duyệt đề xuất khoang)

- **Route:** `/customer/proposals`
- **Actor:** Customer (đã đăng nhập)
- **Flow tham chiếu:** 1.3 (branch `specs/flow-1`)

## Mục đích
Sau khi FM duyệt `RentalRequest` và chỉ định khoang, khách cần xác nhận đồng ý hoặc từ chối khoang được đề xuất (`ProposalFeedback`) trước khi được phép đặt cọc.

## Navigation

- **Vào từ:** `/login` sau khi đăng nhập hoặc notification liên quan đến proposal.
- **Đi tới:** `/invoices/:id` sau khi đồng ý; giữ tại page và chờ FM đề xuất lại sau khi từ chối; `/notifications` để xem event liên quan.

## Dữ liệu hiển thị
- Danh sách `ProposalFeedback.status = Pending` của khách, mỗi entry gồm: thông tin khoang (`unit_id` → code, `unit_type`, `facility`), giá thuê, ngày đề xuất
- Trang chỉ có một danh sách, **không chia tab**: màn này để khách xác nhận khoang FM đề xuất từ yêu cầu thuê. Kho đang thuê xem ở `06-my-contracts-dashboard.md` (`/my-storage`) — tab "Đang sử dụng" của bản cũ đã bỏ.

## Actions
- [Đồng ý] → `ProposalFeedback.status = Agreed`, tạo `Invoice(type=Deposit)`, điều hướng sang trang Hóa đơn
- [Từ chối] → nhập `note` (lý do), `ProposalFeedback.status = Rejected`, chờ FM đề xuất lại

## States / UI trạng thái
- Banner cảnh báo khi khoang đã bị người khác đặt cọc trước lúc khách bấm Đồng ý (409 — "Khoang đã có người đặt cọc trước")
- Đếm số lần đã từ chối / giới hạn tối đa (mặc định 3 lần theo Ngưỡng MVP flow-1) — hiển thị cảnh báo khi gần chạm ngưỡng
- Trạng thái "Hết hạn" nếu quá `expires_at` mà chưa phản hồi
- Empty state: chưa có đề xuất nào

## API liên quan
- `GET /api/proposals?status=Pending`
- `POST /api/proposals/{id}/agree`
- `POST /api/proposals/{id}/reject` (body: `note`)

## Edge case / Lưu ý UX
- Khi [Đồng ý] trả lỗi do khoang không còn `Available`, KHÔNG hủy đơn — chỉ báo khách chờ FM đề xuất khoang khác (proposal hiện tại tự chuyển `Expired`)
- Quá N lần từ chối (mặc định 3) → `RentalOrder.status = Canceled`, hiển thị thông báo rõ ràng khách cần gửi yêu cầu mới từ đầu
- `ProposalFeedback` mới cũng được tạo lại nếu khách từ chối khoang lúc check-in on-site (Flow 2) — trang này cũng nhận case đó
