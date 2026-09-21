# Account
- id
- email (unique, normalized)
- email_verified_at (nullable)
- role_id (N - 1: Role)
- status
- created_at
# Facility
- id
- code (unique)
- name
- address
- operating_hours
- status (Active/Inactive)
# StorageUnit
**Overview:** thông tin và trạng thái của khoang chứa tại mỗi cơ sở.

- code (unique) - mã khoang
- facility_id (N - 1: Facility) - cơ sở quản lý khoang
- unit_type_id (N - 1: UnitType)
- size - kích thước khoang
- monthly_price (Decimal, nullable) - Giá override được BOM phê duyệt
- status:
  - Available: khoang sẵn sàng cho thuê
  - Reserved: khoang đã được giữ sau khi khách đặt cọc
  - Rented: khoang đã bàn giao và đang được thuê
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

**NOTES:**
- Nếu `StorageUnit.monthly_price` là `null`, giá hiệu lực lấy từ `UnitType.monthly_price`.
- MVP để `StorageUnit.monthly_price` là `null`; FM không tự chỉnh giá.
- Giá override chỉ áp dụng sau khi BOM phê duyệt.
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
  - Done: khách đã hoàn tất check-in, ký hợp đồng, thanh toán cần thiết và nhận bàn giao khoang
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
# RentalContract
**Owner:** Flow 2

**Flow 1 usage:** Flow 1 chỉ tham chiếu qua `Invoice.contract_id`; Flow 1 không quản lý vòng đời hợp đồng.
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
- amount - snapshot của giá hiệu lực tại thời điểm tạo hóa đơn.
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
- date
- started_at
- end_at
- arrived_at
- status:
  - Pending: trạng thái mặc định, trước khi khách đến
  - Done: đã ghi nhận khách đến cơ sở; các bước check-in và bàn giao tiếp tục được theo dõi bằng `HandoverRecord`
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

**NOTES:**
- HandoverRecord được thiết kế như một checklist nhằm tối đa sự linh hoạt khi checkin, ví dụ, khách có thể đến xác nhận danh tính (is_identity_verified) và đồng thời xác nhận kho luôn (is_unit_inspected).
- Để `result = COMPLETED`, tất cả các mục bắt buộc trong checklist phải được hoàn tất. Trường hợp `REJECTED` hoặc `CANCELED` có thể kết thúc khi checklist chưa hoàn tất và phải ghi nhận lý do tương ứng.

**CONSTRAINTS:**
- Một `Appointment` chỉ có tối đa một `HandoverRecord`.
- Một `RentalOrder` có thể có nhiều `HandoverRecord` nếu khách từ chối khoang, no-show hoặc phải đặt lại lịch.
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
# AuditLog
**Overview:** lưu lịch sử các thao tác nghiệp vụ nhạy cảm để đối chiếu khi có tranh chấp hoặc lỗi.

- id
- actor_account_id (nullable; null nếu do hệ thống, cron hoặc IPN)
- action (các loại action ghi ở phần NOTES)
- entity_type (String, controlled value) - loại entity bị tác động
- entity_id (String/Text) - ID của entity, lưu dưới dạng chuỗi
- old_value (JSON, nullable)
- new_value (JSON, nullable)
- reason (nullable)
- created_at

**CONSTRAINTS:**
- AuditLog là append-only.
- `entity_type` và `entity_id` do backend tạo, không nhận trực tiếp từ client.
- `action` phải khớp với loại entity trong Audit action catalog.
- Không ghi mật khẩu, secret key, số thẻ hoặc raw payment payload.
- Không ghi thao tác đọc dữ liệu hoặc click giao diện.

**NOTES:**
- `AuditLog` lưu trong database để theo dõi các thao tác nghiệp vụ nhạy cảm. Application log dùng cho lỗi kỹ thuật và không thay thế `AuditLog`. Mọi thao tác làm thay đổi trạng thái, quyền sở hữu hoặc tiền phải tạo một `AuditLog` theo catalog dưới đây. Nếu một thao tác làm thay đổi nhiều entity, tạo một bản ghi cho mỗi entity bị thay đổi.
- Audit action catalog:

| Action | Khi nào ghi | Entity | Actor |
|---|---|---|---|
| `RENTAL_REQUEST_APPROVED` | FM duyệt yêu cầu | `RentalRequest` | FM |
| `RENTAL_REQUEST_REJECTED` | FM từ chối yêu cầu | `RentalRequest` | FM |
| `RENTAL_REQUEST_CLAIMED` | Khách claim đơn sau khi verify email | `RentalRequest` | Customer/System |
| `PROPOSAL_AGREED` | Khách đồng ý proposal | `ProposalFeedback` | Customer |
| `PROPOSAL_REJECTED` | Khách từ chối proposal | `ProposalFeedback` | Customer |
| `PROPOSAL_REPROPOSED` | FM đề xuất khoang khác | `ProposalFeedback` | FM |
| `INVOICE_CREATED` | Hệ thống tạo hóa đơn cọc | `Invoice` | System |
| `INVOICE_CANCELED` | Hóa đơn bị vô hiệu hóa | `Invoice` | System |
| `PAYMENT_SUCCEEDED` | Thanh toán được xác nhận | `PaymentTransaction` | System/IPN |
| `PAYMENT_FAILED` | Thanh toán thất bại | `PaymentTransaction` | System/IPN |
| `RENTAL_ORDER_DEPOSITED` | Đơn chuyển sang đã đặt cọc | `RentalOrder` | System |
| `STORAGE_UNIT_RESERVED` | Khoang chuyển sang Reserved | `StorageUnit` | System |
| `MANUAL_REFUND_RECORDED` | Ghi nhận hoàn tiền thủ công | `PaymentTransaction` | Admin/FM |
| `APPOINTMENT_CREATED` | Tạo lịch hẹn check-in | `Appointment` | System |
| `FS_ASSIGNED` | FM phân công FS | `Appointment` | FM |
| `APPOINTMENT_RESCHEDULED` | Lịch hẹn được đặt lại | `Appointment` | Customer/FM |
| `APPOINTMENT_CANCELED_NO_SHOW` | Cron xử lý khách không đến | `Appointment` | System |
| `HANDOVER_RECORD_CREATED` | Tạo hồ sơ bàn giao | `HandoverRecord` | System |
