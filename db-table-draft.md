# RentalRequest
**Overview:** chứa các thông tin được gửi từ form trên website.
- facility_id (N - 1: Facility)
- customer_id (N - 1: Account, null as default) - được gán khi FM duyệt (nếu email đã có tài khoản) hoặc khi khách đăng ký tài khoản trong thời gian quy định
- customer_email
- customer_phone
- unit_type
- start_date (MM/DD/YYYY)
- period - số tháng thuê
- unit_id (N - 1: StorageUnit, null as default) - khoang FM chỉ định lúc duyệt (chỉ mang tính lịch sử, khoang hiện tại của đơn xem `RentalOrder.unit_id`)
- processed_by (N - 1: Account, null until FM approve/reject) - FM xử lý yêu cầu
- processed_at (null as default)
- reject_reason (null as default) - bắt buộc khi `status = Rejected`
- created_at
- status:
  - Pending: chờ FM xử lý
  - Approved: FM đã duyệt và chỉ định khoang
  - Rejected: FM từ chối
  - Expired: đã duyệt nhưng email chưa có tài khoản và khách không đăng ký trong thời gian quy định

**NOTES:**
- Trạng thái "đã duyệt nhưng chưa có tài khoản" = `status = Approved AND customer_id IS NULL`, lưu trực tiếp trong DB (không dùng Redis/PG Cache để tránh mất yêu cầu khi cache bị xóa)
- Job định kỳ chuyển các bản ghi `status = Approved AND customer_id IS NULL AND processed_at < now - timeout` sang `Expired`
- Nếu chốt Phương án 2 (Wishlist) ở Flow 1.1 thì bổ sung status `Wishlisted`
# RentalOrder
**Overview:** chứa các thông tin đơn hàng đã được `Approved` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho bao gồm các thông tin:
- request_id (1 - 1: RentalRequest)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit) - khoang hiện tại của đơn (source of truth), có thể thay đổi khi khách chuyển sang khoang tương đương
- staff_id (N - 1: Account, null until FM phân công FS)
- appointment_at (MM/DD/YYYY HH:mm, null until khách chọn lịch hẹn)
- cancel_reason (null as default)
- created_at
- status: 
  - AwaitingDeposit: đơn vừa được tạo, chờ khách thanh toán hóa đơn đặt cọc
  - Pending: khách đã đặt cọc thành công (`StorageUnit` -> `Reserved`), chờ FS được phân công xác nhận lịch hẹn
  - InProgress: sau FS được chỉ định đã xác nhận và đang trong quá trình hẹn gặp, tư vấn
  - Canceled: hủy đơn hàng
  - Expired: quá `Invoice.due_date` của hóa đơn đặt cọc mà khách chưa thanh toán
  - Done: Khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử 

**NOTES:**
- Khi đơn chuyển sang `Canceled`/`Expired`, hóa đơn đặt cọc chưa thanh toán của đơn chuyển sang `Invoice.status = Canceled`
# RentalContract
**Overview:** chứa thông tin hợp đồng điện tử được thiết lập khi ký hợp đồng (`RentalOrder` chuyển sang trạng thái `Done`, thuộc Flow 2), là căn cứ để phát sinh hóa đơn định kỳ, gia hạn, tiền phạt, ...
- order_id (1 - 1: RentalOrder)
- customer_id (N - 1: Account)
- staff_id (N - 1: Account) - FS lập/ký hợp đồng với khách
- unit_id (N - 1: StorageUnit)
- code
  - Mã hợp đồng = CTR + mã chi nhánh + YYMMDD (ngày ký) + random
  - Ví dụ: CTR-Q7-260911-B27C: Hợp đồng chi nhánh Quận 7, ký ngày 11/09/2026.
- start_date (MM/DD/YYYY)
- end_date (MM/DD/YYYY) - được cập nhật khi gia hạn
- monthly_price - giá thuê hàng tháng tại thời điểm ký (không phụ thuộc giá hiện tại của unit)
- deposit_amount - tiền cọc
- billing_day - ngày trong tháng phát sinh hóa đơn tiền thuê (1 - 28)
- file_url - file hợp đồng điện tử (PDF)
- signed_at
- terminated_at (null as default)
- termination_reason (null as default)
- created_at
- status:
  - Active: hợp đồng đã ký và đang có hiệu lực (kể cả khi chưa tới `start_date`, hoặc đã quá `end_date` nhưng chưa trả kho)
  - Completed: khách đã trả kho và FS xác nhận hoàn tất (Flow 2.5)
  - Terminated: chấm dứt trước hạn (khách yêu cầu, vi phạm, quá hạn thanh toán, ...)
  - Canceled: hợp đồng đã ký nhưng bị hủy trước khi có hiệu lực (trước `start_date`/bàn giao khoang)

**NOTES:**
- Mỗi `StorageUnit` chỉ có tối đa 1 hợp đồng `Active` tại một thời điểm
- Trạng thái còn hiệu lực / sắp hết hạn / quá hạn **không lưu vào `status`** mà tính trực tiếp từ `end_date` tại thời điểm query (Flow 3.6)
- Không có status `PendingReturn` — tiến trình trả kho được theo dõi ở bảng `ReturnRequest`
- Khi gia hạn: xem `ExtendRequest`, thanh toán thành công thì cập nhật `end_date`
# ExtendRequest
**Overview:** chứa các yêu cầu gia hạn hợp đồng do khách gửi từ trang chi tiết hợp đồng (Flow 3.3), FM duyệt (Flow 6)
- contract_id (N - 1: RentalContract)
- extra_months - số tháng muốn gia hạn thêm
- invoice_id (1 - 1: Invoice, null until FM approve) - hóa đơn gia hạn được tạo khi FM duyệt
- approved_by (N - 1: Account, null until FM approve/reject) - FM xử lý yêu cầu
- reject_reason (null as default)
- requested_at
- processed_at (null as default) - thời điểm FM duyệt/từ chối
- status:
  - PendingApproval: chờ FM duyệt, khách được phép hủy
  - Canceled: khách hủy khi còn `PendingApproval`
  - Rejected: FM từ chối
  - ApprovedPendingPayment: FM đã duyệt, hệ thống tạo `Invoice` và chờ khách thanh toán, khách không được hủy qua web
  - Expired: quá `Invoice.due_date` mà khách chưa thanh toán -> hóa đơn gia hạn chuyển `Canceled`
  - Completed: thanh toán thành công, `RentalContract.end_date += extra_months`

**NOTES:**
- Chỉ được tạo khi `now <= RentalContract.end_date` (hợp đồng quá hạn không được gia hạn qua web)
- Mỗi `RentalContract` chỉ có tối đa 1 `ExtendRequest` ở trạng thái `PendingApproval` hoặc `ApprovedPendingPayment` tại một thời điểm (lock theo `contract_id`)
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
# ReturnRequest
**Overview:** chứa các yêu cầu trả kho do khách gửi (Flow 3.4), FM phân công FS xử lý on-site (Flow 2.5). Tách bảng riêng thay vì dùng `RentalContract.status = PendingReturn` để không đè mất thông tin quá hạn của hợp đồng.
- contract_id (N - 1: RentalContract)
- assigned_staff_id (N - 1: Account, null until FM assign)
- preferred_date (MM/DD/YYYY) - ngày khách mong muốn trả kho
- reason (nullable) - lý do trả kho (tùy chọn)
- created_at
- completed_at (null as default) - thời điểm FS xác nhận trả kho xong
- status:
  - Pending: chờ FM phân công FS, khách được phép hủy
  - Assigned: FM đã phân công FS, khách không được hủy qua web (liên hệ FM/FS trực tiếp)
  - Canceled: khách hủy khi còn `Pending`
  - Completed: FS kiểm tra và xác nhận trả kho hoàn tất -> `RentalContract.status = Completed`, `StorageUnit` chuyển sang `Maintenance` (1-3 ngày)

**NOTES:**
- Mỗi `RentalContract` chỉ có tối đa 1 `ReturnRequest` ở trạng thái `Pending` hoặc `Assigned` tại một thời điểm (lock theo `contract_id`)
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
- Các trạng thái on-site chi tiết (kiểm tra tình trạng, phát sinh phí hư hỏng, ...) sẽ bổ sung khi chốt Flow 2.5
# SupportRequest
**Overview:** chứa các yêu cầu hỗ trợ sự cố do khách gửi (Flow 3.5) hoặc FS ghi nhận tại kho, FM phân công FS xử lý on-site (Flow 7). Không ảnh hưởng tới `RentalContract`.
- unit_id (N - 1: StorageUnit)
- contract_id (N - 1: RentalContract, nullable) - null khi FS ghi nhận sự cố trên khoang không có hợp đồng `Active` (ví dụ khoang trống bị hư hỏng)
- reporter_id (N - 1: Account) - người gửi/ghi nhận yêu cầu (Customer hoặc FS)
- assigned_staff_id (N - 1: Account, null until FM assign)
- invoice_id (1 - 1: Invoice, null as default) - được gán nếu sự cố phát sinh phí cho khách (ví dụ làm lại chìa khóa), `Invoice.type = Service`
- issue_type (LostKey/AccessCode/UnitDamage/Other)
- description
- created_at
- resolved_at (null as default)
- status:
  - Open: vừa được gửi/ghi nhận, chờ FM phân công
  - Assigned: FM đã phân công FS
  - InProgress: FS đang xử lý
  - Resolved: FS xử lý xong
  - Closed: yêu cầu được đóng (khách xác nhận hoặc tự động đóng sau một khoảng thời gian)

**NOTES:**
- Khi Customer gửi: `contract_id` bắt buộc và phải thuộc về khách (`RentalContract.customer_id = reporter_id`)
- Chỉ phát sinh `invoice_id` khi có `contract_id` (có khách để thu phí)
- Một hợp đồng có thể có nhiều `SupportRequest` cùng lúc (khác với `ExtendRequest`/`ReturnRequest`)
- Chi tiết xử lý on-site và quy tắc đóng yêu cầu sẽ bổ sung khi chốt Flow 7
# Invoice
**Overview:** chứa thông tin thanh toán của khách hàng (hóa đơn)
- order_id (1 - 1: RentalOrder) -> null as default -> Được gán nếu hóa đơn phát sinh từ `RentalOrder` (Đặt cọc)
- contract_id (N - 1: RentalContract) -> null as default -> được gán nếu hóa đơn phát sinh từ `RentalContract` (tiền thuê hàng tháng, gia hạn, tiền phạt, dịch vụ, ...)
- customer_id (N - 1: Account)
- type - loại hóa đơn, quyết định tiền tố trong `code`:
  - Deposit (DEP): đặt cọc, gắn với `order_id`
  - Rental (RNT): tiền thuê định kỳ, gắn với `contract_id`
  - Extension (EXT): gia hạn hợp đồng, gắn với `contract_id` (được tham chiếu bởi `ExtendRequest.invoice_id`)
  - Penalty (PEN): tiền phạt (quá hạn, hư hỏng, ...), gắn với `contract_id`
  - Service (SVC): dịch vụ hỗ trợ / sự cố, gắn với `contract_id` (được tham chiếu bởi `SupportRequest.invoice_id`)
- code
  - Cấu trúc mã hóa đề xuất: Gợi nhớ & Dễ lọc. Để thuận tiện tuyệt đối khi kiểm tra, mã hóa đơn nên mang ý nghĩa phân loại theo công thức:
  - Mã hóa đơn = Tiền tố nghiệp vụ (theo `type`) + mã chi nhánh (Q7 - Quận 7, ...) + YYMMDD (ngày tạo hóa đơn) + random
  - Ví dụ:
    + INV-DEP-Q7-260911-A89F: Hóa đơn cọc (DEP), chi nhánh Quận 7, ngày 11/09/2026.
    + INV-RNT-TD-261001-K312: Hóa đơn tiền thuê định kỳ (RNT), chi nhánh Thủ Đức.
    + INV-EXT-Q7-261020-C45D: Hóa đơn gia hạn hợp đồng (EXT), chi nhánh Quận 7.
    + INV-SVC-Q7-260915-091B: Hóa đơn dịch vụ ngoài / sự cố khóa (SVC).
- title
- desc
- discount_amount (default 0) - số tiền được giảm theo chính sách discount của BOM
- amount - số tiền khách phải trả (đã trừ `discount_amount`)
- status:
  - Unpaid: chờ thanh toán
  - Paid: đã thanh toán thành công
  - Canceled: hóa đơn bị hủy (đơn/yêu cầu gốc bị hủy hoặc hết hạn)
- created_at
- due_date
- paid_at (null until Paid)

**NOTES:**
- Xác định nguồn gốc hóa đơn dựa vào `type` (không suy ra từ việc `order_id`/`contract_id` null):
  - `Deposit` -> `order_id`
  - `Rental`/`Penalty` -> `contract_id`
  - `Extension` -> `contract_id` + truy ngược qua `ExtendRequest.invoice_id`
  - `Service` -> `contract_id` + truy ngược qua `SupportRequest.invoice_id`
- Khi `PaymentTransaction` của hóa đơn thành công, xử lý theo `type`:
  - `Deposit` -> `RentalOrder.status = Pending`, `StorageUnit.status = Reserved`
  - `Extension` -> cập nhật `RentalContract.end_date` và `ExtendRequest.status = Completed`
# ProposalFeedback
- order_id (N - 1: RentalOrder) - một đơn có thể được đề xuất nhiều lần nếu khách từ chối và chọn lại
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- status (Pending/Agree/Reject)
- note

**NOTES:**
- field note dùng để khi khách từ chối và muốn chọn lại, sẽ nêu lý do vì sao từ chối, ...
- **Chưa được sử dụng ở flow nào trong draft.md** — cần xác định bước đề xuất khoang nằm trước hay sau khi đặt cọc (Flow 1/2), nếu không dùng thì bỏ bảng này

# PaymentTransaction
- invoice_id (N - 1: Invoice)
- method (BankTransferQR/Card)
- gateway_transaction_no -  Mã giao dịch định danh từ cổng thanh toán/ngân hàng trả về (ví dụ mã vnpay_TransactionNo, payOS reference code, ...) -> Dùng để tra cứu, đối soát khi có khiếu nại
- transaction_content
- response_payload: JSON / TEXT, nullable -> Lưu toàn bộ log raw webhook/IPN để đối soát
- amount
- failure_reason (null as default)
- paid_at (null until Success)
- created_at
- status (Pending/Failed/Success)

**NOTES:**
- Hỗ trợ QR chuyển khoản (Flow 1.3) và thẻ (Visa/Mastercard, dùng thêm cho pre-authorization nếu chốt `OnHold`) — bỏ ràng buộc "visa card only" vì mâu thuẫn với Flow 1.3

# Account
**Overview:** tài khoản của tất cả người dùng trong hệ thống (Admin, BOM, FM, FS, Customer), được tham chiếu bởi các field `customer_id`, `staff_id`, `approved_by`, ... ở các bảng khác.
- role_id (N - 1: Role)
- facility_id (N - 1: Facility, nullable) - chi nhánh được giao, chỉ dùng cho FM/FS (Admin, BOM, Customer để null)
- email (unique)
- phone
- password_hash
- full_name
- status (Active/Inactive/Locked)
- created_at
- updated_at

**NOTES:**
- FM chỉ được thao tác dữ liệu thuộc `facility_id` của mình
- Customer không gắn `facility_id` vì có thể thuê khoang ở nhiều chi nhánh
# Role
**Overview:** các vai trò trong hệ thống, System Administrator quản lý (update role).
- name (Admin/BOM/FM/FS/Customer)
- description
# Permission
**Overview:** các quyền truy cập dữ liệu theo model RBAC.
- code - ví dụ: `rental_request.approve`, `invoice.read`, `policy.update`
- description
# RolePermission
**Overview:** bảng trung gian N - N giữa `Role` và `Permission`, System Administrator thiết lập quyền cho từng role.
- role_id (N - 1: Role)
- permission_id (N - 1: Permission)
# LoginHistory
**Overview:** lịch sử đăng nhập của users, System Administrator theo dõi.
- account_id (N - 1: Account, nullable) - null khi đăng nhập bằng email không tồn tại
- email - email dùng để đăng nhập
- ip_address
- user_agent
- status (Success/Failed)
- failure_reason (null as default)
- created_at
# AuditLog
**Overview:** lịch sử hoạt động của users (duyệt yêu cầu, cập nhật trạng thái, đổi giá, đổi role, ...), System Administrator theo dõi.
- account_id (N - 1: Account) - người thực hiện
- action - ví dụ: `RentalRequest.Approve`, `Policy.Update`, `Account.UpdateRole`
- entity_type - tên bảng bị tác động
- entity_id
- old_value (JSON, nullable)
- new_value (JSON, nullable)
- created_at
# Facility
**Overview:** chi nhánh kho, được tham chiếu bởi `RentalRequest.facility_id`, `StorageUnit.facility_id`, `Account.facility_id`.
- code (unique) - mã chi nhánh dùng trong mã hóa đơn/hợp đồng (Q7 - Quận 7, TD - Thủ Đức, ...)
- name
- address
- phone
- status (Active/Inactive)
- created_at

**NOTES:**
- FM của chi nhánh xác định qua `Account` có role `FM` và `facility_id` tương ứng
# UnitType
**Overview:** loại khoang chứa (type, size, rental price) hiển thị cho khách xem và chọn khi gửi yêu cầu đặt kho.
- name - ví dụ: Small, Medium, Large
- width, depth, height (m)
- area (m2)
- description
- monthly_price - giá thuê hiện tại mỗi tháng (BOM quản lý)
- updated_by (N - 1: Account) - BOM cập nhật giá
- updated_at

**NOTES:**
- `RentalContract.monthly_price` lưu giá tại thời điểm ký, không phụ thuộc giá hiện tại ở bảng này
- Nếu mỗi chi nhánh có giá khác nhau thì tách giá ra bảng riêng theo (`facility_id`, `unit_type_id`)
# StorageUnit
**Overview:** từng khoang chứa cụ thể tại chi nhánh, FM quản lý và chỉ định cho khách.
- facility_id (N - 1: Facility)
- unit_type_id (N - 1: UnitType)
- code - mã khoang, ví dụ: M-101
- location - vị trí trong kho (tầng, dãy, ...)
- status:
  - Available: sẵn sàng cho thuê
  - Reserved: đã được đặt cọc, giữ cho khách tới khi bàn giao (Flow 2)
  - Occupied: đã bàn giao cho khách, đang có hợp đồng `Active`
  - Maintenance: đang bảo trì (1-3 ngày sau khi khách trả kho) hoặc đang sửa chữa sự cố
- created_at
- updated_at

**NOTES:**
- `code` unique trong phạm vi một `facility_id`
- Nếu chốt trạng thái `OnHold` (xem draft.md mục Storage unit) thì bổ sung vào `status`
# Policy
**Overview:** các chính sách chung do BOM đề ra (cho thuê, đặt cọc, gia hạn, hủy, trả khoang chứa, xử lý quá hạn), lưu dạng key - value để không hardcode.
- key (unique) - ví dụ:
  - `deposit.amount_months` - số tháng tiền thuê cần đặt cọc
  - `deposit.due_hours` - hạn thanh toán hóa đơn đặt cọc
  - `request.account_timeout_hours` - thời gian chờ khách tạo tài khoản sau khi yêu cầu được duyệt
  - `contract.expiring_soon_days` - số ngày N để cảnh báo hợp đồng sắp hết hạn (Flow 3)
  - `overdue.fee_per_day` - phí quá hạn mỗi ngày
  - `unit.maintenance_days` - số ngày bảo trì sau khi trả kho
- value
- value_type (Number/Percent/Text/Boolean)
- description
- updated_by (N - 1: Account) - BOM cập nhật
- updated_at
# ExtraFee
**Overview:** các khoản phí extra do BOM quản lý, dùng khi tạo hóa đơn dịch vụ/sự cố (`Invoice.type = Service`).
- name - ví dụ: Làm lại chìa khóa, Reset mã cửa
- amount
- description
- is_active
- updated_by (N - 1: Account)
- updated_at
# Discount
**Overview:** các chương trình giảm giá do BOM quản lý.
- code (unique)
- name
- discount_type (Percent/Fixed)
- value
- apply_to (Deposit/Rental/Extension/All) - loại hóa đơn được áp dụng (theo `Invoice.type`)
- min_months (nullable) - số tháng thuê/gia hạn tối thiểu để được áp dụng
- start_at
- end_at
- is_active
- created_by (N - 1: Account)
- created_at
# AppointmentSlot
**Overview:** các khung giờ hẹn on-site của chi nhánh, hệ thống đưa ra làm lựa chọn lịch hẹn để khách chọn (tư vấn, ký hợp đồng, xem khoang, nhận khoang).
- facility_id (N - 1: Facility)
- start_at (MM/DD/YYYY HH:mm)
- end_at (MM/DD/YYYY HH:mm)
- capacity - số khách tối đa trong khung giờ
- booked_count
- status (Open/Full/Closed)

**NOTES:**
- Khi khách chọn một slot, `RentalOrder.appointment_at` lấy theo `start_at` của slot và `booked_count` tăng lên
# CheckInRecord
**Overview:** FS ghi nhận khách đến/đi tại kho, dùng để quan sát lịch trình trong ngày và làm lịch sử check-in/out của hợp đồng (Flow 3.2).
- facility_id (N - 1: Facility)
- staff_id (N - 1: Account) - FS ghi nhận
- customer_id (N - 1: Account, nullable) - null nếu khách chưa có tài khoản
- visitor_name (nullable) - dùng khi `customer_id` null
- visitor_phone (nullable)
- unit_id (N - 1: StorageUnit, nullable)
- order_id (N - 1: RentalOrder, nullable) - khi khách đến theo lịch hẹn
- contract_id (N - 1: RentalContract, nullable) - khi khách đến lấy/gửi đồ, trả kho, hỗ trợ sự cố
- purpose (Appointment/Handover/Access/Return/Support/Other)
- check_in_at
- check_out_at (null as default)
- note
# AccessCredential
**Overview:** chìa khóa/mã cửa của khoang chứa, FS giao cho khách khi bàn giao và nhận lại khi trả kho.
- unit_id (N - 1: StorageUnit)
- contract_id (N - 1: RentalContract, nullable)
- type (PhysicalKey/AccessCode)
- credential_code - số chìa khóa hoặc mã cửa (mã cửa cần được mã hóa khi lưu)
- issued_by (N - 1: Account) - FS giao
- issued_at
- returned_to (N - 1: Account, null as default) - FS nhận lại
- returned_at (null as default)
- status:
  - Active: đang được khách sử dụng
  - Returned: khách đã trả lại
  - Lost: khách báo mất
  - Revoked: bị vô hiệu hóa (đổi mã, làm lại chìa khóa)

**NOTES:**
- Khi xử lý `SupportRequest` loại `LostKey`/`AccessCode`: bản ghi cũ chuyển sang `Lost`/`Revoked` và tạo bản ghi mới
# UnitInspection
**Overview:** biên bản FS kiểm tra tình trạng khoang chứa khi bàn giao cho khách (Flow 2) và khi khách trả lại khoang (Flow 2.5).
- unit_id (N - 1: StorageUnit)
- contract_id (N - 1: RentalContract)
- return_request_id (1 - 1: ReturnRequest, nullable) - chỉ gán khi `type = Return`
- staff_id (N - 1: Account) - FS kiểm tra
- type (Handover/Return)
- condition (Good/NeedCleaning/Damaged)
- note
- photo_urls (JSON) - ảnh chụp tình trạng khoang
- penalty_invoice_id (1 - 1: Invoice, nullable) - hóa đơn phạt hư hỏng (`Invoice.type = Penalty`) nếu có
- inspected_at

**NOTES:**
- So sánh biên bản `Handover` và `Return` của cùng một hợp đồng để xác định hư hỏng phát sinh
- Chi tiết sẽ bổ sung khi chốt Flow 2 và Flow 2.5
# Notification
**Overview:** thông báo gửi đến khách hàng/nhân viên qua web, email, SMS (duyệt/từ chối yêu cầu, hóa đơn cần thanh toán, hợp đồng sắp hết hạn, ...).
- account_id (N - 1: Account, nullable) - null khi gửi email cho người chưa có tài khoản (Flow 1.1)
- recipient - email/số điện thoại nhận (dùng cho channel Email/SMS)
- channel (Web/Email/SMS)
- type - ví dụ: `RentalRequest.Approved`, `Invoice.Created`, `Contract.ExpiringSoon`
- title
- content
- entity_type (nullable) - bảng liên quan để điều hướng khi bấm vào thông báo
- entity_id (nullable)
- is_read (default false) - chỉ dùng cho channel Web
- sent_at
- read_at (null as default)
- created_at
# Wishlist
**Overview:** danh sách chờ khi không còn khoang chứa phù hợp, hệ thống tự động thông báo khi có khoang trống (draft.md Flow 1.1).
- customer_id (N - 1: Account)
- facility_id (N - 1: Facility)
- unit_type_id (N - 1: UnitType)
- request_id (N - 1: RentalRequest, nullable) - gán khi yêu cầu bị chuyển sang danh sách chờ (Phương án 2)
- status (Waiting/Notified/Canceled)
- notified_at (null as default)
- created_at

**NOTES:**
- Phụ thuộc Phương án 2 ở Flow 1.1 (chưa chốt), có thể để ngoài MVP
