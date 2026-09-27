# Theo dõi khách hàng & hợp đồng

- **Route:** `/fm/contracts`
- **Actor:** Facility Manager
- **Flow tham chiếu:** Cross-flow — suy ra từ mô tả actor FM ("Theo dõi các thông tin của khách hàng: hợp đồng cho thuê, thời gian thuê, payment status"), tương đương bản FM của `customer/06-my-contracts-dashboard.md` + `customer/07-contract-detail.md`

## Mục đích
FM xem toàn bộ hợp đồng đang hiệu lực tại cơ sở mình, tình trạng thanh toán, để chủ động theo dõi thay vì chờ khách chủ động thao tác.

## Navigation

- **Vào từ:** FM shell, `/fm/reports` hoặc notification.
- **Đi tới:** contract detail surface; `/fm/invoices`; `/fm/return-requests`; `/fm/extend-requests`; `/my-storage/:id` chỉ là customer-facing counterpart, không phải link mặc định cho FM.

## Dữ liệu hiển thị
- Danh sách `RentalContract WHERE facility (qua StorageUnit) = FM.facility`: khách hàng, khoang, `start_date`/`end_date`, `status`, tình trạng thanh toán (tổng hợp `Invoice`)
- Badge "còn hiệu lực / sắp hết hạn / quá hạn" tính từ `end_date` giống dashboard khách
- Filter theo trạng thái, sort theo ngày hết hạn

## Actions
- Bấm vào 1 hợp đồng → xem chi tiết (thông tin khoang, hóa đơn, lịch sử `ExtendRequest`/`ReturnRequest`/`SupportRequest` liên quan — read-only cho FM, thao tác duyệt thực hiện ở các trang queue riêng)

## States / UI trạng thái
- Giống logic badge ở `customer/06-my-contracts-dashboard.md`, tính trực tiếp từ `end_date` tại thời điểm query

## API liên quan
- `GET /api/fm/contracts?status=`
- `GET /api/fm/contracts/{id}`

## Edge case / Lưu ý UX
- Đây là trang **đọc tổng quan**, không phải nơi FM duyệt gia hạn/trả kho/sự cố — các action đó nằm ở `fm/04-return-request-queue.md`, `fm/05-extend-request-queue.md`, `fm/06-support-request-queue.md`. Cân nhắc thêm link nhanh từ đây sang các trang đó nếu hợp đồng đang có request mở
- **Lưu ý:** trang được suy ra từ mô tả actor, chưa có flow nào mô tả chi tiết UI — cần xác nhận phạm vi thật sự với team (có thể trùng lặp một phần với dashboard FM tổng nếu thiết kế sau này)
