# StorageUnit
**Overview:** thông tin và trạng thái của khoang chứa tại mỗi cơ sở.

- code (unique) - mã khoang
- facility_id (N - 1: Facility) - cơ sở quản lý khoang
- type - loại khoang
- size - kích thước khoang
- monthly_price (Decimal) - giá thuê tháng hiện tại của khoang
- price_source (POLICY/MANUAL) - nguồn của mức giá
- status:
  - Available: khoang sẵn sàng cho thuê
  - Reserved: khoang đã được giữ sau khi khách đặt cọc
  - Rented: khoang đã hoàn tất bàn giao và đang được thuê
  - Maintenance: khoang đang bảo trì
- created_at
- updated_at

**CONSTRAINTS:**
- Chỉ khoang có `status = Available` mới được chỉ định hoặc đặt cọc.
- Sau khi đặt cọc thành công: `Available → Reserved`.
- Khi Flow 2 hoàn tất bàn giao: `Reserved → Rented`.
- Khi đơn hàng hết hạn hoặc bị no-show sau thời hạn giữ kho: `Reserved → Available`.
- Khoang `Maintenance` không được sử dụng trong quá trình đặt cọc.
- Thời hạn giữ kho được xác định bằng `RentalOrder.expires_at`, không cần `hold_expires_at` trên `StorageUnit`.
- Khi tạo khoang, hệ thống tự lấy `monthly_price` từ policy và đặt `price_source = POLICY`.
- Nếu FM chỉnh giá thủ công, cập nhật `price_source = MANUAL`.

# RentalRequest
**Overview:** chứa các thông tin được gửi từ form trên website.
- facility_id (N - 1: Facility)
- customer_name
- normalized_customer_email (trim + lowercase)
- customer_phone
- unit_type
- start_date
- period - số tháng thuê
- unit_id (N - 1: StorageUnit) - khoang FM chỉ định lúc duyệt; dùng để tạo ProposalFeedback đầu tiên
- status:
  - Pending: trạng thái mặc định khi tạo
  - Rejected: yêu cầu bị FM từ chối
  - Approved: yêu cầu được FM duyệt
  - Converted: request đã sinh `RentalOrder` (đã liên kết account)
  - Expired: quá expires_at mà chưa liên kết account
- created_at
- responded_at
- expires_at

**NOTES:**
- `normalized_customer_email` là email đã chuẩn hóa `trim` + lowercase ngay tại điểm nhập (form/đăng ký/import). Đây là **khóa định danh** để nối `RentalRequest` với `Account` (mục 1.2 Case A) và xác minh quyền sở hữu đơn — không chỉ dùng để gửi thông báo.
  - Lý do chuẩn hóa: các email provider thực tế (Gmail, Outlook, ...) xử lý phần local-part không phân biệt hoa/thường. Không chuẩn hóa thì cùng một người có thể match hụt (`John@...` trên form vs `john@...` lúc đăng ký) hoặc sinh trùng request/account. RFC 5321 yêu cầu transport giữ nguyên case — việc chuẩn hóa là policy của ứng dụng, và RFC cũng khuyến khích không khai thác case sensitivity.
  - Quy ước: chỉ `trim` + lowercase. Không bỏ dấu chấm hay `+tag` — đó là hành vi riêng của Gmail, không phải chuẩn chung.
# RentalOrder
**Overview:** chứa các thông tin đơn hàng đã được `Approved` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho bao gồm các thông tin:
- request_id (1 - 1: RentalRequest)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit, null cho đến khi việc chỉ định khoang chứa giữa FM và Khách hàng hoàn thành)
- cancel_reason
- status: 
  - Pending: trạng thái mặc định khi tạo đơn hàng
  - Deposited: Khách hàng đã đặt cọc
  - Scheduled: Khách đã lên lịch hẹn
  - InProgress: Đơn hàng đang được xử lý (đã có `Appointment.staff_id` được phân công)
  - Canceled: Hủy đơn hàng
  - Done: Khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử 
  - Expired:  quá hạn không thanh toán cọc / không đặt lịch / không đến nhận theo ngưỡng.
- expires_at (nullable) - thời điểm hết hiệu lực giữ kho sau khi đặt cọc

**NOTES:**
- Trạng thái đơn hàng:
  ```text
  Pending ──đặt cọc──> Deposited ──đặt lịch──> Scheduled
                                                │
                                      FM phân công FS
                                                ▼
                                            InProgress
                                                │
                                  hoàn tất check-in/bàn giao
                                                ▼
                                               Done
  ```
  - Các nhánh ngoại lệ:
    - Pending → Expired: khách không thanh toán cọc đúng hạn.
    - Deposited → Expired: hết thời hạn giữ kho nhưng chưa đặt lịch.
    - Scheduled/InProgress → Deposited: khách no-show nhưng vẫn còn trong thời hạn giữ kho.
    - Scheduled/InProgress → Expired: khách no-show và đã hết thời hạn giữ kho.
    - Pending/Deposited/Scheduled/InProgress → Canceled: đơn bị hủy.

# Invoice
**Overview:** chứa thông tin thanh toán của khách hàng (hóa đơn)
- order_id (N - 1: RentalOrder) -> null as default
- contract_id (N - 1: RentalContract) -> null as default
- customer_id (N - 1: Account)
- code
  - Cấu trúc mã hóa đề xuất: Gợi nhớ & Dễ lọc, để thuận tiện tuyệt đối khi kiểm tra, mã hóa đơn nên mang ý nghĩa phân loại theo công thức:
    ```
    INV-{service_code}-{facility_code}-{YYMMDD}-{rand}
    ```
    - Ví dụ:
      + INV-DEP-Q7-260911-A89F: Hóa đơn cọc (DEP), chi nhánh Quận 7, ngày 11/09/2026.
      + INV-RNT-TD-261001-K312: Hóa đơn tiền thuê định kỳ (RNT), chi nhánh Thủ Đức.
      + INV-EXT-Q7-260915-091B: Hóa đơn dịch vụ ngoài / sự cố khóa (EXT).
- title
- type (Deposit/Rental/Extension/Penalty/Service) - xác định nguồn gốc hóa đơn  
- desc
- status:
  - Unpaid: Hóa đơn chưa được thanh toán
  - Paid: Hóa đơn đã được thanh toán
  - Canceled: Hóa đơn đã bị hủy và vô hiệu hóa
  - Expired: Hóa đơn quá hạn thanh toán
- amount
- created_at
- due_date

**Service Code:**
- DEP: đặt cọc khoang chứa
- RNT: tiền thuê hằng tháng
- CLN: phí dọn dẹp khoang sau khi trả
- DMG: phí hư hại kho sau khi trả
- EXT: dịch vụ phát sinh (nếu chưa có mã)


**NOTES:**
- Nguồn gốc hóa đơn xác định qua FK (`order_id` / `contract_id`; bổ sung `support_request_id` khi cần): **đúng một FK được set** (CHECK constraint). `type` là loại hóa đơn.
- `Invoice.amount` được chốt tại thời điểm tạo hóa đơn; thay đổi `StorageUnit.monthly_price` sau đó không làm thay đổi hóa đơn đã tạo.
# ProposalFeedback
**Overview:** chứa thông tin feedback từ khách hàng sau khi khoang chứa được chỉ định từ FM
- order_id (N - 1: RentalOrder)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- status (Pending/Agreed/Rejected/Expired)
- note
- created_at
- expires_at - TTL của proposal; quá hạn coi như khách không phản hồi

**Constraints**
- Mọi lần đề xuất lại đều tạo bản ghi mới cho cùng `RentalOrder`; bản ghi cũ giữ nguyên (lịch sử). Khoang hiệu lực = proposal `Agreed` mới nhất.
- 1 order có thể có nhiều proposal `Agreed` theo thời gian (khách đồng ý online nhưng từ chối khoang lúc check-in).

**NOTES:**
- field note dùng để khi khách từ chối và muốn chọn lại, sẽ nêu lý do vì sao từ chối, ...
# TODO: FacilityTask
# Appointment
**Overview:** lịch hẹn dùng chung cho các cuộc hẹn tại cơ sở. Trong Flow 1, lịch hẹn được tạo cho việc check-in; các flow khác có thể sử dụng cho bàn giao, trả kho hoặc xử lý sự cố.

- customer_id (N - 1: Account)
- facility_id (N - 1: Facility)
- staff_id (N - 1: Account, nullable khi chưa được phân công)
- type (CHECKIN, HANDOVER, RETURN, ...)
- cancel_reason
- appointment_date
- started_at
- end_at
- arrived_at
- status:
  - Pending: trạng thái mặc định, trước khi khách đến
  - Done: khách đã đến
  - Canceled: lịch hẹn bị hủy
- created_at
- updated_at

# RentalAppointment
**Overview:** nối lịch hẹn với đơn hàng.

- order_id (N - 1: RentalOrder)
- appointment_id (1 - 1: Appointment)
# HandoverRecord
**Overview:** theo dõi tiến trình check-in và bàn giao khoang chứa. Flow 1 tạo bản ghi khi lịch hẹn check-in được tạo; Flow 2 sử dụng và cập nhật bản ghi trong quá trình xử lý tại cơ sở.

- order_id (N - 1: RentalOrder)
- appointment_id (1 - 1: Appointment)
- unit_id (N - 1: StorageUnit)
- staff_id (N - 1: Account, nullable khi mới tạo; được cập nhật khi FM phân công FS)

- is_identity_verified (default: false) - xác minh danh tính người đến check-in
- identity_verified_at (nullable)
- is_unit_inspected (default: false) - hiện trạng kho được khách xác nhận
- unit_inspected_at (nullable)
- inspection_notes (nullable)
- inspection_photos - List<String> (nullable), ảnh chụp thực tế lúc check-in
- is_contract_signed (default: false) - hợp đồng đã được ký
- contract_signed_at (nullable)
- is_payment_settled (default: false) - khách đã thanh toán khoản cần thiết để nhận kho
- payment_settled_at (nullable)

- result (IN_PROGRESS/COMPLETED/REJECTED/CANCELED) - default: IN_PROGRESS
- completed_at (nullable)
- reject_reason (nullable, Text) - lý do từ chối hoặc hủy biên bản
- created_at
- updated_at

**CONSTRAINTS:**
- `staff_id` có thể null khi FM chưa phân công FS và được cập nhật sau đó.
- Một `Appointment` chỉ có tối đa một `HandoverRecord`.
- Một `RentalOrder` có thể có nhiều `HandoverRecord` nếu khách từ chối khoang hoặc phải đặt lại lịch.
- Một `RentalOrder` chỉ có tối đa một `HandoverRecord` đang mở (`result = IN_PROGRESS`).

# PaymentTransaction
- invoice_id (N - 1: Invoice)
- gateway_transaction_no (unique) - Mã giao dịch định danh từ cổng thanh toán/ngân hàng trả về (ví dụ mã vnpay_TransactionNo, payOS reference code, ...) -> Dùng để tra cứu, đối soát khi có khiếu nại
- transaction_content
- response_payload: JSON / TEXT, nullable -> Lưu toàn bộ log raw webhook/IPN để đối soát
- vnp_txn_ref - mã tham chiếu merchant gửi sang VNPay, không trùng trong ngày -> dùng để tra cứu IPN và chống xử lý trùng (idempotent)
- amount
- direction (PAY/REFUND)
- failure_reason
- paid_at
- created_at
- status (Pending/Failed/Success)

