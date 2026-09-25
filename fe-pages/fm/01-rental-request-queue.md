# Hàng chờ yêu cầu đặt kho

- **Route:** `/fm/rental-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.1

## Mục đích
FM xử lý các `RentalRequest` gửi tới chi nhánh mình phụ trách: gán khoang phù hợp hoặc từ chối.

## Navigation

- **Vào từ:** FM shell sau login hoặc notification về request mới.
- **Đi tới:** details/approve/reject surface; `/fm/rental-orders/:id/re-propose` khi cần đề xuất lại; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `RentalRequest WHERE facility_id = FM.facility AND status = Pending` (danh sách entry, có nút details/response/update status)
- Filter theo `status` (Pending/Approved/Rejected/Expired/Wishlisted)
- Không hiển thị cột `facility` (đã chốt — request đến thẳng đúng chi nhánh của FM, không cần liệt kê lại)

## Actions
- [Details] — xem đầy đủ thông tin khách + nhu cầu
- [Approve] — nhập `unit_id` phù hợp (chọn từ danh sách khoang `Available` cùng `unit_type`/`facility`)
- [Reject] — nhập `reject_reason`

## States / UI trạng thái
- Conflict 409 khi duyệt: khoang vừa được người khác chiếm dụng ("Khoang đã không còn khả dụng, vui lòng chọn khoang khác") hoặc request đã bị FM khác xử lý trước ("Yêu cầu đã được xử lý bởi FM khác") — request giữ nguyên `Pending`, FE cần refetch danh sách
- Badge "chưa có tài khoản" khi `customer_id IS NULL` sau khi duyệt — nhắc FM biết đơn này đang chờ khách đăng ký

## API liên quan
- `GET /api/fm/rental-requests?status=`
- `POST /api/fm/rental-requests/{id}/approve` (body: `unit_id`)
- `POST /api/fm/rental-requests/{id}/reject` (body: `reject_reason`)

## Edge case / Lưu ý UX
- Toàn bộ API yêu cầu **facility scope**: FM chỉ thấy/thao tác request thuộc `Facility.fm_account_id = current_user`, kiểm tra ở BE (không chỉ ẩn ở FE)
- Duyệt chạy trong 1 transaction với conditional update + re-check khoang — UI nên disable nút Approve ngay sau khi bấm để giảm race, nhưng vẫn phải xử lý lỗi 409 trả về
