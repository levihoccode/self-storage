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
- status (Pending/Agree/Reject)
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

## Bảng mới cần thêm (theo Flow 5)

### Account

Overview: tài khoản nội bộ + khách hàng, chứa role và data-scope (facility_id chỉ áp dụng cho role FM).

* full_name
* email (unique)
* phone
* password_hash
* role (Customer/FacilityStaff/FacilityManager/BusinessOperationManager/SystemAdministrator)
* facility_id (N - 1: Facility, nullable) — chỉ set khi role = FacilityManager (1–1)
* status (Active/Inactive)
* created_at
* activated_at

NOTES:

* FS không dùng field này để map facility — dùng bảng `AccountFacilityAssignment` riêng vì Facility–FS là 1–n.

---

### Facility

Overview: cơ sở/chi nhánh, mỗi facility có đúng 1 FM phụ trách (1–1).


* name
* address
* phone
* operating_hours
* status (Active/Inactive)
* fm_account_id (1 - 1: Account, null as default) -> chỉ được gán khi đã có account role FM phù hợp; facility mới mở có thể chưa có FM
* created_at

## NOTES:

* Không xóa cứng Facility vì còn liên kết StorageUnit, RentalOrder... của cơ sở — khi ngừng hoạt động thì chuyển status sang Inactive.
* Quan hệ FM–Facility là 1–1: field fm_account_id lưu trực tiếp trên Facility (không cần bảng trung gian). Riêng Facility–FS là 1–n, dùng bảng AccountFacilityAssignment riêng (không lưu trên Facility).
---

### StorageUnit

Overview: khoang chứa vật lý thuộc một Facility, trạng thái dùng chung xuyên suốt Flow 1/2/3/5.

* facility_id (N - 1: Facility)
* unit_code
* unit_type
* size
* location
* rental_price
* status (Available/OnHold/Reserved/Rented/Maintenance)
* created_at
* updated_at

---

### AccountFacilityAssignment

Overview: bảng mapping account (chỉ dùng cho FS) với Facility, phục vụ RBAC data-scope cho trường hợp 1 facility có nhiều FS (1–n). Không dùng cho FM.

* account_id (N - 1: Account, role FacilityStaff)
* facility_id (N - 1: Facility)
* assigned_at

---

### AccountCreationRequest

Overview: yêu cầu tạo tài khoản do BOM gửi lên Admin khi facility chưa có FM phù hợp.

* requested_by (N - 1: Account, role BusinessOperationManager)
* proposed_name
* proposed_email
* proposed_role (mặc định FacilityManager)
* target_facility_id (N - 1: Facility)
* status (Pending/Approved/Rejected)
* approved_account_id (N - 1: Account, nullable) — gán sau khi Admin tạo account thành công
* created_at

NOTES:

* Khi status chuyển `Approved`, hệ thống thông báo lại cho BOM để BOM tiến hành gán FM vào Facility.

---

### AuditLog

Overview: ghi nhận các hành động nhạy cảm (đổi role, đổi facility assignment, đổi trạng thái StorageUnit thủ công) — được flow 5 yêu cầu bắt buộc ở nhiều bước nhưng chưa có bảng lưu.

* actor_account_id (N - 1: Account) — ai thực hiện hành động
* action_type (RoleChange/FacilityAssignmentChange/StorageUnitStatusChange/...)
* target_type (Account/StorageUnit/...)
* target_id
* old_value
* new_value
* reason
* created_at

---

### StaffAssignment

Overview: bản ghi phân công FS cho appointment hoặc sự cố cụ thể (Tùy chọn, chưa MVP theo note gốc, nhưng khai báo sẵn để tránh vướng khi mở rộng).

* order_id (N - 1: RentalOrder, nullable)
* support_request_id (N - 1: SupportRequest, nullable)
* staff_id (N - 1: Account, role FacilityStaff)
* assigned_by (N - 1: Account, role FacilityManager)
* status (Assigned/InProgress/Completed/Overdue)
* assigned_at
* completed_at

---

## Bảng bị tham chiếu nhưng chưa định nghĩa (từ schema bạn đã có)

### RentalContract

Overview: hợp đồng thuê kho chính thức, được `Invoice.contract_id` tham chiếu tới nhưng chưa có bảng này.

* order_id (1 - 1: RentalOrder)
* customer_id (N - 1: Account)
* unit_id (N - 1: StorageUnit)
* start_date (MM/DD/YYYY)
* end_date (MM/DD/YYYY)
* monthly_rate
* status (Active/Expired/Terminated)
* signed_at
* contract_file_url
* created_at

---

### SupportRequest

Overview: yêu cầu hỗ trợ/sự cố tại kho — được nhắc tới ở note của `Invoice` (hóa đơn phát sinh từ SupportRequest) và ở Flow 5.2/5.3 nhưng chưa có bảng.

* customer_id (N - 1: Account)
* unit_id (N - 1: StorageUnit, nullable)
* facility_id (N - 1: Facility)
* type (Incident/ServiceRequest/Complaint)
* title
* desc
* status (Pending/InProgress/Resolved/Closed)
* assigned_staff_id (N - 1: Account, nullable)
* created_at
* resolved_at

---

### PricingPolicy

Overview: khung giá thuê do BOM cấu hình — Flow 5.2 yêu cầu "giá thuê FM nhập vào phải nằm trong khung giá đã được BOM cấu hình (validate ở BE)" nhưng chưa có bảng lưu khung giá này.

* unit_type
* min_price
* max_price
* facility_id (N - 1: Facility, nullable) — null = áp dụng toàn hệ thống, có giá trị = override riêng cho 1 facility
* effective_date
* created_by (N - 1: Account, role BusinessOperationManager)

---

## NOTES

* `Account.facility_id` chỉ bắt buộc NOT NULL khi role = FacilityManager; FS dùng `AccountFacilityAssignment` riêng — không gộp chung logic 2 role vào 1 field, đúng theo ràng buộc 1–1 vs 1–n đã chốt.
* `AuditLog` và `PricingPolicy` là 2 bảng dễ bị bỏ sót nhất vì flow chỉ nhắc miệng ("cần ghi audit log", "khung giá đã được BOM cấu hình") chứ không liệt kê field cụ thể trong schema gốc, nhưng cả hai đều là điều kiện bắt buộc để logic (audit, validate giá) chạy được.
