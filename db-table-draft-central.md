# Database Schema — Bản tổng hợp trung tâm (Central)

Tài liệu này gom các **table mà nhiều flow cùng cần** (owner tạo ra, các flow khác đọc/ghi chéo), đối chiếu giữa `db-table-draft.md`/`draft.md` ở **5 branch** hiện có (`specs/flow-1`, `specs/flow-2-2.5`, `specs/flow-3`, `specs/flow-4`, `specs/flow-5`) tại thời điểm viết tài liệu (chưa branch nào merge vào `main`).

**Cách đọc tài liệu:**
- Mỗi bảng ghi rõ **Owner flow** (nơi tạo & quản lý vòng đời) và **Dùng bởi** (flow nào đọc/ghi thêm).
- Khi các branch có field/enum khác nhau, mục **Field (bản reconciled)** chọn phiên bản **mới nhất/đầy đủ nhất** (thường là bản có ghi chú "Đã sửa/Đã thêm/Đã bỏ" — cho thấy đã được rà soát lại), phần **⚠️ Xung đột chưa chốt** liệt kê đúng chỗ các branch còn mâu thuẫn thật sự, cần team quyết định trước khi code — **không tự ý chọn hộ**.
- Bảng chỉ được dùng bởi **đúng 1 flow** (không có flow khác tham chiếu) được liệt kê riêng ở mục 7, không tính là "bảng trung tâm".

---

## 1. Ma trận Bảng × Flow sử dụng

| Bảng | Owner | Flow 1 | Flow 2/2.5 | Flow 3 | Flow 4 | Flow 5 | Flow 6/7 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| RentalRequest | 1 | ●(RW) | – | – | – | ●(R, duyệt) | – |
| RentalOrder | 1→2 | ●(RW) | ●(RW) | ●(R) | – | – | – |
| ProposalFeedback | 1 | ●(RW) | ●(R) | ●(R, note: nghi vấn — xem 2.3) | – | – | – |
| Appointment | 2 | ●(RW, tạo CHECKIN) | ●(RW) | ●(R, lịch sử) | – | ●(RW, phân công) | – |
| RentalAppointment | 2 | ●(RW) | ●(RW) | – | – | – | – |
| HandoverRecord | 2 | – | ●(RW) | ●(R, lịch sử) | – | – | – |
| RentalContract | 2 | – | ●(RW, tạo+ký) | ●(RW, chỉ update end_date) | ●(R, terms_version) | – | ●(RW, Terminated — đề xuất) |
| UnitAccessKey | 2 | – | ●(RW) | – | – | – | – |
| CheckoutRecord | 2.5 | – | ●(RW) | – | – | – | – |
| ExtendRequest | 3 | – | – | ●(RW) | ●(R, giá) | – | ●(RW, duyệt) |
| ReturnRequest | 3 | – | ●(R, consumer) | ●(RW) | – | ●(RW, phân công) | – |
| SupportRequest | 3 | – | – | ●(RW) | – | ●(RW, phân công) | ●(RW, Flow 7) |
| Invoice | 1 (khởi tạo đầu tiên) | ●(RW) | ●(RW) | ●(RW) | ●(R, phục vụ doanh thu) | – | ●(RW, Penalty) |
| PaymentTransaction | 1 | ●(RW) | ●(RW) | ●(R) | – | – | – |
| Facility | 5 | ●(R) | ●(R) | ●(R) | ●(R) | ●(RW) | – |
| StorageUnit | 5 | ●(RW status) | ●(RW status) | ●(R) | – | ●(RW) | – |
| UnitType | 4 | ●(R) | – | – | ●(RW) | ●(R) | – |
| Account | 5 (Admin) | ●(RW, đăng ký) | ●(R) | ●(R) | – | ●(RW) | – |
| Role / Permission / RolePermission | 5 (Admin) | – | – | – | – | ●(RW) | (infra, mọi flow phụ thuộc gián tiếp qua RBAC) |
| AccountFacilityAssignment | 5 (Admin) | – | ●(R, validate FS) | ●(R, validate FS) | – | ●(RW) | – |
| LoginHistory | 5 (Admin) | ●(W, qua auth) | – | – | – | ●(R) | – |
| AuditLog | 3 & 5 (dùng chung 1 cấu trúc) | – | – | ●(W) | – | ●(RW) | – |
| Policy | 3 (đề xuất) / 4 (rental-policies, khác cấu trúc — ⚠️) | ●(R, ngưỡng MVP hard-code, chưa đọc Policy) | ●(R, chưa đọc Policy) | ●(R) | ●(RW) | – | ●(R) |
| ExtraFee / Fee | 3 (ExtraFee) / 4 (Fee Management — ⚠️) | – | ●(R, phí trả kho) | ●(R, Service invoice) | ●(RW) | – | – |
| Discount | 4 (đề xuất, db-table-draft.md) | – | – | ●(R, not-MVP) | ●(RW, ngụ ý) | – | – |
| Notification | (dùng chung) | ●(W) | ●(W) | ●(W) | – | – | – |

*(● = có tham chiếu; R = đọc; W = ghi; RW = đọc+ghi)*

---

## 2. Nhóm A — Vòng đời thuê kho

### 2.1 RentalRequest
- **Owner:** Flow 1 · **Dùng bởi:** Flow 5 (FM duyệt)
- **Nguồn:** flow-1 (bản chi tiết nhất) · flow-3/main (bản rút gọn) · flow-2.5/4/5 (bản cũ, ít field hơn)

**Field (bản reconciled, ưu tiên flow-1 vì chi tiết nhất):**
- `facility_id` (N-1: Facility)
- `customer_name`, `normalized_customer_email` (trim + lowercase — khóa định danh nối với `Account`, không chỉ để gửi mail), `customer_phone`
- `unit_type`, `start_date`, `period`
- `unit_id` (N-1: StorageUnit, null cho tới khi FM duyệt — chỉ mang tính lịch sử sau đó)
- `processed_by` (N-1: Account), `processed_at`, `reject_reason`
- `created_at`, `responded_at`, `expires_at`
- `status`: `Pending → Approved → Converted` (đã liên kết `RentalOrder`) `| Rejected | Expired`

**⚠️ Xung đột chưa chốt:**
- flow-1 tách riêng status `Converted` khi request đã sinh `RentalOrder`; flow-3/main dùng `status=Approved AND customer_id IS NULL` làm cờ "đã duyệt nhưng chưa có tài khoản", **không có** status `Converted` riêng. Cần chọn 1 mô hình.
- flow-1 dùng `customer_name` + `normalized_customer_email`; flow-3/main chỉ có `customer_email` (không chuẩn hóa) — nên áp dụng chuẩn hóa email của flow-1 vì tránh trùng/miss account khi nối liệu.
- Wishlist (status `Wishlisted`) chỉ là đề xuất "chưa chốt" ở flow-3, không xuất hiện ở flow-1 — xem mục 7.

---

### 2.2 RentalOrder ⚠️ Xung đột lớn nhất hệ thống
- **Owner:** Flow 1 (tạo) → Flow 2 (vận hành tiếp, đóng bằng `Done`) · **Dùng bởi:** Flow 3 (chỉ đọc để lấy hóa đơn cọc)
- **Nguồn:** flow-1, flow-2-2.5, flow-3/main, flow-4/5 (bản cũ nhất)

**Field chung (mọi bản đều có):**
- `request_id` (1-1: RentalRequest), `customer_id` (N-1: Account), `unit_id`, `cancel_reason`, `created_at`

**⚠️ Xung đột — 3 bộ `status` khác nhau, CHƯA HỢP NHẤT (tự flow-2-2.5 cũng note việc này còn treo):**
1. **flow-1 (tuyến tính, chi tiết nhất):** `Pending → Deposited → Scheduled → InProgress → Done`, nhánh `Canceled`/`Expired` bất kỳ lúc nào. Đã **bỏ** `staff_id`/`appointment_date` khỏi bảng này — chuyển hẳn sang `Appointment.staff_id` (quyết định "A6").
2. **flow-3/main (base hiện dùng ở Flow 3):** `AwaitingDeposit → Pending → InProgress → Done`, nhánh `Canceled`/`Expired`.
3. **flow-2.5/4/5 (bản cũ, khả năng đã lỗi thời):** `Pending → InProgress → Done/Canceled`, **vẫn còn** field `staff_id`/`appointment_date` ngay trên `RentalOrder` — **mâu thuẫn trực tiếp** với quyết định "A6" đã chốt ở flow-1/flow-2.5 draft.md (chuyển sang `Appointment`). Đây là bản chưa được đồng bộ, nhiều khả năng cần loại bỏ field `staff_id`/`appointment_date` khỏi `RentalOrder` ở mọi nơi.

**Khuyến nghị:** dùng bộ status của **flow-1** (đầy đủ nhất, đã bỏ `staff_id` đúng theo quyết định A6) làm bản nền, đối chiếu lại với flow-3 xem `AwaitingDeposit` có tương đương `Pending` của flow-1 hay là 1 bước riêng cần giữ. **Đây là việc phải chốt trước khi code BE**, vì `RentalOrder.status` là field trung tâm nhiều flow cùng đọc.

---

### 2.3 ProposalFeedback
- **Owner:** Flow 1 · **Dùng bởi:** Flow 2 (đọc, phát sinh bản ghi mới khi khách từ chối tại chỗ)
- **Nguồn:** flow-1 (đầy đủ nhất) · flow-2-2.5, flow-4, flow-5 (bản rút gọn, giống hệt nhau) · flow-3/main (nghi ngờ bảng này "chưa dùng")

**Field (bản reconciled, ưu tiên flow-1):**
- `order_id` (**N-1**: RentalOrder — không phải 1-1, vì 1 order có thể có nhiều proposal theo thời gian)
- `customer_id` (N-1: Account), `unit_id` (N-1: StorageUnit)
- `status`: `Pending / Agreed / Rejected / Expired`
- `note`, `created_at`, `expires_at` (TTL của proposal)

**⚠️ Xung đột chưa chốt:**
- **flow-3/main tự ghi chú:** *"Chưa được sử dụng ở flow nào trong draft.md — cần xác định bước đề xuất khoang nằm trước hay sau khi đặt cọc, nếu không dùng thì bỏ bảng này"*. Ghi chú này **đã lỗi thời** — flow-1 (1.3) và flow-2-2.5 (2.2, nhánh khách từ chối khoang lúc check-in) đều dùng bảng này rất rõ ràng. **Kết luận: `ProposalFeedback` CẦN GIỮ**, không bỏ — flow-3/main cần cập nhật lại ghi chú này khi đồng bộ.
- flow-2.5/4/5 định nghĩa `order_id` là **1-1** (chỉ 1 feedback/order) — **mâu thuẫn** với việc flow-1/flow-2 mô tả rõ mỗi lần đề xuất lại tạo bản ghi mới (nhiều feedback cho 1 order theo thời gian). flow-1 đã tự sửa thành N-1, nên dùng bản này.

---

### 2.4 Appointment
- **Owner:** Flow 2 · **Dùng bởi:** Flow 1 (tạo lịch CHECKIN đầu tiên), Flow 3 (đọc lịch sử), Flow 5 (FM phân công `staff_id`)
- **Nguồn:** chỉ có ở flow-2-2.5 (không branch nào khác định nghĩa lại)

**Field:**
- `customer_id`, `staff_id` (N-1: Account, null cho tới khi FM phân công)
- `type` (CHECKIN/RETURN — bộ `type` **chưa chốt cuối cùng**, ghi chú "Levi sẽ chốt lại sau")
- `appointment_date`, `started_at`, `end_at`, `arrived_at`, `cancel_reason`
- `status`: `Pending / Done / Canceled`

**Ghi chú:** không có xung đột schema giữa các branch — chỉ có 1 nguồn định nghĩa. Điểm cần theo dõi: giá trị `type` có thể mở rộng thêm (vd `HANDOVER` tách khỏi `CHECKIN`?) khi các flow ổn định.

---

### 2.5 RentalAppointment
- **Owner:** Flow 2 · **Dùng bởi:** Flow 1 (tạo cùng lúc với Appointment CHECKIN)
- **Nguồn:** chỉ flow-2-2.5. Bảng nối đơn giản: `order_id` (N-1: RentalOrder), `appointment_id` (1-1: Appointment). Không xung đột.

---

### 2.6 HandoverRecord
- **Owner:** Flow 2 · **Dùng bởi:** Flow 3 (đọc lịch sử, đối chiếu hiện trạng lúc trả kho ở Flow 2.5)
- **Nguồn:** chỉ flow-2-2.5. Checklist: `is_identity_verified`, `is_unit_inspected` (+ `inspection_notes`/`inspection_photos`), `is_contract_signed`, `is_payment_settled` (mỗi cờ kèm timestamp) → `result` (IN_PROGRESS/COMPLETED/REJECTED). `order_id` là **N-1** (1 order có thể check-in nhiều lần nếu bị reject và cấp khoang mới), ràng buộc tối đa 1 bản ghi `IN_PROGRESS`/order. Không xung đột với branch khác.

---

### 2.7 RentalContract
- **Owner:** Flow 2 (tạo + quản lý vòng đời) · **Dùng bởi:** Flow 3 (chỉ đọc + update `end_date` khi gia hạn), Flow 4 (đọc `terms_version`), Flow 6 (đề xuất thêm `Terminated`)
- **Nguồn:** flow-2-2.5 và flow-3/main **khớp nhau tốt** — đây là ví dụ bảng đã reconcile thành công giữa 2 flow.

**Field (đồng thuận):**
- `order_id` (1-1: RentalOrder), `customer_id`, `unit_id`, `code`
- `terms_version`, `monthly_price` (giá chốt lúc ký, không đổi theo `UnitType` sau này), `deposit_amount`, `period`
- `start_date`, `end_date`, `signed_at`, `signature` (URL ảnh), `pdf_url` (bản scan, MVP không tự sinh PDF)
- `status`: `Draft → Signed → Active → Ended` | `Canceled` (huỷ trước bàn giao)

**Đề xuất bổ sung (chưa được Flow 2/4/6 xác nhận chính thức — do Flow 3 đề xuất):**
- `billing_day` (1-28) — ngày phát sinh hóa đơn tiền thuê định kỳ
- `terminated_at`, `termination_reason` + status `Terminated` — chấm dứt trước hạn do vi phạm/quá hạn (Flow 6)

---

### 2.8 UnitAccessKey
- **Owner:** Flow 2 · **Dùng bởi:** Flow 2.5 (thu hồi khi trả kho)
- **Nguồn:** chỉ flow-2-2.5. `unit_id`, `order_id`, `access_type` (PhysicalKey/AccessCode — MVP chỉ PhysicalKey), `quantity`, `code_hash`, `issued_at`/`revoked_at`, `status` (Active/Revoked/Lost). Không xung đột.

---

### 2.9 CheckoutRecord
- **Owner:** Flow 2.5 · **Dùng bởi:** không có flow khác đọc trực tiếp (Flow 3 chỉ đọc kết quả gián tiếp qua trạng thái `ReturnRequest`/`RentalContract`)
- **Nguồn:** chỉ flow-2-2.5. Checklist 5 cột mốc (`is_unit_emptied`, `is_inspected`, `is_access_revoked`, `is_fee_settled`, `is_deposit_settled`) + `result` (IN_PROGRESS/COMPLETED/PENDING_ITEMS). Không xung đột — tách riêng khỏi `HandoverRecord` có chủ đích, đã giải thích rõ lý do trong cả 2 flow (2.5 và 3).

---

### 2.10 ExtendRequest
- **Owner:** Flow 3 · **Dùng bởi:** Flow 4 (đọc giá để tính hóa đơn gia hạn), Flow 6 (duyệt/từ chối — chưa có tài liệu riêng)
- **Nguồn:** chỉ flow-3/main định nghĩa; không branch nào khác redefine. Field: `contract_id`, `extra_months`, `invoice_id`, `approved_by`, `reject_reason`, `cancel_reason`, `requested_at`, `processed_at`, `status` (PendingApproval/Canceled/Rejected/ApprovedPendingPayment/Expired/Completed). Không xung đột schema, nhưng **Flow 6 (nơi FM thực sự duyệt) chưa có tài liệu chi tiết riêng** — chỉ có cross-reference từ Flow 3.

---

### 2.11 ReturnRequest
- **Owner:** Flow 3 · **Dùng bởi:** Flow 2.5 (xử lý on-site sau khi được `Assigned`), Flow 5 (FM phân công FS)
- **Nguồn:** chỉ flow-3/main. Field: `contract_id`, `assigned_staff_id`, `preferred_date`, `reason`, `cancel_reason`, `created_at`, `completed_at`, `status` (Pending/Assigned/Canceled/Completed). Không xung đột.

---

### 2.12 SupportRequest
- **Owner:** Flow 3 (schema) / Flow 7 (xử lý on-site — chưa có tài liệu) · **Dùng bởi:** Flow 5 (FM phân công `assigned_staff_id`)
- **Nguồn:** chỉ flow-3/main định nghĩa. flow-5 **chủ động không redefine lại** bảng này, ghi rõ *"thuộc phạm vi Flow 3/7... Flow 5 chỉ đọc/ghi field `assigned_staff_id`, không tự định nghĩa lại cấu trúc bảng để tránh 2 shape khác nhau cho cùng 1 bảng"* — đây là ví dụ tốt về cách tránh xung đột schema giữa các flow. Field: `unit_id`, `contract_id` (nullable), `reporter_id`, `assigned_staff_id`, `invoice_id`, `issue_type`, `description`, `status` (Open/Assigned/InProgress/Resolved/Closed).

---

## 3. Nhóm B — Thanh toán & hóa đơn

### 3.1 Invoice ⚠️ Xung đột triết lý thiết kế
- **Owner:** phát sinh lần đầu ở Flow 1 (Deposit), tiếp tục ở Flow 2 (Rental), Flow 3 (Extension), Flow 6 (Penalty), Flow 7 (Service) · **Dùng bởi:** Flow 4 (theo dõi doanh thu)
- **Nguồn:** flow-1, flow-2-2.5/4/5 (giống nhau), flow-3/main (đầy đủ nhất)

**Field chung (mọi bản đều có):** `order_id` (N-1, null default), `contract_id` (N-1, null default), `customer_id`, `code`, `title`, `desc`, `amount`, `status`, `created_at`, `due_date`

**⚠️ Xung đột — cách xác định nguồn gốc hóa đơn (2 triết lý trái ngược nhau):**
1. **flow-3/main:** dùng field `type` (Deposit/Rental/Extension/Penalty/Service) làm nguồn xác định chính thức, ghi rõ *"không suy ra từ việc order_id/contract_id null"*. `code` = tiền tố theo `type` (DEP/RNT/EXT/PEN/SVC) + mã chi nhánh + ngày + random.
2. **flow-1:** ngược lại, xác định nguồn gốc **qua chính FK nào được set** (`order_id`/`contract_id`, có thể thêm `support_request_id`), dùng CHECK constraint *"đúng một FK được set"* — **không có field `type` riêng**, chỉ có bảng "Service Code" gợi ý (DEP/RNT/CLN/DMG/EXT) nhúng trong `code`.
3. **flow-2.5/4/5 (bản cũ):** giống flow-1 ở việc không có `type`, nhưng ghi chú *"Nếu cả 2 fields order_id và contract_id đều null → hóa đơn từ SupportRequest"* — suy luận từ NULL, **đối lập hoàn toàn** với nguyên tắc flow-3 vừa nêu.

**Khuyến nghị:** giữ field `type` tường minh như flow-3 (an toàn hơn, không phụ thuộc suy luận NULL dễ vỡ khi thêm nguồn hóa đơn mới) — nhưng đây vẫn là quyết định cần **BE lead xác nhận chính thức** vì ảnh hưởng toàn bộ query hóa đơn ở mọi flow.

**Xung đột phụ:**
- `status` enum: flow-3 có `Unpaid/Paid/Canceled`; flow-1 thêm `Expired`; flow-2.5/4/5 (bản cũ) chỉ có `Unpaid/Paid`. Nên dùng bản flow-1 (đủ nhất) nhưng xác nhận `Canceled` vs `Expired` có phải 2 trạng thái riêng biệt hay gộp chung.
- `discount_amount`: chỉ có trong bản flow-3/main, không thấy ở flow-1/2.5/4/5 — cần xác nhận có giữ lại không (tuỳ số phận `Discount`, xem 6.3).

---

### 3.2 PaymentTransaction
- **Owner:** Flow 1 · **Dùng bởi:** Flow 2 (thanh toán tháng đầu), Flow 3 (đọc lịch sử)
- **Nguồn:** flow-3/main (đầy đủ, đã sửa lỗi cũ) · flow-1 (thêm `direction`) · flow-2.5/4/5 (bản cũ, có ghi chú sai lệch)

**Field (bản reconciled):**
- `invoice_id` (N-1: Invoice)
- `method` (BankTransferQR/Card — theo flow-3) hoặc tách `vnp_txn_ref` riêng (theo flow-1, mã tham chiếu merchant gửi VNPay)
- `gateway_transaction_no` (**unique** — chặn xử lý trùng ở tầng DB)
- `transaction_content`, `response_payload` (JSON/TEXT, raw webhook)
- `amount`, `direction` (PAY/REFUND — đề xuất của flow-1, hữu ích khi làm refund sau MVP)
- `failure_reason`, `paid_at`, `created_at`
- `status`: `Pending / Failed / Success`

**⚠️ Xung đột đã tự giải quyết (không cần chốt lại):** bản flow-2.5/4/5 ghi chú *"visa card only"* — flow-3/main đã **chủ động ghi đè**: *"bỏ ràng buộc visa card only vì mâu thuẫn với Flow 1.3 (hỗ trợ QR chuyển khoản)"*. Dùng bản flow-3, bỏ hẳn ghi chú visa-only.

---

## 4. Nhóm C — Cơ sở vật chất

### 4.1 Facility
- **Owner:** Flow 5 (BOM tạo, gán FM) · **Dùng bởi:** Flow 1/2/3/4 (đọc để hiển thị tên chi nhánh, mã hoá đơn/hợp đồng)
- **Nguồn:** flow-5 (đầy đủ nhất, có ghi chú "đã thêm field `code`") và flow-3/main đã có sẵn `code` từ trước — **không xung đột**, chỉ là flow-5 tự cập nhật cho khớp.

**Field:** `code` (unique), `name`, `address`, `phone`, `operating_hours`, `fm_account_id` (1-1: Account, nguồn duy nhất lưu quan hệ FM–Facility), `status` (Active/Inactive, mặc định Inactive), `created_at`.

---

### 4.2 StorageUnit
- **Owner:** Flow 5 (owner của enum `status`) · **Dùng bởi:** Flow 1 (gán khi duyệt request), Flow 2 (đổi `Reserved→Rented`), Flow 2.5 (đổi `Rented→Maintenance→Available`), Flow 3 (đọc)
- **Nguồn:** flow-5 (bản mới nhất, đã chốt enum) · flow-3/main (chưa cập nhật `OnHold`)

**Field:** `facility_id`, `unit_type_id` (N-1: UnitType — **không** còn `rental_price`/`unit_type` dạng string, đã bỏ), `unit_code`/`code`, `location`, `created_at`, `updated_at`.

**⚠️ Điểm cần đồng bộ (không hẳn xung đột, chỉ là 1 bản chưa cập nhật):**
- **flow-5 (đã chốt — B1):** `status`: `Available | OnHold | Reserved | Rented | Maintenance` (5 giá trị).
- **flow-3/main:** vẫn chỉ có 4 giá trị `Available/Reserved/Rented/Maintenance`, ghi chú "nếu chốt `OnHold` thì bổ sung" — **flow-5 đã chốt rồi**, nên flow-3/main cần cập nhật lại theo bản 5 giá trị của flow-5.

---

### 4.3 UnitType
- **Owner:** Flow 4 (BOM quản lý giá) · **Dùng bởi:** Flow 1 (hiển thị giá), Flow 5 (FM chọn khi tạo StorageUnit, chỉ đọc `monthly_price`)
- **Nguồn:** flow-3/main và flow-5 khớp nhau hoàn toàn — không xung đột.

**Field:** `name` (Small/Medium/Large), `width`/`depth`/`height`/`area`, `description`, `monthly_price`, `updated_by`, `updated_at`. Ghi chú chung: nếu cần giá khác nhau theo chi nhánh, tách bảng riêng theo (`facility_id`, `unit_type_id`) — ngoài MVP.

---

## 5. Nhóm D — Tài khoản & phân quyền

### 5.1 Account
- **Owner:** Flow 5 (Admin thực thi kỹ thuật) · **Dùng bởi:** mọi flow (chủ thể của `customer_id`/`staff_id`/`approved_by`...)
- **Nguồn:** flow-3/main và flow-5 gần giống nhau, lệch nhẹ 2 field.

**Field (bản reconciled — hợp nhất cả 2 bản):**
- `role_id` (N-1: Role), `email` (unique), `phone`, `password_hash`, `full_name`
- `status`: `Active / Inactive / Locked` (flow-3 có `Locked`, flow-5 thiếu — nên giữ `Locked` vì Admin có action khoá tài khoản)
- `created_at`, `updated_at` (flow-3), `activated_at` (flow-5, mốc kích hoạt sau xác minh email) — **giữ cả 2** vì mục đích khác nhau, không trùng lặp thật sự.

**Ghi chú chung (đồng thuận, không xung đột):** không có field `facility_id` trực tiếp trên `Account` — quan hệ FM–Facility qua `Facility.fm_account_id` (1 chiều), FS–Facility qua `AccountFacilityAssignment`.

---

### 5.2 Role / Permission / RolePermission
- **Owner:** Flow 5 (Admin) · **Dùng bởi:** hạ tầng RBAC cho mọi flow (gián tiếp qua middleware phân quyền)
- **Nguồn:** chỉ flow-5 định nghĩa chi tiết (flow-3/main có nhắc tên bảng nhưng không có field chi tiết bằng). Không xung đột — dùng bản flow-5.

**Field:** `Role.name` (Customer/FacilityStaff/FacilityManager/BusinessOperationManager/SystemAdministrator), `Permission.code` (vd `rental_request.approve`), `RolePermission` (role_id N-1, permission_id N-1). Seed mặc định theo bảng RBAC tổng quát ở draft.md Flow 5 mục 5.0.

---

### 5.3 AccountFacilityAssignment
- **Owner:** Flow 5 (Admin gán) · **Dùng bởi:** Flow 2 (validate FS xử lý đúng facility), Flow 3 (validate `assigned_staff_id`)
- **Nguồn:** flow-3/main và flow-5 khớp nhau. Field: `account_id` (N-1: Account, role FS), `facility_id`, `assigned_at`. Chỉ dùng cho FS, không dùng cho FM. Không xung đột.

---

### 5.4 LoginHistory
- **Owner:** Flow 5 (Admin theo dõi) · **Dùng bởi:** không flow nghiệp vụ nào khác đọc trực tiếp
- **Nguồn:** flow-3/main và flow-5 khớp nhau hoàn toàn. Field: `account_id` (nullable), `email`, `ip_address`, `user_agent`, `status` (Success/Failed), `failure_reason`, `created_at`. Không xung đột.

---

### 5.5 AuditLog
- **Owner:** dùng chung cấu trúc giữa Flow 3 và Flow 5 (không flow nào tự nhận là "chủ" duy nhất — đây là ví dụ mẫu mực về đồng bộ schema giữa 2 flow)
- **Nguồn:** flow-3/main và flow-5 khớp nhau 100%, flow-5 ghi rõ *"đã thay thế bản tối giản cũ bằng cấu trúc đầy đủ này để khớp với Flow 3"*. Field: `account_id`, `action`, `entity_type`, `entity_id`, `old_value`/`new_value` (JSON), `created_at`. Không xung đột — **mẫu tham khảo tốt cho cách 2 flow nên đồng bộ 1 bảng dùng chung.**

---

## 6. Nhóm E — Chính sách & phí (BOM, dùng chung nhiều flow)

### 6.1 Policy (flow-3) vs "Business Rules / rental-policies" (flow-4) ⚠️ Xung đột kiến trúc — P0 theo issue #13
- **Dùng bởi:** Flow 3 (đọc `contract.expiring_soon_days`, `overdue.fee_per_day`), Flow 1/2 (ngưỡng MVP hiện đang **hard-code** trong draft.md, chưa thật sự đọc từ bảng Policy nào), Flow 6 (chính sách quá hạn/gia hạn)

**Phương án A — `Policy` (flow-3/main, db-table-draft.md):** bảng **key-value tổng quát**, không hard-code từng chính sách:
- `key` (unique, vd `deposit.amount_months`, `contract.expiring_soon_days`, `overdue.fee_per_day`, `unit.maintenance_days`), `value`, `value_type` (Number/Percent/Text/Boolean), `description`, `updated_by`, `updated_at`.

**Phương án B — "Business Rules" (flow-4, draft.md mục 4.1):** bảng **cột cố định**, gộp nhiều chính sách vào 1 record theo version:
- `id`, `name`, `deposit_type` ($/%), `deposit_value`, `cancel_policy`/`return_policy`/`renewal_policy`/`overdue_policy` (dạng text mô tả), `effective_from`/`effective_to`, `status`, `created_by`, `created_at`. Chỉ 1 bản ghi active tại 1 thời điểm.

**Đây là xung đột kiến trúc thật sự, không phải chênh lệch nhỏ** — 2 mô hình dữ liệu hoàn toàn khác nhau cho cùng 1 mục đích (Policy = linh hoạt, mở rộng dễ, nhưng validate lỏng lẻo hơn; Business Rules = có version theo thời gian rõ ràng, nhưng cứng nhắc khi thêm chính sách mới phải sửa schema). Issue #13 (tracker) đã liệt kê đây là điểm cần chốt trước merge. **Khuyến nghị tạm thời:** Flow 3 hiện đang code theo model Policy (key-value) nên có thể ưu tiên phương án A cho MVP, đồng thời giữ lại ý tưởng "chỉ 1 version active tại 1 thời điểm" của phương án B nếu cần lịch sử thay đổi chính sách.

---

### 6.2 ExtraFee (flow-3) vs "Fee Management" (flow-4) ⚠️ Xung đột — cần hợp nhất
- **Dùng bởi:** Flow 3 (tạo `Invoice type=Service` dựa trên `ExtraFee`), Flow 2.5 (mức phí hư hỏng/vệ sinh/trả trễ đọc từ đây)

**Phương án A — `ExtraFee` (flow-3):** đơn giản — `name`, `amount` (số tiền cố định), `description`, `is_active`, `updated_by`, `updated_at`. **Không hỗ trợ tính phí theo ngày/tháng/%** — chỉ 1 mức cố định.

**Phương án B — `Fee` (flow-4, mục 4.2):** đầy đủ hơn — `id`, `name`, `category`, `amount`, **`calculation`** (fixed/daily/monthly/%), `description`, `status`, `created_by`, `created_at`. Hỗ trợ cách tính linh hoạt, kể cả case đặc biệt `calculation=LOCK` để khoá tài khoản.

**Khuyến nghị:** dùng phương án B (`Fee` của flow-4) làm bản chính vì **Flow 2.5 đã ghi rõ cần công thức "amount × số ngày phát sinh"** cho phí trả trễ — điều này chỉ khả thi với field `calculation` của flow-4, không khả thi với `ExtraFee` đơn giản của flow-3 (chỉ có `amount` cố định). Cần đổi tên/đồng bộ lại ở Flow 3 để dùng chung 1 bảng `Fee` duy nhất, tránh 2 bảng phí song song.

---

### 6.3 Discount
- **Owner:** đề xuất ở flow-3 (db-table-draft.md), flow-4 (draft.md) có nhắc ý tưởng tương tự nhưng chưa định nghĩa field cụ thể
- **Nguồn:** chỉ flow-3/main có schema chi tiết: `code` (unique), `name`, `discount_type` (Percent/Fixed), `value`, `apply_to` (Deposit/Rental/Extension/All), `min_months` (nullable), `start_at`/`end_at`, `is_active`, `created_by`, `created_at`.
- **Lưu ý MVP:** issue pre-merge report Flow 3 (#9) đề xuất **có thể bỏ hoàn toàn khỏi MVP** ("cost addition nhưng có thể omit"). Không có xung đột schema (vì chỉ 1 bản định nghĩa), nhưng **chưa chốt có nằm trong scope MVP hay không**.

---

### 6.4 Notification
- **Owner:** dùng chung, không flow nào là "chủ" duy nhất — mọi flow đều ghi vào bảng này khi cần gửi thông báo
- **Nguồn:** chỉ flow-3/main có schema. Field: `account_id` (nullable — null khi gửi cho người chưa có tài khoản), `recipient`, `channel` (Web/Email), `type`, `title`, `content`, `entity_type`/`entity_id` (điều hướng khi bấm vào), `is_read`, `sent_at`, `read_at`, `created_at`. Không xung đột — các flow khác (1, 2, 2.5) mô tả hành vi gửi email/thông báo bằng lời văn, không tự định nghĩa lại bảng.

---

## 7. Bảng KHÔNG đưa vào bản trung tâm (chỉ 1 flow dùng riêng, không flow khác tham chiếu)

| Bảng | Flow sở hữu | Lý do không tính là "bảng trung tâm" |
|---|---|---|
| `Wishlist` | Flow 1 | Chỉ tồn tại ở flow-3/main dưới dạng "Advanced Feature (not MVP)", phụ thuộc Phương án 2 (chưa chốt) ở Flow 1.1. flow-1 (bản mới nhất) không hề nhắc tới bảng này |
| `AccountRoleRequest` | Flow 5 | Luồng nội bộ BOM → Admin, không flow nghiệp vụ nào khác đọc/ghi |
| "Business Rules"/"Fee Management" (nếu không được chọn làm bản chính ở 6.1/6.2) | Flow 4 | Sau khi team chốt phương án ở 6.1/6.2, bản không được chọn nên loại bỏ khỏi schema thật để tránh trùng khái niệm |
| `FacilityTask` | Flow 1 | Chỉ là `# TODO: FacilityTask` — chưa có nội dung, chưa rõ mục đích |

---

## 8. Tổng hợp xung đột cần chốt trước khi code BE (ưu tiên P0)

1. **`RentalOrder.status`** (mục 2.2) — 3 bộ enum khác nhau giữa flow-1/flow-3/flow-4-5, ảnh hưởng trực tiếp luồng Flow 1→2→3. **Nghiêm trọng nhất.**
2. **`Invoice` — cách xác định nguồn gốc hóa đơn** (mục 3.1) — field `type` tường minh (flow-3) vs suy luận qua FK null (flow-1/4/5). Ảnh hưởng mọi query hóa đơn.
3. **`Policy` vs `Business Rules`** (mục 6.1) — 2 mô hình dữ liệu khác hẳn nhau cho chính sách vận hành, đã được issue #13 (tracker) ghi nhận là điểm chờ chốt.
4. **`ExtraFee` vs `Fee`** (mục 6.2) — cần hợp nhất thành 1 bảng phí duy nhất có hỗ trợ `calculation` (fixed/daily/monthly/%).
5. **`RentalRequest.status`** (mục 2.1) — có/không status `Converted` riêng.
6. **`StorageUnit.status`** (mục 4.2) — flow-3/main cần cập nhật theo bản 5-giá-trị (`+OnHold`) đã chốt ở flow-5.
7. **`ProposalFeedback`** (mục 2.3) — flow-3/main cần gỡ ghi chú "chưa dùng, có thể bỏ" vì flow-1/2.5 đã chứng minh bảng này cần thiết.

*(Mục 1–4 nên ưu tiên xử lý trước vì ảnh hưởng nhiều flow cùng lúc; mục 5–7 là các điểm nhỏ hơn, dễ đồng bộ.)*
