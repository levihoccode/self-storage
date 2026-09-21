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
| `appointment.max_reschedule_count` | 2 lần |
| `appointment.daily_slot_count` | 3 khung/ngày |
| `appointment.capacity_mode` | FM chỉnh thủ công |
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
  - Sau khi submit, hệ thống tạo bản ghi `RentalRequest` với `status = Pending` (mặc định) và `created_at` = thời điểm submit.
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
- Với mỗi request tìm thấy:
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
    - FM vào xem khoang trống khác, chọn `unit_id` mới và bấm "Đề xuất lại".
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

**Schema có trong phần này:**
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Appointment**](./db-table-draft.md#appointment)
- [**RentalAppointment**](./db-table-draft.md#rentalappointment)
- [**HandoverRecord**](./db-table-draft.md#handoverrecord)

**NOTES:**
- `HandoverRecord` được tạo cùng lúc với `Appointment`.
- `result = IN_PROGRESS` ở thời điểm khởi tạo nghĩa là hồ sơ bàn giao đang được mở, không đồng nghĩa khách đã đến cơ sở.

### 2. Check-in và bàn giao kho
### 2.5 Trả kho và bảo trì
### 3. Quản lý kho đã thuê (Customer)
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là MAINTENANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố

## Scheduled Jobs - Cron jobs
### Jobs định kỳ
- `RentalRequest` quá `expires_at` mà vẫn `status = Approved` → set `status = Expired`.
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
- `Invoice` (DEP) quá `due_date` mà chưa thanh toán → set `status = Expired`; `RentalOrder` tương ứng chuyển `Expired` (khách không thanh toán cọc).
