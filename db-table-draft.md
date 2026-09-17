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
**Overview:** hợp đồng thuê, được sinh và ký on-site ở Flow 2.3 sau khi khách xác nhận hiện trạng khoang. **Owner: Flow 2** (tạo + quản lý vòng đời). Flow 3 chỉ đọc và cập nhật `end_date` khi gia hạn. `Invoice.contract_id` tham chiếu tới bảng này.
- order_id (1 - 1: RentalOrder)
- customer_id (N - 1: Account)
- unit_id (N - 1: StorageUnit)
- code
  - Mã hợp đồng = CTR + mã chi nhánh + YYMMDD (ngày ký) + random
  - Ví dụ: CTR-Q7-260911-B27C: Hợp đồng chi nhánh Quận 7, ký ngày 11/09/2026.
- terms_version - snapshot version điều khoản khách đã đồng ý (Flow 4)
- monthly_price - giá thuê chốt tại thời điểm ký (không phụ thuộc giá hiện tại của unit)
- deposit_amount - tiền cọc đã thu ở Flow 1.3
- period - số tháng thuê
- start_date - mốc bắt đầu tính tiền thuê, theo chính sách Flow 4
- end_date - được cập nhật khi gia hạn thành công (Flow 3.3)
- signed_at
- signature
- pdf_url
- status (Draft/Signed/Active/Ended/Canceled)
  - Draft: hợp đồng đã được sinh, chờ khách ký
  - Signed: đã ký nhưng chưa bàn giao
  - Active: đã bàn giao, đang có hiệu lực (kể cả đã quá `end_date` nhưng chưa trả kho)
  - Ended: đã trả kho xong (Flow 2.5)
  - Canceled: hủy trước khi bàn giao khoang

**NOTES:**
- Mỗi `StorageUnit` chỉ có tối đa 1 hợp đồng `Active` tại một thời điểm
- Trạng thái còn hiệu lực / sắp hết hạn / quá hạn **không lưu vào `status`** mà tính trực tiếp từ `end_date` tại thời điểm query (Flow 3.6)
- Không có status `PendingReturn` — tiến trình trả kho được theo dõi ở bảng `ReturnRequest`
- Khi gia hạn: xem `ExtendRequest`, thanh toán thành công thì cập nhật `end_date`
- Đề xuất bổ sung từ Flow 3 (chờ Flow 2/4/6 xác nhận):
  - `billing_day` (1 - 28) - ngày trong tháng phát sinh hóa đơn tiền thuê định kỳ (Flow 4)
  - `terminated_at`, `termination_reason` + status `Terminated` - chấm dứt trước hạn do vi phạm/quá hạn thanh toán (Flow 6)
# ExtendRequest
**Overview:** chứa các yêu cầu gia hạn hợp đồng do khách gửi từ trang chi tiết hợp đồng (Flow 3.3), FM duyệt (Flow 6)
- contract_id (N - 1: RentalContract)
- extra_months - số tháng muốn gia hạn thêm
- invoice_id (1 - 1: Invoice, null until FM approve) - hóa đơn gia hạn được tạo khi FM duyệt
- approved_by (N - 1: Account, null until FM approve/reject) - FM xử lý yêu cầu
- reject_reason (null as default)
- cancel_reason (null as default) - lý do khách tự hủy (tùy chọn)
- requested_at
- processed_at (null as default) - thời điểm FM duyệt/từ chối
- status:
  - PendingApproval: chờ FM duyệt, khách được phép hủy
  - Canceled: khách hủy khi còn `PendingApproval`, hoặc hệ thống hủy khi hợp đồng bị chấm dứt trước hạn
  - Rejected: FM từ chối
  - ApprovedPendingPayment: FM đã duyệt, hệ thống tạo `Invoice` và chờ khách thanh toán, khách không được hủy qua web
  - Expired: quá `Invoice.due_date` mà khách chưa thanh toán -> hóa đơn gia hạn chuyển `Canceled`
  - Completed: thanh toán thành công, `RentalContract.end_date += extra_months`

**NOTES:**
- Chỉ được tạo khi `now <= RentalContract.end_date` (hợp đồng quá hạn không được gia hạn qua web)
- Mỗi `RentalContract` chỉ có tối đa 1 `ExtendRequest` ở trạng thái `PendingApproval` hoặc `ApprovedPendingPayment` tại một thời điểm (partial unique index trên `contract_id`)
- Không được tạo khi hợp đồng đang có `ReturnRequest` ở `Pending`/`Assigned` (kiểm tra trong transaction, Flow 3.6)
- Hợp đồng bị chấm dứt trước hạn: `PendingApproval` -> `Canceled`; `ApprovedPendingPayment` -> `Expired` và hóa đơn gia hạn -> `Canceled`
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
# ReturnRequest
**Overview:** chứa các yêu cầu trả kho do khách gửi (Flow 3.4), FM phân công FS xử lý on-site (Flow 2.5). Tách bảng riêng thay vì dùng `RentalContract.status = PendingReturn` để không đè mất thông tin quá hạn của hợp đồng.
- contract_id (N - 1: RentalContract)
- assigned_staff_id (N - 1: Account, null until FM assign)
- preferred_date (MM/DD/YYYY) - ngày khách mong muốn trả kho
- reason (nullable) - lý do trả kho (tùy chọn)
- cancel_reason (null as default) - lý do khách tự hủy (tùy chọn)
- created_at
- completed_at (null as default) - thời điểm FS xác nhận trả kho xong
- status:
  - Pending: chờ FM phân công FS, khách được phép hủy
  - Assigned: FM đã phân công FS, khách không được hủy qua web (liên hệ FM/FS trực tiếp)
  - Canceled: khách hủy khi còn `Pending`, hoặc hệ thống hủy khi hợp đồng bị chấm dứt trước hạn
  - Completed: Flow 2.5 xác nhận trả kho hoàn tất (`CheckoutRecord.result = COMPLETED`). Việc đóng hợp đồng (`RentalContract.status = Ended`) và chuyển/mở lại `StorageUnit` do Flow 2.5 thực hiện

**NOTES:**
- Mỗi `RentalContract` chỉ có tối đa 1 `ReturnRequest` ở trạng thái `Pending` hoặc `Assigned` tại một thời điểm (partial unique index trên `contract_id`)
- Không được tạo khi hợp đồng đang có `ExtendRequest` ở `PendingApproval`/`ApprovedPendingPayment` (kiểm tra trong transaction, Flow 3.6)
- `assigned_staff_id` phải là FS có `AccountFacilityAssignment` với facility của khoang
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
- Các bước on-site chi tiết (lịch hẹn trả kho, kiểm tra tình trạng, phí hư hỏng, đối trừ cọc) theo dõi ở `Appointment` + `CheckoutRecord` của Flow 2.5
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
- Chỉ phát sinh `invoice_id` khi có `contract_id` (có khách để thu phí). Hóa đơn `Invoice(type=Service)` do Flow 7 tạo theo `ExtraFee`
- `assigned_staff_id` phải là FS có `AccountFacilityAssignment` với facility của khoang
- Một hợp đồng có thể có nhiều `SupportRequest` cùng lúc (khác với `ExtendRequest`/`ReturnRequest`)
- Chi tiết xử lý on-site và quy tắc đóng yêu cầu sẽ bổ sung khi chốt Flow 7
# Invoice
**Overview:** chứa thông tin thanh toán của khách hàng (hóa đơn)
- order_id (N - 1: RentalOrder) -> null as default -> Được gán nếu hóa đơn phát sinh từ `RentalOrder` (Đặt cọc). N - 1 vì một đơn có thể phát sinh lại hóa đơn cọc (hóa đơn cũ `Canceled`)
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
- gateway_transaction_no (unique, nullable until gateway trả về) -  Mã giao dịch định danh từ cổng thanh toán/ngân hàng trả về (ví dụ mã vnpay_TransactionNo, payOS reference code, ...) -> Dùng để tra cứu, đối soát khi có khiếu nại
- transaction_content
- response_payload: JSON / TEXT, nullable -> Lưu toàn bộ log raw webhook/IPN để đối soát
- amount
- failure_reason (null as default)
- paid_at (null until Success)
- created_at
- status (Pending/Failed/Success)

**NOTES:**
- Hỗ trợ QR chuyển khoản (Flow 1.3) và thẻ (Visa/Mastercard, dùng thêm cho pre-authorization nếu chốt `OnHold`) — bỏ ràng buộc "visa card only" vì mâu thuẫn với Flow 1.3
- Bản ghi `Pending` được tạo **trước khi** redirect sang cổng thanh toán
- Webhook/IPN phải idempotent: giao dịch đã `Success`/`Failed` thì bỏ qua; `Invoice` được `SELECT ... FOR UPDATE` và chỉ áp dụng tác dụng (ví dụ cộng `RentalContract.end_date`) khi chuyển từ `Unpaid` sang `Paid` (Flow 3.6)

# Account
**Overview:** tài khoản của tất cả người dùng trong hệ thống (Admin, BOM, FM, FS, Customer), được tham chiếu bởi các field `customer_id`, `staff_id`, `approved_by`, ... ở các bảng khác.
- role_id (N - 1: Role)
- email (unique)
- phone
- password_hash
- full_name
- status (Active/Inactive/Locked)
- created_at
- updated_at

**NOTES:**
- Không có field `facility_id` trên Account. Quan hệ FM–Facility (1 - 1) lưu 1 chiều tại `Facility.fm_account_id`; FS–Facility (N - 1 phía FS) lưu ở `AccountFacilityAssignment`
- FM chỉ được thao tác dữ liệu thuộc facility có `Facility.fm_account_id` trỏ tới mình
- Customer không gắn facility vì có thể thuê khoang ở nhiều chi nhánh
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
**Overview:** chi nhánh kho, có đúng 1 FM phụ trách (1 - 1), được tham chiếu bởi `RentalRequest.facility_id`, `StorageUnit.facility_id`, `AccountFacilityAssignment.facility_id`.
- code (unique) - mã chi nhánh dùng trong mã hóa đơn/hợp đồng (Q7 - Quận 7, TD - Thủ Đức, ...)
- name
- address
- phone
- operating_hours
- fm_account_id (1 - 1: Account, null as default) - FM phụ trách
- status (Active/Inactive) - mặc định `Inactive` khi mới tạo
- created_at

**NOTES:**
- `fm_account_id` là nguồn duy nhất lưu quan hệ FM–Facility trong toàn hệ thống
- Facility chỉ được chuyển sang `Active` sau khi đã có `fm_account_id`
- Khi account đang là FM bị đổi sang role khác, set `fm_account_id = null` trong cùng transaction
- Không xóa cứng Facility vì còn liên kết `StorageUnit`, `RentalOrder`, ...
- `code` cần được bổ sung vào schema của flow-5 (hiện flow-5 chưa có, nhưng mã hợp đồng/hóa đơn cần)
# AccountFacilityAssignment
**Overview:** gán FS vào Facility (1 facility có nhiều FS), phục vụ RBAC data-scope. Không dùng cho FM.
- account_id (N - 1: Account, role FS)
- facility_id (N - 1: Facility)
- assigned_at

**NOTES:**
- Khi account FS bị đổi sang role khác, xóa dòng tương ứng trong cùng transaction
- Dùng để validate `assigned_staff_id` ở `ReturnRequest`/`SupportRequest` thuộc đúng facility của khoang
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
  - Rented: đã bàn giao cho khách, đang có hợp đồng `Active`
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
# Notification
**Overview:** thông báo gửi đến khách hàng/nhân viên qua web, email (duyệt/từ chối yêu cầu, hóa đơn cần thanh toán, hợp đồng sắp hết hạn, ...).
- account_id (N - 1: Account, nullable) - null khi gửi email cho người chưa có tài khoản (Flow 1.1)
- recipient - email nhận (dùng cho channel Email)
- channel (Web/Email)
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
