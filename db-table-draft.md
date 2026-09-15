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
# RentalContract
**Overview:** chứa thông tin hợp đồng điện tử được thiết lập khi `RentalOrder` chuyển sang trạng thái `Done`, là căn cứ để phát sinh hóa đơn định kỳ, gia hạn, tiền phạt, ...
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
  - Active: hợp đồng đang có hiệu lực (kể cả khi đã quá `end_date` nhưng chưa trả kho)
  - Completed: khách đã trả kho và FS xác nhận hoàn tất (Flow 2.5)
  - Terminated: chấm dứt trước hạn (khách yêu cầu, vi phạm, quá hạn thanh toán, ...)
  - Canceled: hợp đồng bị hủy trước khi có hiệu lực (trước `start_date`/bàn giao khoang)

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
  - Completed: thanh toán thành công, `RentalContract.end_date += extra_months`

**NOTES:**
- Chỉ được tạo khi `now <= RentalContract.end_date` (hợp đồng quá hạn không được gia hạn qua web)
- Mỗi `RentalContract` chỉ có tối đa 1 `ExtendRequest` ở trạng thái `PendingApproval` hoặc `ApprovedPendingPayment` tại một thời điểm (lock theo `contract_id`)
- Không cần `customer_id` vì đã xác định qua `RentalContract.customer_id`
# ReturnRequest
**Overview:** chứa các yêu cầu trả kho do khách gửi (Flow 3.4), FM phân công FS xử lý on-site (Flow 2.5). Tách bảng riêng thay vì dùng `RentalContract.status = PendingReturn` để không đè mất thông tin quá hạn của hợp đồng.
- contract_id (N - 1: RentalContract)
- customer_id (N - 1: Account)
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
- Các trạng thái on-site chi tiết (kiểm tra tình trạng, phát sinh phí hư hỏng, ...) sẽ bổ sung khi chốt Flow 2.5
# SupportRequest
**Overview:** chứa các yêu cầu hỗ trợ sự cố do khách gửi (Flow 3.5), FM phân công FS xử lý on-site (Flow 7). Không ảnh hưởng tới `RentalContract`.
- contract_id (N - 1: RentalContract)
- unit_id (N - 1: StorageUnit)
- customer_id (N - 1: Account)
- assigned_staff_id (N - 1: Account, null until FM assign)
- invoice_id (1 - 1: Invoice, null as default) - được gán nếu sự cố phát sinh phí (ví dụ làm lại chìa khóa), `Invoice.type = Service`
- issue_type (LostKey/AccessCode/UnitDamage/Other)
- description
- created_at
- resolved_at (null as default)
- status:
  - Open: khách vừa gửi, chờ FM phân công
  - Assigned: FM đã phân công FS
  - InProgress: FS đang xử lý
  - Resolved: FS xử lý xong
  - Closed: yêu cầu được đóng (khách xác nhận hoặc tự động đóng sau một khoảng thời gian)

**NOTES:**
- Một hợp đồng có thể có nhiều `SupportRequest` cùng lúc (khác với `ExtendRequest`/`ReturnRequest`)
- Chi tiết xử lý on-site và quy tắc đóng yêu cầu sẽ bổ sung khi chốt Flow 7
# Invoice
**Overview:** chứa thông tin thanh toán của khách hàng (hóa đơn)
- order_id (1 - 1: RentalOrder) -> null as default -> Được gán nếu hóa đơn phát sinh từ `RentalOrder` (Đặt cọc)
- contract_id (N - 1: RentalContract) -> null as default -> được gán nếu hóa đơn phát sinh từ `RentalContract` (tiền thuê hàng tháng, gia hạn, tiền phạt, ...)
- customer_id (N - 1: Account)
- type - loại hóa đơn, quyết định tiền tố trong `code`:
  - Deposit (DEP): đặt cọc, gắn với `order_id`
  - Rental (RNT): tiền thuê định kỳ, gắn với `contract_id`
  - Extension (EXT): gia hạn hợp đồng, gắn với `contract_id` (được tham chiếu bởi `ExtendRequest.invoice_id`)
  - Penalty (PEN): tiền phạt (quá hạn, hư hỏng, ...), gắn với `contract_id`
  - Service (SVC): dịch vụ hỗ trợ / sự cố (`SupportRequest`)
- code
  - Cấu trúc mã hóa đề xuất: Gợi nhớ & Dễ lọcĐể thuận tiện tuyệt đối khi kiểm tra, mã hóa đơn nên mang ý nghĩa phân loại theo công thức:
  - Mã hóa đơn = Tiền tố nghiệp vụ (theo `type`) + mã chi nhánh (Q7 - Quận 7, ...) + YYMMDD (ngày tạo hóa đơn) + random
  - Ví dụ:
    + INV-DEP-Q7-260911-A89F: Hóa đơn cọc (DEP), chi nhánh Quận 7, ngày 11/09/2026.
    + INV-RNT-TD-261001-K312: Hóa đơn tiền thuê định kỳ (RNT), chi nhánh Thủ Đức.
    + INV-EXT-Q7-261020-C45D: Hóa đơn gia hạn hợp đồng (EXT), chi nhánh Quận 7.
    + INV-SVC-Q7-260915-091B: Hóa đơn dịch vụ ngoài / sự cố khóa (SVC).
- title
- desc
- status (Unpaid/Paid)
- amount
- created_at
- due_date

**NOTES:**
- Nếu cả 2 fields order_id và contract_id đều null, tức là hóa đơn từ việc yêu cầu dịch vụ hỗ trợ (`SupportRequest`, `type = Service`) -> truy ngược qua `SupportRequest.invoice_id`
- Khi `PaymentTransaction` của hóa đơn `type = Extension` thành công -> cập nhật `RentalContract.end_date` và `ExtendRequest.status = Completed`
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
