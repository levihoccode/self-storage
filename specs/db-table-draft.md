# RentalRequest
**Overview:** chứa các thông tin được gửi từ form trên website.
- facility_id (N - 1: Facility)
- customer_name
- customer_email
- customer_phone
- unit_type
- start_date
- period - số tháng thuê
- unit_id (null as default)
- status (Pending/Rejected/Approved/Expired) - Expired xảy ra khi trong trạng thái chờ khách hàng tạo tài khoản và timeout
# RentalOrder
**Overview:** chứa các thông tin đơn hàng đã được `Approved` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho bao gồm các thông tin:
- request_id (1 - 1: RentalRequest)
- customer_id (N - 1: Account)
- staff_id (N - 1: Account, null cho đến khi trang thái thay đổi từ `Scheduled` -> `InProgress`, FM sẽ chỉ định FS)
- unit_id (N - 1: StorageUnit, null cho đến khi việc chỉ định khoang chứa giữa FM và Khách hàng hoàn thành)
- appointment_date
- cancel_reason
- status: 
  - Pending: trạng thái mặc định khi tạo đơn hàng
  - Deposited: Khách hàng đã đặt cọc
  - Scheduled: Khách đã lên lịch hẹn
  - InProgress: Đơn hàng đang được xử lý (lúc này staff_id bắt buộc != null)
  - Canceled: Hủy đơn hàng
  - Done: Khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử 

**NOTES:**
- Trạng thái đơn hàng là tuyến tính:
  ```
  Pending ──> Deposited ──> Scheduled ──> InProgress ──> Done
     │           │             │             │
     └───────────┴─────────────┴─────────────┴──> Canceled
  ```
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
**Overview:** chứa thông tin feedback từ khách hàng sau khi khoang chứa được chỉ định từ FM
- order_id (N - 1: RentalOrder)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- status (Pending/Agreed/Rejected)
- note

**Constraints**
- 1 order chỉ có 1 proposal được đồng ý bởi khách hàng

**NOTES:**
- field note dùng để khi khách từ chối và muốn chọn lại, sẽ nêu lý do vì sao từ chối, ...
# PaymentTransaction
- invoice_id (N - 1: Invoice)
- gateway_transaction_no - Mã giao dịch định danh từ cổng thanh toán/ngân hàng trả về (ví dụ mã vnpay_TransactionNo, payOS reference code, ...) -> Dùng để tra cứu, đối soát khi có khiếu nại
- transaction_content
- response_payload: JSON / TEXT, nullable -> Lưu toàn bộ log raw webhook/IPN để đối soát
- amount
- failure_reason
- paid_at
- create_at
- status (Pending/Failed/Success)

- NOTES: visa card only
# TODO: FacilityTask
