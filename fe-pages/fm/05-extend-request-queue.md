# Hàng chờ yêu cầu gia hạn

- **Route:** `/fm/extend-requests`
- **Actor:** Facility Manager
- **Flow tham chiếu:** 3.3 (branch `specs/flow-3`) — nghiệp vụ duyệt/từ chối cụ thể thuộc Flow 6, hiện chưa có tài liệu chi tiết riêng, chỉ có cross-reference từ Flow 3

## Mục đích
FM duyệt hoặc từ chối các yêu cầu gia hạn hợp đồng do khách gửi.

## Navigation

- **Vào từ:** FM shell, notification hoặc `/fm/contracts`.
- **Đi tới:** `/fm/contracts` sau khi duyệt/từ chối; customer `/invoices/:id` khi tạo hóa đơn gia hạn; `/notifications` sau mutation.

## Dữ liệu hiển thị
- Danh sách `ExtendRequest WHERE status = PendingApproval` thuộc facility của FM, JOIN hợp đồng/khách
- `extra_months`, `requested_at`, hợp đồng hiện tại (`end_date`, `monthly_price`)
- Số tiền hóa đơn gia hạn dự kiến (= `extra_months × monthly_price`, hiển thị trước để FM tham khảo)

## Actions
- [Duyệt] → hệ thống tự tạo `Invoice(type=Extension)`, `ExtendRequest.status = ApprovedPendingPayment`
- [Từ chối] → nhập `reject_reason`

## States / UI trạng thái
- Badge số lượng đang chờ duyệt
- Sau khi duyệt: chuyển sang tab "Đã duyệt, chờ khách thanh toán" (tham khảo, chờ khách tự thanh toán ở trang Hóa đơn)

## API liên quan
- `GET /api/fm/extend-requests?status=PendingApproval`
- `POST /api/fm/extend-requests/{id}/approve`
- `POST /api/fm/extend-requests/{id}/reject` (body: `reject_reason`)

## Edge case / Lưu ý UX
- **Lưu ý:** Flow 6 (Xử lý quá hạn/gia hạn) chưa có tài liệu phân tích riêng — trang này dựng dựa trên mô tả tối thiểu ở Flow 3.3 ("FM duyệt (Flow 6)"), cần xác nhận lại khi Flow 6 được viết chi tiết, đặc biệt về UI xử lý overdue/penalty đi kèm
- FM cần thấy rõ nếu hợp đồng gần hết hạn hoặc khách đang có tranh chấp phí trước khi duyệt gia hạn (chưa có trong spec, gợi ý bổ sung khi làm UI)
