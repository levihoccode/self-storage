# Chi tiết khoang chứa & hợp đồng

- **Route:** `/customer/contracts/{id}`
- **Actor:** Customer (đã đăng nhập, chủ hợp đồng)
- **Flow tham chiếu:** 3.2 (branch `specs/flow-3`)

## Mục đích
Trang trung tâm để khách xem đầy đủ thông tin 1 hợp đồng và thực hiện các action: gia hạn, trả kho, báo sự cố — theo đúng danh sách `available_actions` do BE trả về.

## Navigation

- **Vào từ:** `/my-storage` khi chọn kho; notification hoặc deep link hợp đồng.
- **Đi tới:** panel gia hạn, panel trả kho, panel báo sự cố; `/invoices/:id`; `/appointments`; quay lại `/my-storage`.

## Dữ liệu hiển thị
- Thông tin khoang: facility, vị trí, type, size (chỉ đọc)
- `RentalContract`: `signed_at`, `start_date`/`end_date`, `deposit_amount`, `monthly_price`; nút [Tải hợp đồng] chỉ hiện khi có `pdf_url`
- Danh sách `Invoice WHERE contract_id = :id OR order_id = RentalContract.order_id` (gồm cả hóa đơn cọc), sort `created_at desc`
- Lịch sử lịch hẹn & bàn giao (`Appointment` + `HandoverRecord`, chỉ đọc)
- `available_actions` — mảng action BE tính sẵn, **FE chỉ render theo mảng này, không tự suy luận business rule**

## Actions (render theo `available_actions`, xem bảng quyết định ở flow 3.2)
- `[Trả kho]` → mở `customer/09-return-request-panel.md`
- `[Yêu cầu gia hạn]` → mở `customer/08-extend-request-panel.md`
- `[Báo sự cố]` → mở `customer/10-support-request-form.md` (luôn hiện khi hợp đồng `Active`)
- `[Hủy yêu cầu gia hạn]` / `[Hủy yêu cầu trả kho]` — khi có request đang mở ở trạng thái cho phép hủy
- `[Thanh toán gia hạn]` — điều hướng sang hóa đơn Extension tương ứng

## States / UI trạng thái
- Banner "sắp hết hạn" (làm nổi bật nút Gia hạn) khi `end_date - today <= N` ngày
- Banner "quá hạn, liên hệ FM" khi `today > end_date` — ẩn nút Gia hạn
- Trạng thái "Đang chờ FM duyệt gia hạn" / "Đang chờ phân công nhân viên" / "Đã phân công, ngày hẹn dự kiến..." khi có request đang mở (xem bảng ở 3.2)

## API liên quan
- `GET /api/customer/contracts/{id}` — trả kèm `open_extend_request`, `open_return_request`, `is_overdue`, `is_expiring_soon`, `available_actions`

## Edge case / Lưu ý UX
- Không được vừa gia hạn vừa trả kho cùng lúc — UI phải ẩn hẳn nút bị chặn theo `available_actions`, không chỉ disable mờ để tránh gây hiểu lầm
- `[Gia hạn]` ẩn hoàn toàn khi hợp đồng đã quá hạn — không gia hạn qua web được nữa, banner hướng dẫn liên hệ FM
- Đây là page phức tạp nhất Flow 3 — nên tách các phần thành section/tab rõ ràng: Thông tin hợp đồng / Hóa đơn / Lịch sử bàn giao / Hành động
