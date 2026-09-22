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
  - Pending: chờ FS được chỉ định xác nhận
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

## Bảng mới cần thêm (theo Flow 4)

# UnitType
**Overview:** loại khoang chứa (kích thước, mô tả, giá thuê hiện tại), dùng chung toàn hệ thống. Đây là nguồn giá chính cho mọi `StorageUnit` cùng loại tại một thời điểm.
- name - ví dụ: Small, Medium, Large
- width, depth, height (m)
- area (m2)
- description
- monthly_price - giá thuê hiện tại mỗi tháng, 1 giá trị cố định duy nhất (không phải khung min–max)
- status (Active/Inactive) - mặc định Active
- updated_by (N - 1: Account)
- updated_at
- created_at

**CONSTRAINTS:**
- Không hard-delete khi đã có ít nhất 1 `StorageUnit` tham chiếu — chỉ chuyển `status = Inactive`.
- `UnitType` `Inactive` không hiển thị cho khách ở Flow 1 và FM không chọn được khi tạo `StorageUnit` mới ở Flow 5.
- Đổi `monthly_price` không ảnh hưởng ngược tới `RentalContract` đã ký (giá đã snapshot ở `RentalContract.monthly_price` tại thời điểm ký) — chỉ ảnh hưởng khách mới đặt hoặc gia hạn sau thời điểm đổi giá.

**NOTES:**
- MVP: 1 giá áp dụng toàn hệ thống cho mỗi `UnitType`, không phân biệt theo chi nhánh.
- `StorageUnit.monthly_price` (nullable) là giá override riêng cho 1 khoang cụ thể, chỉ có hiệu lực sau khi BOM phê duyệt (Advanced Feature) — MVP luôn để `null`, giá hiệu lực lấy từ `UnitType.monthly_price`.
- Ghi `AuditLog` mỗi lần đổi `monthly_price` (old_value/new_value).
- Giá override theo từng `StorageUnit` **không thuộc MVP** — `StorageUnit` (Flow 5) hiện không có field giá riêng. Khi triển khai Advanced Feature này, Flow 5 sẽ bổ sung field `monthly_price` (nullable) vào `StorageUnit` cùng lúc với cơ chế phê duyệt của BOM ở đây.

---

# Policy
**Overview:** toàn bộ ngưỡng thời gian, mốc tính toán, quy tắc mà các flow khác cần nhưng không nên hard-code, dưới dạng key–value. BOM sửa `value` trên UI, flow tiêu thụ đọc theo key ngay lần truy vấn tiếp theo, không cần deploy lại.
- key (unique)
- value
- value_type (Number/Percent/Text/Boolean)
- execution_type (Automated/ManualGuardrail)
- description
- updated_by (N - 1: Account)
- updated_at

**CONSTRAINTS:**
- Validate theo `value_type` khi lưu (Number phải parse được số, Percent phải trong [0,100], Boolean chỉ nhận true/false).
- Ghi `AuditLog` mỗi lần đổi `value`.

**Danh mục key chính thức (đối chiếu trực tiếp với các key đang được tham chiếu trong schema Flow 1/2/2.5/3/5):**

| Key | value_type | execution_type | Dùng ở | Ý nghĩa | Mặc định |
|---|---|---|---|---|---:|
| `request.pending_expiry_days` | Number (ngày) | Automated | Flow 1 (`RentalRequest`) | Hạn `RentalRequest` còn `Pending` trước khi tự `Expired` | 7 |
| `account.claim_ttl_days` | Number (ngày) | Automated | Flow 1 (`RentalRequest.expires_at`) | Hạn khách tạo tài khoản sau khi được duyệt, tính từ `responded_at` | 7 |
| `proposal.response_ttl_days` | Number (ngày) | Automated | Flow 1 (`ProposalFeedback.expires_at`) | Hạn khách phản hồi 1 `ProposalFeedback` trước khi tự `Expired` | 3 |
| `invoice.deposit_due_days` | Number (ngày) | Automated | Flow 1 (`Invoice.due_date`, type=Deposit) | Hạn thanh toán hóa đơn đặt cọc | 3 |
| `appointment.booking_window_days` | Number (ngày) | Automated | Flow 1 | Khách phải chọn lịch check-in trong vòng N ngày kể từ lúc cọc | 7 |
| `appointment.max_days_after_deposit` | Number (ngày) | Automated | Flow 1 | Ngày hẹn check-in tối đa cách lúc cọc bao lâu | 14 |
| `appointment.daily_slot_count` | Number (khung) | Automated | Flow 1 | Số khung giờ cố định mỗi ngày cho lịch check-in/trả kho | 3 |
| `appointment.capacity_mode` | Text | Automated | Flow 1 | Chế độ sinh slot (MVP: `fixed_windows`) | `fixed_windows` |
| `appointment.checkin_reschedule_enabled` | Boolean | Automated | Flow 1 | Bật/tắt dời lịch check-in | `false` |
| `proposal.max_rejection_count` | Number (lần) | Automated | Flow 1 (`ProposalFeedback`) | Số lần khách được từ chối proposal **trước khi cọc** | 3 |
| `order.deposit_expiry_days` | Number (ngày) | Automated | Flow 1 (`RentalOrder.expires_at`) | Hạn giữ khoang `Reserved` kể từ lúc cọc tới khi bàn giao | 30 |
| `fee.unit_change` | Number | Automated | Flow 1 | Phí đổi sang khoang tương đương sau khi bị từ chối | chờ BOM |
| `handover.max_rejection_count` | Number (lần) | Automated | Flow 2 (`HandoverRecord`) | Số lần khách từ chối khoang **tại chỗ, sau khi đã cọc** | 2 |
| `handover.payment_grace_hours` | Number (giờ) | Automated | Flow 2 | Gia hạn thanh toán tháng đầu tại buổi bàn giao trước khi hủy hợp đồng | chờ BOM |
| `contract.start_date_rule` | Text | Automated | Flow 2 (`RentalContract.start_date`) | Quy tắc tính mốc bắt đầu tính tiền thuê | chờ BOM |
| `contract.prepaid_months` | Number (tháng) | Automated | Flow 2 | Số tháng thu trước tại buổi bàn giao | 1 |
| `unit.maintenance_days` | Number (ngày) | Automated | Flow 2.5, Flow 5 (`StorageUnit.maintenance_started_at`) | Số ngày `Maintenance` (phát sinh từ trả kho) trước khi cron mở lại `Available` | 1–3 |
| `contract.expiring_soon_days` | Number (ngày) | Automated | Flow 3 | Ngưỡng N ngày trước `end_date` để cảnh báo hợp đồng sắp hết hạn | chờ BOM |
| `extension.invoice_due_days` | Number (ngày) | Automated | Flow 3 (`ExtendRequest`) | Hạn thanh toán hóa đơn gia hạn, tính từ lúc FM duyệt | chờ BOM |
| `report.default_range_months` | Number (tháng) | Automated | Flow 4, Flow 5 | Khoảng thời gian mặc định trên dashboard doanh thu (4.6) và báo cáo cơ sở (Flow 5, 5.4) | 2 |
| `overdue.fee_per_day` | Number | Automated | Flow 6 | Phí phạt mỗi ngày quá hạn — nơi duy nhất tính phí trễ hạn | chờ BOM |
| `overdue.lock_after_days` | Number (ngày) | Automated | Flow 6 | Số ngày nợ phí trước khi cron khóa hợp đồng/quyền truy cập | chờ BOM |
| `overdue.waive_max_percent` | Percent | ManualGuardrail | Flow 6 | Mức % nhân viên tự quyết miễn giảm phạt | chờ BOM |
| `overdue.waive_max_amount` | Number | ManualGuardrail | Flow 6 | Mức tiền tối đa nhân viên tự quyết miễn giảm phạt | chờ BOM |
| `account_role_request.expiry_days` | Number (ngày) | Automated | Flow 5 (`AccountRoleRequest.expires_at`) | Hạn Admin xử lý 1 dòng AccountRoleRequest trước khi hệ thống cảnh báo/escalate cho BOM | 3 |

**NOTES:**
- MVP không version hoá theo khoảng thời gian hiệu lực — mỗi key chỉ có 1 giá trị hiện hành; lịch sử tra qua `AuditLog`. Không rủi ro cho hợp đồng cũ vì giá trị đã "chốt" luôn được snapshot ở nơi phát sinh (`Invoice.amount`, `RentalContract.monthly_price`, `RentalOrder.expires_at`).
- Key mới không cần đổi schema, nhưng code đọc key đó phải được lập trình sẵn (Cấp độ 1 vs Cấp độ 2, xem 4.0).
- `deposit.type`, `deposit.value`, `deposit.due_hours`, `request.account_timeout_hours`, `order.auto_cancel_days`, `appointment.no_show_limit`, `appointment.reject_limit` đã bị loại khỏi danh mục vì không khớp field nào thực sự tồn tại trong schema Flow 1/2 hiện tại.

---

# ExtraFee
**Overview:** danh mục phí phát sinh thủ công ngoài tiền thuê định kỳ, dùng khi FS/FM lập biên bản phát sinh phí (Flow 2.5 `CheckoutRecord`, Flow 7 `SupportRequest`) và tạo `Invoice(type=Penalty/Service)`.
- name
- category (unique) - mã ngắn tra cứu, ví dụ: `LOST-KEY`, `CLEANING`, `DAMAGE`
- amount
- calculation_type (Fixed/Daily/Monthly/Percent)
- trigger_type (ManualIncident) - MVP chỉ có 1 giá trị, giữ chỗ mở rộng sau
- description
- status (Active/Inactive)
- created_at
- updated_at

**CONSTRAINTS:**
- Không xoá 1 `ExtraFee` đã từng được dùng để tạo `Invoice` — chỉ chuyển `Inactive`.

**NOTES:**
- Cách tính theo `calculation_type`: `Fixed` = amount × số lần; `Daily` = amount × số ngày; `Monthly` = amount mỗi tháng; `Percent` = amount × `RentalContract.monthly_price`.
- `trigger_type` luôn `ManualIncident` cho MVP — tiền thuê định kỳ và phạt quá hạn không phải bản ghi trong `ExtraFee`, dùng cơ chế snapshot riêng đã có (`RentalContract.monthly_price`, `Policy.overdue.fee_per_day`).
- Khi tạo hóa đơn phí, hệ thống tra `ExtraFee` theo `category` để lấy `amount`/`calculation_type`, ghi `category` vào `Invoice.desc` để đối soát.
- **`ExtraFee.category` không phải là prefix của `Invoice.code`.** Bảng "Service Code" trong schema `Invoice` (Flow 1) phục vụ mục đích khác (đặt tên mã hóa đơn hiển thị cho khách) — 2 bảng độc lập, có thể tình cờ trùng ký hiệu (`CLN`/`DMG`) nhưng không tự đồng bộ với nhau. Xem comment gửi Flow 1 bên dưới.

---

# Discount
**Overview:** chương trình giảm giá áp dụng cho hóa đơn đặt cọc/tiền thuê/gia hạn, tự động áp dụng khi tạo `Invoice`.
- code (unique)
- name
- discount_type (Percent/Fixed)
- value
- apply_to (Deposit/Rental/Extension/All) - khớp `Invoice.type`
- min_months (nullable)
- start_at
- end_at
- is_active
- created_at
- updated_at

**CONSTRAINTS:**
- Tại thời điểm tạo `Invoice`, hệ thống tìm `Discount` đang `is_active` và còn hiệu lực (`start_at <= now <= end_at`) khớp `apply_to` và `min_months` (nếu có) → ghi `Invoice.discount_amount`, `Invoice.amount = giá gốc - discount_amount`.
- Mỗi hóa đơn chỉ áp dụng tối đa 1 `Discount`, không cộng dồn.

**NOTES:**
- **Phụ thuộc Flow 1 bổ sung field `discount_amount` vào `Invoice`** — xem comment gửi Flow 1 bên dưới, đây là điều kiện tiên quyết để `Discount` hoạt động được.

---

# RentalTerm
**Overview:** nội dung/version điều khoản hợp đồng. `RentalContract.terms_version` (Flow 2) snapshot version tại đây dưới dạng string, không phải FK.
- version (unique, VD: "v2.0")
- file_url - PDF điều khoản
- content - tuỳ chọn, text hiển thị inline (bao gồm chính sách hủy/trả/gia hạn/quá hạn cho khách)
- status (Active/Inactive)
- created_by (N - 1: Account)
- created_at

**CONSTRAINTS:**
- Tại một thời điểm chỉ có đúng 1 `RentalTerm` `Active`. Khi BOM kích hoạt version mới, version đang `Active` tự chuyển `Inactive` trong cùng transaction.

**NOTES:**
- Hợp đồng đã ký giữ nguyên `terms_version` đã snapshot — đổi điều khoản mới không ảnh hưởng ngược tới hợp đồng cũ.

---

**Audit action Flow 4 bổ sung vào catalog chung (không tạo bảng riêng):**

| Action | Khi nào ghi | Entity | Actor |
|---|---|---|---|
| `UNIT_TYPE_PRICE_UPDATED` | BOM đổi `monthly_price` | `UnitType` | BOM |
| `UNIT_TYPE_STATUS_UPDATED` | BOM chuyển `Active`/`Inactive` | `UnitType` | BOM |
| `POLICY_UPDATED` | BOM đổi `value` của 1 key | `Policy` | BOM |
| `EXTRA_FEE_UPDATED` | BOM thêm/sửa/vô hiệu hoá phí | `ExtraFee` | BOM |
| `DISCOUNT_CREATED` / `DISCOUNT_UPDATED` | BOM tạo/sửa chương trình giảm giá | `Discount` | BOM |
| `RENTAL_TERM_ACTIVATED` | BOM kích hoạt version điều khoản mới | `RentalTerm` | BOM |
| `STORAGE_UNIT_PRICE_OVERRIDE_APPROVED` | BOM duyệt giá override cho 1 `StorageUnit` cụ thể (Advanced Feature) | `StorageUnit` | BOM |

**Cross-reference (ai đọc gì từ Flow 4):**

| Flow | Đọc từ Flow 4 |
|---|---|
| Flow 1 | `request.pending_expiry_days`, `account.claim_ttl_days`, `proposal.response_ttl_days`, `invoice.deposit_due_days`, `appointment.booking_window_days`, `appointment.max_days_after_deposit`, `appointment.daily_slot_count`, `appointment.capacity_mode`, `appointment.checkin_reschedule_enabled`, `proposal.max_rejection_count`, `order.deposit_expiry_days`, `fee.unit_change` |
| Flow 2 | `UnitType.monthly_price`, `RentalTerm` (Active), `contract.start_date_rule`, `contract.prepaid_months`, `handover.payment_grace_hours`, `handover.max_rejection_count` |
| Flow 2.5 | `ExtraFee` (LOST-KEY/CLEANING/DAMAGE...), `unit.maintenance_days` |
| Flow 3 | `contract.expiring_soon_days`, `extension.invoice_due_days`, `Discount` (nếu áp dụng cho gia hạn) |
| Flow 5 | `UnitType` (chỉ đọc `unit_type_id`, `monthly_price`), `unit.maintenance_days`, `report.default_range_months`, `account_role_request.expiry_days` |
| Flow 6 | `overdue.fee_per_day`, `overdue.lock_after_days`, `overdue.waive_max_percent`, `overdue.waive_max_amount` |
