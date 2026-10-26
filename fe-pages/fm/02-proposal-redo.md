# Đề xuất lại khoang

- **Route:** `/fm/rental-orders/{id}/re-propose`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 1.3 (branch `specs/flow-1`)

## Mục đích
Khi khách từ chối khoang được đề xuất, hoặc khoang bị người khác chiếm mất trước khi khách kịp đồng ý/đặt cọc, FM cần chọn khoang khác và gửi đề xuất mới.

## Navigation

- **Vào từ:** `/fm/rental-requests` hoặc notification khi proposal bị từ chối/mất khoang.
- **Đi tới:** quay lại request/order queue sau khi gửi; customer theo dõi tại `/proposals`; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Thông tin đơn: khách hàng, `unit_type` mong muốn, lịch sử các `ProposalFeedback` trước đó (kèm `note` lý do từ chối nếu có)
- Danh sách khoang `Available` còn lại cùng `unit_type`/`facility`
- Số lần đã từ chối / ngưỡng tối đa (3 lần MVP)

## Actions
- Chọn `unit_id` mới → [Đề xuất lại] → tạo `ProposalFeedback(status=Pending)` mới
- [Huỷ đơn] — chỉ hiện khi đơn chưa đóng (`Canceled`/`Done`). Dùng khi khách không phản hồi proposal và FM không liên hệ được. Bấm mở ConfirmDialog (tone danger) ghi rõ lý do cố định "Khách không còn nhu cầu" và hệ quả (huỷ proposal đang chờ, huỷ hoá đơn chưa trả, trả khoang; cọc đã trả chưa hoàn tự động). Xác nhận → `POST /api/fm/rental-orders/{id}/cancel`

## States / UI trạng thái
- Cảnh báo khi đã đạt ngưỡng tối đa lần từ chối — hệ thống tự chuyển `RentalOrder.status = Canceled`, FM chỉ xem lịch sử, không đề xuất tiếp được
- Sau khi huỷ đơn thành công: hiện thông báo "Đã huỷ đơn", đơn chuyển trạng thái đã đóng và khoá form đề xuất. Lỗi 409 (đơn đã đóng ở nơi khác): hiện nguyên câu của BE và tải lại đơn
- Trigger nguồn gốc khác nhau cần phân biệt trên UI: do khách chủ động từ chối (1.3) hay do khách từ chối tại chỗ lúc check-in (Flow 2, `HandoverRecord.Rejected`)

## API liên quan
- `GET /api/fm/rental-orders/{id}`
- `POST /api/fm/rental-orders/{id}/re-propose` (body: `unit_id`)
- `POST /api/fm/rental-orders/{id}/cancel` (không body)

## Edge case / Lưu ý UX
- Đổi khoang sau khi khách đã cọc (trigger từ Flow 2) cần tính thêm chênh lệch mức cọc cũ/mới + phí đổi khoang (`Policy.fee.unit_change`) — hiển thị rõ số tiền chênh lệch cho FM biết trước khi gửi đề xuất, dù việc thu/hoàn tiền thực hiện ở bước sau
