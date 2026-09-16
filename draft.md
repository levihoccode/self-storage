# Bản nháp phân tích
## Techstack
- Platform: Web Only

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
- NOTES:
  - Đang phân vân việc có nên thêm 1 trạng thái cho khoang chứa là `OnHold` (tạm giữ cho khách đặt và đã được approve nhưng chưa đặt cọc) để giữ kho trong ngắn hạn (24h timeout), nếu không có `OnHold`, đơn đặt mặc dù đã `Approve` nhưng nếu chưa đặt cọc, các đơn tới sau và đặt cọc có thể chiếm khoang chứa đó.
    - Vấn đề phát sinh, Holding Attack - kẻ xấu dùng đúng 1 thông tin hợp lệ, lặp đi lặp lại quy trình để kho luôn ở trạng thái bị giữ, người khác không thuê được mà FM cũng không làm gì được nếu không có cơ chế chặn. Giải pháp dự kiến: Pre-authorization bằng visa/master card, tạm giữ 1$ để xác minh.

## Business workflow
### 1. Đặt kho
**FLOW:**
```
[Yêu cầu đặt kho] -> [Khách tạo tài khoản] -> [Đặt cọc] -> [Ký hợp đồng] -> [Thanh toán]
```
#### 1.1 Yêu cầu đặt kho
**Context:** Khách mới, chưa từng sử dụng dịch vụ, muốn tìm cho mình một khoang chứa phù hợp với nhu cầu.

**Flow tổng quát:** Khách lựa chọn khoang chứa dựa trên nhu cầu và điền các thông tin cần thiết (Không được chỉ định khoang chứa cụ thể). Sau đó, FM kiểm tra những khoang chứa còn trống và sẵn sàng cho thuê để chỉ định cho người thuê.

**Details:**
- **Customer:**
  - Khách hàng điền nhu cầu thuê kho qua form (không cần đăng nhập), bao gồm các thông tin được hiển thị trên form: 
    + customer_email
    + customer_phone
    + unit_type
    + facility
    + start_date (MM/DD/YYYY)
    + period - số tháng thuê
  - Nhận phản hồi thông qua email và số điện thoại (telesale sẽ gọi để xác nhận)

- **FM:**
  - Các yêu cầu đặt khoang chứa sẽ được liệt kê ở một trang và có các nút (button) để thao tác (details, response, update status, ...), mỗi entry là một `RentalRequest`.
  - Sau khi xác định được 1 yêu cầu đặt kho cần giải quyết, FM sẽ kiểm tra các kho còn sẵn tại chi nhánh và trong trường hợp:
    - **Tìm thấy khoang chứa thích hợp**:
      - FM nhập unit id phù hợp vào field `unit_id` và bấm `Approve`.
      - `RentalRequest.status` sang `Approve`.
      - **Trong trường hợp email chưa có tài khoản:**
        - Hệ thống ghi nhận trong một khoảng thời gian ngắn, có một yêu cầu được duyệt nhưng chưa có tài khoản. (Có thể sử dụng PG Cache hoặc Redis)
        - Hóa đơn và yêu cầu chọn lịch hẹn sẽ được thêm vào tài khoản khách hàng khi được tạo trong thời gian quy định.
      - **Trong trường hợp email đã có tài khoản:**
        - Hệ thống tạo một bản ghi trong `RentalOrder` để chờ khách đặt cọc.
        - Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
          - code: INV-DEP-XX-XXXXXX-XXXX
          - title: "Đặt cọc khoang chứa A"
          - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
          - amount: ...
        - Hệ thống gửi một thông báo/email thành công đến khách hàng kèm theo thông tin khoang chứa.
        - Hệ thống tạo một lựa chọn lịch hẹn on-site cho khách hàng.
    - **Không tìm thấy khoang chứa thích hợp**: 
      - Chuyển status sang `Reject` và nhập lý do: "Hết khoang chứa phù hợp tại chi nhánh".
      - Hệ thống gửi một thông báo/email không thành công đến khách hàng kèm theo lý do.

- **Hệ thống gửi email:**
  - **Nội dung email nếu khách nhận được phản hồi thành công** và trong trường hợp:
    - *Chưa có tài khoản:*
      - Thông báo rằng email này chưa có tài khoản trên hệ thống, cần đăng ký tài khoản để nhận hóa đơn và lịch hẹn on-site trong vòng một khoảng thời gian.
      - Đính kèm link đăng ký tài khoản trong email.
    - *Đã có tài khoản:*
      - Tạo một hóa đơn "Đặt cọc" cho khách hàng.
      - Thông báo trên web và email rằng có một hóa đơn đặt cọc cần được xử lý.
    - Đính kèm cảnh báo: "Khoang chứa chỉ được xác nhận chính thức cho khách hàng hoàn tất thanh toán cọc đầu tiên. Vui lòng thanh toán sớm để đảm bảo giữ chỗ."
  - **Trong trường hợp yêu cầu được Approve nhưng chưa đặt cọc, và đã có người khác đặt cọc: (chưa chốt)**
    - Phương án 1 (gợi ý khoang tương đương): hệ thống bắn thông báo/email "Khoang M-101 đã có người cọc trước. Cơ sở hiện vẫn còn các khoang M-102, M-103 cùng kích thước. Bấm vào đây để giữ khoang tương đương."
    - Phương án 2 (chuyển sang danh sách mong muốn - Wish Lists): Yêu cầu của các khách còn lại tự động chuyển status sang Wishlisted. Nếu Khách A sau đó hủy cọc hoặc bùng hợp đồng, những người trong danh sách chờ sẽ nhận được thông báo để đặt cọc.

**Schema:**
- [**RentalRequest**](./db-table-draft.md#rentalrequest) - chứa các thông tin được gửi từ form trên website.
- [**RentalOrder**](./db-table-draft.md#rentalorder) - chứa các thông tin đơn hàng đã được `Approve` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho bao gồm các thông tin:
- [**Invoice**](./db-table-draft.md#invoice) -  chứa thông tin thanh toán của khách hàng (hóa đơn)

**NOTES**
- Nếu cả 2 fields order_id và contract_id đều null, tức là hóa đơn từ việc yêu cầu dịch vụ hỗ trợ (`SupportRequest`)
- Entry trong list yêu cầu đặt khoang chứa của FM không có facility vì khi đặt, khách chỉ định một chi nhánh cụ thể và người quản lý tại chi nhánh đó sẽ nhận được yêu cầu => không cần liệt kê facility field.
- Có thể phát triển thêm phần wishlist giành cho các khoang chứa đều không available, nhưng tự động gửi thông báo và đăng ký ngay khi có bất kỳ khoang chứa nào trống (có thể dùng filter).

**Advanced Features (not MVP)**
- Tự động quá trình duyệt.
- Cho khách chỉ định cụ thể khoang chứa để thuê. -> không tối ưu layout khi để khách tự chọn, cần tìm cách hoặc kệ nó luôn đi :))
- Cho khách đặt nhiều khoang chứa trong 1 request. -> cần lưu ý về việc các khoang chứa có cần liên tục nhau hay không, tính toán ra sao nếu không đủ, ...
#### 1.2 Khách tạo tài khoản
**Case A: Sau khi có một yêu cầu được duyệt**

**Context:** Yêu cầu đặt khoang chứa của khách đã được duyệt và cần đặt cọc nhưng chưa có tài khoản.

**Flow tổng quát:** Yêu cầu đã được duyệt và ghi nhận trên hệ thống, khách hàng đăng ký trong thời gian quy định và hóa đơn + chọn lịch hẹn on-site sẽ được thêm tự động cho tài khoản đó. 

**Details:**
- Sau khi đăng ký, hệ thống kiểm tra trên bộ nhớ (Redis hoặc PG Cache) xem tài khoản có nằm trong mục "Có yêu cầu nhưng chưa tạo tài khoản"
- Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
  - code: INV-DEP-XX-XXXXXX-XXXX
  - title: "Đặt cọc khoang chứa A"
  - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
  - amount: ...
- Hệ thống tạo một yêu cầu chọn lịch cho tài khoản.

**Case B: Không có yêu cầu nào được duyệt**
#### 1.3 Đặt cọc
**Context:** Sau khi khách đã điền form và được approve, email phản hồi thành công đã được gửi có kèm theo link kích hoạt tài khoản và tài khoản được kích hoạt thành công.

**Flow tổng quát:**
Khách đăng nhập vào ứng dụng thành công -> vào mục "Thanh toán" -> hiển thị một mục "Đặt cọc để giữ khoang chứa" -> thanh toán thành công -> trạng thái kho chuyển sang `Reserved` trong một khoản thời gian.

**Details:**
- Khách đăng nhập vào ứng dụng và thanh toán
- Ở bước hiện mã QR để chuyển khoản, khoang chứa sẽ tạm thời bị khóa (5-10p timeout) để việc thanh toán hoàn tất mà không bị gián đoạn. -> Tránh nhiều người đặt cọc 1 kho cùng lúc
- **Nếu thanh toán thành công:** trạng thái khoang chứa sẽ được chuyển sang `Reserved`
- **Nếu thanh toán không thành công:** khách hàng quay về trang "Hóa đơn" và khóa tạm thời của khoang chứa được mở.
### 2. Check-in và bàn giao kho

**FLOW:**
```
[Chọn lịch hẹn] -> [FM phân công FS] -> [Check-in & xác minh danh tính] -> [Kiểm tra & xác nhận hiện trạng khoang] -> [Ký hợp đồng] -> [Thanh toán tháng đầu] -> [Nhận khóa/mã truy cập] -> [Khoang chuyển Rented]
```

**Vị trí trong vòng đời thuê kho:** Flow 2 bắt đầu sau khi Flow 1.3 (Đặt cọc) hoàn tất - Flow 1 **chỉ thu tiền cọc**, khoang đang ở trạng thái `Reserved`. Việc **ký hợp đồng và thanh toán tiền thuê** nằm trong Flow 2, thực hiện tại cơ sở sau khi khách check-in và xác nhận hiện trạng khoang. Flow 2 kết thúc khi `HandoverRecord.result` chuyển `COMPLETED` (khoang `Rented`, hợp đồng có hiệu lực, bàn giao sang Flow 3) hoặc `REJECTED` (đơn quay về Flow 1 để FM chỉ định khoang khác, sau đó khách hẹn lại và Flow 2 chạy lại trên `HandoverRecord` mới). Toàn bộ là thao tác on-site.

**ĐÃ CHỐT:**
- MVP thu **tháng đầu tiên**; chính sách trả trước N tháng để mở sau (Flow 4).
- MVP chỉ xử lý trường hợp **khách đã đặt cọc trước ngày hẹn** - buổi hẹn là check-in + bàn giao. Lịch "tham quan kho" cho khách chưa cọc (nhiều khách chung một slot) **không thuộc MVP**, xem Advanced Features.
- Thanh toán đi qua cổng **VNPay**, hệ thống chỉ redirect sang cổng khi cần thanh toán hóa đơn. **Không thu tiền mặt trong MVP.**
- `ProposalFeedback` là bảng của **Flow 1** (khách duyệt online khoang FM chỉ định, trước khi chọn lịch hẹn). Phản hồi hiện trạng khoang lúc check-in ghi trong `HandoverRecord`.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 1: `RentalOrder` đã `Approve`, `unit_id` đã gán và khách đã duyệt qua `ProposalFeedback`, `Invoice` cọc đã `Paid`, ảnh giấy tờ tùy thân khách upload online (nếu có).
- Flow 4: bảng giá thuê, mẫu + version điều khoản hợp đồng, chính sách mốc bắt đầu tính tiền thuê, danh mục phí.
- Flow 5: danh sách FS thuộc cơ sở, giờ hoạt động của cơ sở (sinh slot lịch hẹn), cấu hình loại khóa (`enabledKeyAccess`, `enabledCodeAccess`).

**Context:** Khách đã đặt cọc giữ khoang, đến cơ sở để check-in, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu và nhận quyền truy cập.

#### 2.1 Chọn lịch hẹn và phân công nhân sự

- **Customer:**
  - Sau khi `Invoice` cọc chuyển `Paid`, hệ thống mở chức năng chọn lịch hẹn cho `RentalOrder`.
  - Khách chọn ngày và khung giờ trong các slot sinh từ giờ hoạt động của cơ sở (Flow 5). Hệ thống tạo `Appointment(type = CHECKIN, status = Pending)` + `RentalAppointment` nối với `RentalOrder`.
  - Trước buổi hẹn, hệ thống gửi danh sách việc sẽ làm tại cơ sở: đối chiếu giấy tờ tùy thân, kiểm tra khoang, ký hợp đồng, thanh toán tháng đầu.
  - Dời lịch: cập nhật `appointment_date`, `started_at`, `end_at` của `Appointment` hiện tại.
- **FM:** xem `Appointment` của cơ sở theo ngày, lọc `staff_id IS NULL` để thấy lịch chưa phân công và gán FS (nghiệp vụ phân công thuộc Flow 5.3, Flow 2 chỉ tiêu thụ kết quả).
- **FS:** xem lịch trong ngày được gán cho mình, kèm khách hàng, khoang chứa và `type`.
- **Cron:** `RentalOrder` đã cọc nhưng quá N ngày vẫn chưa có lịch hẹn nào `Done` (`arrived_at IS NULL`) -> tự hủy đơn, trả khoang về `Available`.

#### 2.2 Check-in và kiểm tra khoang chứa

Toàn bộ tiến trình on-site ghi trên bản ghi `HandoverRecord` đang `IN_PROGRESS` của `RentalOrder` (mỗi đơn chỉ có tối đa một bản ghi như vậy), mỗi bước bật một cờ trong checklist.

- **Check-in:**
  - FS ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - `HandoverRecord` được tạo ở mốc `RentalOrder` chuyển `InProgress`, `result = IN_PROGRESS`.
  - **Xác minh danh tính:** FS đối chiếu giấy tờ tùy thân của người đến với `RentalOrder.customer_id`; nếu khách đã upload ảnh giấy tờ online thì hệ thống hiển thị lại để FS đối chiếu. Đạt -> `is_identity_verified = true`, `identity_verified_at`. Không đạt thì dừng, không cho bật các cờ sau.

- **Kiểm tra khoang chứa:**
  - FS dẫn khách kiểm tra toàn bộ hiện trạng: kích thước, vị trí, vệ sinh, kết cấu, cửa/khóa, hư hại sẵn có. FS nhập `inspection_notes`, `inspection_photos`.
  - Khách đồng ý -> `is_unit_inspected = true`, `unit_inspected_at`; đây là điều kiện mở bước ký hợp đồng.
  - Khách **không đồng ý** -> `result = REJECTED`, `reject_reason`, `completed_at`. Flow 2 kết thúc.

- **Khách từ chối khoang (chuyển về Flow 1):** việc chỉ định lại khoang **không xử lý trong Flow 2**. Hệ thống bắn `HandoverRecord.Rejected`, Flow 1 cho FM chỉ định khoang khác và tạo `ProposalFeedback` mới cho khách duyệt online. `RentalOrder` lấy khoang được chấp nhận mới nhất từ `ProposalFeedback`.

- **No-show:** hết `end_at` mà `arrived_at` vẫn null, cron set `Appointment.status = Canceled` kèm `cancel_reason`, nhắc khách đặt lại lịch. Khoang giữ `Reserved` cho tới ngưỡng N ngày của cron hủy đơn ở 2.1.

- **Đặt lại lịch (sau reject hoặc no-show):** tạo một `Appointment` **mới** (`status = Pending`, `staff_id = null`) + `RentalAppointment` mới, FM phân công FS lại từ đầu. `Appointment` cũ giữ nguyên `staff_id` và `status` làm lịch sử, không set `staff_id` về null.

#### 2.3 Ký hợp đồng và thanh toán tháng đầu tiên

**Ký hợp đồng (sinh `RentalContract`)**
- Điều kiện: `is_identity_verified = true` và `is_unit_inspected = true`.
- Hệ thống sinh hợp đồng từ mẫu đang hiệu lực (Flow 4), điền sẵn: thông tin khách, cơ sở, `unit_id` khách vừa xác nhận, giá thuê, `period`, tiền cọc đã đóng, mốc bắt đầu tính tiền thuê; snapshot `terms_version` để đối chiếu về sau.
- Hiện trạng khoang (`inspection_notes`, `inspection_photos`) ở 2.2 được gắn kèm hợp đồng - liên kết qua `order_id` nên không cần thêm khóa ngoại. Đây là căn cứ đối chiếu khi trả kho ở Flow 2.5.
- Khách ký -> xuất PDF lưu trữ, `RentalContract.status = Signed`, set `is_contract_signed`, `contract_signed_at`.
- **Mốc bắt đầu tính tiền thuê** ghi trên hợp đồng, mặc định theo **chính sách Flow 4** (ví dụ: 1 tuần sau ngày ký, ngày 15 hàng tháng...). Cho phép FS thỏa thuận riêng với khách nhưng **phải được FM duyệt**; mặc định vẫn ưu tiên chính sách để tránh xung đột.

**Thanh toán tháng đầu tiên**
- Hệ thống tạo `Invoice` tiền thuê (prefix `RNT`, gắn `contract_id`) với số tiền **tháng đầu tiên**. Tiền cọc ở Flow 1.3 **không** trừ vào hóa đơn này, cọc giữ riêng tới khi trả kho (2.5.3).
- Khách thanh toán qua VNPay; hệ thống nhận kết quả qua webhook/IPN -> `Invoice.status = Paid`, set `is_payment_settled`, `payment_settled_at`.
- Chưa thanh toán xong trong buổi hẹn: `result` giữ `IN_PROGRESS`, khoang vẫn `Reserved`, **chưa bàn giao khóa**; hóa đơn nằm trong mục "Hóa đơn" của khách để thanh toán online.

#### 2.4 Bàn giao khóa và kích hoạt hợp đồng

- **Điều kiện:** cả 4 cờ trên `HandoverRecord` đều `true` (`is_identity_verified`, `is_unit_inspected`, `is_contract_signed`, `is_payment_settled`). Thiếu cờ nào thì API bàn giao trả lỗi rõ ràng cho FS.
- **FS** bàn giao quyền truy cập theo cấu hình cơ sở/khoang chứa:
  - **Khóa cơ** (`enabledKeyAccess`): giao chìa vật lý, ghi `quantity` để đối chiếu khi trả kho.
  - **Khóa mã số** (`enabledCodeAccess`): hệ thống sinh mã, gửi cho khách qua kênh riêng, FS hướng dẫn đổi mã lần đầu. Mã **không lưu plain text**, chỉ lưu hash.
  - Hai bên xác nhận, khách ký nhận.
- **Hệ thống (một transaction):** tạo `UnitAccessKey`; `StorageUnit`: `Reserved -> Rented`; `RentalContract`: `Signed -> Active`; `HandoverRecord.result = COMPLETED` + `completed_at`; cập nhật `RentalOrder`; gửi email kèm PDF hợp đồng và biên bản bàn giao; bắn `RentalOrder.HandoverCompleted` để Flow 3 theo dõi.

#### Backend flow (chi tiết kỹ thuật)

**Nguyên tắc chung:**
- API của FS/FM yêu cầu đăng nhập và kiểm tra **facility scope**: chỉ thao tác trên `Appointment`/`StorageUnit` thuộc cơ sở mình phụ trách, nếu không trả `403`.
- API của customer áp dụng **ownership check** trên `RentalOrder.customer_id`.
- Mọi bước đổi `StorageUnit.status` chạy trong DB transaction và lock theo `unit_id` để không xung đột với luồng gán khoang của Flow 1.
- Các API bật cờ trên `HandoverRecord` phải kiểm tra cờ tiền nhiệm, không cho nhảy bước.

**a) Lấy slot và đặt lịch (2.1)**
- `GET /api/customer/rental-orders/{id}/appointment-slots`: sinh slot từ giờ hoạt động của cơ sở, loại bỏ slot đã đầy theo số FS khả dụng.
- `POST /api/customer/rental-orders/{id}/appointments`: body `{ appointment_date, started_at, end_at }`. Validate `Invoice` cọc đã `Paid`. Tạo `Appointment(CHECKIN, Pending)` + `RentalAppointment`, bắn `Appointment.Created`.
- `PATCH /api/customer/appointments/{id}`: dời lịch.
- Cron: hủy `RentalOrder` đã cọc quá N ngày chưa check-in, trả khoang về `Available`.

**b) Lịch trình của FS (2.1, 2.2)**
- `GET /api/staff/appointments?date=...`: lịch trong ngày của FS đang đăng nhập.
- `POST /api/staff/appointments/{id}/arrive`: set `arrived_at`, `status = Done`.
- Cron: quá `end_at` mà `arrived_at` null -> `status = Canceled`, `cancel_reason = NoShow`, bắn `Appointment.NoShow`.

**c) Checklist on-site (2.2)**
- `POST /api/staff/handover-records/{id}/verify-identity`: set `is_identity_verified`, `identity_verified_at`.
- `POST /api/staff/handover-records/{id}/inspection`: body `{ inspection_notes, inspection_photos[] }`, set `is_unit_inspected`, `unit_inspected_at`.
- `POST /api/staff/handover-records/{id}/reject`: body `{ reject_reason }`, set `result = REJECTED`, `completed_at`, bắn `HandoverRecord.Rejected`.

**d) Ký hợp đồng và thanh toán (2.3)**
- `POST /api/customer/rental-orders/{id}/contracts`: sinh `RentalContract(Draft)`, snapshot `terms_version`, `unit_id`, giá thuê, mốc bắt đầu tính tiền theo chính sách Flow 4. Chỉ cho phép khi `is_unit_inspected = true`.
- `POST /api/customer/contracts/{id}/sign`: ghi nhận chữ ký, xuất PDF lên storage, `status = Signed`, set `is_contract_signed`, tạo `Invoice(RNT)` tháng đầu.
- `POST /api/invoices/{id}/pay`: khởi tạo phiên VNPay, trả URL redirect.
- `POST /api/webhooks/vnpay`: nhận IPN, lưu `PaymentTransaction`, `Invoice.status = Paid`, set `is_payment_settled`, `payment_settled_at`.

**e) Bàn giao (2.4)**
- `POST /api/staff/handover-records/{id}/complete`: body `{ access_type, access_quantity, customer_signature, note? }`. Backend kiểm tra đủ 4 cờ trước khi ghi.
- Transaction: tạo `UnitAccessKey`, `StorageUnit.status = Rented`, `RentalContract.status = Active`, `HandoverRecord.result = COMPLETED`, cập nhật `RentalOrder`.
- Với khóa mã số: sinh mã ngẫu nhiên, lưu hash, gửi cho khách qua kênh riêng chứ không trả trong response của FS.

**Events phát ra từ Flow 2:**

| Event | Consumer |
|---|---|
| `Appointment.Created` | Flow 5 (FM phân công FS) |
| `Appointment.NoShow` | Hệ thống notify khách, cron hủy đơn |
| `HandoverRecord.Rejected` | Flow 1 (FM chỉ định lại khoang, tạo `ProposalFeedback` mới) |
| `RentalContract.Signed` | Flow 4 (ghi nhận hợp đồng mới), hệ thống tạo `Invoice` tháng đầu |
| `Invoice.Created` (RNT tháng đầu) | Flow 4 (theo dõi doanh thu) |
| `RentalOrder.HandoverCompleted` | Flow 3 (bắt đầu theo dõi khoang đang thuê) |

**Schema:**

Các bảng đã có, Flow 2 chỉ đọc hoặc cập nhật trạng thái:
- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được xử lý trong buổi hẹn.
- [**Invoice**](./db-table-draft.md#invoice) - kiểm tra hóa đơn cọc đã `Paid`, tạo hóa đơn tiền thuê tháng đầu (prefix `RNT`).
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback) - dùng ở Flow 1, Flow 2 chỉ đọc.

Các bảng Flow 2 đề xuất thêm, chi tiết field đã đưa vào `db-table-draft.md`:
- [**Appointment**](./db-table-draft.md#appointment) - lịch hẹn tại cơ sở, dùng chung cho check-in, bàn giao, trả kho, xử lý sự cố.
- [**RentalAppointment**](./db-table-draft.md#rentalappointment) - nối lịch hẹn với đơn hàng.
- [**HandoverRecord**](./db-table-draft.md#handoverrecord) - checklist tiến trình on-site từ check-in tới bàn giao kho.
- [**RentalContract**](./db-table-draft.md#rentalcontract) - hợp đồng thuê, sinh và ký ở 2.3.
- [**UnitAccessKey**](./db-table-draft.md#unitaccesskey) - quyền truy cập khoang chứa đã bàn giao cho khách.

**Phụ thuộc cần các flow khác bổ sung (Flow 2 không tự sửa):**
- Sơ đồ FLOW của mục 1 vẫn ghi `[Đặt cọc] -> [Ký hợp đồng] -> [Thanh toán]`; hai bước cuối đã chuyển sang Flow 2.
- Mục 1.1 chưa có bước tạo `ProposalFeedback` cho khách duyệt khoang online, trong khi Flow 2 giả định khách đã duyệt trước khi chọn lịch hẹn.
- Mục 1.1 tạo lựa chọn lịch hẹn ngay khi FM `Approve`, trong khi MVP chốt chỉ khách đã cọc mới được hẹn lịch.
- Nhánh chỉ định lại khoang khi khách từ chối tại chỗ, kèm chính sách khi khách đã cọc: chênh lệch tiền cọc, phí đổi khoang, xử lý hóa đơn cọc đã xuất, thời điểm nhả khoang cũ, số lần được đổi, ai có quyền đổi, thời hạn đổi trước giờ hẹn.
- Flow 5: bổ sung `enabledKeyAccess` và `enabledCodeAccess` trên `Facility`/`StorageUnit`.
- `RentalOrder.status` đang có hai bộ enum (`db-table-draft.md` vs Flow 3) và `db-table-draft.md` chưa có bảng `StorageUnit` - Levi thống nhất sau khi review xong các flow.

**Advanced Features (not MVP)**
- Lịch hẹn "tham quan kho" cho khách chưa cọc: nhiều khách chung một slot để xem cùng một khoang, cần thêm `type = TOUR` và bỏ ràng buộc 1 slot - 1 khách.
- Chính sách trả trước N tháng thay vì cố định 1 tháng.
- Cho khách tự chọn slot theo lịch trống thực tế của từng FS thay vì slot cố định theo giờ hoạt động cơ sở.
- Nhắc lịch hẹn tự động qua email/SMS trước 24h.
- Cho khách xem ảnh/video khoang chứa trước buổi hẹn để giảm tỉ lệ từ chối tại chỗ.
- eKYC khi đăng ký tài khoản, bước xác minh on-site rút gọn còn đối chiếu nhanh.
- Khóa thông minh điều khiển qua app, bỏ hẳn bước giao chìa khóa vật lý.

### 2.5 Trả kho và bảo trì

**FLOW:**
```
[Yêu cầu trả kho] -> [Hẹn lịch trả] -> [FS kiểm tra khoang] -> [Xử lý phí phát sinh] -> [Thu hồi quyền truy cập] -> [Hoàn cọc] -> [Bảo trì] -> [Khoang về Available]
```

**Vị trí trong vòng đời thuê kho:** Flow 2.5 nhận đầu vào từ Flow 3.4 (khách bấm yêu cầu trả kho) và xử lý toàn bộ phần on-site. Kết thúc khi khoang hoàn tất bảo trì và quay về `Available`, sẵn sàng cho yêu cầu mới ở Flow 1. Đây là điểm đóng vòng đời của một `RentalOrder`.

**ĐÃ CHỐT:**
- `Appointment.type` thêm giá trị **`RETURN`** cho buổi hẹn trả kho, `RentalAppointment` cũng được tạo cho loại này. Levi sẽ chốt lại bộ `type` sau khi các flow ổn định.
- MVP chỉ hỗ trợ **luồng trả do khách chủ động yêu cầu**; FM hủy hộ yêu cầu trả kho không thuộc MVP.
- Biên bản trả kho tách thành bảng **`CheckoutRecord`** riêng, vì `HandoverRecord` chỉ chịu trách nhiệm tới khâu bàn giao và kết thúc vòng đời sau đó.
- Bổ sung hai prefix mã hóa đơn: **`DMG`** (phí hư hỏng) và **`CLN`** (phí vệ sinh).
- Khi phát sinh phí, **FS hoặc FM tạo hóa đơn trong hệ thống**, khách thanh toán qua VNPay. Không thu tiền mặt trong MVP.
- Tiền cọc **được hoàn lại cho khách** sau khi đối trừ hết các khoản phát sinh, không trừ vào kỳ thuê cuối.
- Cơ chế khách **phản đối đánh giá hư hỏng** của FS không thuộc MVP; đánh giá của FS là kết quả cuối cùng.
- Hệ thống **tự động tính phí lưu giữ** khi khoang còn đồ không thuộc MVP; FM hoặc FS gửi hóa đơn thủ công theo chính sách của BOM (Flow 4).
- **Luồng hoàn tiền tự động (refund) không thuộc MVP.** Khi làm sẽ cần bổ sung chiều giao dịch cho `PaymentTransaction` hoặc bảng refund riêng, kèm mã giao dịch hoàn để đối soát. Nguyên tắc dự kiến: hoàn về đúng phương tiện khách đã thanh toán.
- Thời gian bảo trì **được cấu hình bởi BOM ở Flow 4**, không hard-code (mặc định 1-3 ngày). FM không tự sửa, chỉ gửi yêu cầu để BOM chỉnh.
- Khách quá hạn không trả, không liên lạc được hoặc bỏ lại tài sản trong khoang: **thuộc Flow 6**, đã note để xử lý sau.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 3: bản ghi `ReturnRequest` (`status = Assigned`, đã có `assigned_staff_id` do FM phân công ở Flow 3.4).
- Flow 2: `HandoverRecord` của đơn (`inspection_notes`, `inspection_photos`) để đối chiếu hiện trạng lúc nhận và lúc trả; `UnitAccessKey` để thu hồi quyền truy cập.
- Flow 4: mức phí hư hỏng, vệ sinh, trả trễ, lưu giữ; chính sách xử lý tiền cọc; thời gian bảo trì do BOM cấu hình.

**Context:** Khách kết thúc nhu cầu thuê và muốn trả lại khoang chứa, cần có người kiểm tra hiện trạng, xử lý các khoản phát sinh và thu hồi quyền truy cập trước khi khoang được cho thuê lại.

#### 2.5.1 Tiếp nhận yêu cầu và hẹn lịch trả kho

- **Hệ thống:** khi `ReturnRequest` chuyển `Assigned` ở Flow 3.4, tạo `Appointment(type = RETURN, status = Pending)` theo `preferred_date` của khách kèm `RentalAppointment`, `staff_id` lấy từ `ReturnRequest.assigned_staff_id`. Việc phân công FS đã do FM làm ở Flow 3, Flow 2.5 không lặp lại.
- **Customer:** trước ngày hẹn phải tự dọn toàn bộ tài sản ra khỏi khoang. Hệ thống nhắc các điều kiện để được nhận lại cọc: khoang trống, không hư hỏng, không còn hóa đơn `Unpaid`.

#### 2.5.2 Kiểm tra và bàn giao lại khoang chứa

- **FS:**
  - Ghi nhận khách đến: set `Appointment.arrived_at`, `status = Done`.
  - Mở `HandoverRecord` đã lập ở **Flow 2** để lấy hiện trạng khoang **lúc bàn giao** (`inspection_notes`, `inspection_photos`). Flow 2.5 chỉ **đọc** bảng này, không tạo mới - đây là hiện trạng hai bên đã cùng xác nhận trước khi khách ký hợp đồng, nên là căn cứ đối chiếu duy nhất.
  - Kiểm tra hiện trạng **lúc trả** theo checklist rồi so với mốc trên: khoang đã dọn trống chưa, tình trạng vệ sinh, hư hỏng kết cấu/cửa/khóa/thiết bị, chụp ảnh hiện trạng.
  - Chênh lệch giữa hai mốc chính là căn cứ tính phí `DMG`/`CLN` ở 2.5.3.
  - Lập `CheckoutRecord`, khách ký xác nhận. Mỗi cột mốc (dọn trống, kiểm tra xong, thu hồi quyền truy cập, thanh toán phí, xử lý cọc) bật một cờ kèm timestamp để FM/FS theo dõi tiến độ khi buổi trả kho kéo dài nhiều ngày.
  - Các trường hợp:
    - **Đạt yêu cầu:** không phát sinh phí, sang 2.5.3.
    - **Còn tài sản trong khoang:** FS ghi nhận, `CheckoutRecord.result = PENDING_ITEMS`, **không hoàn tất trả kho**. Hệ thống tạo một `Appointment(type = RETURN)` mới để khách quay lại dọn nốt. Trong thời gian này:
      - **Không thu hồi `UnitAccessKey`** - khách vẫn cần quyền truy cập để vào lấy đồ.
      - Khoang giữ `Rented`, `RentalContract` giữ `Active`; nếu vượt hạn hợp đồng thì phát sinh phí trả kho trễ theo Flow 4.
      - Thời hạn cho dọn tiếp và mức phí lưu giữ theo **chính sách do BOM viết ở Flow 4**. Hệ thống **không tự động tính phí** khi khoang còn đồ - ngoài MVP; FM hoặc FS gửi hóa đơn thủ công.
    - **Khoang bẩn hoặc hư hỏng:** FS ghi nhận chi tiết kèm ảnh, hệ thống tạo hóa đơn phí tương ứng ở 2.5.3.

- **Thu hồi quyền truy cập:**
  - Chỉ thực hiện khi `CheckoutRecord.result` **không phải** `PENDING_ITEMS` (khoang đã dọn trống hoàn toàn).
  - Khóa cơ: FS thu lại chìa, đối chiếu `UnitAccessKey.quantity` đã giao ở 2.4. Thiếu chìa thì tính phí mất chìa/thay khóa theo Flow 4.
  - Khóa mã số: hệ thống vô hiệu hóa mã ngay khi biên bản được xác nhận.
  - `UnitAccessKey.status` -> `Revoked`, ghi `revoked_at`.

#### 2.5.3 Xử lý phí phát sinh và tiền cọc

- **Các khoản có thể phát sinh:** phí vệ sinh (`CLN`), phí hư hỏng (`DMG`), phí mất chìa/thay khóa, phí trả kho trễ, và các hóa đơn `Unpaid` còn tồn đọng của hợp đồng. Mức phí lấy theo cấu hình Flow 4, Flow 2.5 **không tự định nghĩa mức phí**.
- **Đối trừ tiền cọc:**
  - Cọc lớn hơn tổng phí: hoàn lại phần chênh lệch cho khách (MVP ghi nhận số tiền phải hoàn, FM xử lý thủ công).
  - Cọc nhỏ hơn tổng phí: tạo hóa đơn phần còn thiếu, khách phải thanh toán trước khi hoàn tất trả kho.

#### 2.5.4 Bảo trì và mở lại cho thuê

- Sau khi `CheckoutRecord` được xác nhận và không còn khoản phải thu bắt buộc: `StorageUnit.status -> Maintenance` kèm `maintenance_started_at`, `RentalOrder` chuyển trạng thái kết thúc, `RentalContract.status -> Ended`, `Appointment.status -> Done`.
- Hết thời gian bảo trì (theo cấu hình của BOM ở Flow 4), khoang tự chuyển về `Available` và có thể được gán cho yêu cầu mới ở Flow 1.
- Khoang hư hỏng cần sửa dài hơn: FM giữ ở `Maintenance` cho tới khi xử lý xong, không để cron tự mở.

#### Backend flow (chi tiết kỹ thuật)

**a) Tiếp nhận yêu cầu trả (2.5.1)**
- Consumer của `ReturnRequest` khi chuyển `Assigned`: tạo `Appointment(RETURN, Pending)` + `RentalAppointment`, gán `staff_id` theo `ReturnRequest.assigned_staff_id`, notify FS.

**b) Kiểm tra và lập biên bản (2.5.2)**
- `GET /api/staff/appointments/{id}/handover-record`: lấy `HandoverRecord` của đơn để FS đối chiếu hiện trạng lúc nhận.
- `POST /api/staff/appointments/{id}/inspection`: body `{ is_empty, cleanliness, damages[], photos[], returned_key_quantity, note? }`. Backend tạo `CheckoutRecord` và tính danh sách phí dự kiến theo cấu hình Flow 4, trả về cho FS xem trước. Chưa tạo `Invoice` ở bước này.
- `POST /api/staff/appointments/{id}/finalize-return`: chốt biên bản, tạo các `Invoice` phát sinh, thu hồi `UnitAccessKey`.

**c) Đối trừ và hoàn cọc (2.5.3)**
- Trong một transaction: tổng phí phát sinh + hóa đơn tồn đọng so với số tiền cọc đã thu (`Invoice` prefix `DEP` ở trạng thái `Paid`).
- Thiếu: tạo `Invoice` phần chênh lệch, chặn bước chuyển `Maintenance` cho tới khi thanh toán xong.
- Dư: MVP ghi nhận số tiền phải hoàn để FM xử lý thủ công.

**d) Bảo trì và mở lại (2.5.4)**
- Transaction khi hoàn tất: `StorageUnit.status = Maintenance` + `maintenance_started_at`, `RentalOrder` và `RentalContract` chuyển trạng thái kết thúc.
- Cron hằng ngày quét các khoang `Maintenance` đã đủ thời gian cấu hình và không bị FM giữ lại, chuyển về `Available`, bắn `StorageUnit.BecameAvailable`.
- Lock theo `unit_id` khi chuyển trạng thái để không xung đột với luồng gán khoang của Flow 1.

**Events phát ra từ Flow 2.5:**

| Event | Consumer |
|---|---|
| `Appointment.Created` (type = RETURN) | Hệ thống notify FS được phân công |
| `RentalOrder.ReturnCompleted` | Flow 3 (đóng vòng theo dõi), Flow 4 (ghi nhận doanh thu) |
| `Invoice.Created` (`DMG`/`CLN`/phí trễ) | Flow 4 (theo dõi doanh thu) |
| `StorageUnit.BecameAvailable` | Flow 1 (khoang sẵn sàng cho yêu cầu mới), wishlist nếu có |

**Schema:**

- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được đóng lại sau khi trả kho.
- [**Invoice**](./db-table-draft.md#invoice) - hóa đơn phí phát sinh khi trả kho, thêm prefix `DMG` và `CLN`.
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction) - cơ chế hoàn tiền chưa làm trong MVP.
- [**Appointment**](./db-table-draft.md#appointment), [**RentalAppointment**](./db-table-draft.md#rentalappointment), [**UnitAccessKey**](./db-table-draft.md#unitaccesskey), [**RentalContract**](./db-table-draft.md#rentalcontract) - dùng chung với Flow 2.
- [**CheckoutRecord**](./db-table-draft.md#checkoutrecord) - biên bản trả kho, tách riêng khỏi `HandoverRecord`.

**NOTES**
- Flow 3 mô tả nhánh thuận: FS xác nhận hoàn tất, không phát sinh phí hư hại -> `StorageUnit.status = MAINTENANCE`, `RentalContract.status = Completed`. Flow 2.5 viết khớp với mô tả đó và bổ sung nhánh **có** phát sinh phí.
- Toàn bộ mức phí trong Flow 2.5 phụ thuộc cấu hình của Flow 4. Nếu Flow 4 chưa chốt danh mục phí thì phần này chỉ dừng ở mô tả nghiệp vụ, chưa code được.
- Cron chuyển `Maintenance -> Available` chưa có flow nào nhận phần implement: Flow 3 chỉ nói khoang quay về `Available` sau bảo trì, Flow 5 cho FM chuyển thủ công. Flow 2.5 mô tả cron này ở 2.5.4 và sẵn sàng nhận nếu nhóm đồng ý.
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
NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố
