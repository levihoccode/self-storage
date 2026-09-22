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

#### Các tham số sử dụng trong Flow 2

| Tên | Giá trị |
|---|---:|
| `handover.payment_grace_hours` | chờ BOM |
| `handover.max_rejection_count` | 2 lần |
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
- **Hỗ trợ cả hai loại khóa**: khóa cơ (`access_type = PhysicalKey`) và khóa mã số (`access_type = AccessCode`). Mỗi cơ sở/khoang bật tắt từng loại bằng hai cờ **`enabledKeyAccess`** và **`enabledCodeAccess`** - hai field này thuộc `Facility`/`StorageUnit` của **Flow 5**, Flow 2 chỉ nêu nhu cầu, không tự thêm.
- **Audit (contract MVP):** dùng chung bảng `AuditLog` của Flow 1, **không tạo bảng riêng**. Mọi thao tác đổi trạng thái, quyền sở hữu hoặc tiền trong Flow 2/2.5 phải ghi một bản ghi cho **mỗi entity** bị thay đổi; `entity_id` lưu dạng `String/Text`. Không ghi lượt đọc, click, secret hay raw payload thanh toán. Flow 2 chỉ bổ sung action của mình vào catalog chung.
- **Thông báo (contract MVP):** gửi email cho các sự kiện cần thông báo, đồng thời tạo thông báo để khách xem trên website - thông báo web là kênh chính để không mất thông tin khi email lỗi. **Lỗi gửi email không được rollback** thay đổi nghiệp vụ đã thành công. Flow 2 **không bắt buộc** `OutboxEvent` trong MVP; outbox, retry và bảo đảm giao email là quyết định kỹ thuật lúc code.
- **Ngưỡng vận hành mặc định** (BOM cấu hình sau ở Flow 4): `handover.max_rejection_count` và `handover.payment_grace_hours` là hai ngưỡng duy nhất do Flow 2 kiểm tra. Các ngưỡng quanh lịch hẹn thuộc Flow 1 và lấy theo bảng tham số của Flow 1 (`appointment.booking_window_days` 7, `appointment.max_days_after_deposit` 14, `appointment.daily_slot_count` 3, `order.deposit_expiry_days` 30, `appointment.checkin_reschedule_enabled` false); các ngưỡng Flow 2 từng đề xuất (auto-cancel 7 ngày, 2 lần no-show, dời lịch 2 lần báo trước 24h) bỏ để tránh hai nguồn.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 1: `RentalOrder` đã `Approve`, `unit_id` đã gán và khách đã duyệt qua `ProposalFeedback`, `Invoice` cọc đã `Paid`, **`Appointment(type = CHECKIN, status = Pending)`** đã tạo kèm `facility_id`, ảnh giấy tờ tùy thân khách upload online (nếu có).
- Flow 4: bảng giá thuê, mẫu + version điều khoản hợp đồng, chính sách mốc bắt đầu tính tiền thuê, danh mục phí.
- Flow 5: danh sách FS thuộc cơ sở (`AccountFacilityAssignment`), kết quả phân công FS lên `Appointment.staff_id` (Flow 5.3), và cấu hình loại khóa của cơ sở/khoang (`enabledKeyAccess`, `enabledCodeAccess`).

**Context:** Khách đã đặt cọc giữ khoang, đến cơ sở để check-in, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu và nhận quyền truy cập.

#### 2.1 Tiếp nhận lịch hẹn check-in

Flow 2 **không tạo và không sửa** `Appointment`. Toàn bộ việc sinh slot, đặt lịch, hủy và đặt lại lịch do Flow 1 xử lý; Flow 2 chỉ đọc lịch đã có và ghi nhận khách đến.

- **Điều kiện vào Flow 2:** `Appointment(type = CHECKIN, status = Pending)` của đơn, `facility_id` khớp cơ sở đang thao tác, `staff_id` đã được gán (Flow 1.5 đã chuyển đơn sang `InProgress` ở bước này), `HandoverRecord` của lịch hẹn đó đang `IN_PROGRESS`, `Invoice` cọc đã `Paid`, khoang đang `Reserved`. Thiếu điều kiện nào thì API check-in trả lỗi; Flow 2 không tự tạo lịch hẹn hay biên bản bù.
- **FM:** xem `Appointment` của cơ sở theo ngày, lọc thẳng trên `Appointment.facility_id`, tìm lịch còn `staff_id IS NULL` để gán FS. Nghiệp vụ phân công thuộc Flow 5.3, Flow 2 chỉ tiêu thụ kết quả.
- **FS:** xem lịch trong ngày được gán cho mình, kèm khách hàng, khoang chứa và `type`.

#### 2.2 Check-in và kiểm tra khoang chứa

Toàn bộ tiến trình on-site ghi trên bản ghi `HandoverRecord` đang `IN_PROGRESS` mà Flow 1.5 đã tạo cho lịch hẹn (mỗi `Appointment` một bản ghi, mỗi đơn tối đa một bản ghi đang mở), mỗi bước bật một cờ trong checklist.

- **Check-in:**
  - FS ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - FS mở `HandoverRecord` gắn với `Appointment` đó (Flow 1.5 đã tạo, `result = IN_PROGRESS`). Biên bản **không có `staff_id`** - FS phụ trách lấy qua `Appointment.staff_id`. Flow 2 **không tạo bản ghi mới**; mốc `RentalOrder` chuyển `InProgress` không còn là điểm sinh biên bản.
  - **Xác minh danh tính:** FS đối chiếu giấy tờ tùy thân của người đến với `RentalOrder.customer_id`; nếu khách đã upload ảnh giấy tờ online thì hệ thống hiển thị lại để FS đối chiếu. Đạt -> `is_identity_verified = true`, `identity_verified_at`. Không đạt thì dừng, không cho bật các cờ sau.

- **Kiểm tra khoang chứa:**
  - FS dẫn khách kiểm tra toàn bộ hiện trạng: kích thước, vị trí, vệ sinh, kết cấu, cửa/khóa, hư hại sẵn có. FS nhập `inspection_notes`, `inspection_photos`.
  - Khách đồng ý -> `is_unit_inspected = true`, `unit_inspected_at`; đây là điều kiện mở bước ký hợp đồng.
  - Khách **không đồng ý** -> `result = REJECTED`, `reject_reason`, `completed_at`. Flow 2 kết thúc.

- **Đếm số lần từ chối:** trước khi ghi nhận từ chối, Flow 2 đếm trong **cùng transaction** với bước ghi `REJECTED` để hai FS thao tác song song không đếm lệch:

  ```text
  count = số HandoverRecord có order_id = :order AND result = REJECTED
  count + 1 >= handover.max_rejection_count → nhánh chạm ngưỡng
  ```

  - Tính trên **toàn lịch sử đơn** - mỗi vòng re-propose tạo `HandoverRecord` mới, bản cũ giữ `REJECTED`.
  - **Không đếm `CANCELED`**: no-show và quá hạn thanh toán không phải khách từ chối khoang.
  - Tách hẳn `proposal.max_rejection_count` của Flow 1 (đếm lần từ chối proposal **trước khi cọc**).

- **Nhánh thường (chưa chạm ngưỡng):** ghi `result = REJECTED`, `reject_reason`, `completed_at` rồi bắn `HandoverRecord.Rejected`. Việc chỉ định lại khoang **không xử lý trong Flow 2**: Flow 1 cho FM chỉ định khoang khác, tạo `ProposalFeedback` mới cho khách duyệt online, xử lý chênh lệch tiền cọc rồi tạo `Appointment` mới. `RentalOrder` lấy khoang được chấp nhận mới nhất từ `ProposalFeedback` và **không bị hủy**.

- **Nhánh chạm ngưỡng - phải xác nhận trước khi hủy:** Flow 2 **tự hủy đơn**, không quay về Flow 1.
  - **Chưa ghi `REJECTED` ngay.** Hệ thống hiện xác nhận cho khách, nêu rõ hệ quả: đơn sẽ bị hủy vì đã từ chối tối đa N khoang, **và tiền cọc không được hoàn**.
  - **Khách xác nhận:** ghi `HandoverRecord.result = REJECTED`, `RentalOrder -> Canceled`, khoang `Reserved -> Available`, `ProposalFeedback` đang `Agreed -> Expired`, cọc xử lý theo chính sách Flow 4. **Không** tạo proposal hay appointment mới.
  - **Khách không xác nhận:** không ghi gì, giữ nguyên hiện trạng. Nếu khách cũng không nhận khoang thì để cron no-show của Flow 1 xử lý khi quá `end_at`.
  - **Sau khi hủy:** gửi thông báo xác nhận hủy cho khách (email + thông báo website) theo contract notification MVP.


- **No-show:** hết `end_at` mà `arrived_at` vẫn null - cron của **Flow 1** xử lý: `Appointment.status = Canceled` (`cancel_reason = NoShow`) và **`HandoverRecord.result = CANCELED`** kèm lý do. Còn trong thời hạn giữ kho (`now < RentalOrder.expires_at`): đơn quay về `Deposited`, khoang giữ `Reserved`, khách đặt lịch mới. Hết hạn: đơn `Expired`, khoang về `Available`, xử lý mất cọc theo policy. Flow 2 không ghi gì trong nhánh này.

- **Đặt lịch mới (sau reject hoặc no-show):** do **Flow 1** thực hiện. Lịch cũ phải ở `Canceled` và `HandoverRecord` cũ đã chốt (`REJECTED`/`CANCELED`) trước khi tạo `Appointment` mới (`status = Pending`, `staff_id = null`) kèm `RentalAppointment` và `HandoverRecord` mới - ràng buộc mỗi đơn chỉ có một lịch `CHECKIN` đang hoạt động. Đây **không phải** reschedule: dời lịch không thuộc MVP. FM phân công FS lại từ đầu; Flow 2 chạy lại từ 2.2 trên biên bản mới.

**NOTES:**

```text
Hủy ở nhánh này khác hủy ở Flow 1 (trước check-in):
- Cùng dùng block CANCELLATION CONSTRAINTS; khác trigger và actor:
  Flow 2 tự hủy sau xác nhận của khách, không quay về Flow 1.
- HandoverRecord giữ REJECTED, không chuyển CANCELED
  (CANCELED dành cho no-show / quá hạn thanh toán).
- Appointment CHECKIN đã Done nên không hủy; Flow 1 hủy lịch đang hoạt động.
- Đơn đang InProgress, không phải Pending/Deposited/Scheduled.
- Cọc: mất theo chính sách Flow 4 vì khách chủ động từ chối.
```

#### 2.3 Ký hợp đồng và thanh toán tháng đầu tiên

**Ký hợp đồng (sinh `RentalContract`)**
- Điều kiện: `is_identity_verified = true` và `is_unit_inspected = true`.
- Hệ thống sinh hợp đồng từ mẫu đang hiệu lực (Flow 4), điền sẵn: thông tin khách, cơ sở, `unit_id` khách vừa xác nhận, giá thuê, `period`, tiền cọc đã đóng, mốc bắt đầu tính tiền thuê; snapshot `terms_version` để đối chiếu về sau.
- Hiện trạng khoang (`inspection_notes`, `inspection_photos`) ở 2.2 được gắn kèm hợp đồng - liên kết qua `order_id` nên không cần thêm khóa ngoại. Đây là căn cứ đối chiếu khi trả kho ở Flow 2.5.
- Khách ký trên bản giấy -> FS chụp/scan upload, `signature` và `pdf_url` lưu URL file, `RentalContract.status = Signed`, set `is_contract_signed`, `contract_signed_at`.
- **Mốc bắt đầu tính tiền thuê** ghi trên hợp đồng, **luôn điền sẵn theo chính sách Flow 4** (`contract.start_date_rule`, ví dụ: 1 tuần sau ngày ký, ngày 15 hàng tháng...). Đây là đường đi thông thường; thỏa thuận riêng chỉ là ngoại lệ, để tránh xung đột khi phát sinh yếu tố tự phát.
- **Thỏa thuận riêng mốc tính tiền - phải được FM duyệt:**
  - FS **không tự sửa** `start_date`. Muốn đổi thì nhập ngày đề nghị kèm lý do lên hợp đồng đang `Draft`: `start_date_override_requested`, `start_date_override_reason`, `start_date_override_status = Pending`.
  - Còn `Pending` thì **chặn bước ký** - API ký trả lỗi cho tới khi FM xử lý xong, tránh việc hợp đồng được ký rồi mới đi xin duyệt.
  - FM xem các yêu cầu đang chờ của cơ sở mình:
    - **Duyệt:** `start_date` nhận ngày đề nghị, `start_date_override_status = Approved`, mở lại bước ký.
    - **Từ chối:** `start_date` giữ nguyên ngày theo chính sách, `status = Rejected`, mở lại bước ký. FS có thể gửi đề nghị khác nếu khách vẫn không đồng ý.
  - Cả ba thao tác (gửi đề nghị, duyệt, từ chối) đều ghi `AuditLog` kèm `old_value`/`new_value`/`reason` - đây là thay đổi ảnh hưởng tới tiền nên bắt buộc có vết.

**Thanh toán tháng đầu tiên**
- Hệ thống tạo `Invoice(type = Rental)` - prefix `RNT`, gắn `contract_id` - với số tiền **tháng đầu tiên**. Tiền cọc ở Flow 1.4 **không** trừ vào hóa đơn này, cọc giữ riêng tới khi trả kho (2.5.3).
- Khách thanh toán qua VNPay; kết quả xác nhận từ phía server/gateway -> `PaymentTransaction = Success`, `Invoice.status = Paid`, set `is_payment_settled`, `payment_settled_at`. Thất bại -> `PaymentTransaction = Failed`, hóa đơn giữ nguyên chưa thanh toán và **không đi tiếp sang bàn giao**.
- Chưa thanh toán xong trong buổi hẹn: `result` giữ `IN_PROGRESS`, khoang vẫn `Reserved`, **chưa bàn giao khóa**; hóa đơn nằm trong mục "Hóa đơn" của khách để thanh toán online.
  - Khách có **`payment_grace_hours`** giờ để thanh toán. Quá hạn, cron xử lý **giống nhánh no-show của Flow 1** để một đơn chậm tiền không bị đối xử nặng hơn một đơn không đến:
    - `RentalContract.status -> Canceled`, `HandoverRecord.result -> **CANCELED**` kèm lý do "Quá hạn thanh toán tháng đầu". Dùng `CANCELED` chứ **không** dùng `REJECTED`, vì `REJECTED` là tín hiệu để Flow 1 đề xuất khoang khác - ở đây khoang không có vấn đề gì.
    - Còn trong thời hạn giữ kho (`now < RentalOrder.expires_at`): đơn quay về `Deposited`, **giữ cọc**, khoang vẫn `Reserved`, khách đặt lịch check-in mới ở Flow 1 và làm lại từ 2.2.
    - Hết thời hạn giữ kho: đơn `Expired`, khoang về `Available`, xử lý mất cọc theo chính sách Flow 4.
    - Cron này là cron duy nhất của Flow 2, chạy hằng ngày; 2.1 không còn cron auto-cancel nào.

#### 2.4 Bàn giao khóa và kích hoạt hợp đồng

- **Điều kiện:** cả 4 cờ trên `HandoverRecord` đều `true` (`is_identity_verified`, `is_unit_inspected`, `is_contract_signed`, `is_payment_settled`). Thiếu cờ nào thì API bàn giao trả lỗi rõ ràng cho FS.
- **FS** bàn giao quyền truy cập theo loại khóa mà cơ sở/khoang đang bật:
  - **Khóa cơ** (`enabledKeyAccess`): giao chìa vật lý, ghi `quantity` để đối chiếu khi trả kho.
  - **Khóa mã số** (`enabledCodeAccess`): hệ thống sinh mã gắn với đơn và **chỉ hiển thị một lần trong tài khoản khách sau khi đăng nhập** - không gửi qua email, không hiện trên màn hình của FS, không trả trong response của API bàn giao. FS hướng dẫn khách mở app xem mã và đổi mã ngay lần dùng đầu. Mã **không lưu plain text**, chỉ lưu `code_hash`.
  - Cơ sở bật cả hai cờ thì FS chọn loại bàn giao trong buổi hẹn; `UnitAccessKey` ghi đúng `access_type` đã giao.
  - Hai bên xác nhận, khách ký nhận.
- **Hệ thống (một transaction):** tạo `UnitAccessKey`; `StorageUnit`: `Reserved -> Rented`; `RentalContract`: `Signed -> Active`; `HandoverRecord.result = COMPLETED` + `completed_at`; **`RentalOrder.status -> Done` (Flow 2 là nơi duy nhất set `Done`)**; bắn **`RentalOrder.HandoverCompleted`** - đây là event canonical để Flow 3 bắt đầu theo dõi, Flow 3 không tự poll `status`.
- **Sau transaction:** gửi email kèm hợp đồng và biên bản bàn giao, đồng thời tạo thông báo trên website cho khách. Hai việc này nằm **ngoài** transaction, lỗi gửi không rollback bàn giao.

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

**Audit action Flow 2 bổ sung vào catalog chung:**

| Action | Khi nào ghi | Entity | Actor |
|---|---|---|---|
| `IDENTITY_VERIFIED` | FS xác minh danh tính người đến | `HandoverRecord` | FS |
| `UNIT_INSPECTED` | Khách xác nhận hiện trạng khoang | `HandoverRecord` | FS |
| `HANDOVER_REJECTED` | Khách từ chối khoang tại chỗ | `HandoverRecord` | FS |
| `RENTAL_ORDER_CANCELED` | Hủy đơn khi chạm `handover.max_rejection_count`, sau khi khách xác nhận | `RentalOrder`, `StorageUnit`, `ProposalFeedback` - một bản ghi cho mỗi entity | FS |
| `HANDOVER_COMPLETED` | Hoàn tất bàn giao, đủ 4 cờ | `HandoverRecord` | FS |
| `START_DATE_OVERRIDE_REQUESTED` | FS đề nghị đổi mốc tính tiền | `RentalContract` | FS |
| `START_DATE_OVERRIDE_APPROVED` | FM duyệt đổi mốc tính tiền | `RentalContract` | FM |
| `START_DATE_OVERRIDE_REJECTED` | FM từ chối đổi mốc tính tiền | `RentalContract` | FM |
| `CONTRACT_SIGNED` | FS ghi nhận khách đã ký hợp đồng | `RentalContract` | FS |
| `CONTRACT_ACTIVATED` | Hợp đồng có hiệu lực sau bàn giao | `RentalContract` | System |
| `CONTRACT_CANCELED` | Hủy hợp đồng do quá `handover.payment_grace_hours` | `RentalContract` | System |
| `HANDOVER_CANCELED` | Đóng biên bản do quá hạn thanh toán | `HandoverRecord` | System |
| `ACCESS_KEY_ISSUED` | Bàn giao chìa hoặc mã truy cập | `UnitAccessKey` | FS |
| `STORAGE_UNIT_RENTED` | Khoang chuyển `Rented` sau bàn giao | `StorageUnit` | System |
| `RENTAL_ORDER_DONE` | Đơn chuyển `Done` sau bàn giao | `RentalOrder` | System |

Các action đã có sẵn trong catalog của Flow 1 thì dùng lại, không đặt tên mới: `INVOICE_CREATED` cho hóa đơn `type = Rental` tháng đầu, `PAYMENT_SUCCEEDED`/`PAYMENT_FAILED` cho kết quả IPN.

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
- **Block `CANCELLATION CONSTRAINTS` chưa tồn tại trong spec.** NOTES của 2.2 tham chiếu block này để so sánh hai kiểu hủy đơn, nhưng hiện chưa mục nào định nghĩa. Cần Flow 1 viết block dùng chung, nếu không thì câu tham chiếu treo.
- **Flow 5 bổ sung `enabledKeyAccess` và `enabledCodeAccess`** trên `Facility`/`StorageUnit` để bật tắt từng loại khóa (theo review của Levi ở PR #6). Chưa có hai field này thì 2.4 không xác định được cơ sở đang dùng loại khóa nào; schema Flow 5 hiện vẫn chưa có.
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
| `fee.cleaning` | chờ BOM |
| `fee.damage` | chờ BOM |
| `fee.lost_key` | chờ BOM |
| `fee.late_return_per_day` | chờ BOM |
| `unit.maintenance_days` | 1-3 ngày |

**Vị trí trong vòng đời thuê kho:** Flow 2.5 nhận đầu vào từ Flow 3.4 (khách bấm yêu cầu trả kho) và xử lý toàn bộ phần on-site. Kết thúc khi khoang hoàn tất bảo trì và quay về `Available`, sẵn sàng cho yêu cầu mới ở Flow 1. Đây là điểm đóng vòng đời của một `RentalOrder`.

**ĐÃ CHỐT:**
- `Appointment.type` thêm giá trị **`RETURN`** cho buổi hẹn trả kho, `RentalAppointment` cũng được tạo cho loại này. Levi sẽ chốt lại bộ `type` sau khi các flow ổn định.
- Việc dời vòng đời `Appointment` sang Flow 1 chỉ áp cho **lịch check-in** (thuộc booking lifecycle). Lịch `RETURN` vẫn do Flow 2.5 tạo từ `ReturnRequest` của Flow 3, vì nó không nằm trong vòng đời đặt khoang.
- MVP chỉ hỗ trợ **luồng trả do khách chủ động yêu cầu**; FM hủy hộ yêu cầu trả kho không thuộc MVP.
- Biên bản trả kho tách thành bảng **`CheckoutRecord`** riêng, vì `HandoverRecord` chỉ chịu trách nhiệm tới khâu bàn giao và kết thúc vòng đời sau đó.
- Phí phát sinh khi trả kho (hư hỏng, vệ sinh, mất chìa, trả trễ) dùng **`Invoice.type = Penalty`** theo bộ `type` của schema chung `Deposit/Rental/Extension/Penalty/Service`; **tiền tố trong `code` lấy theo bảng Service Code của Flow 1**: `CLN` cho phí dọn dẹp, `DMG` cho phí hư hại. Hai thứ này ở hai tầng khác nhau - `type` phân loại hóa đơn, prefix chỉ nằm trong `code` - nên không xung đột. Khoản chưa có mã riêng (mất chìa, trả trễ) ghi phân loại ở `title`/`desc`.
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
- Flow 4: mức phí hư hỏng, vệ sinh, trả trễ, lưu giữ; chính sách xử lý tiền cọc; thời gian bảo trì do BOM cấu hình.

**Context:** Khách kết thúc nhu cầu thuê và muốn trả lại khoang chứa, cần có người kiểm tra hiện trạng, xử lý các khoản phát sinh và thu hồi quyền truy cập trước khi khoang được cho thuê lại.

#### 2.5.1 Tiếp nhận yêu cầu và hẹn lịch trả kho

- **Hệ thống:** khi `ReturnRequest` chuyển `Assigned` ở Flow 3.4, tạo `Appointment(type = RETURN, status = Pending)` kèm `RentalAppointment`, gán `facility_id`, `staff_id` lấy từ `ReturnRequest.assigned_staff_id`. Lịch `RETURN` **dùng chung 3 khung giờ cố định** với `CHECKIN` (`appointment.daily_slot_count`) vì cùng FS phục vụ: `preferred_date` của khách được map vào khung còn trống trong ngày đó; hết khung thì đẩy sang ngày gần nhất và báo khách. Việc phân công FS đã do FM làm ở Flow 3, Flow 2.5 không lặp lại.
- **Customer:** trước ngày hẹn phải tự dọn toàn bộ tài sản ra khỏi khoang. Hệ thống nhắc các điều kiện để được nhận lại cọc: khoang trống, không hư hỏng, không còn hóa đơn `Unpaid`.

#### 2.5.2 Kiểm tra và bàn giao lại khoang chứa

- **FS:**
  - Ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - Mở `HandoverRecord` đã lập ở **Flow 2** để lấy hiện trạng khoang **lúc bàn giao** (`inspection_notes`, `inspection_photos`). Flow 2.5 chỉ **đọc** bảng này, không tạo mới - đây là hiện trạng hai bên đã cùng xác nhận trước khi khách ký hợp đồng, nên là căn cứ đối chiếu duy nhất.
  - Kiểm tra hiện trạng **lúc trả** theo checklist rồi so với mốc trên: khoang đã dọn trống chưa, tình trạng vệ sinh, hư hỏng kết cấu/cửa/khóa/thiết bị, chụp ảnh hiện trạng.
  - Chênh lệch giữa hai mốc chính là căn cứ tính phí hư hỏng/vệ sinh ở 2.5.3.
  - Lập `CheckoutRecord`, khách ký xác nhận. Mỗi cột mốc (dọn trống, kiểm tra xong, thu hồi quyền truy cập, thanh toán phí, xử lý cọc) bật một cờ kèm timestamp để FM/FS theo dõi tiến độ khi buổi trả kho kéo dài nhiều ngày.
  - Các trường hợp:
    - **Đạt yêu cầu:** không phát sinh phí, sang 2.5.3.
    - **Còn tài sản trong khoang:** FS ghi nhận, `CheckoutRecord.result = PENDING_ITEMS`, **không hoàn tất trả kho**. Hệ thống tạo một `Appointment(type = RETURN)` mới để khách quay lại dọn nốt và trỏ `CheckoutRecord.appointment_id` sang lịch mới. Khi khách quay lại và FS bắt đầu kiểm tra lần nữa, `result` chuyển **`PENDING_ITEMS -> IN_PROGRESS`** trên cùng bản ghi; vòng này lặp cho tới khi khoang trống hẳn. Trong thời gian này:
      - **Không thu hồi `UnitAccessKey`** - khách vẫn cần quyền truy cập để vào lấy đồ.
      - Khoang giữ `Rented`, `RentalContract` giữ `Active`; nếu vượt hạn hợp đồng thì phát sinh phí trả kho trễ theo Flow 4.
      - Thời hạn cho dọn tiếp và mức phí lưu giữ theo **chính sách do BOM viết ở Flow 4**. Hệ thống **không tự động tính phí** khi khoang còn đồ - ngoài MVP; FM hoặc FS gửi hóa đơn thủ công.
    - **Khoang bẩn hoặc hư hỏng:** FS ghi nhận chi tiết kèm ảnh, hệ thống tạo hóa đơn phí tương ứng ở 2.5.3.

- **Thu hồi quyền truy cập:**
  - Chỉ thực hiện khi `CheckoutRecord.result` **không phải** `PENDING_ITEMS` (khoang đã dọn trống hoàn toàn).
  - Khóa cơ: FS thu lại chìa, đối chiếu `UnitAccessKey.quantity` đã giao ở 2.4. Thiếu chìa thì tính phí `fee.lost_key` và set `UnitAccessKey.status = Lost` thay vì `Revoked` - `Lost` nghĩa là **không thu hồi được quyền truy cập vật lý**, phải thay khóa trước khi cho thuê lại; số chìa thu được ghi ở `returned_key_quantity` để tính số lượng thiếu.
  - Khóa mã số: hệ thống vô hiệu hóa mã ngay khi biên bản được xác nhận.
  - Thu đủ chìa hoặc đã vô hiệu hóa mã: `UnitAccessKey.status` -> `Revoked`, ghi `revoked_at`. Thiếu chìa: `-> Lost`, cũng ghi `revoked_at` và khoang phải thay khóa trong kỳ bảo trì ở 2.5.4.

#### 2.5.3 Xử lý phí phát sinh và tiền cọc

- **Các khoản có thể phát sinh** (đều tạo dưới `Invoice.type = Penalty`): phí vệ sinh, phí hư hỏng, phí mất chìa/thay khóa, phí trả kho trễ; cộng thêm các hóa đơn `Unpaid` còn tồn đọng của hợp đồng. Mức phí lấy theo cấu hình Flow 4, Flow 2.5 **không tự định nghĩa mức phí**.
- **Đối trừ tiền cọc:**
  - Cọc lớn hơn tổng phí: hoàn lại phần chênh lệch cho khách (MVP ghi nhận số tiền phải hoàn, FM xử lý thủ công).
  - Cọc nhỏ hơn tổng phí: tạo hóa đơn phần còn thiếu, khách phải thanh toán trước khi hoàn tất trả kho.

#### 2.5.4 Bảo trì và mở lại cho thuê

- Sau khi `CheckoutRecord` được xác nhận và không còn khoản phải thu bắt buộc: `StorageUnit.status -> Maintenance` kèm `maintenance_started_at`, `RentalContract.status -> Ended`. **`RentalOrder` giữ nguyên `Done`** - đơn đã kết thúc từ lúc bàn giao ở 2.4, việc trả kho thể hiện qua `RentalContract.Ended` chứ không thêm trạng thái mới vào enum của Flow 1. (`Appointment.status` đã được set `Done` ở 2.5.2 khi ghi nhận khách đến, không set lại ở đây.)
- Hết thời gian bảo trì (theo cấu hình của BOM ở Flow 4), khoang tự chuyển về `Available` và có thể được gán cho yêu cầu mới ở Flow 1.
- Khoang hư hỏng cần sửa dài hơn: FM giữ ở `Maintenance` cho tới khi xử lý xong, không để cron tự mở.

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
- Transaction khi hoàn tất: `StorageUnit.status = Maintenance` + `maintenance_started_at`, `RentalContract.status = Ended`. Không đụng `RentalOrder` (đã `Done` từ 2.4).
- Cron hằng ngày quét các khoang `Maintenance` đã đủ thời gian cấu hình và không bị FM giữ lại, chuyển về `Available`, bắn `StorageUnit.BecameAvailable`.
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
- `Invoice` (DEP) quá `due_date` mà chưa thanh toán → set `status = Expired`; `RentalOrder` tương ứng chuyển `Expired` (khách không thanh toán cọc).
