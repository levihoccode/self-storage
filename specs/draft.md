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
  - Đang phân vân việc có nên thêm 1 trạng thái cho khoang chứa là `OnHold` (tạm giữ cho khách đặt và đã được approve nhưng chưa đặt cọc) để giữ kho trong ngắn hạn (24h timeout), nếu không có `OnHold`, đơn đặt mặc dù đã `Approved` nhưng nếu chưa đặt cọc, các đơn tới sau và đặt cọc có thể chiếm khoang chứa đó.
    - Vấn đề phát sinh, Holding Attack - kẻ xấu dùng đúng 1 thông tin hợp lệ, lặp đi lặp lại quy trình để kho luôn ở trạng thái bị giữ, người khác không thuê được mà FM cũng không làm gì được nếu không có cơ chế chặn. Giải pháp dự kiến: Pre-authorization bằng visa/master card, tạm giữ 1$ để xác minh.

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
                              [1.5 Chọn lịch hẹn check-in và bàn giao]

```
#### 1.1 Yêu cầu đặt kho
**Context:** Khách mới, chưa từng sử dụng dịch vụ, muốn tìm cho mình một khoang chứa phù hợp với nhu cầu.

**Flow tổng quát:** Khách lựa chọn khoang chứa dựa trên nhu cầu và điền các thông tin cần thiết (Không được chỉ định khoang chứa cụ thể). Sau đó, FM kiểm tra những khoang chứa còn trống và sẵn sàng cho thuê để chỉ định cho người thuê.

**Details:**
- **Customer:**
  - Khách hàng điền nhu cầu thuê kho qua form (không cần đăng nhập), bao gồm các thông tin được hiển thị trên form: 
    + customer_name
    + customer_email
    + customer_phone
    + unit_type
    + facility
    + start_date (MM/DD/YYYY)
    + period - số tháng thuê
  - Nhận phản hồi thông qua email và số điện thoại (telesale sẽ gọi để xác nhận)
  - **Nếu khách đã có tài khoản:** hệ thống sẽ gửi thông báo vào tài khoản.
    - Khách sẽ thao tác tiếp ở [`Kho của tôi`](#13-kiểm-tra-kho-của-tôi) trước khi sang bước đặt cọc

- **FM:**
  - Các yêu cầu đặt khoang chứa sẽ được liệt kê ở một trang và có các nút (button) để thao tác (details, response, update status, ...), mỗi entry là một `RentalRequest`.
  - Sau khi xác định được 1 yêu cầu đặt kho cần giải quyết, FM sẽ kiểm tra các kho còn sẵn tại chi nhánh và trong trường hợp:
    - **Tìm thấy khoang chứa thích hợp**:
      - FM nhập unit id phù hợp vào field `unit_id` và bấm `Approved`.
      - Hệ thống cập nhật `RentalRequest.status` sang `Approved`.
      - Tạo một instance `RentalOrder` với status = `Pending` (chỉ tạo và sẽ được ghi vào các bước tiếp theo)
      - **Trong trường hợp email chưa có tài khoản:**
        - Hệ thống ghi nhận trong một khoảng thời gian ngắn, có một yêu cầu được duyệt nhưng chưa có tài khoản. (Có thể sử dụng PG Cache hoặc Redis) -> Giả sử tài khoản đã tồn tài và thay vì ghi instance `RentalOrder`vào db thì ghi vào bộ nhớ tạm.
      - **Trong trường hợp email đã có tài khoản:**
        - Hệ thống tạo một bản ghi instance đã tạo vào `RentalOrder` để chờ khách đặt cọc.
        - Hệ thống tạo một `ProposalFeedback` cho khách hàng.
    - **Không tìm thấy khoang chứa thích hợp**: 
      - Chuyển status sang `Rejected` và nhập lý do: "Hết khoang chứa phù hợp tại chi nhánh".
      - Hệ thống gửi một thông báo/email không thành công đến khách hàng kèm theo lý do.

- **Hệ thống gửi email:**
  - **Nội dung email nếu khách nhận được phản hồi thành công** và trong trường hợp:
    - *Chưa có tài khoản:*
      ```
      Yêu cầu đặt khoang của bạn đã được duyệt, nhưng hệ thống nhận thấy email này chưa có tài khoản trên website, vui lòng đăng ký tại [link] và đăng nhập để xác nhận và đặt cọc để đảm bảo kho được giữ chỗ.

      Lưu ý: khoang chứa chỉ được xác nhận chính thức cho khách hàng hoàn tất thanh toán cọc đầu tiên. Vui lòng xác nhận và thanh toán sớm để đảm bảo giữ chỗ.
      ```
    - *Đã có tài khoản:*
      ```
      Có một khoang chứa phù hợp với yêu cầu của bạn:
          Mã (code):
          Loại (type):
          Kích thước (size):
          ...
      Vui lòng kiểm tra [link] để xác nhận.

      Lưu ý: khoang chứa chỉ được xác nhận chính thức cho khách hàng hoàn tất thanh toán cọc đầu tiên. Vui lòng xác nhận và thanh toán sớm để đảm bảo giữ chỗ.
      ```
  - **Trong trường hợp yêu cầu được Approve nhưng chưa đặt cọc, và đã có người khác đặt cọc: (chưa chốt)**
    - Phương án 1 (gợi ý khoang tương đương): hệ thống bắn thông báo/email "Khoang M-101 đã có người cọc trước. Cơ sở hiện vẫn còn các khoang M-102, M-103 cùng kích thước. Bấm vào đây để giữ khoang tương đương."
    - Phương án 2 (chuyển sang danh sách mong muốn - Wish Lists): Yêu cầu của các khách còn lại tự động chuyển status sang Wishlisted. Nếu Khách A sau đó hủy cọc hoặc bùng hợp đồng, những người trong danh sách chờ sẽ nhận được thông báo để đặt cọc.

**Schema:**
- [**RentalRequest**](./db-table-draft.md#rentalrequest) - chứa các thông tin được gửi từ form trên website.
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Invoice**](./db-table-draft.md#invoice) -  chứa thông tin thanh toán của khách hàng (hóa đơn)
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback) -  chứa các feedback từ khách hàng sau khi FM chỉ định kho

**NOTES**
- Entry trong list yêu cầu đặt khoang chứa của FM không có facility vì khi đặt, khách chỉ định một chi nhánh cụ thể và người quản lý tại chi nhánh đó sẽ nhận được yêu cầu => không cần liệt kê facility field.
- Có thể phát triển thêm phần wishlist giành cho các khoang chứa đều không available, nhưng tự động gửi thông báo và đăng ký ngay khi có bất kỳ khoang chứa nào trống (có thể dùng filter).

**Advanced Features (not MVP)**
- Tự động quá trình duyệt.
- Cho khách chỉ định cụ thể khoang chứa để thuê. -> không tối ưu layout khi để khách tự chọn, cần tìm cách hoặc kệ nó luôn đi :))
- Cho khách đặt nhiều khoang chứa trong 1 request. -> cần lưu ý về việc các khoang chứa có cần liên tục nhau hay không, tính toán ra sao nếu không đủ, ...
#### 1.2 Khách tạo tài khoản
**Case A: Sau khi có một yêu cầu được duyệt**

**Context:** Yêu cầu đặt khoang chứa của khách đã được duyệt và cần sang các bước tiếp theo để đặt cọc nhưng chưa có tài khoản.

**Flow tổng quát:** Yêu cầu đã được duyệt và ghi nhận trên hệ thống, khách hàng đăng ký trong thời gian quy định và một yêu cầu xác nhận khoang được chỉ định được thêm vào tài khoản. 

**Details:**
- Tạo một bản ghi `Account` với role là `Customer`.
- Hệ thống kiểm tra trên bộ nhớ tạm (Redis hoặc PG Cache) xem tài khoản có nằm trong mục "Có yêu cầu được duyệt nhưng chưa tạo tài khoản"
- Hệ thống lấy instance `RentalOrder` từ bộ nhớ tạm và tạo một bản ghi `RentalOrder` vào database.
- Xóa key `RentalOrder` đã lấy trong bộ nhớ tạm.
- Hệ thống tạo một bản ghi `ProposalFeedback` (status = `Pending`) cho tài khoản của khách hàng.

**Case B: Không có yêu cầu nào được duyệt**

**Context:** Khách tạo tài khoản nhưng không có yêu cầu được duyệt nào trên hệ thống tạm nhớ.

**Flow tổng quát:** Tạo tài khoản `Customer` trong hệ thống. 

**Details:**
- Tạo một bản ghi `Account` với role là `Customer`.
#### 1.3 Kiểm tra kho của tôi
**Context:** Đơn đặt khoang chứa của một khách hàng đã được duyệt và chỉ định bởi FM (bản ghi `ProposalFeedback` của đơn hàng đã được tạo), hệ thống cần xác nhận từ khách hàng.
**Flow tổng quát:** 
- Khách hàng vào trang xác nhận -> chọn đồng ý hoặc từ chối -> luồng xử lý dựa vào đồng ý hay từ chối
**Details:**
- Khách hàng vào trang xác nhận, trang đó hiển thị các thông tin của khoang (thông tin hiển thị lấy từ `ProposalFeedback`)
- Khách hàng chọn đồng ý hoặc từ chối:
  - *Đồng ý*:
    - Hệ thống cập nhật bản ghi của `ProposalFeedback` sang `Agreed`
    - Hệ thống gán field `unit_id` trong `RentalOrder`: RentalOrder.unit_id = ProposalFeedback.unit_id
    - Hệ thống tạo một bản ghi `Invoice` cho tài khoản để đặt cọc (số tiền cần đặt cọc dựa trên quy định từ BOM) với các thông tin:
      - code: INV-DEP-XX-XXXXXX-XXXX
      - title: "Đặt cọc khoang chứa A"
      - desc: "Thanh toán đặt cọc khoang chứa A để đảm bảo giữ chỗ."
      - amount: ...
  - *Từ chối*:
    - Hệ thống cập nhật bản ghi của `ProposalFeedback` sang `Rejected` (kèm note)
    - Hệ thống gửi thông báo đến FM:
      ```
      Khách đã từ chối khoang [Mã khoang cũ], lý do: [note]
      ```
    - FM vào xem khoang trống khác, chọn `unit_id` mới và bấm "Đề xuất lại".
    - Hệ thống tạo một bản ghi `ProposalFeedback` (status = `Pending`), gắn unit_id mới vừa chọn 
    
- NOTES: 
  - Khi làm trang này, có thể chia thành 2 tabs:
    - Đang sử dụng: Đã ký hợp đồng
    - Chờ được duyệt: các khoang yêu cầu được duyệt bởi FM vần cần khách hàng xác nhận

#### 1.4 Đặt cọc
**Context:** Sau khi khách đã điền form và được approve, đã nhận email phản hồi duyệt thành công và đã đăng ký tài khoản thành công.

**Flow tổng quát:**
Khách đăng nhập vào ứng dụng thành công -> vào mục "Hóa đơn" -> hiển thị một hóa đơn "Đặt cọc" cho khoang yêu cầu -> thanh toán thành công -> trạng thái kho chuyển sang `Reserved`.

**Details:**
- Khách đăng nhập vào ứng dụng 
- Ấn vào mục "Hóa đơn" kiểm tra các hóa đơn cần thanh toán
- Chọn hóa đơn đặt cọc (code=INV-DEP-...) cần thanh toán và bấm vào "Tiến hành thanh toán"
- Ở đây hệ thống sẽ kiểm tra 3 trường hợp theo thứ tự:
  - **Khoang chứa đã được đặt cọc (status = `Reserved`):**
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa đã được đặt cọc bởi khách hàng khác. Hóa đơn này đã hết hiệu lực.
      ```
    - Hệ thống sửa trạng thái của đơn này (`RentalOrder.status`) thành `Canceled`.
    - Hệ thống sửa trạng thái của hóa đơn này (`Invoice.status`) trong tài khoản thành `Canceled`.
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang có giao dịch khác xử lý (chưa bị timeout):**
    - Hệ thống từ chối giao dịch và hiển thị lỗi:
      ```
      Khoang chứa này hiện đang trong quá trình xử lý thanh toán (đặt cọc / gia hạn) bởi một khách hàng khác. Vui lòng quay lại thử lại sau ít phút hoặc chọn khoang chứa khác! 
      ```
    - Trả về trang "Hóa đơn".
  - **Khoang chứa đang không có bất kỳ giao dịch nào:** 
    - Tạm khóa khoang chứa cho đến khi việc thanh toán hoàn tất hoặc trong một khoảng thời gian (timeout)
    - Khách ấn "Thanh toán"
    - Hệ thống chuyển hướng khách sang cổng thanh toán VNPay để nhập thông tin thẻ quốc tế
    - Hệ thống nhận phản hồi từ gateway, có 2 trường hợp:
      - **Thất bại:** 
        - Mở khóa khoang chứa để trả trạng thái về tự do
        - Tạo một bản ghi `PaymentTransaction` với `status = Failed`
        - Hiển thị lỗi "Thanh toán thất bại, vui lòng thử lại!".
      - **Thành công**
        - Tạo một bản ghi `PaymentTransaction` với `status = Success`
        - Hệ thống cập nhật `Invoice.status` thành `Paid`
        - Hệ thống cập nhật `RentalOrder.status` thành `Deposited`
        - Hệ thống cập nhật trạng thái khoang chứa thành `Reserved`
        - Mở khóa khoang chứa và được trạng thái `Reserved` bảo vệ
        - Hiển thị thông báo "Thanh toán thành công".
        - Hệ thống điều hướng khách hàng sang màn hình chọn lịch hẹn check-in và bàn giao kho (Mục 1.5).

- NOTES:
  - Luồng từ việc đặt khoang -> đặt cọc -> chọn lịch hẹn là tuyến tính, tức là chỉ có đặt cọc mới có thể đặt lịch hẹn (check-in và bàn giao). Vì thế nên suy nghĩ đến việc cho đặt lịch hẹn (với loại là xem kho) trước khi đặt cọc, ở luồng này, mình có thể để FS xử lý nhiều lịch hẹn xem kho cùng 1 thời điểm (giống như 1 tour du lịch).
  - Sau khi khách đã trả tiền cọc, khoang chứa phải được giữ ở trạng thái Reserved cho đến ngày hẹn check-in/bàn giao. Nó chỉ hết hạn nếu có quy định: "Khách cọc xong nhưng quá N ngày không đến nhận kho thì mất cọc và hủy đơn".
#### 1.5 Chọn lịch check-in và bàn giao
**Context:** Sau khi khách đã đặt cọc thành công (`RentalOrder.status = Deposited`), trường `appointment_date` đang là `NULL`. Hệ thống điều hướng khách hàng sang màn hình lên lịch hẹn on-site tại chi nhánh.

**Flow tổng quát:** 
Hệ thống điều hướng user đến trang đặt lịch hẹn -> Khách chọn ngày & giờ trong giới hạn quy định -> Hệ thống lưu `appointment_date` và chuyển đơn sang `Scheduled` -> FM chỉ định nhân viên FS đón tiếp -> Hệ thống gán `staff_id` và chuyển đơn sang `InProgress`.

**Details:**
- **Customer:**
  - Hệ thống điều hướng user đến trang chọn lịch hẹn.
  - Khách chọn ngày và khung giờ hẹn đến nhận khoang (Ràng buộc: trong vòng N ngày kể từ lúc cọc và nằm trong khung giờ làm việc của chi nhánh).
  - Khách ấn "Xác nhận".
  - Hệ thống cập nhật trường `appointment_date` vào `RentalOrder`.
  - Hệ thống cập nhật trạng thái `RentalOrder.status` sang `Scheduled`.
  - Hệ thống gửi email/thông báo xác nhận lịch hẹn kèm địa chỉ cơ sở và hướng dẫn mang theo giấy tờ tùy thân (CCCD/Passport).
- **FM:**
  - FM nhận thông báo và xem danh sách các đơn đang ở trạng thái `Scheduled`.
  - FM chỉ định một nhân viên cơ sở (`FS`) phụ trách ca tiếp đón khách:
    - Hệ thống gán `RentalOrder.staff_id = [FS_Account_ID]`.
    - Hệ thống cập nhật trạng thái `RentalOrder.status` sang `InProgress`.
    - Thông báo nhiệm vụ tiếp đón được gửi đến tài khoản của nhân viên FS tương ứng.
### 2. Check-in và bàn giao kho
### 2.5 Trả kho và bảo trì
### 3. Quản lý kho đã thuê (Customer)
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố
