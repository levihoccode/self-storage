# Bản nháp phân tích
## Techstack (TODO)
- **Platform:** Web Only
- **Backend:** Java, Springboot
- **Frontend:** React, Typescript, TailwindCSS, ShadCN
- **Database & Cache:** Postgres, Redis
- **DevOps & Infra:** Docker, Docker Compose, Github Action
- **File & Media Storage:** Cloudinary (hoặc AWS S3 / MinIO) - Lưu ảnh nghiệm thu khoang, PDF hợp đồng
- **Third-party Services:**
  - Payment: VNPay Gateway (Sandbox)
  - Notification: Spring Mail (Gmail SMTP / Resend)
- **Payment Gateway:** VNPay

## Overview
- Một ứng dụng quản lý việc cho thuê các chuỗi kho tự chứa (thuê xong muốn chứa gì chứa)
## Terms:
**FM:** Facility Manager - Quản lý cơ sở \
**FS:** Facility Staff - Nhân viên vận hành kho \
**BOM:** Business Operation Manager - Quản lý tổng các chuỗi
## Actors:
**Business Operation Manager - Quản lý tổng các chuỗi**:  Người quản lý tất cả các chuỗi có trong hệ thống
- Theo dõi báo cáo của tất cả các cơ sở kho
- Đề ra các chính sách chung cho thuê, đặt cọc, gia hạn, hủy, trả khoang chứa, xử lý quá hạn
- Quản lý giá thuê, phí quá hạn, discount, phí extra

**Facility Manger - Quản lý cơ sở**: Trưởng kho của từng chi nhánh, chỉ phụ trách các thông tin liên quan đến cơ sở được giao
- Quản lý từng khoang chứa
- Phân bổ khoang chứa cho khách dựa trên yêu cầu của khách
- Quản lý quá trình duyệt hợp đồng, trả, gia hạn
- Theo dõi các thông tin của khách hàng: hợp đồng cho thuê, thời gian thuê, payment status
- Điều phối nhân viên ở kho để nhận trả/bàn giao, kiểm tra khoang chứa, xử lý sự cố
- Quản lý hóa đơn

**Facility Staff - Nhân viên vận hành kho**: Người làm việc trực tiếp tại chỗ (on-site)
- Ghi nhận khách đến (check-in record) -> Có thể dùng để quan sát lịch trình của 1 ngày, nếu có các khách đến xem/nhận khoang chứa hoặc cần hỗ trợ các sự cố on-site
- Giao/nhận chìa khóa/mã cửa
- Kiểm tra và xác nhận tình trạng khi khách bàn giao lại khoang chứa
- Cập nhật trạng thái sau khi khách nhận khoang chứa
- Ghi nhận và xử lý các sự cố tại kho (on-site) ví dụ như khách mất chìa khóa, vấn đề của mã truy cập cửa, khoang chứa gặp sự cố, yêu cầu hỗ trợ từ khách hàng...

**Customer - Khách thuê kho**: người đến thuê kho và sử dụng dịch vụ
- Xem thông tin của các kho có sẵn (type, size, available unit, rental price)
- Thuê nhiều khoang chứa bằng nhiều requests
- Đặt khoang chứa bằng cách chọn facility (chi nhánh), type, thời gian bắt đầu thuê và thời gian thuê
- Đặt cọc để giữ kho sau khi đã nhận được thư phản hồi thành công
- Nhận lịch bàn giao (check-in) khoang chứa (có thể tạo tài khoản từ lúc hợp đồng được duyệt để bắt đầu theo dõi)
- Quản lý các khoang chứa đã thuê
- Gửi yêu cầu hỗ trợ các sự cố liên quan đến khóa truy cập, khoang chứa, ...

**System Administrator - Quản trị viên hệ thống**: người vận hành kỹ thuật có cấp quyền cao nhất
- Quản lý tài khoản (update role)
- Thiết lập quyền truy cập dữ liệu cho các role dựa trên model RBAC
- Theo dõi hoạt động và lịch sử đăng nhập của users

**Storage unit - Khoang chứa**
- First-paid first-serve
- Trạng thái được chuyển sang "Đã được đặt cọc" ngay khi khách chuyển tiền đặt cọc thành công
- Khách không được yêu cầu đặt kho đã được đặt cọc




## Business workflow
### 1. Đặt kho
**FLOW:**
```
[1.1 Yêu cầu đặt kho]
          │
          ▼
[1.2 Khách tạo tài khoản]
          │
          ▼
[1.3 Kiểm tra Kho của tôi (Xác nhận/Từ chối khoang)]
          │
     ┌────┴────────────────────────┐
     │ (Từ chối)                   │ (Đồng ý)
     ▼                             ▼
[Yêu cầu chọn lại khoang]     [1.4 Đặt cọc (Deposit)]
                                   │
                                   ▼
                              [1.5 Chọn lịch hẹn check-in sau khi đặt cọc]

```
#### Các tham số sử dụng trong Flow 1

| Tên | Giá trị |
|---|---:|
| `account.claim_ttl_days` | 7 ngày |
| `proposal.response_ttl_days` | 3 ngày |
| `invoice.deposit_due_days` | 3 ngày |
| `appointment.booking_window_days` | 7 ngày |
| `appointment.max_days_after_deposit` | 14 ngày |
| `proposal.max_rejection_count` | 3 lần |
| `appointment.checkin_reschedule_enabled` | false (MVP) |
| `appointment.daily_slot_count` | 3 khung/ngày |
| `appointment.capacity_mode` | fixed_windows |
| `request.pending_expiry_days` | 7 ngày |
| `order.deposit_expiry_days` | 30 ngày |

#### 1.1 Yêu cầu đặt kho
**Context:** Khách mới, chưa từng sử dụng dịch vụ, muốn tìm cho mình một khoang chứa phù hợp với nhu cầu.

**Flow tổng quát:** Khách lựa chọn khoang chứa dựa trên nhu cầu và điền các thông tin cần thiết (Không được chỉ định khoang chứa cụ thể). Sau đó, FM kiểm tra những khoang chứa còn trống và sẵn sàng cho thuê để chỉ định cho người thuê.

**Details:**
- **Customer:**
  - Khách hàng điền nhu cầu thuê kho qua form (không cần đăng nhập), bao gồm các thông tin được hiển thị trên form:
    + customer_name
    + normalized_customer_email
    + customer_phone
    + unit_type
    + facility
    + start_date (MM/DD/YYYY)
    + period - số tháng thuê
  - Server-side constraints:
    + `start_date` không được ở trước ngày hiện tại theo timezone của `Facility`.
    + `period` là số nguyên dương.
    + `normalized_customer_email` phải là email hợp lệ; `customer_phone` phải đúng format số điện thoại được hỗ trợ.
    + `unit_type` phải tồn tại và được cung cấp tại `facility`.
   - Sau khi submit, hệ thống tạo bản ghi `RentalRequest` với `status = Pending` (mặc định), `created_at` = thời điểm submit và `expires_at = created_at + request.pending_expiry_days`.
  - Nhận phản hồi thông qua email và số điện thoại (telesale sẽ gọi để xác nhận)
  - Sau khi FM duyệt request, notification `RENTAL_REQUEST_APPROVED` được ghi vào tài khoản khách tại thời điểm `RentalOrder` được tạo (lúc này tài khoản chắc chắn đã tồn tại):
    - **Khách đã có tài khoản**: `RentalOrder` được tạo ngay trong transaction duyệt (xem mục FM bên dưới).
    - **Khách chưa có account**: chỉ nhận email. `RentalOrder` được tạo ở [mục 1.2](#12-khách-tạo-tài-khoản) sau khi khách xác minh email.
    - Tại thời điểm `RentalOrder` được tạo, hệ thống ghi notification `RENTAL_REQUEST_APPROVED` cho khách, title “Yêu cầu thuê kho đã được duyệt” và body có link trỏ tới trang duyệt proposal.
    - Khách thao tác tiếp ở [`Kho của tôi`](#13-kiểm-tra-kho-của-tôi) trước khi sang bước đặt cọc.

- **FM:**
  - Các yêu cầu đặt khoang chứa sẽ được liệt kê ở một trang và có các nút (button) để thao tác (details, response, update status, ...), mỗi entry là một `RentalRequest`.
  - Sau khi xác định được 1 yêu cầu đặt kho cần giải quyết, FM sẽ kiểm tra các kho còn sẵn tại chi nhánh và trong trường hợp:
    - **Tìm thấy khoang chứa thích hợp**:
      - FM nhập unit id phù hợp vào field `unit_id` và bấm `Approved`.
      - Thao tác duyệt chạy trong 1 transaction: conditional update `WHERE status = 'Pending'` + re-check khoang `Available`.
        - **Khoang không còn `Available`** (đã `Reserved`/`Maintenance`): chặn duyệt, hiển thị lỗi "Khoang đã không còn khả dụng, vui lòng chọn khoang khác"; request giữ `status = Pending` để FM gán lại.
        - **Request không còn `Pending`** (2 FM duyệt cùng lúc / double-click): chặn thao tác, hiển thị "Yêu cầu đã được xử lý bởi FM khác"; refresh danh sách.
      - Hệ thống cập nhật `RentalRequest.status` sang `Approved`.
      - Hệ thống ghi `RentalRequest.responded_at` = thời điểm duyệt và đặt `expires_at = responded_at + account.claim_ttl_days` (7 ngày).
      - **Trong trường hợp email chưa có tài khoản:**
        - Hệ thống **không tạo `RentalOrder`** — `RentalRequest` giữ `status = Approved` + `unit_id` đã chỉ định + `expires_at`. Đơn được tạo khi khách đăng ký và xác minh email (mục 1.2).
      - **Trong trường hợp email đã có tài khoản:**
        - Hệ thống tạo một bản ghi `ProposalFeedback` (status = `Pending`) cho khách hàng.
        - Hệ thống tạo một bản ghi `RentalOrder` (status = `Pending`, `unit_id` chưa được gán cho đến khi `ProposalFeedback.status = Agreed`).
        - Hệ thống chuyển `RentalRequest.status` sang `Converted`.
    - **Không tìm thấy khoang chứa thích hợp**:
      - Chuyển status sang `Rejected` và nhập lý do: "Hết khoang chứa phù hợp tại chi nhánh".
      - Hệ thống ghi `RentalRequest.responded_at` = thời điểm từ chối.
      - Hệ thống gửi một thông báo/email không thành công đến khách hàng kèm theo lý do.
      - Nếu khách đã có tài khoản, hệ thống tạo notification `RENTAL_REQUEST_REJECTED` cho khách, title “Yêu cầu thuê kho bị từ chối” và body nêu lý do từ chối.

- **Hệ thống gửi email:**
  - **Nội dung email nếu khách nhận được phản hồi thành công** và trong trường hợp:
    - *Chưa có tài khoản:*
      ```
      Yêu cầu đặt khoang của bạn đã được duyệt, nhưng hệ thống nhận thấy email này chưa có tài khoản trên website, vui lòng đăng ký tại [link] trước [expires_at] và đăng nhập để xác nhận và đặt cọc.

      Lưu ý: khoang chứa không được giữ trong lúc chờ. Khoang được xác nhận chính thức cho khách hàng hoàn tất thanh toán cọc đầu tiên (cọc trước giữ trước).
      ```
    - *Đã có tài khoản:*
      ```
      Có một khoang chứa phù hợp với yêu cầu của bạn:
          Mã (code):
          Loại (type):
          Kích thước (size):
          ...
      Vui lòng kiểm tra [link] để xác nhận.

      Lưu ý: khoang chứa không được giữ trong lúc chờ. Khoang được xác nhận chính thức cho khách hàng hoàn tất thanh toán cọc đầu tiên (cọc trước giữ trước).
      ```
  - **Trong trường hợp yêu cầu được Approve nhưng chưa đặt cọc, và đã có người khác đặt cọc:**
    - Không hủy đơn — hệ thống tạo notification `PROPOSAL_REPROPOSAL_REQUIRED` cho FM, title “Cần đề xuất khoang khác” và body có mã đơn cùng mã khoang cũ (re-propose theo luồng 1.3).
    - Khách nhận email:
      ```
      Khoang [mã] đã có người đặt cọc trước. Cơ sở đang tìm khoang khác phù hợp cho bạn.
      ```
**Schema có trong phần này:**
- [**RentalRequest**](./db-table-draft.md#rentalrequest)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Invoice**](./db-table-draft.md#invoice)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback)

**NOTES**
- Entry trong list yêu cầu đặt khoang chứa của FM không có facility vì khi đặt, khách chỉ định một chi nhánh cụ thể và người quản lý tại chi nhánh đó sẽ nhận được yêu cầu => không cần liệt kê facility field.
- Có thể phát triển thêm phần wishlist giành cho các khoang chứa đều không available, nhưng tự động gửi thông báo và đăng ký ngay khi có bất kỳ khoang chứa nào trống (có thể dùng filter).
- Nhiều khách có thể cùng nhận đề xuất cho một khoang; khoang thuộc về người thanh toán trước. Email đã ghi rõ điều này.
- RBAC: mọi API của FM chỉ thao tác trên request/đơn thuộc facility mình phụ trách; truy cập chéo cơ sở trả 403 (validate ở BE).

**Advanced Features (not MVP)**
- Gợi ý khoang tương đương tự động + wishlist khi `RentalRequest` đã được Approve nhưng bị người khác đặt cọc
- Chống lạm dụng gửi form: rate limit + giới hạn số request mở trên mỗi phone/email.
- Tự động quá trình duyệt.
- Cho khách chỉ định cụ thể khoang chứa để thuê. -> không tối ưu layout khi để khách tự chọn, cần tìm cách hoặc kệ nó luôn đi :))
- Cho khách đặt nhiều khoang chứa trong 1 request. -> cần lưu ý về việc các khoang chứa có cần liên tục nhau hay không, tính toán ra sao nếu không đủ, ...
#### 1.2 Khách tạo tài khoản
**Case A: Sau khi có một yêu cầu được duyệt**

**Context:** Yêu cầu đặt khoang chứa của khách đã được duyệt và cần sang các bước tiếp theo để đặt cọc nhưng chưa có tài khoản.

**Flow tổng quát:** Yêu cầu đã được duyệt và ghi nhận trên hệ thống, khách hàng đăng ký trong thời gian quy định (`responded_at` -> `expires_at`) và một yêu cầu xác nhận khoang được chỉ định được thêm vào tài khoản.

**Details:**
- Tạo một bản ghi `Account` với role là `Customer` (chưa verify).
- Hệ thống gửi email xác minh; **đơn chỉ được liên kết sau khi khách click xác minh** (chống chiếm đơn).
- Sau khi verify, hệ thống tìm các `RentalRequest` có `status = Approved`, chưa quá `expires_at`, khớp `normalized_customer_email` với account.
- Chỉ hiển thị và xử lý các request còn hạn. Request đã quá `expires_at` không được claim.
- Khi convert, hệ thống dùng transaction và không tạo hai `RentalOrder`/proposal đang hoạt động cho cùng một account và cùng một `unit_id`. Nếu nhiều request hợp lệ trỏ đến cùng khoang, hệ thống chỉ tạo một conversion; request trùng được giữ lại để FM xử lý re-propose, không tạo đơn trùng khoang.
- Với mỗi request hợp lệ không bị trùng khoang:
  - Hệ thống tạo `ProposalFeedback` với `unit_id` FM đã chỉ định và `status = Pending`;
  - Hệ thống tạo `RentalOrder` với `status = Pending`, chưa gán `unit_id`
  - Hệ thống chuyển `RentalRequest.status` sang `Converted`.
  - Hệ thống tạo notification `RENTAL_REQUEST_APPROVED` cho khách, title “Yêu cầu thuê kho đã được duyệt” và body có link tới proposal cần duyệt.
- Không tìm thấy request nào → giữ nguyên (xem Case B).

**Case B: Không có yêu cầu nào được duyệt**

**Context:** Khách tạo tài khoản nhưng không có yêu cầu `Approved` nào khớp email (chưa hết hạn).

**Flow tổng quát:** Tạo tài khoản `Customer` trong hệ thống.

**Details:**
- Tạo một bản ghi `Account` với role là `Customer`.

**NOTES:**
- `Account` thuộc auth module (đăng ký / verify email / hash credentials); Flow 1 chỉ tạo account qua API auth và đọc `customer_id`.

**Schema có trong phần này:**
- [**Account**](./db-table-draft.md#account)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback)


#### 1.3 Kiểm tra kho của tôi
**Context:** Đơn đặt khoang chứa của một khách hàng đã được duyệt và chỉ định bởi FM (bản ghi `ProposalFeedback` của đơn hàng đã được tạo), hệ thống cần xác nhận từ khách hàng.
**Flow tổng quát:**
- Khách hàng vào trang xác nhận -> chọn đồng ý hoặc từ chối -> luồng xử lý dựa vào đồng ý hay từ chối
**Details:**
- Khách hàng vào trang xác nhận, trang đó hiển thị các thông tin của khoang (thông tin hiển thị lấy từ `ProposalFeedback`)
- Khách hàng chọn đồng ý hoặc từ chối:
  - *Đồng ý*:
    - Hệ thống kiểm tra khoang vẫn `Available`; nếu không (đã `Reserved`/`Maintenance`):
      - Không cho đồng ý, hiển thị thông báo "Khoang đã có người đặt cọc / không khả dụng".
      - Hệ thống tạo notification `PROPOSAL_REPROPOSAL_REQUIRED` cho FM, title “Cần đề xuất khoang khác” và body nêu mã đơn cùng khoang không còn khả dụng.
      - Proposal hiện tại chuyển `Expired` (khoang không còn khả dụng).
      - Khách hàng duyệt proposal mới sau khi FM đề xuất.
    - Hệ thống cập nhật bản ghi của `ProposalFeedback` sang `Agreed`
    - Hệ thống gán field `unit_id` trong `RentalOrder`: `RentalOrder.unit_id` = `ProposalFeedback.unit_id` mới nhất
    - Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
      - code: INV-DEP-{facility_code}-{YYMMDD}-{rand}
      - title: "Đặt cọc khoang chứa A"
      - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
      - order_id: `RentalOrder.id`
      - customer_id: `Account.id`
      - type: `Deposit`
      - status: `Unpaid`
      - amount: số tiền cọc theo chính sách
      - due_date: thời hạn thanh toán cọc theo `invoice.deposit_due_days` (3 ngày)
  - *Từ chối < `proposal.max_rejection_count` lần*:
    - Hệ thống cập nhật bản ghi của `ProposalFeedback` sang `Rejected` (kèm note)
    - Hệ thống tạo notification `PROPOSAL_REJECTED` cho FM, title “Khách hàng đã từ chối đề xuất” và body:
      ```
      Khách đã từ chối khoang [Mã khoang cũ], lý do: [note]
      ```
    - FM vào xem khoang trống khác, chọn `unit_id` mới và bấm "Đề xuất lại". Hệ thống loại các khoang mà khách đã từ chối trong cùng `RentalOrder`.
    - Nếu FM cần đề xuất lại một khoang đã bị khách từ chối, phải dùng quyền override và nhập lý do; thao tác này được ghi vào `AuditLog`.
    - Hệ thống tạo một bản ghi `ProposalFeedback` (status = `Pending`), gắn unit_id mới vừa chọn
    - Hệ thống tạo notification `PROPOSAL_REPROPOSED` cho khách, title “Có đề xuất kho mới” và body có link xác nhận.
  - Quá `proposal.max_rejection_count` lần từ chối:
    - Hệ thống dừng đề xuất, `RentalOrder` → `Canceled` (khách không chọn được khoang)
    - Hệ thống tạo notification `RENTAL_ORDER_CANCELED` cho FM và khách đã có account với title “Đơn thuê kho đã bị hủy” và body nêu mã đơn cùng lý do hủy.

**NOTES:**
- Khi làm trang này, có thể chia thành 2 tabs:
  - Đang sử dụng: Đã ký hợp đồng
  - Chờ được duyệt: các khoang yêu cầu được duyệt bởi FM vẫn cần khách hàng xác nhận
- Đổi khoang sau khi đã cọc (khách từ chối ở check-in — Flow 2): so sánh mức cọc khoang cũ vs khoang mới + phí đổi khoang theo policy (Flow 4, key `fee.unit_change`). Dư → hoàn thủ công; thiếu → phát sinh hóa đơn bổ sung.
- Đề xuất lại khi khách từ chối khoang ở check-in: FS ghi nhận `HandoverRecord` là `Rejected` và nhập lý do. Hệ thống thông báo cho FM, kèm nút **"Đề xuất khoang khác"**. FM chọn khoang mới; hệ thống tạo `ProposalFeedback` mới để khách duyệt online. `RentalOrder` không bị hủy.

**Schema có trong phần này:**
- [**Invoice**](./db-table-draft.md#invoice)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback)


#### 1.4 Đặt cọc
**Context:** Sau khi khách đã điền form và được approve, đã nhận email phản hồi duyệt thành công và đã đăng ký tài khoản thành công.

**Flow tổng quát:**
Khách đăng nhập vào ứng dụng thành công -> vào mục "Hóa đơn" -> hiển thị một hóa đơn "Đặt cọc" cho khoang yêu cầu -> thanh toán thành công -> trạng thái kho chuyển sang `Reserved`.

**Details:**
- Khách đăng nhập vào ứng dụng
- Ấn vào mục "Hóa đơn" kiểm tra các hóa đơn cần thanh toán
- Chọn hóa đơn đặt cọc (code=INV-DEP-...) cần thanh toán và bấm vào "Tiến hành thanh toán"
- Ở đây hệ thống sẽ kiểm tra 4 trường hợp theo thứ tự:
  - **Khoang chứa đã được đặt cọc (status = `Reserved`):**
    - `RentalOrder` giữ nguyên (không hủy đơn)
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa đã được đặt cọc bởi khách hàng khác. Hóa đơn này đã hết hiệu lực.
      ```
    - Hệ thống sửa trạng thái của hóa đơn này (`Invoice.status`) trong tài khoản thành `Canceled`.
    - Hệ thống tạo notification `PROPOSAL_REPROPOSAL_REQUIRED` cho FM, title “Cần đề xuất khoang khác” và body nêu mã đơn cùng khoang không còn khả dụng.
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang bảo trì (status = `Maintenance`):**
    - `RentalOrder` giữ nguyên (không hủy đơn)
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa hiện không khả dụng. Vui lòng liên hệ cơ sở để được hỗ trợ.
      ```
    - Hệ thống cập nhật `Invoice.status` = `Canceled`
    - Tạo notification `PROPOSAL_REPROPOSAL_REQUIRED` cho FM, title “Cần đề xuất khoang khác” và body nêu mã đơn cùng khoang đang bảo trì.
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang có giao dịch khác xử lý (chưa bị timeout):**
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa này hiện đang trong quá trình xử lý thanh toán (đặt cọc / gia hạn) bởi một khách hàng khác. Vui lòng quay lại thử lại sau ít phút hoặc chọn khoang chứa khác!
      ```
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang không có bất kỳ giao dịch nào:**
    - Hệ thống tạo khóa giao dịch thanh toán ngắn hạn để tránh xử lý đồng thời (không làm thay đổi trạng thái `StorageUnit`).
    - Hệ thống tạo bản ghi `PaymentTransaction` (`status = Pending`) trước khi chuyển hướng — dùng để đối soát khi khách đóng browser hoặc IPN đến muộn.
    - Khách ấn "Thanh toán"
    - Hệ thống chuyển hướng khách sang cổng thanh toán VNPay để nhập thông tin thẻ quốc tế
    - Sau khi khách hoàn tất thanh toán tại gateway, hệ thống chờ xác nhận từ cổng thanh toán và hiển thị một trong 3 trạng thái:
      - **Đang chờ xác nhận**
        - Hiển thị trạng thái đang chờ xác nhận.
      - **Thất bại:**
        - Giải phóng khóa giao dịch; `StorageUnit` vẫn giữ trạng thái `Available`.
        - Cập nhật bản ghi `PaymentTransaction` sang status = `Failed`
        - Hiển thị lỗi "Thanh toán thất bại, vui lòng thử lại!".
      - **Thành công**
        - Cập nhật bản ghi `PaymentTransaction` sang status = `Success`
        - Hệ thống cập nhật `Invoice.status` thành `Paid`
        - Hệ thống cập nhật `RentalOrder.status` thành `Deposited`
        - Hệ thống cập nhật `RentalOrder.expires_at = now + order.deposit_expiry_days` (30 ngày)
        - Hệ thống cập nhật trạng thái khoang chứa thành `Reserved`
        - Giải phóng khóa giao dịch; `StorageUnit` chuyển sang `Reserved` và được trạng thái này bảo vệ.
        - Hiển thị thông báo "Thanh toán thành công".
        - Tạo notification `DEPOSIT_PAYMENT_SUCCEEDED` cho khách với title “Đặt cọc thành công” và body xác nhận đã thanh toán tiền cọc.
        - Hệ thống điều hướng khách hàng sang màn hình chọn lịch hẹn check-in và bàn giao kho (Mục 1.5).

**Schema có trong phần này:**
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)
- [**Invoice**](./db-table-draft.md#invoice)
- [**RentalOrder**](./db-table-draft.md#rentalorder)

**Advanced Features:**
- Nếu khoang chỉ bảo trì tạm thời, cho phép giữ Invoice = Unpaid và khách thanh toán lại khi khoang trở về Available.

**NOTES:**
- Mọi khoản thanh toán trong MVP đều qua cổng VNPay — một phương thức duy nhất (cọc, tiền thuê tháng, phí phát sinh); không thu tiền mặt. Các flow khác dùng chung nguyên tắc này.
- Luồng từ việc đặt khoang -> đặt cọc -> chọn lịch hẹn là tuyến tính, tức là chỉ có đặt cọc mới có thể đặt lịch hẹn (check-in và bàn giao). Vì thế nên suy nghĩ đến việc cho đặt lịch hẹn (với loại là xem kho) trước khi đặt cọc, ở luồng này, mình có thể để FS xử lý nhiều lịch hẹn xem kho cùng 1 thời điểm (giống như 1 tour du lịch).
- Sau khi khách đã trả tiền cọc, khoang chứa phải được giữ ở trạng thái Reserved cho đến ngày hẹn check-in/bàn giao. Hết hạn nếu quá `order.deposit_expiry_days` kể từ lúc cọc mà chưa bàn giao → mất cọc và chuyển đơn sang trạng thái Expired. Trường hợp hủy do lỗi cơ sở (hết khoang phù hợp) → hoàn cọc thủ công (C6); chính sách chi tiết thuộc Flow 4.
- IPN là nguồn xác nhận thanh toán duy nhất: verify checksum, kiểm tra `vnp_TmnCode` + `vnp_Amount` khớp invoice; handler idempotent theo `vnp_txn_ref` (VNPay retry tối đa 10 lần × 5 phút); trả đúng `RspCode` theo quy định VNPay; `vnp_ReturnUrl` chỉ dùng để hiển thị kết quả.
#### 1.5 Chọn lịch check-in sau khi đặt cọc
**Context:** Sau khi khách đã đặt cọc thành công (`RentalOrder.status = Deposited`), khoang chứa đã được giữ ở trạng thái `Reserved` nhưng đơn hàng chưa có lịch hẹn check-in. Flow 1 tiếp tục hỗ trợ khách hàng đặt lịch hẹn tại cơ sở, sau đó chuyển thông tin lịch hẹn cho Flow 2 để thực hiện các bước check-in và bàn giao khoang.

**Flow tổng quát:**
Hệ thống điều hướng khách hàng đến trang đặt lịch hẹn -> Khách chọn ngày và giờ trong giới hạn quy định -> Hệ thống tạo `Appointment(type = CHECKIN, status = Pending)` + `RentalAppointment` nối với `RentalOrder` + `HandoverRecord` cho đơn hàng -> Hệ thống cập nhật `RentalOrder.status` sang `Scheduled` -> FM chỉ định một nhân viên FS phụ trách -> Hệ thống gán `Appointment.staff_id` và cập nhật `RentalOrder.status` sang `InProgress`

**Details:**
- **Customer:**
  - Hệ thống điều hướng user đến trang chọn lịch hẹn.
  - Khách chọn ngày và khung giờ hẹn đến nhận khoang: chọn lịch trong vòng `appointment.booking_window_days` (7 ngày) kể từ lúc cọc, ngày hẹn cách lúc cọc tối đa `appointment.max_days_after_deposit` (14 ngày).
  - Khách ấn "Xác nhận".
  - Hệ thống tạo `Appointment(type = CHECKIN, status = Pending)` + `RentalAppointment` nối với `RentalOrder`.
  - Hệ thống tạo một bản ghi `HandoverRecord` cho đơn hàng để Flow 2 tiếp tục xử lý các bước check-in và bàn giao.
  - Hệ thống cập nhật trạng thái `RentalOrder.status` sang `Scheduled`.
  - Hệ thống gửi email và tạo notification `APPOINTMENT_CREATED` cho khách với title “Lịch hẹn check-in đã được tạo” và body có ngày/khung giờ hẹn, địa chỉ cơ sở, hướng dẫn mang CCCD/Passport.
- **FM:**
  - FM nhận notification `APPOINTMENT_CREATED`, title “Có lịch check-in mới cần phân công” và body có mã đơn, thời gian hẹn, cơ sở
  - FM xem danh sách các đơn đang ở trạng thái `Scheduled`.
  - FM chỉ định một nhân viên cơ sở (`FS`) phụ trách ca tiếp đón khách:
    - Hệ thống gán `Appointment.staff_id = [FS_Account_ID]`.
    - Hệ thống cập nhật trạng thái `RentalOrder.status` sang `InProgress`.
    - Tạo notification `FS_ASSIGNED` cho FS được phân công, title “Bạn được phân công lịch check-in” và body có mã đơn, thời gian hẹn, cơ sở và thông tin khoang.
    - Nếu chưa có FS phù hợp, `Appointment.staff_id` để trống, `RentalOrder` giữ `Scheduled`
    - Hệ thống tạo notification `APPOINTMENT_CREATED` cho FM, title “Lịch check-in chưa có nhân viên phụ trách” và body có mã đơn, thời gian hẹn, cơ sở. Không tự động gán FS.

**Schema có trong phần này:**
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalAppointment**](./db-table-draft.md#rentalappointment)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)

**NOTES:**
- `HandoverRecord` được tạo cùng lúc với `Appointment`.
- `result = IN_PROGRESS` ở thời điểm khởi tạo nghĩa là hồ sơ bàn giao đang được mở, không đồng nghĩa khách đã đến cơ sở.
- MVP dùng ba khung giờ cố định mỗi ngày; không có `AppointmentSlot` động và không có reschedule. Một lịch `CHECKIN` chỉ phục vụ một khách và một FS tại một thời điểm.
- Lịch xem kho theo tour và capacity động là Advanced Feature.
- Hủy trước khi đặt cọc không hoàn tiền vì chưa phát sinh thanh toán. Hủy sau khi đặt cọc do khách chủ động thì mất cọc; hủy do lỗi cơ sở thì hoàn thủ công theo policy và ghi `AuditLog`.

**Advanced Features (not MVP):**
- Reschedule lịch hẹn.
- Lịch xem kho theo tour và capacity động.

### 2. Check-in và bàn giao kho

**FLOW:**
```
[Nhận lịch hẹn từ Flow 1] -> [Check-in & xác minh danh tính] -> [Kiểm tra & xác nhận hiện trạng khoang] -> [Ký hợp đồng] -> [Thanh toán tháng đầu] -> [Nhận khóa/mã truy cập] -> [Khoang chuyển Rented]
```

#### Điều kiện và dữ liệu đầu vào
- **Flow 1:**
  - `RentalOrder.status = InProgress`.
  - `RentalOrder.unit_id` đã được gán.
  - `StorageUnit.status = Reserved`.
  - `ProposalFeedback.status = Agreed`.
  - Invoice đặt cọc có `status = Paid`.
  - Có `Appointment(type = CHECKIN, status = Pending)`.
  - `Appointment.facility_id` đã được gán.
  - Có `RentalAppointment` nối `Appointment` với `RentalOrder`.
  - Có `HandoverRecord` tương ứng với `Appointment`, với `result = IN_PROGRESS`.

- **Flow 5:**
  - `Appointment.staff_id` đã được FM phân công.
  - FS thuộc cơ sở của `Appointment`.
  - Cấu hình loại khóa của cơ sở: `enabledKeyAccess`, `enabledCodeAccess`.

- **Flow 4:**
  - Bảng giá thuê.
  - Chính sách mốc bắt đầu tính tiền thuê.
  - Danh mục phí.
  - Mẫu hợp đồng đang `Active` kèm version và **danh sách biến** mẫu hỗ trợ.

#### Các tham số sử dụng

| Tên | Giá trị |
|---|---:|
| `handover.payment_grace_hours` | chờ BOM |
| `handover.max_rejection_count` | 2 lần |
| `handover.due_days` | chờ BOM (gợi ý 1–2 ngày) |
| `contract.start_date_rule` | chờ BOM |
| `contract.prepaid_months` | 1 tháng |

`handover.max_rejection_count` đếm số lần khách **từ chối khoang tại chỗ** trên một đơn, tách khỏi `proposal.max_rejection_count` của Flow 1 (đếm lần từ chối proposal trước khi cọc).

**Vị trí trong vòng đời thuê kho:** Flow 1 giữ trọn vòng đời đặt khoang `RentalRequest -> ProposalFeedback -> Deposit -> Appointment`. Flow 2 bắt đầu khi đơn đã có `Appointment(type = CHECKIN, status = Pending)` và `HandoverRecord` (`result = IN_PROGRESS`) do Flow 1.5 tạo sẵn, và FS đã được phân công - theo Flow 1.5 thì lúc này `RentalOrder.status = InProgress`, khoang `Reserved`, `Invoice` cọc đã `Paid`, chỉ chịu trách nhiệm phần on-site: check-in -> kiểm tra khoang -> ký hợp đồng -> thanh toán tháng đầu -> bàn giao. Flow 2 kết thúc khi `HandoverRecord.result` chuyển `COMPLETED` (khoang `Rented`, hợp đồng có hiệu lực, bàn giao sang Flow 3) hoặc `REJECTED` (Flow 1 để FM đề xuất khoang khác, khách duyệt `ProposalFeedback` mới và Flow 1 tạo lịch hẹn mới; Flow 2 chạy lại trên `HandoverRecord` mới). Toàn bộ là thao tác on-site.

**Context:** Khách đã đặt cọc giữ khoang, đến cơ sở để check-in, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu và nhận quyền truy cập.

#### 2.1 Tiếp nhận lịch hẹn check-in

**Context:** Sau khi khách đã đặt cọc, Flow 1 tạo lịch hẹn `CHECKIN` và `HandoverRecord`. Flow 2 tiếp nhận lịch đã được phân công để thực hiện phần check-in và bàn giao tại cơ sở.

**Flow tổng quát:** Flow 2 đọc lịch hẹn do Flow 1 tạo -> kiểm tra điều kiện vào flow -> FS tiếp nhận khách theo lịch được phân công.

**Details:**
- **FM:**
  - FM xem danh sách `Appointment` của cơ sở theo ngày.
  - Hệ thống lọc lịch theo `Appointment.facility_id`; FM chỉ thấy lịch của cơ sở mình đảm nhận.
  - FM tìm các lịch chưa có `staff_id` để phân công FS.
  - Nghiệp vụ phân công thuộc Flow 5.3; Flow 2 chỉ sử dụng kết quả phân công.
- **FS:**
  - FS xem các lịch trong ngày được phân công cho mình.
  - Hệ thống hiển thị thông tin khách hàng, khoang chứa và `type` của lịch hẹn.
  - FS chọn lịch hẹn và bấm nút **Done** để xác nhận khách đã đến cơ sở.
  - Hệ thống ghi nhận `Appointment.arrived_at`, cập nhật `Appointment.status = Done` và set `HandoverRecord.due_at = now + handover.due_days`.
  - Trong trường hợp **khách không đến**, cron job của Flow 1 sẽ tự động cập nhật trạng thái của `Appointment` theo [Scheduled Jobs - Cron jobs](#scheduled-jobs---cron-jobs).
- **Hệ thống:**
  - Kiểm tra các điều kiện:
    - `Appointment(type = CHECKIN, status = Pending)` thuộc đơn.
    - `facility_id` khớp cơ sở đang thao tác.
    - `staff_id` đã được gán; Flow 1.5 đã chuyển `RentalOrder.status` sang `InProgress` ở bước này.
    - `HandoverRecord` của lịch hẹn đang có `result = IN_PROGRESS`.
    - Invoice cọc đã có `status = Paid`.
    - Khoang đang có `status = Reserved`.
  - Nếu thiếu bất kỳ điều kiện nào, API check-in trả lỗi.

**Schema có trong phần này:**
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalAppointment**](./db-table-draft.md#rentalappointment)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)

**NOTES:**
- FS được gán phải thuộc cơ sở của `Appointment.facility_id`; validate qua [**AccountFacilityAssignment**](./db-table-draft.md#accountfacilityassignment).
- FS đang đăng nhập phải khớp `Appointment.staff_id`; sai thì chặn thao tác.
- Flow 2 không tạo hoặc đặt lại `Appointment`.
- Flow 1 sở hữu việc sinh slot, đặt lịch, hủy và đặt lại lịch.
- Nghiệp vụ phân công FS thuộc Flow 5.3.

#### 2.2 Check-in và kiểm tra khoang chứa

**Context:** Khách đã đến cơ sở theo lịch `CHECKIN`. FS cần xác minh danh tính và cùng khách kiểm tra hiện trạng khoang trên `HandoverRecord` do Flow 1.5 tạo.

**Flow tổng quát:** FS xác minh danh tính -> kiểm tra hiện trạng khoang -> khách đồng ý (hoặc từ chối được xử lý ở 2.5) -> hệ thống tiếp tục bàn giao hoặc chuyển thông tin về Flow 1.

**Details:**
- Các bước thực hiện theo thứ tự: xác minh danh tính → kiểm tra hiện trạng khoang (2.3, 2.4 tiếp theo).
- FS phụ trách được xác định qua `Appointment.staff_id`.
- **FS:**
  - FS mở `HandoverRecord` gắn với `Appointment` đó; Flow 1.5 đã tạo record với `result = IN_PROGRESS`.
  - FS đối chiếu giấy tờ người đến với thông tin Account của `RentalOrder.customer_id`.
  - Nếu khách đã upload ảnh giấy tờ online, FS đối chiếu với ảnh hiển thị trên hệ thống.
  - Nếu xác minh **đạt**, hệ thống cập nhật `HandoverRecord.identity_status = Verified`, `HandoverRecord.identity_verified_at`.
  - Nếu xác minh **không đạt**, FS dừng quy trình, bật cờ `HandoverRecord.identity_status = Failed` và mời khách ra về; **không hủy lịch/đơn** ở bước này.
    - MVP chỉ chấp nhận đúng người trên đơn (RentalOrder.customer_id); không xử lý người nhận thay.
    - `HandoverRecord` giữ `IN_PROGRESS`; quá `due_at` thì cron của Flow 2 xử lý như no-show ([Cron jobs](#scheduled-jobs---cron-jobs)).
  - FS dẫn khách kiểm tra toàn bộ hiện trạng: kích thước, vị trí, vệ sinh, kết cấu, cửa/khóa và hư hại sẵn có.
  - FS nhập `HandoverRecord.inspection_notes` và `HandoverRecord.inspection_photos`.
- **Customer:**
  - Khách vào trang **Kho của tôi**.
  - Khách chọn khoang đang bàn giao (`StorageUnit.status = Reserved`).
  - Ấn vào nút "Bàn giao" để chuyển hướng qua trang để thao tác bàn giao.
  - Khách xác nhận hiện trạng khoang sau khi kiểm tra.
  - Khách chọn một trong 3 thao tác:
    - **Đồng ý:**
      - Hệ thống cập nhật `HandoverRecord.inspection_status = Agreed`, `HandoverRecord.unit_inspected_at`
      - Khách có thể nhập những thứ cơ sở cần lưu ý (`HandoverRecord.inspection_notes`, vd khoang chưa sạch). -> không bắt buộc
    - **Từ chối** được xử lý ở 2.5
    - **Hủy đơn** được xử lý ở 2.5

- **Hệ thống:**
  - Nếu khách không đến, cron của Flow 1 xử lý theo [Scheduled Jobs - Cron jobs](#scheduled-jobs---cron-jobs).

**Schema có trong phần này:**
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback)
- [**StorageUnit**](./db-table-draft.md#storageunit)

#### 2.3 Ký hợp đồng và thanh toán tháng đầu tiên

**Context:** Khách đã hoàn tất xác minh danh tính và xác nhận hiện trạng khoang. Flow 2 cần tạo hợp đồng, xử lý thỏa thuận riêng về mốc tính tiền nếu có, và thu thanh toán tháng đầu.

**Flow tổng quát:** Đủ điều kiện ký -> sinh hợp đồng -> khách ký -> tạo hóa đơn tháng đầu -> khách thanh toán hoặc chờ xử lý quá hạn.

**Details:**

**Giai đoạn 1 — Sinh và ký hợp đồng:**

- **Hệ thống:**
  - Chỉ cho phép tiếp tục khi `HandoverRecord.identity_status = Verified` và `HandoverRecord.inspection_status = Agreed`.
  - Sinh `RentalContract` (`status = Draft`) từ mẫu đang hiệu lực của Flow 4: khách, cơ sở, `unit_id`, giá thuê, `period`, tiền cọc đã đóng, `start_date`.
  - Gắn hiện trạng khoang (`inspection_notes`, `inspection_photos`) ở 2.2 với hợp đồng qua `order_id`; đây là căn cứ đối chiếu khi trả kho ở Flow 2.a.
  - Mốc bắt đầu tính tiền thuê luôn được điền sẵn theo chính sách Flow 4 (`contract.start_date_rule`).
- **FS:**
  - FS không tự sửa `RentalContract.start_date`. Nếu cần thỏa thuận riêng, FS nhập ngày đề nghị và lý do vào hợp đồng `Draft`: `start_date_override_requested`, `start_date_override_reason`, `start_date_override_status = Pending`.
- **FM:**
  - FM xem các yêu cầu đổi mốc tính tiền đang chờ của cơ sở mình.
  - Khi duyệt, hệ thống dùng ngày đề nghị cho `RentalContract.start_date`, cập nhật `RentalContract.start_date_override_status = Approved` và mở lại bước ký.
  - Khi từ chối, hệ thống giữ ngày theo chính sách, cập nhật `RentalContract.start_date_override_status = Rejected` và mở lại bước ký. FS có thể gửi đề nghị khác nếu khách vẫn không đồng ý.
  - Việc gửi đề nghị, duyệt và từ chối đều ghi `AuditLog` kèm `old_value`/`new_value`/`reason`.
- **Hệ thống:**
  - Khi yêu cầu đổi mốc tính tiền còn `Pending`, chặn bước ký cho tới khi FM xử lý.
  - Render tài liệu hợp đồng từ mẫu đang `Active` + dữ liệu của phiên; lưu bản chưa ký vào `document_url`.
    - Chỉ render tài liệu khi không còn yêu cầu đổi `start_date` đang `Pending` (record có thể sửa khi còn `Draft`, tài liệu thì không regenerate).
  - Ghi `terms_version` = version mẫu đã dùng để sinh tài liệu.
- **FS:**
  - In tài liệu cho khách đọc và ký trên giấy.
  - Chụp/scan bản đã ký, upload; lưu `pdf_url`, cập nhật `RentalContract.status = Signed`.

**Giai đoạn 2 — Hóa đơn và thanh toán tháng đầu:**

- **Hệ thống:**
  - Tạo `Invoice(type = Rental)`, prefix `RNT`, gắn `contract_id`, với số tiền tháng đầu và `due_date = now + handover.payment_grace_hours`. Tiền cọc ở Flow 1.4 không trừ vào hóa đơn này và được giữ riêng tới khi trả kho ở 2.a.3.
  - Khi gateway xác nhận thành công, cập nhật `PaymentTransaction = Success`, `Invoice.status = Paid`.
  - Khi thanh toán thất bại, cập nhật `PaymentTransaction = Failed`; hóa đơn giữ nguyên chưa thanh toán và không tiếp tục bàn giao.
  - Quá hạn, [cron hóa đơn](#scheduled-jobs---cron-jobs) xử lý. Khi đơn còn trong hạn giữ kho, Flow 1 tạo lịch check-in mới, FM phân công FS theo Flow 5.3, và Flow 2 chạy lại từ 2.1 trên `Appointment` + `HandoverRecord` mới.
- **Customer:**
  - Khách thanh toán hóa đơn tháng đầu qua VNPay.
  - Nếu chưa thanh toán xong trong buổi hẹn, hệ thống giữ `HandoverRecord.result = IN_PROGRESS`, giữ khoang `Reserved`, chưa bàn giao khóa; hóa đơn nằm trong mục "Hóa đơn" của khách.


**Schema có trong phần này:**
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**Invoice**](./db-table-draft.md#invoice)
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)

**NOTES:**
- Hợp đồng được ký ở bản hợp đồng giấy nên `RentalContract.signature` tạm thời không đụng đến.
- Mốc bắt đầu tính tiền thuê mặc định theo chính sách Flow 4; thỏa thuận riêng phải được FM duyệt.
- Flow 2 dùng chung schema/nguyên tắc thanh toán của Flow 1, không mô hình riêng.
- Hợp đồng đã ký → Flow 4 ghi nhận; hóa đơn tháng đầu tạo trong transaction ký, không tạo từ bước khác.
- Hóa đơn RNT là căn cứ để Flow 4 theo dõi doanh thu.

#### 2.4 Bàn giao khóa và kích hoạt hợp đồng

**Context:** Hợp đồng đã được ký, hóa đơn tháng đầu đã thanh toán và khách đủ điều kiện nhận quyền truy cập khoang.

**Flow tổng quát:** Kiểm tra checklist -> FS bàn giao chìa hoặc mã truy cập -> hệ thống cập nhật trạng thái khoang, hợp đồng, biên bản và đơn hàng.

**Details:**
- **FS:**
  - Chỉ thực hiện bàn giao khi hai cờ trạng thái trên `HandoverRecord` (`identity_status`, `inspection_status`) đều thành công, đồng thời `RentalContract.signed_at != null` và hóa đơn tháng đầu `Paid`.
  - Nếu khách chọn khóa cơ (`enabledKeyAccess`): giao chìa vật lý, ghi `quantity`;
  - Nếu khách chọn cả hai: giao chìa và nhắc khách lấy mã trong tài khoản.
  - Nếu giao khóa cơ: hai bên xác nhận bàn giao — FS xác nhận trên hệ thống, khách xác nhận trong tài khoản.
  - Mỗi loại khóa được giao tạo một dòng `UnitAccessKey` với `access_type` tương ứng.
- **Hệ thống:**
  - Nếu thiếu một cờ bắt buộc, API bàn giao trả lỗi cho FS.
  - Với khóa mã số, sinh mã gắn với `RentalContract`, lưu `code_hash`, không lưu plain text.
  - Trong một transaction, tạo `UnitAccessKey`, chuyển `StorageUnit: Reserved -> Rented`, `RentalContract: Signed -> Active`, `HandoverRecord.result = COMPLETED` với `completed_at`, và `RentalOrder.status -> Done`.
  - **Khách xác nhận với mã khóa số:** hệ thống tự chạy transaction trên ngay khi khách xác nhận, không cần thao tác của FS.
  - Chốt bàn giao chỉ chạy một lần: chỉ xử lý khi `HandoverRecord.result = IN_PROGRESS`.
  - Flow 2 là nơi duy nhất set `RentalOrder.status = Done`.
  - Sau transaction, gửi email kèm hợp đồng và link xem biên bản bàn giao
  - Tạo notification `HANDOVER_COMPLETED` cho khách với title “Bàn giao kho hoàn tất” và body có mã đơn, mã khoang, link xem biên bản. Lỗi gửi email hoặc notification không rollback bàn giao.
  - Sau bàn giao, Flow 3 tiếp nhận khoang đang thuê.
- **Customer:**
  - Sau khi hóa đơn tháng đầu đã `Paid`, khách xác nhận biên bản bàn giao trong tài khoản và chọn hình thức nhận quyền truy cập (chỉ hiện các loại mà cơ sở đang bật):
    - **Mã khóa số** — xác nhận là bàn giao hoàn tất; hệ thống cấp mã, khách xem trong tài khoản, không cần FS.
    - **Khóa cơ** — hệ thống chuyển yêu cầu giao chìa cho FS; FS giao chìa và xác nhận thì bàn giao hoàn tất.

**Schema có trong phần này:**
- [**StorageUnit**](./db-table-draft.md#storageunit)
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey)

**NOTES:**
- MVP chưa sinh file biên bản: biên bản bàn giao là dữ liệu `HandoverRecord` xem trên website. Khách xác nhận khi đang đăng nhập là đủ, không cần chữ ký giấy; cần bản giấy thì làm sau.
- Sau khi biên bản đã chốt, hạn chế sửa `inspection_notes`/`inspection_photos`; nếu phải sửa (sai sót, bổ sung) thì ghi lý do và để lại vết trong `AuditLog`.
- Ảnh hiện trạng nên giữ tới khi `RentalContract` chuyển `Ended` (trả kho hoàn tất), vì đây là mốc đối chiếu lúc trả kho.

#### 2.5 Xử lý từ chối / hủy trong quá trình bàn giao

**Context:** Khách có thể đổi ý ở bất kỳ bước nào từ khi đến cơ sở đến trước khi nhận khoang. Sau khi 2.4 hoàn tất, không xử lý ở đây (thuộc Flow 3/6).

**Flow tổng quát:** Xác định thời điểm khách dừng -> rẽ theo lựa chọn -> dọn dẹp những gì đã sinh -> kết thúc Flow 2.

**Details:**

- **Customer:**
  - Khách vào trang "Kho của tôi" để bắt đầu thực hiện thao tác
  - Ba lựa chọn khi không muốn tiếp tục:
    - Từ chối — trước khi `RentalOrder.status = Done` và muốn đổi đơn hàng.
    - Hủy đơn - trước khi `RentalOrder.status = Done` và muốn hủy đơn hàng.
    - Dừng phiên, chưa quyết — đang trong quá trình bàn giao nhưng chưa thể chốt trong 1 ngày.
  - **Từ chối:**
    - Hệ thống cập nhật `inspection_status = Rejected` và `unit_inspected_at`
    - Khách nhập lý do (`HandoverRecord.reject_reason`).
  - **Hủy đơn**:
    - Hệ thống yêu cầu khách xác nhận việc hủy và thông báo hậu quả theo chính sách.
    - Khách xác nhận:
      - Thực hiện [RentalOrder Cancellation Cascade](./db-table-draft.md#rentalorder-cancellation-cascade)
      - Appointment đã `Done` nên giữ nguyên.
      - `HandoverRecord` hiện tại chuyển `IN_PROGRESS -> CANCELED`.
      - Flow 2 kết thúc.
    - Khách không xác nhận:
      - Không ghi gì.
      - Giữ nguyên `HandoverRecord` để khách tiếp tục kiểm tra hoặc chọn từ chối khoang.
      - Nếu quá `due_at`, cron của Flow 2 đóng biên bản và xử lý như no-show ([Cron jobs](#scheduled-jobs---cron-jobs)).
  - **Dừng phiên**:
    - Không cần thao tác
    - Nếu `HandoverRecord` quá hạn sẽ bị xử lý ở [Cron jobs](#scheduled-jobs---cron-jobs)

- **FS:**
  - FS vào mục "Đơn đang bàn giao" để chọn đơn của khách hàng để kiểm tra quá trình và hướng dẫn khách hàng các thao tác tiếp theo để hủy / từ chối.

- **Hệ thống:**
  - Xử lý theo bảng:

| Khách đổi ý tại | Từ chối khoang | Hủy đơn |
|---|---|---|
| Sau xác minh, trước kiểm tra | `REJECTED` -> Flow 1 | `CANCELED` -> Cascade |
| Sau kiểm tra, trước ký | `REJECTED` -> Flow 1 | `CANCELED` -> Cascade |
| Sau ký, trước trả tiền | — (chỉ còn hủy) | contract `Canceled`, invoice `Canceled`, đơn `Canceled`, cọc theo chính sách |
| Sau trả tiền, trước nhận khoang | — (chỉ còn hủy) | như trên + ghi nhận khoản hoàn, FM xử lý thủ công |
| Sau sinh hợp đồng `Draft`, khách không ký | — | `RentalContract: Draft -> Canceled`, record đóng CANCELED kèm reject_reason, hủy đơn theo chính sách |

  - Mọi nhánh hủy đơn chạy theo [RentalOrder Cancellation Cascade](./db-table-draft.md#rentalorder-cancellation-cascade).
  - Nếu khách chọn **từ chối khoang này**:
    - Trước khi ghi nhận lần từ chối, hệ thống đếm số `HandoverRecord` cùng `RentalOrder` có `result = REJECTED` (không đếm `CANCELED`, tính trên toàn lịch sử đơn); việc đếm và cập nhật nằm trong cùng transaction.
    - Điều kiện chạm ngưỡng: `count + 1 >= handover.max_rejection_count`.
      - **Chưa chạm ngưỡng** số lần từ chối:
        - Cập nhật `HandoverRecord.result = REJECTED`.
        - Tạo notification `HANDOVER_REJECTED` cho FM, title “Khách từ chối khoang tại check-in” và body có mã khoang cùng `HandoverRecord.reject_reason`.
        - Flow 2 kết thúc.
        - Flow 1 thực hiện re-propose theo [Flow 1.3 Kiểm tra kho của tôi](#13-kiểm-tra-kho-của-tôi).
        - Sau khi khách duyệt proposal mới và Flow 1 hoàn tất các bước liên quan, Flow 2 bắt đầu lại trên `Appointment` và `HandoverRecord` mới.
      - **Chạm ngưỡng** số lần từ chối:
        - Hệ thống hiển thị xác nhận cho khách, nêu rõ đơn sẽ bị hủy vì đã từ chối tối đa N khoang và tiền cọc không được hoàn.
        - **Khách xác nhận:**
          - Cập nhật `HandoverRecord.result = REJECTED`.
          - Thực hiện [RentalOrder Cancellation Cascade](./db-table-draft.md#rentalorder-cancellation-cascade)
          - Kết thúc Flow 2
        - **Khách không xác nhận:**
          - Không ghi gì và giữ nguyên hiện trạng.
          - Nếu `HandoverRecord` quá `due_at`, cron của Flow 2 đóng biên bản và xử lý như no-show theo [Cron jobs](#scheduled-jobs---cron-jobs).

- **FM:**
  - Xử lý hoàn tiền thủ công theo chính sách Flow 4.
  - Ghi `PaymentTransaction(direction = REFUND)` và audit `MANUAL_REFUND_RECORDED`.

**Schema có trong phần này:**
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**Invoice**](./db-table-draft.md#invoice)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback)
- [**StorageUnit**](./db-table-draft.md#storageunit)

**Advanced Features (not MVP)**
- Chính sách trả trước N tháng thay vì cố định 1 tháng (`prepaid_months` > 1).
- Outbox, retry tự động và bảo đảm giao email cho thông báo.
- Ký hợp đồng online: panel ký tay trên web, hệ thống tự sinh PDF thay vì FS upload bản scan.
- Các ý tưởng quanh lịch hẹn chuyển sang Flow 1 cùng vòng đời `Appointment`: dời lịch (reschedule), lịch "tham quan kho" cho khách chưa cọc (`type = TOUR`, nhiều khách chung một slot), giới hạn số khách trên slot theo số FS khả dụng, cho khách chọn slot theo lịch trống thực tế của từng FS. Riêng việc **nhắc việc trước buổi hẹn** thì Flow 1 đã làm trong MVP (gửi kèm lúc xác nhận lịch); cái nằm ngoài MVP là nhắc **tự động trước 24h** bằng job riêng.
- Cho khách xem ảnh/video khoang chứa trước buổi hẹn để giảm tỉ lệ từ chối tại chỗ.
- eKYC khi đăng ký tài khoản, bước xác minh on-site rút gọn còn đối chiếu nhanh.
- Khóa thông minh điều khiển qua app, bỏ hẳn bước giao chìa khóa vật lý.

### 2.a Trả kho và bảo trì (Chưa qua review, không được phép implement phần này)

**FLOW:**
```
[Yêu cầu trả kho] -> [Hẹn lịch trả] -> [FS kiểm tra khoang] -> [Xử lý phí phát sinh] -> [Thu hồi quyền truy cập] -> [Hoàn cọc] -> [Bảo trì] -> [Khoang về Available]
```

#### Điều kiện và dữ liệu đầu vào
- **Flow 3:**
  - Có `ReturnRequest` với `status = Assigned`.
  - `ReturnRequest.assigned_staff_id` đã được FM phân công (Flow 3.4).

- **Flow 2:**
  - Khoang đang `Rented` và hợp đồng đang `Active`.
  - `HandoverRecord` của đơn có `inspection_notes`, `inspection_photos` — mốc đối chiếu hiện trạng lúc nhận.
  - `UnitAccessKey` của đơn đang `Active` để thu hồi ở 2.a.2.

- **Flow 4:**
  - `ExtraFee` theo `category`: `CLEANING`, `DAMAGE`, `LOST-KEY`.
  - `unit.maintenance_days` cho thời gian bảo trì.
  - Chính sách xử lý tiền cọc.

#### Các tham số sử dụng trong Flow 2.a

| Tên | Giá trị |
|---|---:|
| `unit.maintenance_days` | 1-3 ngày |

**Vị trí trong vòng đời thuê kho:** Flow 2.a nhận đầu vào từ Flow 3.4 (khách bấm yêu cầu trả kho) và xử lý toàn bộ phần on-site. Kết thúc khi khoang hoàn tất bảo trì và quay về `Available`, sẵn sàng cho yêu cầu mới ở Flow 1. Đây là điểm đóng vòng đời của một `RentalOrder`.

**Context:** Khách kết thúc nhu cầu thuê và muốn trả lại khoang chứa, cần có người kiểm tra hiện trạng, xử lý các khoản phát sinh và thu hồi quyền truy cập trước khi khoang được cho thuê lại.

#### 2.a.1 Tiếp nhận yêu cầu và hẹn lịch trả kho

**Context:** Khách đã gửi yêu cầu trả kho. `ReturnRequest` được Flow 3 chuyển sang `Assigned` và đã có FS do FM phân công.

**Flow tổng quát:** Hệ thống tạo lịch `RETURN` -> khách chuẩn bị khoang trước ngày hẹn -> FS tiếp nhận buổi trả kho.

**Details:**
- **Hệ thống:**
  - Khi `ReturnRequest` chuyển `Assigned` ở Flow 3.4, tạo `Appointment(type = RETURN, status = Pending)` kèm `RentalAppointment`.
  - Gán `facility_id` và `staff_id` từ `ReturnRequest.assigned_staff_id`.
  - Thông báo cho FS được phân công về buổi trả kho.
  - Lịch `RETURN` dùng chung ba khung giờ cố định với `CHECKIN` (`appointment.daily_slot_count`) vì cùng FS phục vụ.
  - Map `preferred_date` của khách vào khung còn trống trong ngày đó. Nếu hết khung, đẩy sang ngày gần nhất và báo khách.
  - Việc phân công FS đã do FM làm ở Flow 3; Flow 2.a không lặp lại.
- **Customer:**
  - Trước ngày hẹn, khách tự dọn toàn bộ tài sản ra khỏi khoang.
  - Hệ thống nhắc điều kiện để được nhận lại cọc: khoang trống, không hư hỏng và không còn hóa đơn `Unpaid`.

**Schema có trong phần này:**
- [**ReturnRequest**](./db-table-draft.md#returnrequest)
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalAppointment**](./db-table-draft.md#rentalappointment)

#### 2.a.2 Kiểm tra và bàn giao lại khoang chứa

**Context:** Khách đến theo lịch `RETURN`. FS đối chiếu hiện trạng khoang lúc trả với hiện trạng đã ghi trong `HandoverRecord` của Flow 2.

**Flow tổng quát:** FS ghi nhận khách đến -> đối chiếu hiện trạng -> lập `CheckoutRecord` -> xử lý nhánh khoang trống hoặc còn tài sản -> thu hồi quyền truy cập khi đủ điều kiện.

**Details:**
- **FS:**
  - Ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - Mở `HandoverRecord` đã lập ở Flow 2 để lấy `inspection_notes`, `inspection_photos`; Flow 2.a chỉ đọc bảng này.
  - Kiểm tra hiện trạng lúc trả: khoang đã dọn trống chưa, tình trạng vệ sinh, hư hỏng kết cấu/cửa/khóa/thiết bị và chụp ảnh hiện trạng.
  - Dùng chênh lệch giữa hai mốc làm căn cứ tính phí hư hỏng/vệ sinh ở 2.a.3.
  - Lập `CheckoutRecord`, cho khách ký xác nhận. Mỗi cột mốc bật một cờ kèm timestamp để FM/FS theo dõi khi buổi trả kho kéo dài nhiều ngày.
  - Nếu khoang bẩn hoặc hư hỏng, ghi nhận chi tiết kèm ảnh; hệ thống tạo hóa đơn phí tương ứng ở 2.a.3.
- **Customer:**
  - Ký xác nhận biên bản trả kho.
- **Hệ thống:**
  - Hệ thống hiển thị danh sách phí dự kiến cho FS xem trước khi chốt biên bản; chưa tạo hóa đơn ở bước này.
  - Nếu khoang đạt yêu cầu, không phát sinh phí và chuyển sang 2.a.3.
  - Nếu còn tài sản, ghi `CheckoutRecord.result = PENDING_ITEMS` và chưa hoàn tất trả kho.
  - Tạo `Appointment(type = RETURN)` mới để khách quay lại dọn nốt và trỏ `CheckoutRecord.appointment_id` sang lịch mới.
  - Khi khách quay lại và FS bắt đầu kiểm tra lần nữa, chuyển `result` trên cùng bản ghi từ `PENDING_ITEMS -> IN_PROGRESS`. Vòng này lặp tới khi khoang trống.
  - Trong thời gian `PENDING_ITEMS`, không thu hồi `UnitAccessKey`; khách vẫn cần quyền truy cập để lấy đồ.
  - Trong thời gian `PENDING_ITEMS`, khoang giữ `Rented`, hợp đồng giữ `Active`. Phí quá hạn do Flow 6 tính theo `overdue.fee_per_day`; Flow 2.a không tự tính.
  - Thời hạn dọn tiếp và phí lưu giữ theo chính sách BOM ở Flow 4. Hệ thống không tự động tính phí lưu giữ trong MVP; FM hoặc FS gửi hóa đơn thủ công.
  - Chỉ thu hồi quyền truy cập khi `CheckoutRecord.result` không phải `PENDING_ITEMS`.
  - Với khóa cơ, FS thu lại chìa và đối chiếu `UnitAccessKey.quantity`. Thiếu chìa thì tính `fee.lost_key`, set `UnitAccessKey.status = Lost` thay vì `Revoked`, ghi số chìa thu được vào `returned_key_quantity`; khoang phải thay khóa trước khi cho thuê lại.
  - Với khóa mã số, vô hiệu hóa mã ngay khi biên bản được xác nhận.
  - Thu đủ chìa hoặc vô hiệu hóa mã thì `UnitAccessKey.status -> Revoked`, ghi `revoked_at`. Thiếu chìa thì `-> Lost`; khoang phải thay khóa trong kỳ bảo trì ở 2.a.4.

**Schema có trong phần này:**
- [**Appointment**](./db-table-draft.md#appointment)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord)
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey)
- [**RentalContract**](./db-table-draft.md#rentalcontract)

**NOTES:**
- Khách báo mất chìa: thu phí `LOST-KEY` (đối trừ cọc theo 2.a.3) và giữ khoang `Maintenance` tới khi thay khóa xong.
- Khách trả lại được chìa trước khi khóa được thay: không phát sinh thay khóa; phần phí đã thu để FM điều chỉnh thủ công.
- Mức phí `LOST-KEY` thuộc Flow 4 (`ExtraFee`).

#### 2.a.3 Xử lý phí phát sinh và tiền cọc

**Context:** FS đã kiểm tra khoang và xác định các khoản phí phát sinh hoặc các hóa đơn còn tồn đọng cần đối trừ với tiền cọc.

**Flow tổng quát:** Tập hợp phí -> đối trừ với tiền cọc -> hoàn phần dư hoặc yêu cầu khách thanh toán phần thiếu.

**Details:**
- **FS/FM:**
  - Tạo hóa đơn cho phí vệ sinh (`CLEANING`), phí hư hỏng (`DAMAGE`), phí mất chìa hoặc thay khóa (`LOST-KEY`) dưới `Invoice.type = Penalty`.
  - Mức tiền và cách tính đọc từ `ExtraFee`; Flow 2.a không tự định nghĩa mức phí.
- **Hệ thống:**
  - Gom hóa đơn phạt quá hạn của Flow 6 và các hóa đơn `Unpaid` còn tồn đọng của hợp đồng để đối trừ; Flow 2.a không tạo các khoản này.
  - Nếu cọc lớn hơn tổng phí, ghi nhận phần chênh lệch phải hoàn cho khách; MVP để FM xử lý thủ công.
  - Nếu cọc nhỏ hơn tổng phí, tạo hóa đơn phần còn thiếu. Khách phải thanh toán trước khi hoàn tất trả kho.

**Schema có trong phần này:**
- [**Invoice**](./db-table-draft.md#invoice)
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)

#### 2.a.4 Bảo trì và mở lại cho thuê

**Context:** `CheckoutRecord` đã được xác nhận, khoang không còn khoản phải thu bắt buộc và có thể chuyển sang giai đoạn bảo trì trước khi cho thuê lại.

**Flow tổng quát:** Chuyển khoang sang `Maintenance` -> kết thúc hợp đồng và yêu cầu trả kho -> hết thời hạn bảo trì -> khoang về `Available`.

**Details:**
- **Hệ thống:**
  - Sau khi `CheckoutRecord` được xác nhận và không còn khoản phải thu bắt buộc, cập nhật `StorageUnit.status -> Maintenance` kèm `maintenance_started_at`.
  - Cập nhật `RentalContract.status -> Ended`.
  - Cập nhật `ReturnRequest.status -> Completed` kèm `completed_at`. Flow 3 định nghĩa giá trị này do Flow 2.a set; nếu không đóng, yêu cầu kẹt ở `Assigned` và Flow 3 vĩnh viễn ẩn nút [Gia hạn]/[Trả kho].
  - Giữ `RentalOrder.status = Done`. Đơn đã kết thúc từ lúc bàn giao ở 2.4; việc trả kho thể hiện qua `RentalContract.Ended`, không thêm trạng thái mới vào enum Flow 1.
  - `Appointment.status` đã được set `Done` ở 2.a.2 khi ghi nhận khách đến, không set lại ở đây.
  - Hết `unit.maintenance_days` do BOM cấu hình ở Flow 4, chuyển khoang về `Available` để Flow 1 có thể gán cho yêu cầu mới.
  - Chỉ áp dụng cron mở lại cho khoang có `maintenance_started_at` khác null, tức `Maintenance` phát sinh từ luồng trả kho.
- **FM:**
  - Nếu khoang hư hỏng cần sửa lâu hơn, FM chuyển `Maintenance` thủ công ở Flow 5.2 và không set `maintenance_started_at`.
  - FM tự chuyển khoang về `Available` khi sửa xong; cron không xử lý khoang này.

**Schema có trong phần này:**
- [**StorageUnit**](./db-table-draft.md#storageunit)
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**ReturnRequest**](./db-table-draft.md#returnrequest)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord)

**NOTES**
- Flow 3 bản hiện tại đã dùng `RentalContract.status = Ended` giống schema chung (`Draft/Signed/Active/Ended/Canceled`); giá trị `Completed` trong bản cũ của Flow 3 không còn. Flow 2.a viết theo `Ended` và bổ sung nhánh **có** phát sinh phí so với nhánh thuận Flow 3 mô tả.
- Toàn bộ mức phí trong Flow 2.a phụ thuộc cấu hình của Flow 4. Nếu Flow 4 chưa chốt danh mục phí thì phần này chỉ dừng ở mô tả nghiệp vụ, chưa code được.
- **Flow 2.a sở hữu cron mở lại khoang sau bảo trì** (`Maintenance -> Available`, mô tả ở 2.a.4). Schema Flow 3 đã ghi rõ việc chuyển/mở lại `StorageUnit` do Flow 2.a thực hiện; Flow 5 chỉ giữ thao tác chuyển `Maintenance` **thủ công** của FM cho các sự cố ngoài luồng trả kho, không đụng cron này.
- **Prefix `code` của `Invoice` đang lệch giữa Flow 1 và Flow 3, cần nhóm chốt.** Flow 1 dùng bảng `Service Code` riêng (`DEP/RNT/CLN/DMG/EXT`, trong đó `EXT` = dịch vụ phát sinh); Flow 3 suy prefix thẳng từ `type` (`DEP/RNT/EXT/PEN/SVC`, trong đó `EXT` = gia hạn). Cùng một mã `EXT` đang mang hai nghĩa. Flow 2.a viết theo bảng của Flow 1 (`CLN`/`DMG`) vì đó là bản schema `Invoice` đang được dùng làm chuẩn.
- Trường hợp khách quá hạn không trả, không liên lạc được, hoặc bỏ lại tài sản quá thời hạn dọn: thuộc Flow 6.

**Advanced Features (not MVP)**
- Luồng hoàn tiền tự động qua cổng thanh toán, kèm đối soát mã giao dịch hoàn.
- Cho khách ghi ý kiến phản đối đánh giá hư hỏng vào biên bản và chuyển FM xử lý trước khi xuất hóa đơn.
- Hệ thống tự động tính và xuất hóa đơn phí lưu giữ khi khách chưa dọn hết đồ.
- FM hủy hộ yêu cầu trả kho khi khách đổi ý.
- Cho khách tự chụp ảnh hiện trạng khoang qua app trước buổi hẹn để rút ngắn thời gian kiểm tra.
- Tự động ước tính phí hư hỏng dựa trên danh mục thiệt hại có sẵn thay vì FS nhập tay.
- Cho phép khách trả kho sớm và được hoàn lại phần tiền thuê chưa sử dụng.
- Lịch bảo trì định kỳ cho khoang chứa, tách khỏi bảo trì sau khi trả kho.

### 3. Quản lý kho đã thuê (Customer)
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là MAINTENANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố

## Scheduled Jobs - Cron jobs
### Jobs định kỳ
- `RentalRequest` quá `expires_at` mà vẫn `status = Pending` hoặc `Approved` → set `status = Expired`.
- `ProposalFeedback` quá `expires_at` mà khách chưa duyệt → set `status = Expired`.
- `Appointment` (type = `CHECKIN`) đã quá `end_at` nhưng `arrived_at` vẫn null:
  - `Appointment.status = Canceled`, `cancel_reason = NoShow`.
  - `HandoverRecord.result = CANCELED`.
  - `HandoverRecord.reject_reason` = "Khách không đến nhận kho theo lịch hẹn."
  - Nếu `now < RentalOrder.expires_at`:
    - `RentalOrder.status` quay về `Deposited`.
    - `StorageUnit` tiếp tục giữ `Reserved`.
    - Gửi notification `APPOINTMENT_CANCELED_NO_SHOW` cho khách (title “Lịch hẹn check-in đã bị hủy do bạn không đến”, body có thời gian hẹn cũ và link đặt lịch mới) và cho phép khách đặt lịch mới.
  - Nếu `now >= RentalOrder.expires_at`:
    - `RentalOrder.status = Expired`.
    - `StorageUnit.status` chuyển từ `Reserved` về `Available`.
    - Xử lý mất cọc theo policy.
- `HandoverRecord` (`result = IN_PROGRESS`, `arrived_at` đã có) quá `due_at`:
  - `HandoverRecord.result = CANCELED`, `reject_reason` = "Chưa hoàn tất bàn giao trong hạn".
  - `RentalContract` (`Draft`/`Signed`) chuyển `Canceled`.
  - Đơn, khoang và cọc xử lý như nhánh no-show ở trên.
- `Invoice` (`status = Unpaid`) quá `due_date`:
  - `type = Deposit`: invoice `Expired`, `RentalOrder` tương ứng `Expired`.
  - `type = Rental` + `RentalContract.status = Signed` (chưa bàn giao):
    - contract `Canceled`, `HandoverRecord = CANCELED` lý do "Quá hạn thanh toán tháng đầu".
    - Đơn về `Deposited` hoặc `Expired` theo `RentalOrder.expires_at`; cọc theo policy.
    - Invoice `Expired`.
  - `type = Rental` + `RentalContract.status = Active` (đang thuê):
    - Tiền thuê định kỳ quá hạn → xử lý theo Flow 6. Ngoài scope hiện tại, đánh dấu chờ.
