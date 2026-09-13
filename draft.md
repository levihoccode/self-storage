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
### 2.5 Trả kho và bảo trì
### 3. Quản lý kho đã thuê (Customer)
**FLOW:**
```
[Dashboard tổng quan] -> [Chi tiết khoang chứa/hợp đồng] -> [Gia hạn / Trả kho / Thanh toán / Báo sự cố]
```

**Vị trí trong vòng đời thuê kho:** Flow 3 bắt đầu ngay khi `RentalContract` được tạo (cuối Flow 2), và là flow có thời gian sống dài nhất — chạy liên tục cho tới khi khoang được trả và chuyển `MAINTENANCE`, `RentalContract.status = Completed`, khoang quay về `Available` cho Flow 1.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 2: tạo ra `RentalContract` (đề xuất) ngay sau khi `RentalOrder.status = Done`.
- Flow 4: cung cấp `FeeRule`/`FeeType` để tính hóa đơn hàng tháng, phí gia hạn, phí quá hạn (không tự định nghĩa mức phí trong Flow 3).
- Flow 5: cung cấp thông tin facility hiển thị kèm khoang chứa.

**Context:** Khách hàng đã có ít nhất một `RentalContract` ở trạng thái `Active` và muốn theo dõi, quản lý các khoang chứa đang thuê thông qua tài khoản cá nhân.

**Flow tổng quát:** Khách đăng nhập -> xem danh sách khoang đang thuê (dashboard) -> chọn 1 khoang để xem chi tiết -> từ màn hình chi tiết, khách có thể gia hạn, yêu cầu trả kho, xem/thanh toán hóa đơn hoặc gửi yêu cầu hỗ trợ sự cố.

**Đề xuất schema `RentalContract` (cần team xác nhận):**
- order_id (1 - 1: RentalOrder) — hợp đồng luôn xuất phát từ 1 RentalOrder đã `Done`
- unit_id (N - 1: StorageUnit)
- customer_id (N - 1: Account)
- start_date, end_date
- signed_at, file_url — ngày ký + link PDF hợp đồng
- deposit_amount, monthly_price
- status (`Active` / `PendingReturn` / `Completed` / `Terminated`)
- created_at

#### 3.1 Dashboard tổng quan
**Details:**
- Hiển thị danh sách các `RentalContract` khách đang thuê, gồm:
  + facility (join qua `StorageUnit.facility_id`)
  + unit_id / unit_type / size
  + rental_status (`Active`, `Expiring Soon`, `Overdue`, `Pending Return`, ...) — derived từ `RentalContract.status` + `end_date`
  + start_date, end_date
  + payment_status (đã thanh toán / còn nợ / quá hạn) — tính từ `Invoice WHERE contract_id = :id`
- Lọc/sắp xếp theo facility, trạng thái, ngày hết hạn gần nhất.
- **Quyết định:** khách thuê nhiều khoang ở nhiều chi nhánh sẽ được **tách hiển thị theo từng facility** (nhóm/tab theo facility), không gộp chung 1 danh sách phẳng.
- Cảnh báo nổi bật (badge/màu) cho các khoang sắp hết hạn (≤ 7 ngày) hoặc đang quá hạn thanh toán.
- Với khách chỉ thuê 1 khoang tại 1 facility, hệ thống có thể bỏ qua bước dashboard và đưa thẳng vào 3.2.

#### 3.2 Chi tiết khoang chứa & hợp đồng
**Details:**
- Thông tin khoang chứa: facility, vị trí, type, size — chỉ hiển thị (mã truy cập/chìa khóa do FS quản lý ở Flow 2).
- Thông tin hợp đồng lấy từ `RentalContract`: signed_at, start_date/end_date, deposit_amount, monthly_price; cho phép xem/tải `file_url` (PDF).
- Lịch sử hóa đơn: `Invoice WHERE contract_id = :id` (tiền thuê hàng tháng, phí gia hạn, phí quá hạn) — mỗi hóa đơn `Unpaid` có thể thanh toán trực tiếp từ đây. Số tiền tính theo `FeeRule`/`FeeType` (Flow 4).
- Lịch sử check-in/check-out on-site (tham chiếu Flow 2).
- Action khả dụng tùy theo `rental_status` — giống bản trước (`Active` / `Expiring Soon` / `Overdue` / `Pending Return`).

#### 3.3 Yêu cầu gia hạn
**Details:**
- Khách chọn [Gia hạn], nhập số tháng muốn gia hạn thêm.
- Hệ thống tạo `ExtendRequest(contract_id, extra_months, status=PendingApproval)` — chuyển cho FM duyệt theo `FeeRule` gia hạn (Flow 6/Flow 4).
- Sau khi FM duyệt, hệ thống tạo `Invoice(contract_id, type=Extension)` theo mã `INV-EXT-{facility}-{YYMMDD}-{random}`. `RentalContract.end_date` chỉ update sau khi `Invoice` được thanh toán thành công (`PaymentTransaction.status = Success`).
- **Quyết định:** khách **không được hủy** yêu cầu gia hạn sau khi đã gửi. Nếu gửi nhầm, cần liên hệ FM để FM chủ động từ chối.

#### 3.4 Yêu cầu trả kho
**Details:**
- Khách chọn [Yêu cầu trả kho], chọn ngày dự kiến trả và (tuỳ chọn) lý do.
- `RentalContract.status` chuyển `PendingReturn`, hệ thống tạo lịch hẹn on-site để FS xác nhận tình trạng khoang (Flow 2.5).
- Sau khi FS xác nhận hoàn tất, không phát sinh phí hư hại: `StorageUnit.status = MAINTENANCE` (1-3 ngày), `RentalContract.status = Completed`.
- Đây là **điểm kết thúc vòng đời** của Flow 3 cho khoang này: sau `MAINTENANCE`, khoang quay về `Available`, sẵn sàng cho Flow 1.
- **Quyết định:** khách **không được hủy** yêu cầu trả kho sau khi đã gửi. Cần liên hệ FM/FS trực tiếp để hủy hộ.

#### 3.5 Gửi yêu cầu hỗ trợ sự cố
**Details:**
- Khách chọn [Báo sự cố] (mất chìa khóa, lỗi mã truy cập, khoang hư hỏng, hỗ trợ khác).
- Hệ thống tạo `SupportRequest(contract_id, unit_id, status=Open)`, khách theo dõi trạng thái ngay tại đây (xử lý chi tiết ở Flow 7). Không đổi `rental_status`.

#### 3.6 Backend flow (chi tiết kỹ thuật)
**Nguyên tắc chung:**
- Mọi API yêu cầu ownership check: `RentalContract.customer_id` phải khớp `current_user`.
- `rental_status` là derived field, tính từ `RentalContract.status` + `end_date`, không lưu cứng.

**a) Dashboard (3.1) — `GET /api/customer/contracts`**
- Query: `RentalContract WHERE customer_id = :current_user`, JOIN `Facility`, `StorageUnit`; JOIN `Invoice WHERE contract_id = ...` để tính `payment_status`.
- Response group theo `facility_id` (mảng facility, mỗi facility chứa mảng contracts con).

**b) Chi tiết (3.2) — `GET /api/customer/contracts/{id}`**
- Trả về `RentalContract` + danh sách `Invoice` (order by created_at desc) + `available_actions` tính sẵn theo `rental_status`.
- Logic tính `rental_status`:
  ```
  if status == PendingReturn: return "Pending Return"
  if status == Completed: return "Completed"
  if now > end_date: return "Overdue"
  if end_date - now <= 7 days: return "Expiring Soon"
  return "Active"
  ```

**c) Gia hạn (3.3) — `POST /api/customer/contracts/{id}/extend-requests`**
- Validate `RentalContract.status == Active` (không cho khi `PendingReturn`).
- Tạo `ExtendRequest(status=PendingApproval)`. Không expose API hủy.
- FM approve → tạo `Invoice(contract_id, type=Extension, code=INV-EXT-...)`.
- `PaymentTransaction.status = Success` (webhook) → transaction: `RentalContract.end_date += extra_months`, `ExtendRequest.status = Completed`.

**d) Trả kho (3.4) — `POST /api/customer/contracts/{id}/return-requests`**
- Validate `RentalContract.status == Active` (chặn nếu có `ExtendRequest` đang `PendingApproval`).
- Transaction: `RentalContract.status = PendingReturn`, tạo lịch hẹn on-site cho FS (Flow 2.5).
- FS xác nhận (Flow 2.5) → `StorageUnit.status = MAINTENANCE`, `RentalContract.status = Completed`.

**e) Báo sự cố (3.5) — `POST /api/customer/contracts/{id}/support-requests`**
- Tạo `SupportRequest(contract_id, unit_id, status=Open)` → notify FS (Flow 7).

**f) Thanh toán hóa đơn — `POST /api/invoices/{id}/pay`**
- Tạo `PaymentTransaction(invoice_id, status=Pending)` + payment session (visa card only, theo NOTE của bảng).
- Webhook cổng thanh toán trả về `gateway_transaction_no`, `response_payload` (raw log đối soát) → cập nhật `PaymentTransaction.status = Success/Failed`.
- Nếu `Success`: `Invoice.status = Paid` → trigger theo `type`: `Extension` cập nhật `end_date` (xem 3.3), `RNT` (tiền thuê định kỳ) chỉ đóng invoice, phí quá hạn thì clear cảnh báo.
- Nếu `Failed`: lưu `failure_reason`, giữ `Invoice.status = Unpaid` để khách thử lại.

**Concurrency cần lưu ý:**
- Lock theo `RentalContract.id` khi tạo `ExtendRequest`/`ReturnRequest` cùng lúc, tránh race.
- Tính `rental_status` on-the-fly khi validate action, không tin cache cũ.

**Events phát ra từ Flow 3:**
| Event | Consumer |
|---|---|
| `ExtendRequest.Created` | Flow 6 (FM duyệt gia hạn) |
| `RentalContract.ReturnRequested` | Flow 2.5 (FS xử lý bàn giao) |
| `SupportRequest.Created` | Flow 7 (FS xử lý sự cố) |
| `RentalContract.BecameOverdue` | Flow 6 (tính phí quá hạn), notify email/SMS |

**Schema:**
- `RentalOrder` — giai đoạn trước ký hợp đồng (Flow 1/2 sở hữu, Flow 3 chỉ đọc để biết nguồn gốc).
- **`RentalContract`** — **đề xuất mới**, cần team xác nhận field trước khi implement (xem box cảnh báo đầu mục).
- `Invoice`, `PaymentTransaction` — theo schema thật đã có.
- `ExtendRequest`, `SupportRequest` — cần bổ sung vào db-table-draft.md, tham chiếu `contract_id` thay vì `order_id`.

**NOTES**
- **Mới phát sinh:** cần team chốt field chính thức cho `RentalContract` — nếu tên bảng/field khác đề xuất trên, phải update lại toàn bộ mục 3.6.
- Cần chốt: FM/FS hủy hộ yêu cầu gia hạn/trả kho có cần log lý do/xác nhận với khách trước không.

**Advanced Features (not MVP)**
- Tự động nhắc gia hạn qua email/SMS trước X ngày hết hạn.
- Cho khách xem lịch sử đầy đủ các khoang đã từng thuê (kể cả `Completed`).
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố
