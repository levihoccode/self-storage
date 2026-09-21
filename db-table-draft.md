# RentalRequest
**Owner:** Flow 1

**Flow 2 usage:** không đọc trực tiếp; thông tin thuê lấy qua `RentalOrder`.
# RentalOrder
**Owner:** Flow 1

**Flow 2 usage:** đọc `customer_id`/`unit_id`; set `status = Done` ở 2.4 - Flow 2 là nơi duy nhất set giá trị này. Enum theo contract Flow 1: `Pending/Deposited/Scheduled/InProgress/Done/Canceled/Expired`.
# Invoice
**Owner:** Flow 1

**Flow 2 usage:** kiểm tra hóa đơn `type = Deposit` đã `Paid` trước khi check-in; tạo `type = Rental` cho tháng đầu (2.3) và `type = Penalty` cho phí trả kho (2.5.3).
# ProposalFeedback
**Owner:** Flow 1

**Flow 2 usage:** chỉ đọc để biết khoang khách đã duyệt; khoang hiệu lực là proposal `Agreed` mới nhất.
# PaymentTransaction
**Owner:** Flow 1

**Flow 2 usage:** dùng chung cơ chế thanh toán của Flow 1 - tạo bản ghi `Pending` trước khi redirect sang VNPay, IPN idempotent theo `gateway_transaction_no` (2.3).
# Appointment
**Owner:** Flow 1 (tạo ở 1.5)

**Flow 2 usage:** đọc lịch hẹn và FS phụ trách qua `date`, `staff_id`, `facility_id`; set `arrived_at` + `status = Done` khi ghi nhận khách đến (2.2) - đây là ghi duy nhất của Flow 2 lên bảng này. Flow 2.5 tạo `Appointment(type = RETURN)` từ `ReturnRequest` (2.5.1).
# RentalAppointment
**Owner:** Flow 1

**Flow 2 usage:** đọc để lấy đơn của một lịch hẹn; Flow 2.5 tạo bản ghi tương ứng khi sinh lịch `RETURN`.
# HandoverRecord
**Owner:** Flow 1 (tạo ở 1.5 cùng `Appointment`)

**Flow 2 usage:** bật 4 cờ checklist và chốt `result` trong 2.2-2.4; không có `staff_id` - FS phụ trách lấy qua `Appointment.staff_id`. Flow 2.5 chỉ đọc `inspection_notes`/`inspection_photos` để đối chiếu hiện trạng lúc trả kho.
# RentalContract
**Overview:** hợp đồng thuê, được sinh và ký on-site ở Flow 2.3 sau khi khách xác nhận hiện trạng khoang. `Invoice.contract_id` tham chiếu tới bảng này.
- order_id (1 - 1: RentalOrder)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- code
- terms_version - snapshot version điều khoản khách đã đồng ý (Flow 4)
- monthly_price - giá thuê chốt tại thời điểm ký
- deposit_amount - tiền cọc đã thu ở Flow 1.3
- period - số tháng thuê
- start_date - mốc bắt đầu tính tiền thuê, theo chính sách Flow 4
- end_date
- signed_at
- signature - URL ảnh chữ ký; MVP là ảnh/scan trang ký của hợp đồng giấy
- pdf_url - file hợp đồng lưu trữ; MVP là bản scan FS upload, không sinh PDF tự động
- status (Draft/Signed/Active/Ended/Canceled)
  - Signed: đã ký nhưng chưa bàn giao
  - Active: đã bàn giao, đang có hiệu lực

# UnitAccessKey
**Overview:** quyền truy cập khoang chứa đã bàn giao cho khách.
- unit_id (N - 1: StorageUnit)
- order_id (N - 1: RentalOrder)
- access_type (PhysicalKey/AccessCode) - loại khóa đã bàn giao, theo cờ `enabledKeyAccess`/`enabledCodeAccess` của cơ sở/khoang (Flow 5)
- quantity - số chìa đã giao, dùng khi `access_type = PhysicalKey`
- code_hash - hash của mã truy cập, dùng khi `access_type = AccessCode`
- issued_at, revoked_at
- status (Active/Revoked/Lost)

# CheckoutRecord
**Overview:** biên bản trả kho (Flow 2.5). Tách riêng khỏi `HandoverRecord` vì `HandoverRecord` chỉ chịu trách nhiệm tới khâu bàn giao.
- order_id (1 - 1: RentalOrder)
- appointment_id (N - 1: Appointment) - lịch hẹn trả kho đang xử lý; cập nhật sang lịch mới khi khách phải quay lại dọn nốt
- unit_id (N - 1: StorageUnit)
- staff_id (N - 1: Account) - FS lập biên bản
- **Checklist cột mốc trả kho** (buổi trả kho có thể kéo dài vài ngày, mỗi cột mốc có timestamp để FM/FS theo dõi tiến độ):
  - is_unit_emptied (default: false) - khoang đã dọn trống hoàn toàn
  - unit_emptied_at (nullable)
  - is_inspected (default: false) - FS đã kiểm tra hiện trạng và khách đã ký biên bản
  - inspected_at (nullable)
  - is_access_revoked (default: false) - đã thu chìa / vô hiệu hóa mã truy cập
  - access_revoked_at (nullable)
  - is_fee_settled (default: false) - các hóa đơn phát sinh đã `Paid`
  - fee_settled_at (nullable)
  - is_deposit_settled (default: false) - đã đối trừ và xử lý xong tiền cọc
  - deposit_settled_at (nullable)
- **Kết quả kiểm tra:**
  - cleanliness - tình trạng vệ sinh
  - damages - danh sách hư hỏng ghi nhận so với `HandoverRecord`
  - photos - List<String>, ảnh hiện trạng lúc trả
  - returned_key_quantity - số chìa thu lại, đối chiếu `UnitAccessKey.quantity`
  - customer_signature
- **Trạng thái cuối cùng của biên bản:**
  - result (IN_PROGRESS/COMPLETED/PENDING_ITEMS) - default: IN_PROGRESS
  - completed_at (nullable)
- note
- created_at

**NOTES:**
- **Giữ `staff_id`** dù `HandoverRecord` đã bỏ: một biên bản trả kho có thể trải qua nhiều `Appointment` (nhánh `PENDING_ITEMS` sinh lịch hẹn mới cho khách quay lại dọn), nên không suy được FS từ một lịch hẹn duy nhất như `HandoverRecord`.
- `PENDING_ITEMS`: khoang còn tài sản, chưa hoàn tất trả kho, **chưa thu hồi `UnitAccessKey`** vì khách còn cần vào lấy đồ. Khách quay lại dọn thì cập nhật tiếp trên **cùng một bản ghi**, không tạo mới.
- `COMPLETED`: đủ 5 cột mốc `true`, khoang sẵn sàng chuyển `Maintenance`.
