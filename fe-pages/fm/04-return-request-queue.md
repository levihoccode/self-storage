# Hàng chờ yêu cầu trả kho

- **Route:** `/fm/return-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 3.4 (branch `specs/flow-3`)

## Mục đích
FM phân công FS xử lý các yêu cầu trả kho do khách gửi từ trang chi tiết hợp đồng.

## Dữ liệu hiển thị
- Danh sách `ReturnRequest WHERE status = Pending` thuộc facility của FM, JOIN thông tin hợp đồng/khách/khoang
- `preferred_date`, `reason` (nếu có)

## Actions
- Chọn 1 request → chọn FS (thuộc facility) → [Phân công] → `ReturnRequest.status = Assigned`, hệ thống tự tạo `Appointment(type=RETURN)` (Flow 2.5.1, FM không thao tác thêm ở đó)

## States / UI trạng thái
- Badge số lượng request đang chờ phân công
- Sau khi phân công, item chuyển sang tab "Đã phân công" (tham chiếu, không còn thao tác gì thêm ở trang này — theo dõi tiến độ tiếp theo thuộc Flow 2.5)

## API liên quan
- `GET /api/fm/return-requests?status=Pending`
- `POST /api/fm/return-requests/{id}/assign` (body: `assigned_staff_id`)

## Edge case / Lưu ý UX
- `assigned_staff_id` phải là FS có `AccountFacilityAssignment` với đúng facility của khoang — validate ở BE (403 nếu sai), FE chỉ nên cho chọn từ danh sách FS hợp lệ
- Được phép có request này ngay cả khi hợp đồng đang quá hạn — không cần cảnh báo đặc biệt, xử lý bình thường như mọi request khác
