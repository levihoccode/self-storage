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
  - **Nếu khách đã có tài khoản:** hệ thống sẽ gửi thông báo vào tài khoản.
    - Khách sẽ thao tác tiếp ở [`Kho của tôi`](#13-kiểm-tra-kho-của-tôi) trước khi sang bước đặt cọc

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
    - Không hủy đơn — hệ thống thông báo FM để đề xuất lại khoang khác (re-propose theo luồng 1.3); khách nhận email:
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
      - Hệ thống gửi thông báo đến FM để đề xuất khoang khác (re-propose).
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
    - Hệ thống gửi thông báo đến FM:
      ```
      Khách đã từ chối khoang [Mã khoang cũ], lý do: [note]
      ```
    - FM vào xem khoang trống khác, chọn `unit_id` mới và bấm "Đề xuất lại". Hệ thống loại các khoang mà khách đã từ chối trong cùng `RentalOrder`.
    - Nếu FM cần đề xuất lại một khoang đã bị khách từ chối, phải dùng quyền override và nhập lý do; thao tác này được ghi vào `AuditLog`.
    - Hệ thống tạo một bản ghi `ProposalFeedback` (status = `Pending`), gắn unit_id mới vừa chọn
  - Quá `proposal.max_rejection_count` lần từ chối: hệ thống dừng đề xuất, `RentalOrder` → `Canceled` (khách không chọn được khoang), thông báo FM + khách.

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
    - Hệ thống thông báo FM để đề xuất lại khoang khác (re-propose)
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang bảo trì (status = `Maintenance`):**
    - `RentalOrder` giữ nguyên (không hủy đơn)
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa hiện không khả dụng. Vui lòng liên hệ cơ sở để được hỗ trợ.
      ```
    - Hệ thống cập nhật `Invoice.status` = `Canceled`
    - Thông báo FM đề xuất khoang khác.
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
        - Hệ thống điều hướng khách hàng sang màn hình chọn lịch hẹn check-in và bàn giao kho (Mục 1.5).

**Schema có trong phần này:**
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)
- [**Invoice**](./db-table-draft.md#invoice)
- [**RentalOrder**](./db-table-draft.md#rentalorder)

**Advanced Features:**
- Nếu khoang chỉ bảo trì tạm thời, cho phép giữ Invoice = Unpaid và khách thanh toán lại khi khoang trở về Available.

**NOTES:**
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
  - Hệ thống gửi email/thông báo xác nhận lịch hẹn kèm địa chỉ cơ sở và hướng dẫn mang theo giấy tờ tùy thân (CCCD/Passport).
- **FM:**
  - FM nhận thông báo và xem danh sách các đơn đang ở trạng thái `Scheduled`.
  - FM chỉ định một nhân viên cơ sở (`FS`) phụ trách ca tiếp đón khách:
    - Hệ thống gán `Appointment.staff_id = [FS_Account_ID]`.
    - Hệ thống cập nhật trạng thái `RentalOrder.status` sang `InProgress`.
     - Thông báo nhiệm vụ tiếp đón được gửi đến tài khoản của nhân viên FS tương ứng.
   - Nếu chưa có FS phù hợp, `Appointment.staff_id` để trống, `RentalOrder` giữ `Scheduled`, và FM nhận task phân công. Không tự động gán FS.

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

- **Flow 4:**
  - Bảng giá thuê.
  - Mẫu và version điều khoản hợp đồng.
  - Chính sách mốc bắt đầu tính tiền thuê.
  - Danh mục phí.

#### Các tham số sử dụng

| Tên | Giá trị |
|---|---:|
| `handover.payment_grace_hours` | chờ BOM |
| `handover.max_rejection_count` | 2 lần |
| `handover.due_days` | chờ BOM (gợi ý 1–2 ngày) |
| `contract.start_date_rule` | chờ BOM |
| `contract.prepaid_months` | 1 tháng |

`handover.max_rejection_count` đếm số lần khách **từ chối khoang tại chỗ** trên một đơn, tách khỏi `proposal.max_rejection_count` của Flow 1 (đếm lần từ chối proposal trước khi cọc).

**Vị trí trong vòng đời thuê kho:** Flow 1 giữ trọn vòng đời đặt khoang `RentalRequest -> ProposalFeedback -> Deposit -> Appointment`. Flow 2 bắt đầu khi đơn đã có `Appointment(type = CHECKIN, status = Pending)` và `HandoverRecord` (`result = IN_PROGRESS`) do Flow 1.5 tạo sẵn, và FS đã được phân công - theo Flow 1.5 thì lúc này `RentalOrder.status = InProgress`, khoang `Reserved`, `Invoice` cọc đã `Paid`, chỉ chịu trách nhiệm phần on-site: check-in -> kiểm tra khoang -> ký hợp đồng -> thanh toán tháng đầu -> bàn giao. Flow 2 kết thúc khi `HandoverRecord.result` chuyển `COMPLETED` (khoang `Rented`, hợp đồng có hiệu lực, bàn giao sang Flow 3) hoặc `REJECTED` (bắn event về Flow 1 để FM đề xuất khoang khác, khách duyệt `ProposalFeedback` mới và Flow 1 tạo lịch hẹn mới; Flow 2 chạy lại trên `HandoverRecord` mới). Toàn bộ là thao tác on-site.

**ĐÃ CHỐT:**
- **Vòng đời `Appointment` thuộc Flow 1**: chọn slot, đặt lịch, hủy lịch và tạo lịch mới sau `HandoverRecord.Rejected`/no-show. Flow 2 chỉ đọc lịch đã có và ghi đúng một field `arrived_at` (kèm `status = Done`) khi khách đến cơ sở.
- **Không có dời lịch (reschedule) trong MVP** - Flow 1 đặt `appointment.checkin_reschedule_enabled = false` và ràng buộc mỗi `RentalOrder` chỉ có tối đa một `Appointment(CHECKIN)` đang hoạt động. Sau reject hoặc no-show thì lịch cũ phải `Canceled` rồi mới tạo lịch mới, không phải dời lịch cũ.
- **`HandoverRecord` cũng do Flow 1.5 tạo**, cùng lúc với `Appointment` khi khách xác nhận lịch hẹn (Flow 1 commit `0585bfb`). `result = IN_PROGRESS` lúc khởi tạo nghĩa là hồ sơ đang mở, **không** đồng nghĩa khách đã đến. Flow 2 không tạo bản ghi mới, chỉ bật cờ và chốt `result` trên bản ghi có sẵn.
- **Contract dùng chung với Flow 1** (chốt 20/09): `Appointment` là nguồn duy nhất cho lịch hẹn và FS phụ trách (`date`, `staff_id`, `facility_id`); `RentalAppointment` giữ để nối lịch hẹn với `RentalOrder`; `HandoverRecord` **không có `staff_id`**. Enum đối chiếu: `StorageUnit` `Available/Reserved/Rented/Maintenance`, `RentalOrder` `Pending/Deposited/Scheduled/InProgress/Done/Canceled/Expired`.
- **Mô hình lịch hẹn theo A6/B5:** `Appointment` + bảng nối `RentalAppointment` (`order_id` - `appointment_id`), thêm `facility_id` trên `Appointment` để FM lọc lịch theo cơ sở và validate FS mà không phải join `order -> unit -> facility`; lịch hẹn không gắn đơn (xử lý sự cố) vẫn xác định được cơ sở. `RentalOrder` bỏ `staff_id`/`appointment_date`.
- MVP thu **tháng đầu tiên**; chính sách trả trước N tháng để mở sau (Flow 4).
- MVP chỉ xử lý trường hợp **khách đã đặt cọc trước ngày hẹn** - buổi hẹn là check-in + bàn giao. Lịch "tham quan kho" cho khách chưa cọc (nhiều khách chung một slot) **không thuộc MVP**, xem Advanced Features.
- Thanh toán đi qua cổng **VNPay**, **một phương thức duy nhất** cho MVP: hệ thống redirect sang cổng khi cần thanh toán hóa đơn. Không thu tiền mặt.
- `ProposalFeedback` là bảng của **Flow 1** (khách duyệt online khoang FM chỉ định, trước khi chọn lịch hẹn, status `Pending/Agreed/Rejected`). Flow 2 chỉ đọc; phản hồi hiện trạng khoang lúc check-in ghi trong `HandoverRecord`.
- **Ký hợp đồng offline cho MVP:** FS đánh dấu khách đã ký trên ứng dụng, hợp đồng giấy được chụp/scan và upload; `RentalContract.signature` lưu URL ảnh, `pdf_url` lưu bản scan. Hệ thống **không sinh PDF tự động** trong MVP; panel ký tay trên web để sau.
- **Hỗ trợ cả hai loại khóa**: khóa cơ (`access_type = PhysicalKey`) và khóa mã số (`access_type = AccessCode`). Mỗi cơ sở bật tắt từng loại bằng hai cờ **`enabledKeyAccess`** và **`enabledCodeAccess`** trên bảng `Facility` của **Flow 5** (đã có trong schema Flow 5 từ 23/09). Cấu hình theo cơ sở chứ không theo từng khoang.
- **Audit (contract MVP):** dùng chung bảng `AuditLog` của Flow 1, **không tạo bảng riêng**. Mọi thao tác đổi trạng thái, quyền sở hữu hoặc tiền trong Flow 2/2.5 phải ghi một bản ghi cho **mỗi entity** bị thay đổi; `entity_id` lưu dạng `String/Text`. Không ghi lượt đọc, click, secret hay raw payload thanh toán. Flow 2 chỉ bổ sung action của mình vào catalog chung.
- **Thông báo (contract MVP):** gửi email cho các sự kiện cần thông báo, đồng thời tạo thông báo để khách xem trên website - thông báo web là kênh chính để không mất thông tin khi email lỗi. **Lỗi gửi email không được rollback** thay đổi nghiệp vụ đã thành công. Flow 2 **không bắt buộc** `OutboxEvent` trong MVP; outbox, retry và bảo đảm giao email là quyết định kỹ thuật lúc code.
- **Ngưỡng vận hành mặc định** (BOM cấu hình sau ở Flow 4): `handover.max_rejection_count` và `handover.payment_grace_hours` là hai ngưỡng duy nhất do Flow 2 kiểm tra. Các ngưỡng quanh lịch hẹn thuộc Flow 1 và lấy theo bảng tham số của Flow 1 (`appointment.booking_window_days` 7, `appointment.max_days_after_deposit` 14, `appointment.daily_slot_count` 3, `order.deposit_expiry_days` 30, `appointment.checkin_reschedule_enabled` false); các ngưỡng Flow 2 từng đề xuất (auto-cancel 7 ngày, 2 lần no-show, dời lịch 2 lần báo trước 24h) bỏ để tránh hai nguồn.

**Context:** Khách đã đặt cọc giữ khoang, đến cơ sở để check-in, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu và nhận quyền truy cập.

#### 2.1 Tiếp nhận lịch hẹn check-in

**Context:** Sau khi khách đã đặt cọc, Flow 1 tạo lịch hẹn `CHECKIN` và `HandoverRecord`. Flow 2 tiếp nhận lịch đã được phân công để thực hiện phần check-in và bàn giao tại cơ sở.

**Flow tổng quát:** Flow 2 đọc lịch hẹn do Flow 1 tạo -> kiểm tra điều kiện vào flow -> FS tiếp nhận khách theo lịch được phân công.

**Details:**
- **FM:**
  - FM xem danh sách `Appointment` của cơ sở theo ngày.
  - Hệ thống hiển thị các lịch được lọc trực tiếp dựa trên `Appointment.facility_id`. -> FM chỉ thấy lịch của cơ sở mình đảm nhận
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
- FS đang đăng nhập phải khớp Appointment.staff_id; sai thì chặn thao tác.
- Flow 2 không tạo hoặc đặt lại `Appointment`.
- Flow 1 sở hữu việc sinh slot, đặt lịch, hủy và đặt lại lịch.
- Nghiệp vụ phân công FS thuộc Flow 5.3.

#### 2.2 Check-in và kiểm tra khoang chứa

**Context:** Khách đã đến cơ sở theo lịch `CHECKIN`. FS cần xác minh danh tính và cùng khách kiểm tra hiện trạng khoang trên `HandoverRecord` do Flow 1.5 tạo.

**Flow tổng quát:** FS xác minh danh tính -> kiểm tra hiện trạng khoang -> khách đồng ý hoặc từ chối -> hệ thống tiếp tục bàn giao hoặc chuyển thông tin về Flow 1.

**Details:**
- FS phụ trách được xác định qua `Appointment.staff_id`.
- **FS:**
  - FS mở `HandoverRecord` gắn với `Appointment` đó; Flow 1.5 đã tạo record với `result = IN_PROGRESS`.
  - FS đối chiếu giấy tờ người đến với thông tin Account của `RentalOrder.customer_id`.
  - Nếu khách đã upload ảnh giấy tờ online, FS đối chiếu với ảnh hiển thị trên hệ thống.
  - Nếu xác minh **đạt**, hệ thống bật `is_identity_verified = true`, `identity_verified_at`.
  - Nếu xác minh **không đạt**, FS dừng quy trình, không bật các cờ tiếp theo và mời khách ra về; **không hủy lịch/đơn** ở bước này.
    - MVP chỉ chấp nhận đúng người trên đơn (RentalOrder.customer_id); không xử lý người nhận thay.
    - `HandoverRecord` giữ `IN_PROGRESS`; quá `due_at` thì cron của Flow 2 xử lý như no-show ([2.3](#23-ký-hợp-đồng-và-thanh-toán-tháng-đầu-tiên) / [Cron jobs](#scheduled-jobs---cron-jobs)).
  - FS dẫn khách kiểm tra toàn bộ hiện trạng: kích thước, vị trí, vệ sinh, kết cấu, cửa/khóa và hư hại sẵn có.
  - FS nhập `HandoverRecord.inspection_notes` và `HandoverRecord.inspection_photos`.
- **Customer:**
  - Khách vào trang **Kho của tôi**.
  - Khách chọn khoang đang bàn giao (`StorageUnit.status = Reserved`).
  - Ấn vào nút "Bàn giao" để chuyển hướng qua trang để thao tác bàn giao.
  - Khách xác nhận hiện trạng khoang sau khi kiểm tra.
  - Khách chọn một trong 3 thao tác:
    - **Đồng ý:** 
      - Hệ thống cập nhật `HandoverRecord.is_unit_inspected = true`, `HandoverRecord.unit_inspected_at`
      - Khách có thể nhập những thứ cơ sở cần lưu ý (`HandoverRecord.inspection_notes`, vd khoang chưa sạch). -> không bắt buộc
    - **Không đồng ý:** 
      - Hệ thống giữ nguyên `is_unit_inspected = false` (không ghi `unit_inspected_at`)
      - Khách nhập lý do (`HandoverRecord.reject_reason`).
    - **Yêu cầu hủy đơn**:
      - Hệ thống yêu cầu khách xác nhận việc hủy và thông báo hậu quả theo chính sách.
      - Khách xác nhận:
        - Thực hiện [RentalOrder Cancellation Cascade](./db-table-draft.md#rentalorder-cancellation-cascade).
        - Appointment đã `Done` nên giữ nguyên.
        - `HandoverRecord` hiện tại chuyển `IN_PROGRESS -> CANCELED`.
        - Flow 2 kết thúc.
      - Khách không xác nhận:
        - Không ghi gì.
        - Giữ nguyên `HandoverRecord` để khách tiếp tục kiểm tra hoặc chọn từ chối khoang.
        - Nếu quá `due_at`, cron của Flow 2 đóng biên bản và xử lý như no-show (xem [2.3](#23-ký-hợp-đồng-và-thanh-toán-tháng-đầu-tiên) và [Cron jobs](#scheduled-jobs---cron-jobs)).
  - Nếu khách chọn **từ chối khoang này**:
    - Trước khi ghi nhận lần từ chối, hệ thống đếm số `HandoverRecord` cùng `RentalOrder` có `result = REJECTED` (không đếm `CANCELED`, tính trên toàn lịch sử đơn); việc đếm và cập nhật nằm trong cùng transaction.
    - Điều kiện chạm ngưỡng: `count + 1 >= handover.max_rejection_count`.
      - **Chưa chạm ngưỡng** số lần từ chối:
        - Cập nhật `HandoverRecord.result = REJECTED`.
        - Bắn event `HandoverRecord.Rejected` cho Flow 1.
        - Flow 2 kết thúc.
        - Flow 1 thực hiện re-propose theo [Flow 1.3 Kiểm tra kho của tôi](#13-kiểm-tra-kho-của-tôi).
        - Sau khi khách duyệt proposal mới và Flow 1 hoàn tất các bước liên quan, Flow 2 bắt đầu lại trên `Appointment` và `HandoverRecord` mới.
      - **Chạm ngưỡng** số lần từ chối:
        - Hệ thống hiển thị xác nhận cho khách, nêu rõ đơn sẽ bị hủy vì đã từ chối tối đa N khoang và tiền cọc không được hoàn.
        - **Khách xác nhận:**
          - Cập nhật `HandoverRecord.result = REJECTED`.
          - Thực hiện [RentalOrder Cancellation Cascade](./db-table-draft.md#rentalorder-cancellation-cascade).
          - Kết thúc Flow 2
        - **Khách không xác nhận:**
          - Không ghi gì và giữ nguyên hiện trạng.
          - Nếu `HandoverRecord` quá `due_at`, cron của Flow 2 đóng biên bản và xử lý như no-show theo [Cron jobs](#scheduled-jobs---cron-jobs).

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
- **Hệ thống:**
  - Chỉ cho phép tiếp tục khi `is_identity_verified = true` và `is_unit_inspected = true`.
  - Sinh hợp đồng từ mẫu đang hiệu lực của Flow 4, điền thông tin khách, cơ sở, `unit_id` khách vừa xác nhận, giá thuê, `period`, tiền cọc đã đóng, mốc bắt đầu tính tiền thuê và snapshot `terms_version`.
  - Gắn hiện trạng khoang (`inspection_notes`, `inspection_photos`) ở 2.2 với hợp đồng qua `order_id`; đây là căn cứ đối chiếu khi trả kho ở Flow 2.5.
  - Mốc bắt đầu tính tiền thuê luôn được điền sẵn theo chính sách Flow 4 (`contract.start_date_rule`).
- **FS:**
  - Nếu khách ký trên bản giấy, FS chụp/scan và upload hợp đồng.
  - Hệ thống lưu URL vào `signature` và `pdf_url`, cập nhật `RentalContract.status = Signed`, `is_contract_signed`, `contract_signed_at`.
  - FS không tự sửa `start_date`. Nếu cần thỏa thuận riêng, FS nhập ngày đề nghị và lý do vào hợp đồng `Draft`: `start_date_override_requested`, `start_date_override_reason`, `start_date_override_status = Pending`.
- **FM:**
  - FM xem các yêu cầu đổi mốc tính tiền đang chờ của cơ sở mình.
  - Khi duyệt, hệ thống dùng ngày đề nghị cho `start_date`, cập nhật `start_date_override_status = Approved` và mở lại bước ký.
  - Khi từ chối, hệ thống giữ ngày theo chính sách, cập nhật `status = Rejected` và mở lại bước ký. FS có thể gửi đề nghị khác nếu khách vẫn không đồng ý.
  - Việc gửi đề nghị, duyệt và từ chối đều ghi `AuditLog` kèm `old_value`/`new_value`/`reason`.
- **Customer:**
  - Khách thanh toán hóa đơn tháng đầu qua VNPay.
- **Hệ thống:**
  - Khi yêu cầu đổi mốc tính tiền còn `Pending`, chặn bước ký cho tới khi FM xử lý.
  - Tạo `Invoice(type = Rental)`, prefix `RNT`, gắn `contract_id`, với số tiền tháng đầu. Tiền cọc ở Flow 1.4 không trừ vào hóa đơn này và được giữ riêng tới khi trả kho ở 2.5.3.
  - Khi gateway xác nhận thành công, cập nhật `PaymentTransaction = Success`, `Invoice.status = Paid`, `is_payment_settled`, `payment_settled_at`.
  - Khi thanh toán thất bại, cập nhật `PaymentTransaction = Failed`; hóa đơn giữ nguyên chưa thanh toán và không tiếp tục bàn giao.
  - Nếu chưa thanh toán xong trong buổi hẹn, giữ `HandoverRecord.result = IN_PROGRESS`, giữ khoang `Reserved`, chưa bàn giao khóa; hóa đơn nằm trong mục "Hóa đơn" của khách.
  - Khách có `payment_grace_hours` giờ để thanh toán. Quá hạn, [cron](#scheduled-jobs---cron-jobs) xử lý giống nhánh no-show của Flow 1:
    - `RentalContract.status -> Canceled`, `HandoverRecord.result -> CANCELED` với lý do "Quá hạn thanh toán tháng đầu".
    - Nếu `now < RentalOrder.expires_at`, đơn quay về `Deposited`, giữ cọc, khoang vẫn `Reserved`, khách đặt lịch check-in mới ở Flow 1 và làm lại từ 2.2.
    - Nếu `now >= RentalOrder.expires_at`, đơn `Expired`, khoang về `Available`, xử lý mất cọc theo chính sách Flow 4.
    - Đây là cron duy nhất của Flow 2, chạy hằng ngày; 2.1 không còn cron auto-cancel.

**Schema có trong phần này:**
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**Invoice**](./db-table-draft.md#invoice)
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)

**NOTES:**
- Mốc bắt đầu tính tiền thuê mặc định theo chính sách Flow 4; thỏa thuận riêng phải được FM duyệt.

#### 2.4 Bàn giao khóa và kích hoạt hợp đồng

**Context:** Hợp đồng đã được ký, hóa đơn tháng đầu đã thanh toán và khách đủ điều kiện nhận quyền truy cập khoang.

**Flow tổng quát:** Kiểm tra checklist -> FS bàn giao chìa hoặc mã truy cập -> hệ thống cập nhật trạng thái khoang, hợp đồng, biên bản và đơn hàng.

**Details:**
- **FS:**
  - Chỉ thực hiện bàn giao khi bốn cờ trên `HandoverRecord` đều `true`: `is_identity_verified`, `is_unit_inspected`, `is_contract_signed`, `is_payment_settled`.
  - Với khóa cơ (`enabledKeyAccess`), giao chìa vật lý và ghi `quantity`.
  - Với khóa mã số (`enabledCodeAccess`), hướng dẫn khách đăng nhập để xem mã. Mã chỉ hiển thị một lần trong tài khoản khách, không gửi email, không hiện trên màn hình FS và không trả trong response API bàn giao.
  - Nếu cơ sở bật cả hai loại khóa, FS chọn loại bàn giao; `UnitAccessKey` ghi đúng `access_type`.
  - Hai bên xác nhận và khách ký nhận.
- **Hệ thống:**
  - Nếu thiếu một cờ bắt buộc, API bàn giao trả lỗi cho FS.
  - Với khóa mã số, sinh mã gắn với đơn, lưu `code_hash`, không lưu plain text.
  - Trong một transaction, tạo `UnitAccessKey`, chuyển `StorageUnit: Reserved -> Rented`, `RentalContract: Signed -> Active`, `HandoverRecord.result = COMPLETED` với `completed_at`, và `RentalOrder.status -> Done`.
  - Flow 2 là nơi duy nhất set `RentalOrder.status = Done`.
  - Bắn `RentalOrder.HandoverCompleted` để Flow 3 bắt đầu theo dõi; Flow 3 không tự poll status.
  - Sau transaction, gửi email kèm hợp đồng và biên bản bàn giao, đồng thời tạo thông báo trên website. Lỗi gửi không rollback bàn giao.

**Schema có trong phần này:**
- [**StorageUnit**](./db-table-draft.md#storageunit)
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey)

#### Backend flow (chi tiết kỹ thuật)

**Nguyên tắc chung:**
- API của FS/FM yêu cầu đăng nhập và kiểm tra **facility scope**: chỉ thao tác trên `Appointment`/`StorageUnit` thuộc cơ sở mình phụ trách, nếu không trả `403`.
- API của customer áp dụng **ownership check** trên `RentalOrder.customer_id`.
- Mọi bước đổi `StorageUnit.status` chạy trong DB transaction và lock theo `unit_id` để không xung đột với luồng gán khoang của Flow 1.
- Các API bật cờ trên `HandoverRecord` phải kiểm tra cờ tiền nhiệm, không cho nhảy bước.
- **Thông báo:** mỗi sự kiện cần báo thì gửi email **và** ghi một thông báo hiển thị trên website. Việc gửi email tách khỏi transaction nghiệp vụ - SMTP lỗi thì chỉ log lại, không rollback bước đã thành công. Cơ chế bảo đảm giao (outbox, retry) không bắt buộc trong MVP.
- **Validate phân công:** FS được gán phải có `AccountFacilityAssignment` khớp **`Appointment.facility_id`** - so trực tiếp, không join `RentalAppointment -> RentalOrder -> StorageUnit -> Facility` (A12, thống nhất với Flow 5); sai facility trả `422`. FS đang đăng nhập cũng phải khớp `staff_id` của lịch hẹn mới được thao tác.
- **Thanh toán:** Flow 2 dùng chung schema và nguyên tắc thanh toán của Flow 1, **không tạo mô hình riêng**. Tạo `PaymentTransaction(status = Pending)` trước khi chuyển hướng sang VNPay; kết quả xác nhận từ phía server/gateway, `vnp_ReturnUrl` chỉ để hiển thị. Thành công: `PaymentTransaction = Success` + `Invoice = Paid`. Thất bại: `PaymentTransaction = Failed`, hóa đơn vẫn chưa thanh toán và **không tiếp tục bàn giao**. Lock, IPN và idempotency theo đúng quy tắc của Flow 1, chi tiết kỹ thuật thống nhất khi code.

**a) Lịch hẹn của cơ sở (2.1)**
- `GET /api/fm/appointments?facility_id=...&date=...`: lịch của cơ sở theo ngày, lọc thẳng trên `Appointment.facility_id`, không join qua `RentalOrder -> StorageUnit -> Facility`; dùng để FM thấy lịch chưa có `staff_id`.
- Các API sinh slot, đặt lịch và hủy lịch nằm ở **Flow 1**. Flow 2 không expose endpoint nào ghi lên `Appointment` ngoài `arrive` ở mục b.
- Cron hằng ngày của Flow 2: hủy hợp đồng `Signed` quá `payment_grace_hours` chưa thanh toán, trả khoang về `Available`. Việc tự hủy đơn đã cọc mà chưa bàn giao chuyển sang **Flow 1** cùng vòng đời `Appointment`, và Flow 1 dùng field `RentalOrder.expires_at` (set lúc cọc, mặc định 30 ngày) chứ không dùng `Policy` key - nên Flow 2 **không còn xin Flow 4** key `order.auto_cancel_days`.

**b) Lịch trình của FS (2.1, 2.2)**
- `GET /api/staff/appointments?date=...`: lịch trong ngày của FS đang đăng nhập.
- `POST /api/staff/appointments/{id}/arrive`: set `arrived_at`, `status = Done` - đây là ghi duy nhất của Flow 2 lên `Appointment`.
- Cron no-show thuộc Flow 1: `Appointment.status = Canceled` + `cancel_reason = NoShow`, `HandoverRecord.result = CANCELED`, và đơn quay về `Deposited` hoặc `Expired` tùy `RentalOrder.expires_at`.

**c) Checklist on-site (2.2)**
- `POST /api/staff/handover-records/{id}/verify-identity`: set `is_identity_verified`, `identity_verified_at`.
- `POST /api/staff/handover-records/{id}/inspection`: body `{ inspection_notes, inspection_photos[] }`, set `is_unit_inspected`, `unit_inspected_at`.
- `POST /api/staff/handover-records/{id}/reject`: body `{ reject_reason }`. Backend đếm số bản ghi `REJECTED` của `order_id` **trong cùng transaction** với bước ghi, rồi rẽ hai nhánh:
  - **Chưa chạm ngưỡng:** set `result = REJECTED`, `completed_at`, bắn `HandoverRecord.Rejected` để quay về luồng re-propose của Flow 1.
  - **Chạm ngưỡng:** chưa ghi gì, trả về trạng thái **yêu cầu khách xác nhận hủy** kèm hệ quả mất cọc. Chỉ khi khách xác nhận qua `POST /api/staff/handover-records/{id}/confirm-cancel` mới ghi `REJECTED` + `RentalOrder -> Canceled` + khoang về `Available` + proposal `Agreed -> Expired`, tất cả trong một transaction. Không bắn `HandoverRecord.Rejected` ở nhánh này.

**d) Ký hợp đồng và thanh toán (2.3)**
- `POST /api/staff/rental-orders/{id}/contracts`: FS sinh `RentalContract(Draft)` tại buổi check-in, snapshot `terms_version`, `unit_id`, giá thuê, mốc bắt đầu tính tiền theo chính sách Flow 4. Chỉ cho phép khi `is_unit_inspected = true`.
- `POST /api/staff/contracts/{id}/start-date-override`: body `{ requested_start_date, reason }`. Chỉ cho phép khi hợp đồng `Draft` và chưa có yêu cầu nào `Pending`. Set ba field override, ghi `AuditLog`.
- `GET /api/fm/contracts/start-date-overrides?status=Pending`: danh sách yêu cầu chờ duyệt của cơ sở FM phụ trách.
- `POST /api/fm/contracts/{id}/start-date-override/approve` và `.../reject`: body `{ note? }`. Duyệt thì ghi đè `start_date`; từ chối thì giữ ngày theo chính sách. Cả hai ghi `AuditLog` kèm giá trị trước/sau.
- `POST /api/staff/contracts/{id}/sign`: FS upload ảnh/scan hợp đồng đã ký, lưu `signature` + `pdf_url`, `status = Signed`, set `is_contract_signed`, và **tạo `Invoice(type = Rental)` tháng đầu ngay trong cùng transaction**. Trả lỗi nếu còn yêu cầu đổi `start_date` đang `Pending`. Event `RentalContract.Signed` chỉ để Flow 4 ghi nhận, **không** phải trigger tạo hóa đơn - tránh hai nơi cùng tạo.
- `POST /api/invoices/{id}/pay`: khởi tạo phiên VNPay, trả URL redirect.
- `POST /api/webhooks/vnpay`: nhận IPN, lưu `PaymentTransaction`, `Invoice.status = Paid`, set `is_payment_settled`, `payment_settled_at`.

**e) Bàn giao (2.4)**
- `POST /api/staff/handover-records/{id}/complete`: body `{ access_type, access_quantity, customer_signature, note? }`. Backend kiểm tra đủ 4 cờ trước khi ghi.
- Transaction: tạo `UnitAccessKey`, `StorageUnit.status = Rented`, `RentalContract.status = Active`, `HandoverRecord.result = COMPLETED`, cập nhật `RentalOrder`.
- Với khóa mã số: sinh mã ngẫu nhiên, lưu `code_hash`, trả mã đúng một lần qua API của khách (`GET /api/customer/orders/{id}/access-code`, yêu cầu đăng nhập) chứ không trả trong response của FS và không gửi qua email.

**Events phát ra từ Flow 2:**

| Event | Consumer |
|---|---|
| `HandoverRecord.Rejected` | Flow 1 (FM chỉ định lại khoang, tạo `ProposalFeedback` mới và `Appointment` mới). **Chỉ bắn ở nhánh chưa chạm ngưỡng**; nhánh chạm `handover.max_rejection_count` do Flow 2 tự hủy đơn, không handoff về Flow 1 |
| `RentalContract.Signed` | Flow 4 (ghi nhận hợp đồng mới). Hóa đơn tháng đầu tạo trong transaction ký, không tạo từ event này |
| `Invoice.Created` (RNT tháng đầu) | Flow 4 (theo dõi doanh thu) |
| `RentalOrder.HandoverCompleted` | Flow 3 (bắt đầu theo dõi khoang đang thuê) |

**Schema:**

**Flow 1 sở hữu, Flow 2 chỉ đọc hoặc cập nhật** - định nghĩa field do Flow 1 viết, nay đã nằm chung trong `db-table-draft.md` sau khi merge:
- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được xử lý trong buổi hẹn; Flow 2 set `status = Done` ở 2.4.
- [**Invoice**](./db-table-draft.md#invoice) - kiểm tra hóa đơn `type = Deposit` đã `Paid`, tạo hóa đơn `type = Rental` cho tháng đầu.
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback) - chỉ đọc để biết khoang khách đã duyệt.
- [**Appointment**](./db-table-draft.md#appointment) - lịch hẹn và FS phụ trách (`date`, `staff_id`, `facility_id`); Flow 2 chỉ set `arrived_at` + `status = Done`.
- [**RentalAppointment**](./db-table-draft.md#rentalappointment) - nối lịch hẹn với đơn hàng; Flow 1 tạo cùng `Appointment`.
- [**HandoverRecord**](./db-table-draft.md#handoverrecord) - checklist tiến trình on-site; Flow 1 tạo ở 1.5, Flow 2 bật cờ và chốt `result`.

**Flow 2/2.5 sở hữu**, chi tiết field trong `db-table-draft.md`:
- [**RentalContract**](./db-table-draft.md#rentalcontract) - hợp đồng thuê, sinh và ký ở 2.3.
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey) - quyền truy cập khoang chứa đã bàn giao cho khách.

**Phụ thuộc cần các flow khác bổ sung (Flow 2 không tự sửa):**
- **Flow 1 nhận lại toàn bộ vòng đời `Appointment`** (theo đề xuất đã thống nhất): sinh slot theo giờ hoạt động của cơ sở (không giới hạn số khách trên một slot trong MVP), cho khách chọn lịch sau khi cọc `Paid`, hủy lịch, chuyển `RentalOrder`: `Deposited -> Scheduled`, cron no-show, cron tự hủy đơn theo `RentalOrder.expires_at`, và tạo `Appointment` mới sau nhánh `HandoverRecord.Rejected`. Flow 1 cũng là nơi gửi nhắc việc trước buổi hẹn (đối chiếu giấy tờ, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu).
- **Bảng nối `RentalAppointment` giữ theo A6** (Flow 1 `0585bfb`, Flow 5 `db-table-draft.md`). Phần `facility_id` trên `Appointment` đã được Flow 1 và Flow 5 áp. Đề xuất đưa `order_id` thẳng lên `Appointment` và bỏ bảng nối **không được chốt**, Flow 2/2.5 viết theo bảng nối.
- Đổi khoang sau khi khách đã cọc (do từ chối tại chỗ ở 2.2) do **Flow 1** xử lý: FM chỉ định khoang mới, hệ thống tạo `ProposalFeedback` **mới** (bản cũ giữ nguyên, khoang hiệu lực là proposal `Agreed` mới nhất), khách duyệt online. Flow 1 đã chốt (E6): khi đề xuất lại, hệ thống **loại các khoang khách đã từ chối** trong cùng đơn; FM muốn đề xuất lại khoang đã bị từ chối thì phải override kèm lý do và ghi `AuditLog`. Quá `proposal.max_rejection_count` lần thì Flow 1 hủy đơn. Chênh lệch mức cọc cũ/mới cộng phí đổi khoang: dư thì hoàn thủ công, thiếu thì xuất hóa đơn cọc bù. Còn mở: thứ tự chuyển khoang mới sang `Reserved` so với việc hoàn tiền.
- Vì mỗi lần đề xuất lại tạo một `ProposalFeedback` mới, **không** đặt unique index `(order_id) WHERE status = 'Agreed'` - index đó sẽ chặn đúng reject path của Flow 2.
- ~~Flow 5 bổ sung `enabledKeyAccess` và `enabledCodeAccess`~~ - **đã xong**: Flow 5 thêm hai cờ vào bảng `Facility` ngày 23/09, mục 2.4 đọc theo cơ sở.
- `RentalOrder.status` theo contract Flow 1 (20/09): `Pending/Deposited/Scheduled/InProgress/Done/Canceled/Expired` - khác đề xuất B3 ở chỗ giữ `Pending` thay cho `AwaitingDeposit`. Flow 2 bám theo bộ này: vào flow ở `InProgress`, kết ở `Done`.

**Advanced Features (not MVP)**
- Chính sách trả trước N tháng thay vì cố định 1 tháng (`prepaid_months` > 1).
- Outbox, retry tự động và bảo đảm giao email cho thông báo.
- Ký hợp đồng online: panel ký tay trên web, hệ thống tự sinh PDF thay vì FS upload bản scan.
- Các ý tưởng quanh lịch hẹn chuyển sang Flow 1 cùng vòng đời `Appointment`: dời lịch (reschedule), lịch "tham quan kho" cho khách chưa cọc (`type = TOUR`, nhiều khách chung một slot), giới hạn số khách trên slot theo số FS khả dụng, cho khách chọn slot theo lịch trống thực tế của từng FS. Riêng việc **nhắc việc trước buổi hẹn** thì Flow 1 đã làm trong MVP (gửi kèm lúc xác nhận lịch); cái nằm ngoài MVP là nhắc **tự động trước 24h** bằng job riêng.
- Cho khách xem ảnh/video khoang chứa trước buổi hẹn để giảm tỉ lệ từ chối tại chỗ.
- eKYC khi đăng ký tài khoản, bước xác minh on-site rút gọn còn đối chiếu nhanh.
- Khóa thông minh điều khiển qua app, bỏ hẳn bước giao chìa khóa vật lý.

### 2.5 Trả kho và bảo trì

**FLOW:**
```
[Yêu cầu trả kho] -> [Hẹn lịch trả] -> [FS kiểm tra khoang] -> [Xử lý phí phát sinh] -> [Thu hồi quyền truy cập] -> [Hoàn cọc] -> [Bảo trì] -> [Khoang về Available]
```

#### Các tham số sử dụng trong Flow 2.5

| Tên | Giá trị |
|---|---:|
| `unit.maintenance_days` | 1-3 ngày |

Mức phí phát sinh lúc trả kho **không phải tham số `Policy`** mà là bản ghi trong bảng `ExtraFee` của Flow 4, tra theo `category`: `CLEANING`, `DAMAGE`, `LOST-KEY`. Mỗi bản ghi có `amount` và `calculation_type` (`Fixed`/`Daily`/`Monthly`/`Percent`) riêng, Flow 2.5 chỉ đọc chứ không tự định nghĩa mức.

**Phí trả kho trễ không thuộc Flow 2.5.** Flow 4 chốt `overdue.fee_per_day` và Flow 6 là **nơi duy nhất** tính phí quá hạn, tính từ `end_date` của hợp đồng. Flow 2.5 chỉ gom các hóa đơn phạt đã có vào bước đối trừ cọc.

**Vị trí trong vòng đời thuê kho:** Flow 2.5 nhận đầu vào từ Flow 3.4 (khách bấm yêu cầu trả kho) và xử lý toàn bộ phần on-site. Kết thúc khi khoang hoàn tất bảo trì và quay về `Available`, sẵn sàng cho yêu cầu mới ở Flow 1. Đây là điểm đóng vòng đời của một `RentalOrder`.

**ĐÃ CHỐT:**
- `Appointment.type` thêm giá trị **`RETURN`** cho buổi hẹn trả kho, `RentalAppointment` cũng được tạo cho loại này. Levi sẽ chốt lại bộ `type` sau khi các flow ổn định.
- Việc dời vòng đời `Appointment` sang Flow 1 chỉ áp cho **lịch check-in** (thuộc booking lifecycle). Lịch `RETURN` vẫn do Flow 2.5 tạo từ `ReturnRequest` của Flow 3, vì nó không nằm trong vòng đời đặt khoang.
- MVP chỉ hỗ trợ **luồng trả do khách chủ động yêu cầu**; FM hủy hộ yêu cầu trả kho không thuộc MVP.
- Biên bản trả kho tách thành bảng **`CheckoutRecord`** riêng, vì `HandoverRecord` chỉ chịu trách nhiệm tới khâu bàn giao và kết thúc vòng đời sau đó.
- Phí phát sinh khi trả kho (hư hỏng, vệ sinh, mất chìa) dùng **`Invoice.type = Penalty`** theo bộ `type` của schema chung `Deposit/Rental/Extension/Penalty/Service`; **tiền tố trong `code` lấy theo bảng Service Code của Flow 1**: `CLN` cho phí dọn dẹp, `DMG` cho phí hư hại. Hai thứ này ở hai tầng khác nhau - `type` phân loại hóa đơn, prefix chỉ nằm trong `code` - nên không xung đột. Khoản chưa có mã riêng (mất chìa, trả trễ) ghi phân loại ở `title`/`desc`.
- Khi phát sinh phí, **FS hoặc FM tạo hóa đơn trong hệ thống**, khách thanh toán qua VNPay. Không thu tiền mặt trong MVP.
- Tiền cọc **được hoàn lại cho khách** sau khi đối trừ hết các khoản phát sinh, không trừ vào kỳ thuê cuối.
- Cơ chế khách **phản đối đánh giá hư hỏng** của FS không thuộc MVP; đánh giá của FS là kết quả cuối cùng.
- Hệ thống **tự động tính phí lưu giữ** khi khoang còn đồ không thuộc MVP; FM hoặc FS gửi hóa đơn thủ công theo chính sách của BOM (Flow 4).
- **Luồng hoàn tiền tự động (refund) không thuộc MVP.** Khi làm sẽ cần bổ sung chiều giao dịch cho `PaymentTransaction` hoặc bảng refund riêng, kèm mã giao dịch hoàn để đối soát. Nguyên tắc dự kiến: hoàn về đúng phương tiện khách đã thanh toán.
- Enum `StorageUnit.status`: `Available/Reserved/Rented/Maintenance` theo contract Flow 1 công bố ngày 20/09, khớp cả Flow 3. **`OnHold` không nằm trong contract** (A9 đã chốt MVP không giữ chỗ). Flow 5 đã gỡ giá trị này khỏi schema của họ, ba nhánh Flow 1/3/5 giờ khớp nhau.
- Thời gian bảo trì **được cấu hình bởi BOM ở Flow 4**, không hard-code (mặc định 1-3 ngày). FM không tự sửa, chỉ gửi yêu cầu để BOM chỉnh.
- Khách quá hạn không trả, không liên lạc được hoặc bỏ lại tài sản trong khoang: **thuộc Flow 6**, đã note để xử lý sau.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 3: bản ghi `ReturnRequest` (`status = Assigned`, đã có `assigned_staff_id` do FM phân công ở Flow 3.4).
- Flow 2: `HandoverRecord` của đơn (`inspection_notes`, `inspection_photos`) để đối chiếu hiện trạng lúc nhận và lúc trả; `UnitAccessKey` để thu hồi quyền truy cập.
- Flow 4: bảng `ExtraFee` (`CLEANING`, `DAMAGE`, `LOST-KEY`) cho mức phí phát sinh; `Policy.unit.maintenance_days` cho thời gian bảo trì; chính sách xử lý tiền cọc. Phí quá hạn (`overdue.fee_per_day`) thuộc Flow 6, Flow 2.5 không đọc.

**Context:** Khách kết thúc nhu cầu thuê và muốn trả lại khoang chứa, cần có người kiểm tra hiện trạng, xử lý các khoản phát sinh và thu hồi quyền truy cập trước khi khoang được cho thuê lại.

#### 2.5.1 Tiếp nhận yêu cầu và hẹn lịch trả kho

**Context:** Khách đã gửi yêu cầu trả kho. `ReturnRequest` được Flow 3 chuyển sang `Assigned` và đã có FS do FM phân công.

**Flow tổng quát:** Hệ thống tạo lịch `RETURN` -> khách chuẩn bị khoang trước ngày hẹn -> FS tiếp nhận buổi trả kho.

**Details:**
- **Hệ thống:**
  - Khi `ReturnRequest` chuyển `Assigned` ở Flow 3.4, tạo `Appointment(type = RETURN, status = Pending)` kèm `RentalAppointment`.
  - Gán `facility_id` và `staff_id` từ `ReturnRequest.assigned_staff_id`.
  - Lịch `RETURN` dùng chung ba khung giờ cố định với `CHECKIN` (`appointment.daily_slot_count`) vì cùng FS phục vụ.
  - Map `preferred_date` của khách vào khung còn trống trong ngày đó. Nếu hết khung, đẩy sang ngày gần nhất và báo khách.
  - Việc phân công FS đã do FM làm ở Flow 3; Flow 2.5 không lặp lại.
- **Customer:**
  - Trước ngày hẹn, khách tự dọn toàn bộ tài sản ra khỏi khoang.
  - Hệ thống nhắc điều kiện để được nhận lại cọc: khoang trống, không hư hỏng và không còn hóa đơn `Unpaid`.

**Schema có trong phần này:**
- [**ReturnRequest**](./db-table-draft.md#returnrequest)
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalAppointment**](./db-table-draft.md#rentalappointment)

#### 2.5.2 Kiểm tra và bàn giao lại khoang chứa

**Context:** Khách đến theo lịch `RETURN`. FS đối chiếu hiện trạng khoang lúc trả với hiện trạng đã ghi trong `HandoverRecord` của Flow 2.

**Flow tổng quát:** FS ghi nhận khách đến -> đối chiếu hiện trạng -> lập `CheckoutRecord` -> xử lý nhánh khoang trống hoặc còn tài sản -> thu hồi quyền truy cập khi đủ điều kiện.

**Details:**
- **FS:**
  - Ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - Mở `HandoverRecord` đã lập ở Flow 2 để lấy `inspection_notes`, `inspection_photos`; Flow 2.5 chỉ đọc bảng này.
  - Kiểm tra hiện trạng lúc trả: khoang đã dọn trống chưa, tình trạng vệ sinh, hư hỏng kết cấu/cửa/khóa/thiết bị và chụp ảnh hiện trạng.
  - Dùng chênh lệch giữa hai mốc làm căn cứ tính phí hư hỏng/vệ sinh ở 2.5.3.
  - Lập `CheckoutRecord`, cho khách ký xác nhận. Mỗi cột mốc bật một cờ kèm timestamp để FM/FS theo dõi khi buổi trả kho kéo dài nhiều ngày.
  - Nếu khoang bẩn hoặc hư hỏng, ghi nhận chi tiết kèm ảnh; hệ thống tạo hóa đơn phí tương ứng ở 2.5.3.
- **Customer:**
  - Ký xác nhận biên bản trả kho.
- **Hệ thống:**
  - Nếu khoang đạt yêu cầu, không phát sinh phí và chuyển sang 2.5.3.
  - Nếu còn tài sản, ghi `CheckoutRecord.result = PENDING_ITEMS` và chưa hoàn tất trả kho.
  - Tạo `Appointment(type = RETURN)` mới để khách quay lại dọn nốt và trỏ `CheckoutRecord.appointment_id` sang lịch mới.
  - Khi khách quay lại và FS bắt đầu kiểm tra lần nữa, chuyển `result` trên cùng bản ghi từ `PENDING_ITEMS -> IN_PROGRESS`. Vòng này lặp tới khi khoang trống.
  - Trong thời gian `PENDING_ITEMS`, không thu hồi `UnitAccessKey`; khách vẫn cần quyền truy cập để lấy đồ.
  - Trong thời gian `PENDING_ITEMS`, khoang giữ `Rented`, hợp đồng giữ `Active`. Phí quá hạn do Flow 6 tính theo `overdue.fee_per_day`; Flow 2.5 không tự tính.
  - Thời hạn dọn tiếp và phí lưu giữ theo chính sách BOM ở Flow 4. Hệ thống không tự động tính phí lưu giữ trong MVP; FM hoặc FS gửi hóa đơn thủ công.
  - Chỉ thu hồi quyền truy cập khi `CheckoutRecord.result` không phải `PENDING_ITEMS`.
  - Với khóa cơ, FS thu lại chìa và đối chiếu `UnitAccessKey.quantity`. Thiếu chìa thì tính `fee.lost_key`, set `UnitAccessKey.status = Lost` thay vì `Revoked`, ghi số chìa thu được vào `returned_key_quantity`; khoang phải thay khóa trước khi cho thuê lại.
  - Với khóa mã số, vô hiệu hóa mã ngay khi biên bản được xác nhận.
  - Thu đủ chìa hoặc vô hiệu hóa mã thì `UnitAccessKey.status -> Revoked`, ghi `revoked_at`. Thiếu chìa thì `-> Lost`, cũng ghi `revoked_at`; khoang phải thay khóa trong kỳ bảo trì ở 2.5.4.

**Schema có trong phần này:**
- [**Appointment**](./db-table-draft.md#appointment)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord)
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey)
- [**RentalContract**](./db-table-draft.md#rentalcontract)

#### 2.5.3 Xử lý phí phát sinh và tiền cọc

**Context:** FS đã kiểm tra khoang và xác định các khoản phí phát sinh hoặc các hóa đơn còn tồn đọng cần đối trừ với tiền cọc.

**Flow tổng quát:** Tập hợp phí -> đối trừ với tiền cọc -> hoàn phần dư hoặc yêu cầu khách thanh toán phần thiếu.

**Details:**
- **FS/FM:**
  - Tạo hóa đơn cho phí vệ sinh (`CLEANING`), phí hư hỏng (`DAMAGE`), phí mất chìa hoặc thay khóa (`LOST-KEY`) dưới `Invoice.type = Penalty`.
  - Mức tiền và cách tính đọc từ `ExtraFee`; Flow 2.5 không tự định nghĩa mức phí.
- **Hệ thống:**
  - Gom hóa đơn phạt quá hạn của Flow 6 và các hóa đơn `Unpaid` còn tồn đọng của hợp đồng để đối trừ; Flow 2.5 không tạo các khoản này.
  - Nếu cọc lớn hơn tổng phí, ghi nhận phần chênh lệch phải hoàn cho khách; MVP để FM xử lý thủ công.
  - Nếu cọc nhỏ hơn tổng phí, tạo hóa đơn phần còn thiếu. Khách phải thanh toán trước khi hoàn tất trả kho.

**Schema có trong phần này:**
- [**Invoice**](./db-table-draft.md#invoice)
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction)

#### 2.5.4 Bảo trì và mở lại cho thuê

**Context:** `CheckoutRecord` đã được xác nhận, khoang không còn khoản phải thu bắt buộc và có thể chuyển sang giai đoạn bảo trì trước khi cho thuê lại.

**Flow tổng quát:** Chuyển khoang sang `Maintenance` -> kết thúc hợp đồng và yêu cầu trả kho -> hết thời hạn bảo trì -> khoang về `Available`.

**Details:**
- **Hệ thống:**
  - Sau khi `CheckoutRecord` được xác nhận và không còn khoản phải thu bắt buộc, cập nhật `StorageUnit.status -> Maintenance` kèm `maintenance_started_at`.
  - Cập nhật `RentalContract.status -> Ended`.
  - Cập nhật `ReturnRequest.status -> Completed` kèm `completed_at`. Flow 3 định nghĩa giá trị này do Flow 2.5 set; nếu không đóng, yêu cầu kẹt ở `Assigned` và Flow 3 vĩnh viễn ẩn nút [Gia hạn]/[Trả kho].
  - Giữ `RentalOrder.status = Done`. Đơn đã kết thúc từ lúc bàn giao ở 2.4; việc trả kho thể hiện qua `RentalContract.Ended`, không thêm trạng thái mới vào enum Flow 1.
  - `Appointment.status` đã được set `Done` ở 2.5.2 khi ghi nhận khách đến, không set lại ở đây.
  - Hết `unit.maintenance_days` do BOM cấu hình ở Flow 4, chuyển khoang về `Available` để Flow 1 có thể gán cho yêu cầu mới.
  - Chỉ áp dụng cron mở lại cho khoang có `maintenance_started_at` khác null, tức `Maintenance` phát sinh từ luồng trả kho.
- **FM:**
  - Nếu khoang hư hỏng cần sửa lâu hơn, FM chuyển `Maintenance` thủ công ở Flow 5.2 và không set `maintenance_started_at`.
  - FM tự chuyển khoang về `Available` khi sửa xong; cron không xử lý khoang này.

**Schema có trong phần này:**
- [**StorageUnit**](./db-table-draft.md#storageunit)
- [**RentalContract**](./db-table-draft.md#rentalcontract)
- [**ReturnRequest**](./db-table-draft.md#returnrequest)
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord)

#### Backend flow (chi tiết kỹ thuật)

**a) Tiếp nhận yêu cầu trả (2.5.1)**
- Consumer của `ReturnRequest` khi chuyển `Assigned`: tạo `Appointment(RETURN, Pending)` + `RentalAppointment`, gán `facility_id` và `staff_id` theo `ReturnRequest.assigned_staff_id`, notify FS.

**b) Kiểm tra và lập biên bản (2.5.2)**
- `GET /api/staff/appointments/{id}/handover-record`: lấy `HandoverRecord` của đơn để FS đối chiếu hiện trạng lúc nhận.
- `POST /api/staff/appointments/{id}/inspection`: body `{ is_empty, cleanliness, damages[], photos[], returned_key_quantity, note? }`. Backend tạo `CheckoutRecord` và tính danh sách phí dự kiến theo cấu hình Flow 4, trả về cho FS xem trước. Chưa tạo `Invoice` ở bước này.
- `POST /api/staff/appointments/{id}/finalize-return`: chốt biên bản, tạo các `Invoice` phát sinh, thu hồi `UnitAccessKey`.

**c) Đối trừ và hoàn cọc (2.5.3)**
- Trong một transaction: tổng phí phát sinh + hóa đơn tồn đọng so với số tiền cọc đã thu (`Invoice.type = Deposit`, `status = Paid`).
- Thiếu: tạo `Invoice` phần chênh lệch, chặn bước chuyển `Maintenance` cho tới khi thanh toán xong.
- Dư: MVP ghi nhận số tiền phải hoàn để FM xử lý thủ công.

**d) Bảo trì và mở lại (2.5.4)**
- Transaction khi hoàn tất: `StorageUnit.status = Maintenance` + `maintenance_started_at`, `RentalContract.status = Ended`, `ReturnRequest.status = Completed` + `completed_at`. Không đụng `RentalOrder` (đã `Done` từ 2.4).
- Cron hằng ngày quét các khoang `Maintenance` có **`maintenance_started_at IS NOT NULL`** và đã đủ `unit.maintenance_days`, chuyển về `Available`, bắn `StorageUnit.BecameAvailable`. Điều kiện `IS NOT NULL` chính là cách phân biệt với khoang FM tự chuyển `Maintenance` do sự cố - Flow 5 chốt FM không set field này nên cron không bao giờ tự mở lại khoang FM đang giữ.
- Lock theo `unit_id` khi chuyển trạng thái để không xung đột với luồng gán khoang của Flow 1.

**Events phát ra từ Flow 2.5:**

| Event | Consumer |
|---|---|
| `Appointment.Created` (type = RETURN) | Hệ thống notify FS được phân công |
| `RentalOrder.ReturnCompleted` | Flow 3 (đóng vòng theo dõi), Flow 4 (ghi nhận doanh thu) |
| `Invoice.Created` (`type = Penalty`) | Flow 4 (theo dõi doanh thu) |
| `StorageUnit.BecameAvailable` | Flow 1 (khoang sẵn sàng cho yêu cầu mới), wishlist nếu có |

**Audit action Flow 2.5 bổ sung vào catalog chung:**

| Action | Khi nào ghi | Entity | Actor |
|---|---|---|---|
| `CHECKOUT_INSPECTED` | FS chốt kiểm tra hiện trạng lúc trả | `CheckoutRecord` | FS |
| `CHECKOUT_COMPLETED` | Hoàn tất biên bản trả kho | `CheckoutRecord` | FS |
| `ACCESS_KEY_REVOKED` | Thu hồi chìa hoặc vô hiệu hóa mã | `UnitAccessKey` | FS |
| `CONTRACT_ENDED` | Hợp đồng đóng sau khi trả kho | `RentalContract` | System |
| `STORAGE_UNIT_MAINTENANCE` | Khoang chuyển `Maintenance` sau trả kho | `StorageUnit` | System |
| `STORAGE_UNIT_AVAILABLE` | Cron mở lại khoang sau bảo trì | `StorageUnit` | System |

Dùng lại action sẵn có của Flow 1: `INVOICE_CREATED` cho hóa đơn `type = Penalty`, `MANUAL_REFUND_RECORDED` cho phần cọc phải hoàn thủ công ở 2.5.3.

**Schema:**

- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được đóng lại sau khi trả kho.
- [**Invoice**](./db-table-draft.md#invoice) - hóa đơn phí phát sinh khi trả kho, dùng `type = Penalty`.
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction) - cơ chế hoàn tiền chưa làm trong MVP.
- [**Appointment**](./db-table-draft.md#appointment), [**RentalAppointment**](./db-table-draft.md#rentalappointment), [**UnitAccessKey**](./db-table-draft.md#unitaccesskey), [**RentalContract**](./db-table-draft.md#rentalcontract) - dùng chung với Flow 2.
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord) - biên bản trả kho, tách riêng khỏi `HandoverRecord`.

**NOTES**
- Flow 3 bản hiện tại đã dùng `RentalContract.status = Ended` giống schema chung (`Draft/Signed/Active/Ended/Canceled`); giá trị `Completed` trong bản cũ của Flow 3 không còn. Flow 2.5 viết theo `Ended` và bổ sung nhánh **có** phát sinh phí so với nhánh thuận Flow 3 mô tả.
- Toàn bộ mức phí trong Flow 2.5 phụ thuộc cấu hình của Flow 4. Nếu Flow 4 chưa chốt danh mục phí thì phần này chỉ dừng ở mô tả nghiệp vụ, chưa code được.
- **Flow 2.5 sở hữu cron mở lại khoang sau bảo trì** (`Maintenance -> Available`, mô tả ở 2.5.4). Schema Flow 3 đã ghi rõ việc chuyển/mở lại `StorageUnit` do Flow 2.5 thực hiện; Flow 5 chỉ giữ thao tác chuyển `Maintenance` **thủ công** của FM cho các sự cố ngoài luồng trả kho, không đụng cron này.
- **Prefix `code` của `Invoice` đang lệch giữa Flow 1 và Flow 3, cần nhóm chốt.** Flow 1 dùng bảng `Service Code` riêng (`DEP/RNT/CLN/DMG/EXT`, trong đó `EXT` = dịch vụ phát sinh); Flow 3 suy prefix thẳng từ `type` (`DEP/RNT/EXT/PEN/SVC`, trong đó `EXT` = gia hạn). Cùng một mã `EXT` đang mang hai nghĩa. Flow 2.5 viết theo bảng của Flow 1 (`CLN`/`DMG`) vì đó là bản schema `Invoice` đang được dùng làm chuẩn.
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
    - Gửi thông báo và cho phép khách đặt lịch mới.
  - Nếu `now >= RentalOrder.expires_at`:
    - `RentalOrder.status = Expired`.
    - `StorageUnit.status` chuyển từ `Reserved` về `Available`.
    - Xử lý mất cọc theo policy.
- `HandoverRecord` (`result = IN_PROGRESS`, `arrived_at` đã có) quá `due_at`:
  - `HandoverRecord.result = CANCELED`, `reject_reason` = "Chưa hoàn tất bàn giao trong hạn".
  - `RentalContract` (`Draft`/`Signed`) chuyển `Canceled`.
  - Đơn, khoang và cọc xử lý như nhánh no-show ở trên.
- `Invoice` (DEP) quá `due_date` mà chưa thanh toán → set `status = Expired`; `RentalOrder` tương ứng chuyển `Expired` (khách không thanh toán cọc).
