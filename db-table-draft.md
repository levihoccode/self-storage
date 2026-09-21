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
- unit_id
- cancel_reason
- status: 
  - Pending: chờ khách chọn lịch hẹn on-site
  - InProgress: sau FS được chỉ đã xác nhận và đang trong quá trình hẹn gặp, tư vấn
  - Canceled: hủy đơn hàng
  - Done: Khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử 

  **NOTES:**

- Đã bỏ `staff_id` và `appointment_date` (theo A6, thống nhất với Flow 2) — việc phân công FS và lịch hẹn không còn nằm trên RentalOrder, chuyển hẳn sang bảng Appointment (Flow 2 sở hữu), nối qua RentalAppointment. Muốn biết FS/lịch hẹn của 1 đơn thì join qua RentalAppointment.order_id → Appointment.
- Đây là bản tham chiếu trong phạm vi Flow 5 (Flow 5 không sở hữu bảng này) — schema đầy đủ/chính thức do Flow 2 quản lý.
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
- role_id (N - 1: Role)
- status (Active/Inactive)
- created_at
- activated_at

**NOTES:**
- Không còn field `facility_id` trên Account. Quan hệ FM–Facility (1–1) chỉ lưu 1 chiều tại `Facility.fm_account_id`, tránh trùng lặp 2 nguồn dữ liệu. Cần biết FM đang phụ trách facility nào thì query ngược từ Facility.
- FS dùng bảng `AccountFacilityAssignment` riêng (Facility–FS là 1–n).
- **Đã sửa:** `role` không còn là enum trực tiếp trên `Account` mà là `role_id` tham chiếu tới bảng `Role` (mô hình RBAC data-driven, xem `Role`/`Permission`/`RolePermission` bên dưới) — khớp với nhiệm vụ gốc của System Administrator: *"Thiết lập quyền truy cập dữ liệu cho các role dựa trên model RBAC"*.

---

# Role
**Overview:** các vai trò trong hệ thống. System Administrator quản lý (tạo/sửa role nếu cần, seed mặc định 5 role cho MVP).
- name (Customer/FacilityStaff/FacilityManager/BusinessOperationManager/SystemAdministrator)
- description

# Permission
**Overview:** các quyền truy cập/hành động cụ thể trong hệ thống theo model RBAC.
- code — ví dụ: `rental_request.approve`, `invoice.read`, `policy.update`, `storage_unit.create`
- description

# RolePermission
**Overview:** bảng trung gian N–N giữa `Role` và `Permission`. System Administrator thiết lập/điều chỉnh quyền cho từng role qua đây (data-driven, không hard-code trong source code).
- role_id (N - 1: Role)
- permission_id (N - 1: Permission)

**NOTES:**
- MVP seed sẵn 1 bộ permission mặc định cho mỗi role theo bảng RBAC tổng quát ở mục 5.0 của draft.md — Admin có thể chỉnh sửa thêm qua bảng này mà không cần deploy lại code.

---

# Facility
**Overview:** cơ sở/chi nhánh, có đúng 1 FM phụ trách (1–1).
- code (unique) — mã chi nhánh dùng trong `Invoice.code`/`RentalContract.code` (VD: Q7 - Quận 7, TD - Thủ Đức)
- name
- address
- phone
- operating_hours
- status (Active/Inactive) — mặc định Inactive khi mới tạo
- fm_account_id (1 - 1: Account, null as default)
- created_at
- enabledKeyAccess (boolean, default true) — facility có hỗ trợ bàn giao bằng khóa cơ (chìa vật lý)
- enabledCodeAccess (boolean, default false) — facility có hỗ trợ bàn giao bằng mã số

**NOTES:**
- Không xóa cứng Facility vì còn liên kết StorageUnit, RentalOrder... của cơ sở.
- `fm_account_id` là nguồn duy nhất lưu quan hệ FM–Facility trong toàn hệ thống.
- Ràng buộc bắt buộc: Facility chỉ được chuyển sang `Active` sau khi `fm_account_id` đã có giá trị. Facility `Inactive` không hiển thị cho khách và FM không tạo được StorageUnit cho tới khi Facility `Active`.
- Khi account đang là FM bị đổi sang role khác, hệ thống phải tự động set `fm_account_id = null` trong cùng transaction với thao tác đổi role (xem `AccountRoleRequest` / audit ở mục 5.0).
- **Đã thêm field `code`:** bắt buộc phải có vì `Invoice.code` và `RentalContract.code` (Flow 1/2) dùng mã chi nhánh này làm phần giữa của mã hóa đơn/hợp đồng — trước đây Flow 5 chưa có field này dù các flow khác đã tham chiếu.
- Ít nhất 1 trong 2 cờ phải true — 1 facility không thể không hỗ trợ loại khóa nào.
- Khi cả 2 cờ cùng true, FS chọn loại khóa lúc bàn giao (Flow 2 mục 2.4), hệ thống ghi UnitAccessKey.access_type tương ứng.

---

# AccountFacilityAssignment
**Overview:** mapping account (chỉ dùng cho FS) với Facility, phục vụ RBAC data-scope cho trường hợp 1 facility có nhiều FS (1–n). Không dùng cho FM.
- account_id (N - 1: Account, role FacilityStaff)
- facility_id (N - 1: Facility)
- assigned_at

**NOTES:**
- Khi account FS bị đổi sang role khác, dòng tương ứng phải bị xoá trong cùng transaction với thao tác đổi role, tránh để lại data-scope "mồ côi".
- Dùng để validate FS được gán vào Appointment.staff_id/SupportRequest.assigned_staff_id phải thuộc đúng facility, qua 2 đường khác nhau tùy bảng:

- SupportRequest: có sẵn unit_id → join StorageUnit.facility_id → so sánh với AccountFacilityAssignment.facility_id.
- Appointment: không có field facility_id trực tiếp (theo schema Flow 2) — phải join qua RentalAppointment.appointment_id → RentalOrder.unit_id → StorageUnit.facility_id rồi mới so sánh với AccountFacilityAssignment.facility_id. Nếu Appointment dùng cho lịch hẹn không gắn RentalOrder (case xử lý sự cố không qua đơn thuê), không có đường join này — cần Flow 2 xác nhận: những Appointment không có RentalAppointment thì lấy facility_id để validate bằng cách nào (có thể phải thêm facility_id trực tiếp vào Appointment cho riêng case đó, hoặc case đó dùng SupportRequest thay vì Appointment).


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
**Overview:** khoang chứa vật lý thuộc một Facility, trạng thái dùng chung xuyên suốt Flow 1/2/2.5/3/5.
- facility_id (N - 1: Facility)
- unit_type_id (N - 1: UnitType)
- unit_code
- location
- status (Available/Reserved/Rented/Maintenance)
- created_at
- updated_at
- maintenance_started_at (nullable) — chỉ do Flow 2.5 set khi Maintenance phát sinh từ trả kho; Flow 5 để trống khi chuyển Maintenance do sự cố

**NOTES:**
- **Đã bỏ field `rental_price` và `unit_type` (string).** Giá thuê là thuộc tính của `UnitType.monthly_price` (Flow 4, BOM quản lý tập trung) — mọi `StorageUnit` cùng `unit_type_id` dùng chung một mức giá tại một thời điểm. FM không tự nhập giá cho từng khoang, nên không còn nhu cầu validate khung giá riêng lẻ.
- `unit_type` (string) đổi thành `unit_type_id` (FK tới bảng `UnitType`) để tránh dữ liệu tự do, đảm bảo đồng bộ với giá và thông tin loại kho.
- Enum `status` là bản chính thức do Flow 5 sở hữu, dùng chung toàn hệ thống — chi tiết từng giá trị xem mục 5.2 của draft.md.
- Chỉ tạo được khi Facility đang `Active`.

---

# AuditLog
**Overview:** ghi nhận các hành động nhạy cảm (tạo account, đổi role, gán/xoá facility assignment, đổi trạng thái StorageUnit thủ công, duyệt/từ chối yêu cầu...). Dùng chung cấu trúc với quyết định của Flow 3 (mục 3.6), không phải bản riêng của Flow 5.
- account_id (N - 1: Account) — người thực hiện
- action — ví dụ: `RentalRequest.Approve`, `Account.UpdateRole`, `Facility.AssignFM`
- entity_type — tên bảng bị tác động
- entity_id
- old_value (JSON, nullable)
- new_value (JSON, nullable)
- created_at

**NOTES:**
- **Đã thay thế bản tối giản (`actor_account_id`/`action_description` dạng text) bằng cấu trúc đầy đủ này** để khớp với Flow 3 — Flow 3 (mục 3.6) đã tham chiếu tới "cấu trúc bảng AuditLog theo quyết định chung", nên Flow 5 không tự định nghĩa bản khác.
- Chỉ ghi các thao tác làm thay đổi quyền lợi/quyền hạn (đổi role, gán facility, duyệt yêu cầu...), không ghi mọi hành động đọc dữ liệu.

---

# LoginHistory
**Overview:** lịch sử đăng nhập của users, System Administrator theo dõi (nhiệm vụ đã ghi trong phần Actors nhưng trước đây Flow 5 chưa có bảng riêng).
- account_id (N - 1: Account, nullable) — null khi đăng nhập bằng email không tồn tại
- email — email dùng để đăng nhập
- ip_address
- user_agent
- status (Success/Failed)
- failure_reason (null as default)
- created_at

**NOTES:**
- Tách riêng khỏi `AuditLog`: `LoginHistory` ghi nhận sự kiện đăng nhập (kể cả thất bại/email không tồn tại), `AuditLog` ghi nhận hành động nghiệp vụ có tác động dữ liệu.

---

# UnitType
**Overview:** loại khoang chứa (type, size, giá thuê hiện tại), thuộc phạm vi thiết kế của **Flow 4** — Flow 5 chỉ đọc `monthly_price`/thông tin loại khi tạo `StorageUnit`, không tự định nghĩa lại bảng này.
- name — ví dụ: Small, Medium, Large
- width, depth, height (m)
- area (m2)
- description
- monthly_price — giá thuê hiện tại mỗi tháng, do BOM cập nhật trực tiếp (Flow 4)
- updated_by (N - 1: Account)
- updated_at

**NOTES:**
- `RentalContract.monthly_price` (Flow 2) lưu giá tại thời điểm ký, không phụ thuộc giá hiện tại ở bảng này.
- Nếu sau này cần giá khác nhau theo từng chi nhánh, tách giá ra bảng riêng theo (`facility_id`, `unit_type_id`) — đẩy sang Advanced Features, MVP dùng 1 giá áp dụng toàn hệ thống theo `unit_type_id`.

---

~~# PricingPolicy~~
**Đã loại bỏ.** Xung đột với mô hình `UnitType.monthly_price` (Flow 4 quản lý giá trực tiếp, không phải khung min–max để FM tự nhập). FM khi tạo `StorageUnit` chỉ chọn `unit_type_id` có sẵn, không tự nhập số tiền — do đó không cần bảng khung giá riêng cho FM.

---

~~# SupportRequest~~
**Không định nghĩa ở đây.** Bảng này thuộc phạm vi thiết kế của **Flow 3/7**, đã có schema đầy đủ riêng (`issue_type`, `reporter_id`, `contract_id`, `invoice_id`...) ở phần schema của Flow 3. Flow 5 (mục 5.3) chỉ đọc/ghi field `assigned_staff_id` khi FM phân công FS, không tự định nghĩa lại cấu trúc bảng để tránh 2 shape khác nhau cho cùng 1 bảng.

---

~~# StaffAssignment~~
**Đã loại khỏi phạm vi MVP.** Phân công FS lưu trực tiếp trên `Appointment.staff_id` (cho lịch hẹn check-in/bàn giao/trả kho — bảng do Flow 2 sở hữu) và `SupportRequest.assigned_staff_id` (cho sự cố) — không dùng bảng riêng, tránh 2 nguồn dữ liệu song song cho cùng 1 mục đích.

**NOTES:**
- **Đã sửa so với bản cũ:** trước đây ghi "lưu trên `RentalOrder.staff_id`" — theo quyết định chung mới nhất (A6, thống nhất với Flow 2), `staff_id`/`appointment_date` không còn nằm trên `RentalOrder` nữa mà chuyển hẳn sang bảng `Appointment`.

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
  - Draft: hợp đồng đã sinh, chờ khách ký
  - Signed: đã ký nhưng chưa bàn giao
  - Active: đã bàn giao, đang có hiệu lực (kể cả đã quá `end_date` nhưng chưa trả kho)
  - Ended: đã trả kho xong (Flow 2.5)
  - Canceled: hủy trước khi bàn giao khoang

**NOTES:**
- Mỗi `StorageUnit` chỉ có tối đa 1 hợp đồng `Active` tại một thời điểm.
- Không có status `PendingReturn` — tiến trình trả kho theo dõi ở bảng `ReturnRequest` (Flow 3), để không đè mất thông tin quá hạn của hợp đồng.