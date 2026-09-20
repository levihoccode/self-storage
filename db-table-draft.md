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
**Overview:** loại khoang chứa (kích thước, mô tả) và giá thuê hiện hành, do BOM quản lý tập trung. FM chỉ chọn `unit_type_id` khi tạo `StorageUnit`, không tự nhập giá.
- name
- width, depth, height (m)
- area (m2)
- description
- monthly_price — giá thuê hiện tại mỗi tháng, 1 giá trị cố định, không phải khung min–max
- status (Active/Inactive)
- updated_by (N - 1: Account)
- updated_at

**NOTES:**
- `RentalContract.monthly_price` (Flow 2) lưu giá tại thời điểm ký, không phụ thuộc giá hiện tại ở bảng này — đổi `monthly_price` không ảnh hưởng ngược hợp đồng cũ.
- Đã được dùng bởi ít nhất 1 `StorageUnit` thì không hard-delete, chỉ chuyển `Inactive`.
- MVP: 1 giá áp dụng toàn hệ thống theo `unit_type_id`, không override theo facility.

---

# Policy
**Overview:** key–value chính sách vận hành dùng chung toàn hệ thống (đặt cọc, bàn giao, gia hạn, quá hạn, bảo trì, appointment...). Flow 1/2/2.5/3/5/6 đọc từ đây, không tự định nghĩa lại.
- key (unique) — dạng `domain.action`, VD: `deposit.value`, `overdue.fee_per_day`
- value
- value_type (Number/Percent/Text/Boolean)
- execution_type (Automated/ManualGuardrail)
- description
- updated_by (N - 1: Account)
- updated_at

**NOTES:**
- `Automated`: đọc bởi cron/event của flow tiêu thụ, không có người can thiệp giữa chừng.
- `ManualGuardrail`: mức trần/sàn cho thao tác thủ công của FS/FM — flow tiêu thụ dùng để chặn nếu vượt ngưỡng, có thể escalate BOM duyệt (Maker-Checker thuộc flow tiêu thụ, không phải Flow 4).
- MVP không version hoá theo `effective_from`/`effective_to` — mỗi key chỉ giữ 1 giá trị hiện hành, lịch sử tra qua `AuditLog`. Giá trị đã "chốt" cho khách luôn snapshot ở nơi phát sinh (`Invoice.amount`, `RentalContract.monthly_price`/`terms_version`), không phụ thuộc `Policy` hiện tại.
- Tham số cần đi cùng nhau (VD `overdue.waive_max_percent` + `overdue.waive_max_amount`) tách thành 2 key phẳng riêng, không gộp JSON.

---

# ExtraFee
**Overview:** danh mục phí phát sinh thủ công ngoài tiền thuê định kỳ (hư hỏng, mất chìa, vệ sinh...), FS/FM chọn khi lập biên bản, dùng để tạo `Invoice(type=Penalty/Service)`.
- name
- category — mã ngắn, VD: LOST-KEY, CLN, DMG
- amount
- calculation_type (Fixed/Daily/Monthly/Percent)
- trigger_type (ManualIncident) — MVP chỉ có 1 giá trị
- description
- status (Active/Inactive)

**NOTES:**
- Cách tính: `Fixed` = amount × số lần; `Daily` = amount × số ngày; `Monthly` = amount/tháng; `Percent` = amount × giá thuê hợp đồng.
- Rental Fee và Penalty quá hạn **không phải bản ghi trong `ExtraFee`** — dùng snapshot riêng (`RentalContract.monthly_price`, `Policy.overdue.fee_per_day`). `trigger_type` chỉ giữ chỗ mở rộng sau.
- Đã dùng để tạo `Invoice` thì không hard-delete, chỉ chuyển `Inactive`.
- MVP dùng chung 1 mức phí toàn hệ thống, không override theo facility.

---

# Discount
**Overview:** chương trình khuyến mãi áp dụng cho hóa đơn đặt cọc/tiền thuê/gia hạn.
- code (unique)
- name
- discount_type (Percent/Fixed)
- value
- apply_to (Deposit/Rental/Extension/All) — theo `Invoice.type`
- min_months (nullable) — số tháng thuê/gia hạn tối thiểu để áp dụng
- start_at
- end_at
- is_active

**NOTES:**
- Áp dụng tự động khi tạo `Invoice`: tìm `Discount` đang `is_active`, còn hiệu lực (`start_at <= now <= end_at`), khớp `apply_to`/`min_months` → ghi `Invoice.discount_amount`.
- MVP: mỗi hóa đơn chỉ áp dụng tối đa 1 `Discount`, không cộng dồn.

---

# RentalTerm
**Overview:** các phiên bản điều khoản hợp đồng, bao gồm cả chính sách hủy/trả/gia hạn/quá hạn dạng văn bản cho khách đọc. `RentalContract.terms_version` (Flow 2) snapshot tới version `Active` tại thời điểm ký.
- version (unique, VD: "v2.0")
- file_url — PDF điều khoản
- content (nullable) — text hiển thị inline
- status (Active/Inactive)
- created_by (N - 1: Account)
- created_at

**NOTES:**
- Tại một thời điểm chỉ có đúng 1 `RentalTerm` ở trạng thái `Active` — khi kích hoạt version mới, version đang `Active` tự chuyển `Inactive` trong cùng transaction.
- Hợp đồng đã ký giữ nguyên `terms_version` đã snapshot, không bị ảnh hưởng khi có version mới.