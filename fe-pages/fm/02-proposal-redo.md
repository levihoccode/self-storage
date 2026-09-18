# Đề xuất lại khoang

- **Route:** `/fm/rental-orders/{id}/re-propose`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.3 (branch `specs/flow-1`)

## Mục đích
Khi khách từ chối khoang được đề xuất, hoặc khoang bị người khác chiếm mất trước khi khách kịp đồng ý/đặt cọc, FM cần chọn khoang khác và gửi đề xuất mới.

## Dữ liệu hiển thị
- Thông tin đơn: khách hàng, `unit_type` mong muốn, lịch sử các `ProposalFeedback` trước đó (kèm `note` lý do từ chối nếu có)
- Danh sách khoang `Available` còn lại cùng `unit_type`/`facility`
- Số lần đã từ chối / ngưỡng tối đa (3 lần MVP)

## Actions
- Chọn `unit_id` mới → [Đề xuất lại] → tạo `ProposalFeedback(status=Pending)` mới

## States / UI trạng thái
- Cảnh báo khi đã đạt ngưỡng tối đa lần từ chối — hệ thống tự chuyển `RentalOrder.status = Canceled`, FM chỉ xem lịch sử, không đề xuất tiếp được
- Trigger nguồn gốc khác nhau cần phân biệt trên UI: do khách chủ động từ chối (1.3) hay do khách từ chối tại chỗ lúc check-in (Flow 2, `HandoverRecord.Rejected`)

## API liên quan
- `GET /api/fm/rental-orders/{id}`
- `POST /api/fm/rental-orders/{id}/re-propose` (body: `unit_id`)

## Edge case / Lưu ý UX
- Đổi khoang sau khi khách đã cọc (trigger từ Flow 2) cần tính thêm chênh lệch mức cọc cũ/mới + phí đổi khoang (`Policy.fee.unit_change`) — hiển thị rõ số tiền chênh lệch cho FM biết trước khi gửi đề xuất, dù việc thu/hoàn tiền thực hiện ở bước sau
