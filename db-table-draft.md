# RentalRequest
**⚠️ Bản tham khảo — KHÔNG phải nguồn chính thức.** Bảng này do **Flow 1** sở hữu và đã tiến xa hơn bản dưới đây (thêm `normalized_customer_email`, status `Converted` thay vì suy ra qua `customer_id IS NULL`, ...). Flow 3 giữ lại bản cũ này chỉ để tra cứu ngữ cảnh lịch sử (Flow 3 không đọc/ghi trực tiếp bảng này) — khi cần schema mới nhất, xem `specs/flow-1/db-table-draft.md`.

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
**⚠️ Bản tham khảo — KHÔNG phải nguồn chính thức.** Owner: Flow 1 (tạo) → Flow 2 (vận hành tiếp tới `Done`). Bộ `status` hiện có nhiều bản khác nhau giữa các branch, **chưa hợp nhất** (xem `db-table-draft-central.md` mục 2.2) — đây là xung đột P0 của toàn hệ thống, không phải việc Flow 3 tự quyết. Flow 3 **chỉ đọc gián tiếp** qua `RentalContract.order_id` (để lấy hóa đơn đặt cọc ở 3.2), không phụ thuộc `RentalOrder.status` cụ thể là gì — nên xung đột này không chặn Flow 3. Schema chính thức xem `specs/flow-1` / `specs/flow-2-2.5`.

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
- period - số tháng thuê (Flow 3 cộng thêm `extra_months` mỗi lần gia hạn thành công — xem `ExtendRequest.Completed` và review PR #4 mục A3 — để khớp với `end_date`)
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
  - Expired: quá `Invoice.due_date` mà khách chưa thanh toán -> hóa đơn gia hạn chuyển `Canceled` (job định kỳ, Flow 3.6 g)
  - Completed: thanh toán thành công, `RentalContract.end_date += extra_months`, `RentalContract.period += extra_months` (PR #4, A3)

**NOTES:**
- Chỉ được tạo khi `today <= RentalContract.end_date` (so sánh theo ngày; hợp đồng quá hạn không được gia hạn qua web)
- Mỗi `RentalContract` chỉ có tối đa 1 `ExtendRequest` ở trạng thái `PendingApproval` hoặc `ApprovedPendingPayment` tại một thời điểm (partial unique index trên `contract_id`)
- Không được tạo khi hợp đồng đang có `ReturnRequest` ở `Pending`/`Assigned` (kiểm tra trong transaction, Flow 3.6)
- Hợp đồng bị chấm dứt trước hạn (nếu chốt status `Terminated`): `PendingApproval` -> `Canceled`; `ApprovedPendingPayment` -> `Expired` và hóa đơn gia hạn -> `Canceled`
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
# ReturnRequest
**Overview:** chứa các yêu cầu trả kho do khách gửi (Flow 3.4), FM phân công FS xử lý on-site (Flow 2.5). Tách bảng riêng thay vì dùng `RentalContract.status = PendingReturn` để không đè mất thông tin quá hạn của hợp đồng.
- contract_id (N - 1: RentalContract)
- assigned_staff_id (N - 1: Account, null until FM assign)
- preferred_date (MM/DD/YYYY) - ngày khách **đề xuất ban đầu** để trả kho, phải `>= today` lúc tạo; được gửi cả khi hợp đồng đã quá hạn. **Không cập nhật lại sau khi tạo** — chỉ mang tính lịch sử/căn cứ ban đầu cho FM. Ngày hẹn thực tế sau khi `Assigned` đọc từ `Appointment` (Flow 2.5), không đọc field này (PR #4, A2)
- reason (nullable) - lý do trả kho (tùy chọn)
- cancel_reason (null as default) - lý do khách tự hủy (tùy chọn)
- created_at
- completed_at (null as default) - thời điểm FS xác nhận trả kho xong
- status:
  - Pending: chờ FM phân công FS, khách được phép hủy
  - Assigned: FM đã phân công FS, khách không được hủy qua web (liên hệ FM/FS trực tiếp)
  - Canceled: khách hủy khi còn `Pending`, hoặc hệ thống hủy khi hợp đồng bị chấm dứt trước hạn (nếu chốt status `Terminated`)
  - Completed: Flow 2.5 xác nhận trả kho hoàn tất (`CheckoutRecord.result = COMPLETED`). Việc đóng hợp đồng (`RentalContract.status = Ended`) và chuyển/mở lại `StorageUnit` do Flow 2.5 thực hiện

**NOTES:**
- Mỗi `RentalContract` chỉ có tối đa 1 `ReturnRequest` ở trạng thái `Pending` hoặc `Assigned` tại một thời điểm (partial unique index trên `contract_id`)
- Không được tạo khi hợp đồng đang có `ExtendRequest` ở `PendingApproval`/`ApprovedPendingPayment` (kiểm tra trong transaction, Flow 3.6)
- `assigned_staff_id` phải là FS có `AccountFacilityAssignment` với facility của khoang
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
- Các bước on-site chi tiết (lịch hẹn trả kho, kiểm tra tình trạng, phí hư hỏng, đối trừ cọc) theo dõi ở `Appointment` + `CheckoutRecord` của Flow 2.5
- **PR #4, A2:** nếu khoang còn đồ lúc kiểm tra (`CheckoutRecord.result = PENDING_ITEMS`), Flow 2.5 tạo `Appointment` mới cho lần hẹn lại — `ReturnRequest.preferred_date`/`status` giữ nguyên `Assigned`, không đổi theo. FE đọc ngày hẹn hiện tại từ `Appointment` mới nhất, không phải `preferred_date`.
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
- Khi FS ghi nhận (`POST /api/staff/support-requests`): FS phải thuộc facility của khoang; `contract_id` tự gán theo hợp đồng `Active` của khoang, không có thì null
- Chỉ phát sinh `invoice_id` khi có `contract_id` (có khách để thu phí). Hóa đơn `Invoice(type=Service)` do Flow 7 tạo theo `ExtraFee`
- **PR #4, A5:** chỉ dùng bảng này khi sự cố phát hiện **giữa kỳ thuê** (hợp đồng còn `Active`). Sự cố phát hiện **trong lúc trả kho** (vd mất chìa khi FS kiểm tra `CheckoutRecord`) không tạo `SupportRequest` — tính phí trực tiếp qua `Invoice(type=Penalty)` của Flow 2.5. Ranh giới là thời điểm phát hiện, không phải loại sự cố.
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
  - `Extension` -> cập nhật `RentalContract.end_date`, `RentalContract.period` (PR #4, A3) và `ExtendRequest.status = Completed`
# ProposalFeedback
**Không thuộc phạm vi Flow 3.** Owner: Flow 1 (tạo lần đầu khi FM chỉ định khoang) · Dùng bởi: Flow 2 (khách từ chối khoang lúc check-in, FM đề xuất lại). Ghi chú cũ ở đây ("chưa dùng ở flow nào, cân nhắc bỏ") **đã lỗi thời** — flow-1 và flow-2-2.5 đều dùng bảng này rõ ràng (status `Pending/Agreed/Rejected/Expired`, có `expires_at`, ràng buộc không chọn lại `unit_id` đã bị từ chối). Flow 3 không đọc/ghi bảng này. Schema chính thức xem `specs/flow-1/db-table-draft.md`.

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
**Không thuộc phạm vi Flow 3 để tự định nghĩa lại.** **Contract dùng chung giữa các flow** — chốt bởi lead ở PR #4 (21/9): không flow nào sở hữu riêng bảng này, mỗi flow giữ đúng 1 định nghĩa giống nhau trong `db-table-draft.md` của mình, gộp lại khi merge.

**Field (theo contract chung):** `actor_account_id` (nullable — null nếu do hệ thống/cron/IPN), `action`, `entity_type`, `entity_id` (**String/Text** — hỗ trợ ID số, UUID hoặc chuỗi), `old_value`/`new_value` (JSON, nullable), `created_at`.

**Quy tắc chung:**
- Audit nghiệp vụ lưu trong database, không thay bằng application log.
- Mọi thay đổi trạng thái, quyền sở hữu hoặc tiền phải ghi audit.
- 1 action đổi nhiều entity → ghi **một bản ghi riêng cho mỗi entity**, không gộp chung.
- Không ghi lượt đọc dữ liệu, click giao diện, secret hoặc raw payment payload.

**Action Flow 3 bổ sung vào catalog chung khi merge (không tạo bảng riêng):**
- `EXTEND_REQUEST_APPROVED` / `EXTEND_REQUEST_REJECTED` — FM duyệt/từ chối gia hạn, entity `ExtendRequest`
- `RETURN_REQUEST_ASSIGNED` — FM phân công FS xử lý trả kho, entity `ReturnRequest`
- `SUPPORT_REQUEST_ASSIGNED` — FM phân công FS xử lý sự cố, entity `SupportRequest`
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
**Owner: Flow 5** (giữ nguyên đây vì Flow 3 đọc trực tiếp `status`/`facility_id` ở 3.2 — không redefine, chỉ đồng bộ theo bản mới nhất của Flow 5).

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
- Cập nhật 22/9: bản Flow 5 mới nhất (Sep 21) vẫn giữ 4 giá trị như trên, **chưa thêm `OnHold`** — ý tưởng `OnHold` (chống Holding Attack, xem draft.md mục Storage unit) vẫn đang là thảo luận mở, chưa vào schema chính thức. Flow 3 không tự thêm giá trị này khi chưa thấy Flow 5 chốt.
- Flow 5 mới thêm `maintenance_started_at` (nullable) để Flow 2.5 phân biệt `Maintenance` phát sinh từ trả kho hay từ sự cố FM tự chuyển thủ công — Flow 3 không đọc/ghi field này, chỉ nêu ở đây để không nhầm là thiếu sót.
# Policy
**Không thuộc phạm vi Flow 3 — chỉ giữ lại đây phần Flow 3 tiêu thụ.** Owner chính thức: **Flow 4** (`specs/flow-4-2.0`), cùng mô hình key-value như Flow 3 từng đề xuất, có thêm `execution_type` (Automated/ManualGuardrail). Schema đầy đủ xem `specs/flow-4-2.0/db-table-draft.md`.

**2 key Flow 3 đọc (đã được Flow 4 xác nhận, đúng tên — không còn là đề xuất chờ duyệt):**
- `contract.expiring_soon_days` (Number, ngày) — N trong bảng action 3.2 và thông báo sắp hết hạn 3.1
- `extension.invoice_due_days` (Number, ngày) — `Invoice.due_date` của hóa đơn gia hạn = ngày FM duyệt + giá trị này

`overdue.fee_per_day` thuộc phạm vi Flow 6, không phải Flow 3 (Flow 3 chỉ hiển thị hóa đơn phạt đã được tạo, không tự đọc key này).
# ExtraFee
**Không thuộc phạm vi Flow 3.** Owner chính thức: **Flow 4** (`specs/flow-4-2.0`), giữ nguyên tên `ExtraFee` nhưng bổ sung `calculation_type` (Fixed/Daily/Monthly/Percent) — hỗ trợ tính phí trả trễ theo "amount × số ngày" mà Flow 2.5 cần, điều bản cũ của Flow 3 (chỉ có `amount` cố định) không làm được. Flow 3 không tự tạo hóa đơn Service — việc này thuộc Flow 7, Flow 3 chỉ hiển thị hóa đơn đã tạo ở trang chi tiết hợp đồng (3.2). Schema đầy đủ xem `specs/flow-4-2.0/db-table-draft.md`.
# Discount
**Không thuộc phạm vi Flow 3 (not MVP theo report #9).** Owner chính thức: **Flow 4** (`specs/flow-4-2.0`), schema khớp gần như nguyên vẹn với đề xuất cũ của Flow 3 (`code`, `discount_type`, `apply_to` theo `Invoice.type`, `min_months`, `start_at`/`end_at`, `is_active`). Áp dụng tự động khi tạo `Invoice`, mỗi hóa đơn tối đa 1 `Discount`. Schema đầy đủ xem `specs/flow-4-2.0/db-table-draft.md`.
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
- Cập nhật 22/9: bản mới nhất của Flow 1 **không còn nhắc tới** ý tưởng Wishlist/Phương án 2 — nhiều khả năng team đã nghiêng về Phương án 1 (gợi ý khoang tương đương). Giữ bảng này ở trạng thái Advanced Feature, không tính vào MVP cho tới khi Flow 1 xác nhận lại.
