# Chọn lịch hẹn check-in & bàn giao

- **Route:** `/customer/orders/{orderId}/appointment`
- **Actor:** Customer (đã đăng nhập, đã đặt cọc thành công)
- **Flow tham chiếu:** 1.5 (branch `specs/flow-1`) / 2.1 (branch `specs/flow-2-2.5`)

## Mục đích
Sau khi đặt cọc thành công (`RentalOrder.status = Deposited`), khách chọn ngày giờ đến cơ sở để check-in, kiểm tra khoang, ký hợp đồng và nhận bàn giao.

## Navigation

- **Vào từ:** `/invoices/:id` sau khi thanh toán cọc; notification hoặc link từ order.
- **Đi tới:** `/staff/schedule` là workspace của FS sau khi appointment được tạo; customer quay lại `/my-storage` hoặc `/notifications`.

## Dữ liệu hiển thị
- Thông tin cơ sở: địa chỉ, giờ hoạt động
- Danh sách khung giờ khả dụng, sinh từ giờ hoạt động `Facility` (MVP: 3 khung cố định/ngày, không giới hạn capacity theo số khách)
- Checklist "cần chuẩn bị gì": giấy tờ tùy thân (CCCD/Passport)
- Giới hạn ngày hẹn: trong vòng 7 ngày kể từ lúc cọc, ngày hẹn cách lúc cọc tối đa 14 ngày (Ngưỡng mặc định MVP)

## Actions
- Chọn ngày + khung giờ → [Xác nhận lịch hẹn] → tạo `Appointment(type=CHECKIN, status=Pending)`
- [Dời lịch] (khi đã có `Appointment`) — tối đa 2 lần, báo trước ít nhất 24h

## States / UI trạng thái
- Trạng thái chờ FM phân công FS (`RentalOrder.status: Scheduled → InProgress`)
- Trạng thái đã có FS phụ trách (hiển thị tên nhân viên nếu cần)
- Cảnh báo nếu sắp hết hạn 30 ngày kể từ lúc cọc mà chưa bàn giao (mất cọc + hủy đơn)

## API liên quan
- `GET /api/customer/rental-orders/{id}/appointment-slots`
- `POST /api/customer/rental-orders/{id}/appointments`
- `PATCH /api/customer/appointments/{id}` (dời lịch)

## Edge case / Lưu ý UX
- Chỉ hiện trang này sau khi `Invoice` cọc đã `Paid` — nếu khách vào thẳng URL mà chưa cọc, redirect về trang Hóa đơn
- Không hỗ trợ lịch "tham quan kho trước khi cọc" trong MVP (Advanced Feature) — không nên thiết kế UI gợi ý tính năng này
- Nhắc khách rõ ràng: đặt lịch trong 7 ngày kể từ lúc cọc, nếu không sẽ có cron tự hủy đơn (mất cọc)
