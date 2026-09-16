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

# Account
**Overview:** tài khoản nội bộ + khách hàng, chứa role và data-scope.
- full_name
- email (unique)
- phone
- password_hash
- role (Customer/FacilityStaff/FacilityManager/BusinessOperationManager/SystemAdministrator)
- status (Active/Inactive)
- created_at
- activated_at

**NOTES:**
- Không còn field `facility_id` trên Account. Quan hệ FM–Facility (1–1) chỉ lưu 1 chiều tại `Facility.fm_account_id`, tránh trùng lặp 2 nguồn dữ liệu. Cần biết FM đang phụ trách facility nào thì query ngược từ Facility.
- FS dùng bảng `AccountFacilityAssignment` riêng (Facility–FS là 1–n).

---

# Facility
**Overview:** cơ sở/chi nhánh, có đúng 1 FM phụ trách (1–1).
- name
- address
- phone
- operating_hours
- status (Active/Inactive) — mặc định Inactive khi mới tạo
- fm_account_id (1 - 1: Account, null as default)
- created_at

**NOTES:**
- Không xóa cứng Facility vì còn liên kết StorageUnit, RentalOrder... của cơ sở.
- `fm_account_id` là nguồn duy nhất lưu quan hệ FM–Facility trong toàn hệ thống.
- Ràng buộc bắt buộc: Facility chỉ được chuyển sang `Active` sau khi `fm_account_id` đã có giá trị. Facility `Inactive` không hiển thị cho khách và FM không tạo được StorageUnit cho tới khi Facility `Active`.
- Khi account đang là FM bị đổi sang role khác, hệ thống phải tự động set `fm_account_id = null` trong cùng transaction với thao tác đổi role (xem `AccountRoleRequest` / audit ở mục 5.0).

---

# AccountFacilityAssignment
**Overview:** mapping account (chỉ dùng cho FS) với Facility, phục vụ RBAC data-scope cho trường hợp 1 facility có nhiều FS (1–n). Không dùng cho FM.
- account_id (N - 1: Account, role FacilityStaff)
- facility_id (N - 1: Facility)
- assigned_at

**NOTES:**
- Khi account FS bị đổi sang role khác, dòng tương ứng phải bị xoá trong cùng transaction với thao tác đổi role, tránh để lại data-scope "mồ côi".

---

# AccountRoleRequest
*(thay thế `AccountCreationRequest`)*

**Overview:** danh sách nhân sự do BOM chỉ định role (FM/FS) trực tiếp, gửi lên Admin để thực thi tạo mới hoặc cập nhật role account đã tồn tại.
- batch_id (nullable) — nhóm các dòng cùng 1 lần BOM gửi lên
- requested_by (N - 1: Account, role BusinessOperationManager)
- target_name
- target_email
- role (FM hoặc FS — do BOM chỉ định trực tiếp, không phải đề xuất chờ duyệt)
- target_facility_id (N - 1: Facility)
- status (Pending/Done)
- account_id (N - 1: Account, nullable) — gán sau khi Admin xử lý xong dòng đó (account mới hoặc account được đổi role)
- created_at
- expires_at

**NOTES:**
- Không còn status Approved/Rejected — Admin không có quyền từ chối role do BOM chỉ định, chỉ thực thi kỹ thuật.
- Nếu role là FS: Admin tạo/cập nhật account và gán luôn `AccountFacilityAssignment` trong cùng bước xử lý.
- Nếu role là FM: Admin chỉ tạo/cập nhật account; việc set `Facility.fm_account_id` vẫn do BOM thực hiện riêng sau đó.
- `expires_at` dùng để cảnh báo/escalate nếu Admin chưa xử lý kịp, tránh tồn đọng Pending vô thời hạn.

---

# StorageUnit
**Overview:** khoang chứa vật lý thuộc một Facility, trạng thái dùng chung xuyên suốt Flow 1/2/3/5.
- facility_id (N - 1: Facility)
- unit_code
- unit_type
- size
- location
- rental_price
- status (Available/OnHold/Reserved/Rented/Maintenance)
- created_at
- updated_at

**NOTES:**
- rental_price phải nằm trong khung giá của `PricingPolicy` (Flow 4 — bảng này KHÔNG thuộc phạm vi thiết kế của Flow 5, chỉ đọc/tham chiếu).
- Chỉ tạo được khi Facility đang `Active`.

---

# AuditLog
**Overview:** ghi nhận các hành động nhạy cảm (tạo account, đổi role, gán/xoá facility assignment, đổi trạng thái StorageUnit thủ công). Dạng tối giản cho MVP.
- actor_account_id (N - 1: Account) — ai thực hiện
- action_description — mô tả dạng text (VD: "Đổi role account X từ FS sang FM")
- created_at

**NOTES:**
- Bản đầy đủ (structured old_value/new_value theo từng loại entity) thuộc Advanced Features, không cần cho MVP.

---

# SupportRequest
**Overview:** yêu cầu hỗ trợ/sự cố tại kho.
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit, nullable)
- facility_id (N - 1: Facility)
- type (Incident/ServiceRequest/Complaint)
- title
- desc
- status (Pending/InProgress/Resolved/Closed)
- assigned_staff_id (N - 1: Account, nullable) — FM gán trực tiếp qua field này (không dùng bảng StaffAssignment riêng)
- created_at
- resolved_at

**NOTES:**
- Bảng này thuộc phạm vi thiết kế của Flow 7, không phải Flow 5 — Flow 5 (mục 5.3) chỉ đọc/ghi field `assigned_staff_id`, không tự định nghĩa lại cấu trúc bảng.

---

# PricingPolicy
**Overview:** khung giá thuê do BOM cấu hình.
- unit_type
- min_price
- max_price
- effective_date
- created_by (N - 1: Account, role BusinessOperationManager)

**NOTES:**
- Bảng này thuộc phạm vi thiết kế của Flow 4, không phải Flow 5 — Flow 5 (mục 5.2) chỉ đọc để validate rental_price.
- MVP chỉ 1 khung giá toàn hệ thống theo unit_type (bỏ field facility_id override — đẩy sang Advanced Features).

---

~~# StaffAssignment~~
**Đã loại khỏi phạm vi MVP.** Phân công FS lưu trực tiếp trên `RentalOrder.staff_id` (cho appointment) và `SupportRequest.assigned_staff_id` (cho sự cố) — không dùng bảng riêng, tránh 2 nguồn dữ liệu song song cho cùng 1 mục đích.
