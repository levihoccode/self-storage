# RentalRequest
**Overview:** chứa các thông tin được gửi từ form trên website.
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
- customer_id (1 - N: Account)
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
- customer_id (1 - N: Account)
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
