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

**Facility Manager - Quản lý cơ sở**: Trưởng kho của từng chi nhánh, chỉ phụ trách các thông tin liên quan đến cơ sở được giao
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
- Trạng thái (`StorageUnit.status`):
  - `Available`: sẵn sàng cho thuê
  - `Reserved`: đã được đặt cọc, giữ cho khách tới khi bàn giao (Flow 2)
  - `Rented`: đã bàn giao cho khách, đang có hợp đồng `Active`
  - `Maintenance`: đang bảo trì (1-3 ngày sau khi khách trả kho) hoặc đang sửa chữa sự cố
- Trạng thái được chuyển sang `Reserved` ngay khi khách chuyển tiền đặt cọc thành công
- Khóa tạm 5-10 phút khi khách đang thanh toán cọc (Flow 1.3) **không phải là một status**, chỉ là lock ngắn hạn (ví dụ Redis key có TTL) để chặn thanh toán song song
- Khách không được yêu cầu đặt kho đã được đặt cọc
- NOTES:
  - Đang phân vân việc có nên thêm 1 trạng thái cho khoang chứa là `OnHold` (tạm giữ cho khách đặt và đã được approve nhưng chưa đặt cọc) để giữ kho trong ngắn hạn (24h timeout), nếu không có `OnHold`, đơn đặt mặc dù đã `Approved` nhưng nếu chưa đặt cọc, các đơn tới sau và đặt cọc có thể chiếm khoang chứa đó.
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
      - `RentalRequest.status` sang `Approved`, lưu `unit_id`, `processed_by`, `processed_at`.
      - **Trong trường hợp email chưa có tài khoản:**
        - `RentalRequest.customer_id` giữ null. Trạng thái "đã duyệt nhưng chưa có tài khoản" chính là `status = Approved AND customer_id IS NULL`, lưu trực tiếp trong DB (không dùng Redis/PG Cache để tránh mất yêu cầu khi cache bị xóa).
        - `RentalOrder`, hóa đơn và yêu cầu chọn lịch hẹn sẽ được tạo khi khách đăng ký tài khoản trong thời gian quy định (Flow 1.2 Case A). Quá thời gian, job định kỳ chuyển `RentalRequest.status` sang `Expired`.
      - **Trong trường hợp email đã có tài khoản:**
        - Gán `RentalRequest.customer_id`.
        - Hệ thống tạo một bản ghi trong `RentalOrder` (`status = AwaitingDeposit`, `unit_id` = khoang FM chỉ định) để chờ khách đặt cọc.
        - Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
          - code: INV-DEP-XX-XXXXXX-XXXX
          - title: "Đặt cọc khoang chứa A"
          - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
          - amount: ...
          - due_date: hạn thanh toán cọc, quá hạn thì `RentalOrder.status = Expired`, `Invoice.status = Canceled`
        - Hệ thống gửi một thông báo/email thành công đến khách hàng kèm theo thông tin khoang chứa.
        - Hệ thống tạo một lựa chọn lịch hẹn on-site cho khách hàng.
    - **Không tìm thấy khoang chứa thích hợp**: 
      - Chuyển status sang `Rejected` và nhập `reject_reason`: "Hết khoang chứa phù hợp tại chi nhánh".
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
  - **Trong trường hợp yêu cầu được Approved nhưng chưa đặt cọc, và đã có người khác đặt cọc: (chưa chốt)**
    - Phương án 1 (gợi ý khoang tương đương): hệ thống bắn thông báo/email "Khoang M-101 đã có người cọc trước. Cơ sở hiện vẫn còn các khoang M-102, M-103 cùng kích thước. Bấm vào đây để giữ khoang tương đương." -> tạo `RentalOrder` mới (hoặc cập nhật `RentalOrder.unit_id`) cho khoang tương đương.
    - Phương án 2 (chuyển sang danh sách mong muốn - Wish Lists): Yêu cầu của các khách còn lại tự động chuyển status sang `Wishlisted` (cần bổ sung status này vào `RentalRequest` nếu chốt). Nếu Khách A sau đó hủy cọc hoặc bùng hợp đồng, những người trong danh sách chờ sẽ nhận được thông báo để đặt cọc.

**Schema:**
- [**RentalRequest**](./db-table-draft.md#rentalrequest) - chứa các thông tin được gửi từ form trên website.
- [**RentalOrder**](./db-table-draft.md#rentalorder) - chứa các thông tin đơn hàng đã được `Approved` từ FM, sử dụng cho việc hẹn lịch của FS và khách hàng để tư vấn, ký hợp đồng, xem khoang tại kho.
- [**Invoice**](./db-table-draft.md#invoice) -  chứa thông tin thanh toán của khách hàng (hóa đơn)

**NOTES**
- Nguồn gốc hóa đơn xác định bằng `Invoice.type` (Deposit/Rental/Extension/Penalty/Service), không suy ra từ việc `order_id`/`contract_id` null — xem NOTES của `Invoice`.
- Entry trong list yêu cầu đặt khoang chứa của FM không có facility vì khi đặt, khách chỉ định một chi nhánh cụ thể và người quản lý tại chi nhánh đó sẽ nhận được yêu cầu => không cần liệt kê facility field.
- Có thể phát triển thêm phần wishlist giành cho các khoang chứa đều không available, nhưng tự động gửi thông báo và đăng ký ngay khi có bất kỳ khoang chứa nào trống (có thể dùng filter).

**Advanced Features (not MVP)**
- Tự động quá trình duyệt.
- Cho khách chỉ định cụ thể khoang chứa để thuê. -> không tối ưu layout khi để khách tự chọn, cần tìm cách hoặc kệ nó luôn đi :))
- Cho khách đặt nhiều khoang chứa trong 1 request. -> cần lưu ý về việc các khoang chứa có cần liên tục nhau hay không, tính toán ra sao nếu không đủ, ...
#### 1.2 Khách tạo tài khoản
**Case A: Sau khi có một yêu cầu được duyệt**

**Context:** Yêu cầu đặt khoang chứa của khách đã được duyệt và cần đặt cọc nhưng chưa có tài khoản.

**Flow tổng quát:** Yêu cầu đã được duyệt và ghi nhận trên hệ thống, khách hàng đăng ký trong thời gian quy định và đơn hàng + hóa đơn + chọn lịch hẹn on-site sẽ được thêm tự động cho tài khoản đó. 

**Details:**
- Sau khi đăng ký, hệ thống query `RentalRequest WHERE customer_email = :email AND status = Approved AND customer_id IS NULL AND processed_at >= now - timeout`
- Nếu có kết quả, với mỗi yêu cầu tìm được:
  - Gán `RentalRequest.customer_id` = tài khoản vừa tạo.
  - Kiểm tra `unit_id` còn `Available` (chưa bị ai đặt cọc). Nếu không còn -> xử lý như trường hợp "đã có người khác đặt cọc" ở Flow 1.1.
  - Hệ thống tạo một bản ghi `RentalOrder` (`status = AwaitingDeposit`, `unit_id` lấy từ `RentalRequest.unit_id`).
- Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
  - code: INV-DEP-XX-XXXXXX-XXXX
  - title: "Đặt cọc khoang chứa A"
  - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
  - amount: ...
  - due_date: hạn thanh toán cọc
- Hệ thống tạo một yêu cầu chọn lịch cho tài khoản.

**Case B: Không có yêu cầu nào được duyệt**

**Context:** Khách tự đăng ký tài khoản (chưa gửi yêu cầu, yêu cầu còn `Pending`/`Rejected`, hoặc đã quá thời gian và `Expired`).

**Details:**
- Query ở Case A không trả về kết quả -> chỉ tạo tài khoản, không tạo `RentalOrder`/`Invoice`.
- Các yêu cầu còn `Pending` gửi bằng email này sẽ được gán `customer_id` khi FM duyệt (Flow 1.1, trường hợp email đã có tài khoản).
- Yêu cầu đã `Expired` không được khôi phục, khách cần gửi yêu cầu mới.
#### 1.3 Đặt cọc
**Context:** Yêu cầu của khách đã được `Approved`, khách đã có tài khoản và có `RentalOrder` ở trạng thái `AwaitingDeposit` kèm hóa đơn đặt cọc chưa thanh toán.

**Flow tổng quát:**
Khách đăng nhập vào ứng dụng thành công -> vào mục "Thanh toán" -> hiển thị một mục "Đặt cọc để giữ khoang chứa" -> thanh toán thành công -> trạng thái kho chuyển sang `Reserved` cho tới khi bàn giao (Flow 2).

**Details:**
- Khách đăng nhập vào ứng dụng và thanh toán
- Ở bước hiện mã QR để chuyển khoản, khoang chứa sẽ tạm thời bị khóa (5-10p timeout, lock ngắn hạn, không đổi `StorageUnit.status`) để việc thanh toán hoàn tất mà không bị gián đoạn. -> Tránh nhiều người đặt cọc 1 kho cùng lúc
- **Nếu thanh toán thành công:**
  - `PaymentTransaction.status = Success`, `Invoice.status = Paid`, `Invoice.paid_at = now`
  - `StorageUnit.status = Reserved`
  - `RentalOrder.status` chuyển từ `AwaitingDeposit` sang `Pending` (chờ FS xác nhận lịch hẹn)
- **Nếu thanh toán không thành công:** khách hàng quay về trang "Hóa đơn" và khóa tạm thời của khoang chứa được mở.
- **Nếu quá `Invoice.due_date` mà chưa thanh toán:** `RentalOrder.status = Expired`, `Invoice.status = Canceled`.
### 2. Check-in và bàn giao kho
### 2.5 Trả kho và bảo trì
### 3. Quản lý kho đã thuê (Customer)
**FLOW:**
```
[Dashboard tổng quan] -> [Chi tiết khoang chứa/hợp đồng] -> [Gia hạn / Trả kho / Báo sự cố]
```
**Ghi chú schema:** `RentalOrder` chỉ là giai đoạn trước khi ký hợp đồng. Hợp đồng đang hiệu lực được lưu ở [**`RentalContract`**](./db-table-draft.md#rentalcontract).

**Vị trí trong vòng đời:** Flow 3 bắt đầu khi nhận event `RentalOrder.HandoverCompleted` từ Flow 2.4 (khoang đã bàn giao: `RentalContract` `Signed -> Active`, `StorageUnit = Rented`), kết thúc khi Flow 2.5 xác nhận trả kho xong và đóng hợp đồng (`RentalContract.status = Ended`).

**Ranh giới với Flow 2:** `RentalContract` do Flow 2 tạo và quản lý vòng đời (`Draft -> Signed -> Active -> Ended`). Flow 3 **chỉ đọc** hợp đồng và **chỉ cập nhật `end_date`** khi gia hạn thành công (3.3), không tạo mới và không đổi `status`.

**Dữ liệu phụ thuộc:** Flow 2 (`RentalContract`, `Appointment`, `HandoverRecord`) · Flow 4 (bảng giá/phí, số ngày N để cảnh báo sắp hết hạn) · Flow 5 (facility info, phân quyền FM/FS theo facility).
- Flow 3 **không đọc `RentalOrder.status` và không dùng `ProposalFeedback`** — chỉ dùng `RentalContract.order_id` để lấy hóa đơn đặt cọc hiển thị ở 3.2. Quyết định giữ/bỏ `ProposalFeedback` (A5) và enum `RentalOrder` (B3) không làm thay đổi Flow 3.
- `start_date`/`end_date` do Flow 2 tính khi kích hoạt hợp đồng theo mốc bắt đầu tính tiền (A10). Flow 3 chỉ dùng `end_date` (ngày cuối cùng còn hiệu lực) và không tự tính lại từ `start_date`.

**Dữ liệu Flow 3 cần Flow 4 cung cấp** (giá trị dạng số, đọc qua tên key trong file constant dùng chung giữa Flow 3 và Flow 4, không hardcode string ở từng flow):

| Key đề xuất | Kiểu | Dùng ở |
|---|---|---|
| `contract.expiring_soon_days` | Number (ngày) | N trong bảng action 3.2 và thông báo sắp hết hạn 3.1 |
| `extension.invoice_due_days` | Number (ngày) | `Invoice.due_date` của hóa đơn gia hạn (3.3) = ngày FM duyệt + giá trị này |
| Giá gia hạn | Quy tắc | Số tiền hóa đơn gia hạn = `extra_months × RentalContract.monthly_price` (giá đã chốt lúc ký), trừ khi Flow 4 quy định khác |

- `overdue.fee_per_day` và việc tạo `Invoice(type=Penalty)` khi quá hạn **không thuộc Flow 3** (Flow 6); Flow 3 chỉ hiển thị banner quá hạn và các hóa đơn phạt đã được tạo.

#### 3.1 Dashboard tổng quan
**Details:**
- User (Customer) vào trang **"Kho của tôi"**.
- System: query `RentalContract WHERE customer_id = current_user AND status = Active`, JOIN `StorageUnit`, `Facility` để lấy tên chi nhánh/loại kho (`facility_id` lấy qua `StorageUnit`); JOIN `Invoice WHERE contract_id = ...` để tính tình trạng thanh toán.
- System: nhóm kết quả theo `facility_id`, trả về danh sách con theo từng chi nhánh (đã chốt: tách theo facility).
- System: với mỗi hợp đồng, so sánh `today` (ngày hiện tại theo múi giờ hệ thống, không phải timestamp `now`) với `end_date` để tính hiển thị "còn hiệu lực / sắp hết hạn / quá hạn" ngay tại thời điểm trả dữ liệu (không lưu field trạng thái riêng cho việc này).

**NOTES:**
- UI/UX trang này cần thiết kế kỹ để dễ dùng: nút filter theo chi nhánh, theo khoảng thời hạn hợp đồng, sort theo ngày hết hạn gần nhất.
- Hợp đồng gần hết hạn (trước N ngày, N = `contract.expiring_soon_days` do BOM cấu hình ở Flow 4) nên được hệ thống tự động gửi thông báo qua email và thông báo web.
- Khách chỉ có 1 hợp đồng vẫn hiển thị dashboard như bình thường (danh sách 1 item), không tách luồng riêng để vào thẳng 3.2 -> giữ 1 flow duy nhất, đỡ phải xử lý thêm case đặc biệt ở FE/BE.

#### 3.2 Chi tiết khoang chứa & hợp đồng
**Details:**
- User: bấm vào 1 hợp đồng từ dashboard.
- System: trả về thông tin khoang (facility, vị trí, type, size — chỉ đọc), thông tin `RentalContract` (signed_at, start_date/end_date, deposit_amount, monthly_price; nút tải hợp đồng chỉ hiển thị khi có `pdf_url` — hình thức ký và file hợp đồng chờ chốt A7/C1, Flow 3 không phụ thuộc), danh sách `Invoice WHERE contract_id = :id OR order_id = RentalContract.order_id` (bao gồm cả hóa đơn đặt cọc, order theo created_at desc), lịch sử lịch hẹn và bàn giao (`Appointment` + `HandoverRecord` của Flow 2, chỉ đọc).
- System: tính toán và trả về danh sách action khả dụng (`available_actions`) theo bảng event dưới đây — FE chỉ render theo mảng này, không tự suy luận business rule.

**Bảng action/event (thay cho khái niệm status `Expiring Soon` — không cần lưu riêng, tính trực tiếp từ `end_date`):**

**Bước 1 — theo thời hạn hợp đồng** (so sánh theo ngày, `end_date` là ngày cuối cùng còn hiệu lực):

| Điều kiện | Hành động hiển thị cho khách |
|---|---|
| `today <= end_date` (còn hiệu lực) | [Trả kho], [Yêu cầu gia hạn], [Báo sự cố] |
| `end_date - today <= N ngày` (N theo policy Flow 4), vẫn còn hiệu lực | Như trên + banner nhắc gia hạn, làm nổi bật nút [Gia hạn] |
| `today > end_date` (quá hạn) | [Trả kho], [Báo sự cố] + banner "Hợp đồng đã quá hạn, vui lòng liên hệ FM để gia hạn/xử lý phí" (Flow 6). **Ẩn [Gia hạn]** — không gia hạn qua web khi đã quá hạn |

**Bước 2 — theo yêu cầu đang mở** (ghi đè bước 1, vì không được vừa gia hạn vừa trả kho — xem 3.3, 3.4):

| Yêu cầu đang mở | Ẩn | Hiển thị thêm |
|---|---|---|
| `ExtendRequest.status = PendingApproval` | [Gia hạn], [Trả kho] | Trạng thái "Đang chờ FM duyệt gia hạn" + [Hủy yêu cầu gia hạn] |
| `ExtendRequest.status = ApprovedPendingPayment` | [Gia hạn], [Trả kho] | [Thanh toán gia hạn] (dẫn tới hóa đơn `Extension`, hiển thị `due_date`) |
| `ReturnRequest.status = Pending` | [Gia hạn], [Trả kho] | Trạng thái "Đang chờ phân công nhân viên" + [Hủy yêu cầu trả kho] |
| `ReturnRequest.status = Assigned` | [Gia hạn], [Trả kho] | Trạng thái "Đã phân công nhân viên, ngày hẹn dự kiến `preferred_date`" |

- [Báo sự cố] luôn hiển thị khi hợp đồng `Active` (kể cả quá hạn hoặc đang có yêu cầu mở) — sự cố khoang/chìa khóa vẫn có thể xảy ra bất kỳ lúc nào.

#### 3.3 Yêu cầu gia hạn
**Details:**
- User: bấm [Gia hạn], nhập số tháng muốn gia hạn thêm.
- System: tạo `ExtendRequest(contract_id, extra_months, status=PendingApproval)`.
- System: notify FM để duyệt (chi tiết duyệt thuộc Flow 6).
- FM duyệt → System: tạo `Invoice(contract_id, type=Extension, due_date)` theo bảng phí Flow 4, gán `ExtendRequest.invoice_id`, `approved_by`, `processed_at`, `status = ApprovedPendingPayment` → notify khách (web + email) kèm số tiền và `due_date` của hóa đơn gia hạn.
- FM từ chối → System: `status = Rejected`, lưu `reject_reason`, `approved_by`, `processed_at` → notify khách (web + email) kèm lý do. Khách có thể gửi yêu cầu gia hạn mới nếu hợp đồng còn hiệu lực.
- Khách thanh toán thành công → System: cập nhật `RentalContract.end_date += extra_months`, `ExtendRequest.status = Completed` → notify khách `end_date` mới.
  - Cộng theo tháng lịch vào `end_date` hiện tại (không tính lại từ `start_date` hay ngày thanh toán). Nếu ngày không tồn tại ở tháng đích thì lấy ngày cuối tháng (ví dụ 31/01 + 1 tháng = 28/02 hoặc 29/02).
- Quá `Invoice.due_date` mà chưa thanh toán → job định kỳ (3.6 g): `ExtendRequest.status = Expired`, `Invoice.status = Canceled` (giải phóng ràng buộc để khách có thể gửi yêu cầu gia hạn mới) → notify khách.
- **Ràng buộc:** không cho tạo khi hợp đồng đã có `ExtendRequest` đang mở (`PendingApproval`/`ApprovedPendingPayment`) hoặc `ReturnRequest` đang mở (`Pending`/`Assigned`) — khách không thể vừa gia hạn vừa trả kho.
- **Hủy yêu cầu:** User có thể bấm [Hủy yêu cầu gia hạn] và nhập `cancel_reason` (tuỳ chọn) **khi `ExtendRequest.status = PendingApproval`** (FM chưa duyệt). Sau khi FM đã duyệt, hệ thống **không cho hủy qua web** — khách cần liên hệ FM trực tiếp.

#### 3.4 Yêu cầu trả kho
**Details (chỉ mô tả tới bước FM phân công FS — phần xử lý on-site sau đó thuộc Flow 2.5):**
- User: bấm [Yêu cầu trả kho], chọn `preferred_date`, nhập lý do (tuỳ chọn).
  - Validate `preferred_date >= today` (không chọn ngày trong quá khứ). Giờ/ca hẹn cụ thể do Flow 2.5 chốt khi tạo `Appointment`.
  - Được phép gửi cả khi hợp đồng đã quá hạn (`today > end_date`) — khách quá hạn chính là trường hợp cần trả kho nhất.
- System: tạo bản ghi mới trong bảng riêng **`ReturnRequest`** (xem lý do tách bảng bên dưới), `status = Pending`.
- System: notify FM.
- FM: phân công FS (thuộc facility của khoang) xử lý yêu cầu → System: cập nhật `ReturnRequest.assigned_staff_id`, `status = Assigned`, notify khách → trigger sang Flow 2.5 để FS xử lý on-site (lịch hẹn trả kho, `CheckoutRecord`, cập nhật `StorageUnit`/`RentalContract` khi hoàn tất — **đã được Flow 2.5 mô tả, không lặp lại ở đây**).
- **Ràng buộc:** không cho tạo khi hợp đồng đã có `ReturnRequest` đang mở (`Pending`/`Assigned`) hoặc `ExtendRequest` đang mở (`PendingApproval`/`ApprovedPendingPayment`). Nếu gia hạn còn `PendingApproval`, khách hủy yêu cầu gia hạn trước rồi mới gửi yêu cầu trả kho.
- **Hủy yêu cầu:** User có thể bấm [Hủy yêu cầu trả kho] và nhập `cancel_reason` (tuỳ chọn) **khi `ReturnRequest.status = Pending`** (FM chưa phân công FS). Sau khi đã `Assigned`, không tự hủy qua web được — cần liên hệ FM/FS trực tiếp.

**Vì sao tách `ReturnRequest` thành bảng riêng thay vì set `RentalContract.status = PendingReturn`:**
1. Sau khi gửi yêu cầu trả kho, khách vẫn cần thời gian di dời đồ và FS cần thời gian kiểm tra — đây là **một tiến trình có nhiều bước riêng** (`Pending → Assigned → ...` do Flow 2.5 tiếp tục cập nhật), xứng đáng có bảng theo dõi riêng.
2. Nếu dùng chung field `status` trên `RentalContract`, khi hợp đồng đang quá hạn (`today > end_date`) mà khách gửi yêu cầu trả kho, việc set `status = PendingReturn` sẽ **đè mất thông tin quá hạn** — không còn biết được hợp đồng này vốn dĩ đang trễ hạn. Tách bảng riêng giữ nguyên được cả 2 thông tin cùng lúc.

#### 3.5 Gửi yêu cầu hỗ trợ sự cố
**Details:**
- User: bấm [Báo sự cố], nhập `issue_type`, `description` (mất chìa khóa, lỗi mã truy cập, khoang hư hỏng...).
- System: tạo `SupportRequest(contract_id, unit_id, reporter_id=current_user, status=Open)`.
- FS cũng có thể tự ghi nhận sự cố tại kho: `reporter_id` = FS, `contract_id` có thể null nếu khoang không có hợp đồng `Active`.
- System: notify FM.
- FM: phân công FS (thuộc facility của khoang) xử lý → System: cập nhật `SupportRequest.assigned_staff_id`, `status = Assigned` → chi tiết xử lý on-site thuộc Flow 7.
- Nếu sự cố phát sinh phí cho khách (ví dụ làm lại chìa khóa): hóa đơn `Invoice(type=Service)` do Flow 7 tạo theo danh mục phí của Flow 4 (tên bảng chờ chốt: `ExtraFee` / `Fee Management`) và gán vào `SupportRequest.invoice_id`. Flow 3 chỉ hiển thị hóa đơn này trong danh sách `Invoice` ở 3.2.
- Action này không ảnh hưởng tới `RentalContract`.

#### 3.6 Backend flow (chi tiết kỹ thuật)
**Nguyên tắc chung:**
- Mọi API phía Customer yêu cầu ownership check: `RentalContract.customer_id` phải khớp `current_user`.
- Mọi API phía FM yêu cầu facility scope: facility của khoang (`StorageUnit.facility_id`) phải có `Facility.fm_account_id = current_user` (kiểm tra ở BE, không chỉ ẩn ở FE).
- FS được gán (`assigned_staff_id`) phải có bản ghi `AccountFacilityAssignment` với đúng facility đó, ngược lại trả lỗi 422.
- Trạng thái "còn hiệu lực / sắp hết hạn / quá hạn" **luôn tính trực tiếp từ `end_date` tại thời điểm query**, so sánh theo **ngày** (`today`, cùng một múi giờ hệ thống cho BE và job) chứ không so với timestamp `now` — tránh tính quá hạn sớm ngay trong ngày cuối hợp đồng; không lưu field riêng — tránh dữ liệu bị lệch theo thời gian và tránh xung đột với các bảng request khác (xem lý do ở 3.4).

**a) Dashboard (3.1) — `GET /api/customer/contracts`**
- Query `RentalContract WHERE customer_id = :current_user AND status = Active`, JOIN `StorageUnit` rồi group theo `StorageUnit.facility_id`.

**b) Chi tiết (3.2) — `GET /api/customer/contracts/{id}`**
- Trả về `RentalContract` + `Invoice` list (theo `contract_id` và hóa đơn cọc theo `order_id`) + `open_extend_request` / `open_return_request` (nếu có) + cờ `is_overdue`, `is_expiring_soon` + `available_actions` tính theo 2 bước ở 3.2:
  ```
  today = current_date()                      # theo ngày, không dùng timestamp
  is_overdue = today > end_date
  is_expiring_soon = not is_overdue and (end_date - today) <= N
  actions = [SupportRequest]                  # luôn có khi hợp đồng Active

  if open_extend_request:                     # PendingApproval / ApprovedPendingPayment
      if open_extend_request.status == PendingApproval: actions += [CancelExtendRequest]
      else: actions += [PayExtensionInvoice]
  elif open_return_request:                   # Pending / Assigned
      if open_return_request.status == Pending: actions += [CancelReturnRequest]
  else:
      actions += [ReturnRequest]
      if not is_overdue: actions += [ExtendRequest(highlight = is_expiring_soon)]
  ```
  N = `contract.expiring_soon_days` (Flow 4), đọc qua file constant dùng chung, không hardcode.

**c) Gia hạn (3.3)**
- `POST /api/customer/contracts/{id}/extend-requests` — tạo `ExtendRequest(status=PendingApproval)`, validate `today <= end_date` (không cho gia hạn khi đã quá hạn — theo bảng action 3.2) và không có `ExtendRequest`/`ReturnRequest` đang mở (409).
- `DELETE /api/customer/extend-requests/{id}` (body: `cancel_reason` tuỳ chọn) — chỉ cho phép khi `status = PendingApproval`, ngược lại trả lỗi 409.

**d) Trả kho (3.4)**
- `POST /api/customer/contracts/{id}/return-requests` — tạo `ReturnRequest(status=Pending)`, validate `preferred_date >= today` (422) và không có `ReturnRequest`/`ExtendRequest` đang mở (409), notify FM. Không chặn khi hợp đồng quá hạn.
- `DELETE /api/customer/return-requests/{id}` (body: `cancel_reason` tuỳ chọn) — chỉ cho phép khi `status = Pending`, ngược lại trả lỗi 409.
- `POST /api/fm/return-requests/{id}/assign` (phía FM) — kiểm tra facility scope + FS thuộc facility, set `assigned_staff_id`, `status = Assigned`, bắn event cho Flow 2.5.

**e) Báo sự cố (3.5)**
- `POST /api/customer/contracts/{id}/support-requests` — tạo `SupportRequest(reporter_id=current_user, status=Open)`.
- `POST /api/staff/support-requests` (phía FS, body: `unit_id`, `issue_type`, `description`) — FS phải có `AccountFacilityAssignment` với facility của khoang (403); `contract_id` tự gán = hợp đồng `Active` của khoang nếu có, ngược lại null; tạo `SupportRequest(reporter_id=current_user, status=Open)`, notify FM.
- `POST /api/fm/support-requests/{id}/assign` (phía FM) — kiểm tra facility scope + FS thuộc facility, set `assigned_staff_id`, `status = Assigned`, bắn event cho Flow 7.

**f) Thanh toán hóa đơn**
- `POST /api/invoices/{id}/pay` → tạo `PaymentTransaction(status=Pending)` **trước khi** chuyển khách sang bước thanh toán (redirect cổng thanh toán hoặc hiển thị QR — logic bên dưới giống nhau cho mọi phương thức, phương thức MVP chờ chốt A8). Chỉ cho thanh toán hóa đơn `status = Unpaid`, hóa đơn `Paid`/`Canceled` trả lỗi 409.
- Webhook/IPN từ cổng thanh toán xử lý trong **một transaction**:
  1. Tìm `PaymentTransaction` theo mã giao dịch; nếu đã `Success`/`Failed` → bỏ qua, trả 200 (webhook bị gọi lặp khi retry).
  2. `SELECT Invoice ... FOR UPDATE`; nếu `Invoice.status != Unpaid` → không áp dụng lại tác dụng (chống 2 tab cùng thanh toán 1 hóa đơn), giao dịch thừa được ghi nhận để FM hoàn tiền thủ công.
  3. Cập nhật `PaymentTransaction.status = Success/Failed`; nếu `Success` → `Invoice.status = Paid`, `paid_at = now`, và nếu `Invoice.type = Extension` → `RentalContract.end_date += extra_months`, `ExtendRequest.status = Completed`.
- `PaymentTransaction.gateway_transaction_no` là unique để DB chặn xử lý trùng kể cả khi code check bị bỏ sót → `end_date` không bao giờ bị cộng 2 lần.

**g) Job định kỳ — hết hạn hóa đơn gia hạn (3.3)**
- Chạy định kỳ (ví dụ mỗi 15 phút): tìm `ExtendRequest WHERE status = ApprovedPendingPayment` có `Invoice.status = Unpaid AND Invoice.due_date < now`.
- Mỗi bản ghi xử lý trong **một transaction**: `SELECT Invoice ... FOR UPDATE` rồi kiểm tra lại `Invoice.status = Unpaid` → `Invoice.status = Canceled`, `ExtendRequest.status = Expired`, bắn `ExtendRequest.Expired`. Nếu webhook đã kịp chuyển `Paid` trước đó → bỏ qua.
- Webhook thanh toán tới **sau khi** job đã `Canceled` hóa đơn → rơi vào bước 2 của mục f: không cộng `end_date`, giao dịch được ghi nhận để FM hoàn tiền thủ công.

**Audit (mức tối thiểu cho MVP, cấu trúc bảng `AuditLog` theo quyết định chung C5/B13):**
- Chỉ ghi các thao tác của nhân viên làm thay đổi quyền lợi của khách: FM duyệt/từ chối `ExtendRequest`, FM phân công FS cho `ReturnRequest`/`SupportRequest`.
- Không ghi các thao tác khách tự tạo/hủy yêu cầu và các cập nhật của job (đã có `status`, `processed_at`, `cancel_reason` trên chính bản ghi).

**Concurrency:**
- Không dùng Redis lock. Dùng partial unique index ở DB:
  - `ExtendRequest(contract_id) WHERE status IN (PendingApproval, ApprovedPendingPayment)`
  - `ReturnRequest(contract_id) WHERE status IN (Pending, Assigned)`
- Ràng buộc chéo `ExtendRequest` ↔ `ReturnRequest` (3.3, 3.4) kiểm tra trong cùng transaction tạo request, sau khi `SELECT RentalContract ... FOR UPDATE`.

**Khi hợp đồng bị chấm dứt trước hạn (Flow 6):**
- Chỉ áp dụng nếu Flow 2/6 chấp nhận trạng thái `Terminated` cho `RentalContract` (hiện mới là đề xuất trong db-table-draft.md). Hợp đồng `Canceled` xảy ra trước bàn giao nên chưa thể có yêu cầu nào của Flow 3.
- `ExtendRequest` đang `PendingApproval` → `Canceled`; đang `ApprovedPendingPayment` → `Expired` và `Invoice.status = Canceled` (khóa `Invoice` như job ở mục g).
- `ReturnRequest` đang `Pending` → `Canceled`; đang `Assigned` giữ nguyên để Flow 2.5 vẫn thu hồi khoang.
- `SupportRequest` đang mở giữ nguyên (sự cố của khoang vẫn cần xử lý).

**Events phát ra từ Flow 3:** (`ExtendRequest.Approved`/`Rejected` phát ra khi FM thao tác ở trang duyệt của Flow 6, nhưng thuộc state machine của `ExtendRequest`)
| Event | Consumer |
|---|---|
| `ExtendRequest.Created` | FM (trang duyệt gia hạn — Flow 6) |
| `ExtendRequest.Canceled` | FM (nếu đang xem danh sách chờ duyệt) |
| `ExtendRequest.Approved` | Customer (hóa đơn gia hạn cần thanh toán, kèm `due_date`) |
| `ExtendRequest.Rejected` | Customer (kèm `reject_reason`) |
| `ExtendRequest.Expired` | Customer, FM |
| `ExtendRequest.Completed` | Customer (`end_date` mới), Flow 4 (ghi nhận doanh thu) |
| `ReturnRequest.Created` | FM (trang phân công FS) |
| `ReturnRequest.Canceled` | FM (gỡ khỏi danh sách chờ phân công) |
| `ReturnRequest.Assigned` | Flow 2.5 (FS xử lý on-site), Customer |
| `SupportRequest.Created` | FM (trang phân công FS) — cả khi do Customer hay FS tạo |
| `SupportRequest.Assigned` | Flow 7 (FS xử lý sự cố) |

**Advanced Features (not MVP)**
- Tự động nhắc gia hạn qua email trước N ngày hết hạn.
- Cho khách xem lịch sử đầy đủ các hợp đồng đã `Ended`.
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là `Maintenance` trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố
