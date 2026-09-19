# RentalRequest
**Overview:** chứa các thông tin được gửi từ form trên website.
- facility_id (N - 1: Facility)
- customer_email
- customer_phone
- unit_type
- start_date (MM/DD/YYYY)
- period - số tháng thuê
- unit_id (null as default)
- status (Pending/Reject/Approve/Expired) - Expired xảy ra khi trong trạng thái chờ khách hàng tạo tài khoản và timeout
# RentalOrder
**Overview:** chứa các thông tin đơn hàng đã được `Approve` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho bao gồm các thông tin:
- request_id (1 - 1: RentalRequest)
- customer_id (N - 1: Account)
- staff_id (N - 1: Account, null until the FS confirm and status pending -> in progress)
- appointment_date (MM/DD/YYYY)
- unit_id
- cancel_reason
- status: 
  - Pending: chờ FS được chỉ định xác nhận
  - InProgress: sau FS được chỉ đã xác nhận và đang trong quá trình hẹn gặp, tư vấn
  - Canceled: hủy đơn hàng
  - Done: Khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử 
# Invoice
**Overview:** chứa thông tin thanh toán của khách hàng (hóa đơn)
- order_id (1 - 1: RentalOrder) -> null as default -> Được gán nếu hóa đơn phát sinh từ `RentalOrder` (Đặt cọc)
- contract_id (N - 1: RentalContract) -> null as default -> được gán nếu hóa đơn phát sinh từ `RentalContract` (tiền thuê hàng tháng, gia hạn, tiền phạt, ...)
- customer_id (N - 1: Account)
- code
  - Cấu trúc mã hóa đề xuất: Gợi nhớ & Dễ lọcĐể thuận tiện tuyệt đối khi kiểm tra, mã hóa đơn nên mang ý nghĩa phân loại theo công thức:
  - Mã hóa đơn = Tiền tố nghiệp vụ (DEP - deposit, RNT - rental fee, ...) + mã chi nhánh (Q7 - Quận 7, ...) + YYMMDD (ngày tạo hóa đơn) + random
  - Ví dụ:
    + INV-DEP-Q7-260911-A89F: Hóa đơn cọc (DEP), chi nhánh Quận 7, ngày 11/09/2026.
    + INV-RNT-TD-261001-K312: Hóa đơn tiền thuê định kỳ (RNT), chi nhánh Thủ Đức.
    + INV-EXT-Q7-260915-091B: Hóa đơn dịch vụ ngoài / sự cố khóa (EXT).
- title
- desc
- status (Unpaid/Paid)
- amount
- created_at
- due_date

**NOTES:**
- Nếu cả 2 fields order_id và contract_id đều null, tức là hóa đơn từ việc yêu cầu dịch vụ hỗ trợ (`SupportRequest`)
# ProposalFeedback
- order_id (1 - 1: RentalOrder)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- status (Pending/Agreed/Rejected)
- note

**NOTES:**
- field note dùng để khi khách từ chối và muốn chọn lại, sẽ nêu lý do vì sao từ chối, ...

# PaymentTransaction
- invoice_id (N - 1: Invoice)
- gateway_transaction_no -  Mã giao dịch định danh từ cổng thanh toán/ngân hàng trả về (ví dụ mã vnpay_TransactionNo, payOS reference code, ...) -> Dùng để tra cứu, đối soát khi có khiếu nại
- transaction_content
- response_payload: JSON / TEXT, nullable -> Lưu toàn bộ log raw webhook/IPN để đối soát
- amount
- failure_reason
- paid_at
- create_at
- status (Pending/Failed/Success)

- NOTES: visa card only
# Appointment
**Overview:** lịch hẹn dùng chung cho mọi loại cuộc hẹn tại cơ sở. Được tạo khi khách yêu cầu hoặc hệ thống tạo lịch; dùng cho check-in, bàn giao, trả kho, xử lý sự cố tại kho, ...
- customer_id (1 - N: Account)
- staff_id (1 - N: Account) - nullable, FM gán ở Flow 5.3
- order_id (N - 1: RentalOrder) - nullable, null với lịch không gắn đơn (xử lý sự cố tại kho)
- facility_id (N - 1: Facility)
- type (CHECKIN, HANDOVER, RETURN, ...)
- cancel_reason
- appointment_date
- started_at - giờ bắt đầu có thể check-in
- end_at - giờ kết thúc ca xem kho này
- arrived_at - giờ ghi nhận khách check-in
- status:
  - Pending: trạng thái mặc định, trước khi khách đến
  - Done: khách đã đến
  - Canceled: lịch hẹn bị hủy

**NOTES:**
- `RETURN` do Flow 2.5 thêm vào để dùng cho buổi hẹn trả kho; Levi sẽ chốt lại bộ `type` sau khi các flow ổn định.
- Bảng nối `RentalAppointment` đã bỏ (A6/B5): `order_id` nằm thẳng trên bảng này, `facility_id` để FM lọc lịch theo cơ sở và validate FS mà không phải join `order -> unit -> facility`.
- Vòng đời lịch check-in (tạo, dời, hủy, tạo lại sau reject/no-show) do **Flow 1** quản lý; Flow 2 chỉ set `arrived_at` + `status = Done`. Lịch `RETURN` do Flow 2.5 tạo từ `ReturnRequest`.
- Khi đặt lại lịch sau reject/no-show thì tạo bản ghi **mới** (`staff_id = null`) để FM phân công lại; bản ghi cũ giữ nguyên làm lịch sử.
# HandoverRecord
**Overview:** theo dõi tiến trình check-in và bàn giao khoang chứa. **Flow 1.5 tạo** bản ghi cùng lúc với `Appointment` khi khách xác nhận lịch hẹn; Flow 2 sử dụng và cập nhật trong buổi on-site. Khi `result = COMPLETED` hoặc `REJECTED` là Flow 2 kết thúc.
- order_id (N - 1: RentalOrder)
- appointment_id (1 - 1: Appointment)
- unit_id (N - 1: StorageUnit)
- staff_id (N - 1: Account, nullable khi mới tạo; điền khi FM phân công FS)
- **Checklist tiến trình on-site:**
  - is_identity_verified (default: false) - xác minh danh tính người đến check-in
  - identity_verified_at (nullable)
  - is_unit_inspected (default: false) - hiện trạng kho được khách xác nhận
  - unit_inspected_at (nullable)
  - inspection_notes (nullable)
  - inspection_photos - List<String> (nullable), ảnh chụp thực tế lúc check-in
  - is_contract_signed (default: false) - hợp đồng đã được ký
  - contract_signed_at (nullable)
  - is_payment_settled (default: false) - khách đã thanh toán phần tiên quyết để nhận kho
  - payment_settled_at (nullable)
- **Trạng thái cuối cùng của biên bản:**
  - result (IN_PROGRESS/COMPLETED/REJECTED/CANCELED) - default: IN_PROGRESS
  - completed_at (nullable)
  - reject_reason (nullable, Text) - lý do từ chối hoặc hủy biên bản

**NOTES:**
- `IN_PROGRESS` lúc khởi tạo chỉ có nghĩa hồ sơ đang mở, **không** đồng nghĩa khách đã đến cơ sở.
- `CANCELED` do cron no-show của Flow 1 set khi hết `Appointment.end_at` mà `arrived_at` vẫn null.
- `order_id` là `N - 1` vì một đơn có thể check-in nhiều lần (khách từ chối khoang rồi được chỉ định khoang khác). Ràng buộc: mỗi `Appointment` tối đa một bản ghi, và mỗi đơn chỉ có tối đa một bản ghi đang `IN_PROGRESS`.
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
- access_type (PhysicalKey/AccessCode) - MVP chỉ dùng `PhysicalKey`
- quantity - số chìa đã giao, dùng khi `access_type = PhysicalKey`
- code_hash - hash của mã truy cập, dùng khi `access_type = AccessCode`
- issued_at, revoked_at
- status (Active/Revoked/Lost)

# CheckoutRecord
**Overview:** biên bản trả kho (Flow 2.5). Tách riêng khỏi `HandoverRecord` vì `HandoverRecord` chỉ chịu trách nhiệm tới khâu bàn giao.
- order_id (1 - 1: RentalOrder)
- appointment_id (1 - 1: Appointment)
- unit_id (N - 1: StorageUnit)
- staff_id (N - 1: Account)
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
- `PENDING_ITEMS`: khoang còn tài sản, chưa hoàn tất trả kho, **chưa thu hồi `UnitAccessKey`** vì khách còn cần vào lấy đồ. Khách quay lại dọn thì cập nhật tiếp trên **cùng một bản ghi**, không tạo mới.
- `COMPLETED`: đủ 5 cột mốc `true`, khoang sẵn sàng chuyển `Maintenance`.
