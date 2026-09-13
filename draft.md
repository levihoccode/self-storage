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
[Chọn lịch hẹn] -> [FM phân công FS] -> [Khách đến cơ sở] -> [Xem khoang & xác nhận] -> [Ký hợp đồng] -> [Thanh toán] -> [Nhận khóa] -> [Khoang chuyển Rented]
```

**Vị trí trong vòng đời thuê kho:** Flow 2 bắt đầu ngay sau khi Flow 1.3 (Đặt cọc) hoàn tất, lúc này khoang chứa đang ở trạng thái `Reserved` và đang được giữ cho khách. Flow 2 kết thúc khi khách nhận khoang thành công: khoang chuyển `Rented`, hợp đồng có hiệu lực và việc theo dõi được bàn giao sang Flow 3. Đây là flow **chỉ diễn ra một lần** cho mỗi `RentalOrder` và phần lớn là thao tác on-site.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 1: `RentalOrder` đã được `Approve`, `unit_id` đã được gán, `Invoice` đặt cọc đã ở trạng thái `Paid`.
- Flow 4: bảng giá thuê, danh mục các khoản phí và **version điều khoản đang hiệu lực** (để ghi nhận khách đã ký trên version nào).
- Flow 5: danh sách FS thuộc cơ sở và giờ hoạt động của cơ sở (để sinh ra các slot lịch hẹn hợp lệ).

**Context:** Khách đã đặt cọc thành công, cần đến cơ sở để xem khoang chứa thực tế, hoàn tất thủ tục hợp đồng và nhận quyền truy cập khoang chứa.

**Flow tổng quát:** Khách chọn lịch hẹn on-site -> FM phân công FS phụ trách -> khách đến cơ sở, FS ghi nhận check-in và dẫn khách xem khoang -> khách xác nhận đồng ý -> ký hợp đồng điện tử và thanh toán các khoản còn lại -> FS bàn giao chìa khóa/mã truy cập và lập biên bản -> khoang chuyển `Rented`.

#### 2.1 Chọn lịch hẹn và phân công nhân sự

**Details:**

- **Customer:**
  - Sau khi thanh toán cọc thành công, khách nhận được yêu cầu chọn lịch hẹn on-site (đã được tạo ở Flow 1).
  - Khách chọn ngày và khung giờ trong các slot mà hệ thống đưa ra. Slot chỉ được sinh trong giờ hoạt động của cơ sở (dữ liệu từ Flow 5).
  - Khách được phép **dời lịch tối đa 2 lần** trước thời điểm hẹn ít nhất 24h. (CẦN CHỐT: số lần dời lịch và thời hạn báo trước là chính sách của BOM - Flow 4.)
  - Nếu khách không chọn lịch trong vòng N ngày kể từ khi cọc thành công, hệ thống nhắc qua email/thông báo. (CẦN CHỐT: quá hạn không chọn lịch thì xử lý ra sao - giữ khoang vô thời hạn, hay thu hồi và xử lý cọc theo Flow 4?)

- **FM:**
  - Xem danh sách các lịch hẹn của cơ sở theo ngày.
  - Phân công một FS phụ trách cho từng lịch hẹn (nghiệp vụ phân công thuộc Flow 5.3, Flow 2 chỉ tiêu thụ kết quả).
  - Lịch hẹn chưa được phân công FS sẽ được đánh dấu nổi bật để FM không bỏ sót.

- **FS:**
  - Xem lịch trình trong ngày của mình: danh sách khách sẽ đến, khoang chứa tương ứng, loại lịch hẹn (nhận khoang / trả khoang / hỗ trợ sự cố).
  - Xác nhận đã nhận việc, `Appointment.status` chuyển từ `Scheduled` sang `Assigned`.

**NOTES:**
- (CẦN CHỐT - mâu thuẫn giữa các flow) Flow 1.1 ghi hệ thống tạo lựa chọn lịch hẹn **ngay khi FM Approve**, trong khi Flow 5.3 ghi lịch hẹn được tạo **sau khi khách đặt cọc thành công**. Flow 2 đang viết theo hướng tạo sau khi cọc thành công vì trước đó khoang chưa thực sự được giữ.

#### 2.2 Check-in và xem khoang tại cơ sở

**Details:**

- **FS:**
  - Ghi nhận khách đến (check-in record): thời điểm khách tới, giấy tờ tùy thân đã đối chiếu, lịch hẹn tương ứng.
  - Đối chiếu thông tin khách với `RentalOrder.customer_id`. Nếu người đến không phải chủ đơn thì phải có giấy ủy quyền. (CẦN CHỐT: có cho phép người được ủy quyền nhận khoang thay không?)
  - Dẫn khách tới khoang chứa đã được gán, giới thiệu tình trạng thực tế: kích thước, vị trí, tình trạng vệ sinh, hệ thống khóa.
  - `Appointment.status` chuyển sang `InProgress`.

- **Customer:**
  - Xem khoang chứa thực tế và đưa ra phản hồi. Hệ thống ghi nhận vào `ProposalFeedback`:
    - `Agree`: đồng ý nhận khoang, đi tiếp sang 2.3.
    - `Reject`: từ chối khoang, bắt buộc nhập lý do vào field `note`.

- **Xử lý khi khách `Reject` khoang chứa:**
  - FS ghi nhận lý do và báo lại FM ngay trong phiên làm việc.
  - FM kiểm tra tại cơ sở còn khoang nào khác cùng `unit_type` đang `Available` hay không:
    - **Còn khoang thay thế:** FM gán lại `unit_id` mới cho `RentalOrder`, khoang cũ trả về `Available`, khoang mới chuyển `Reserved`. Khách xem lại ngay trong buổi hẹn, không phải đặt cọc lại.
    - **Không còn khoang thay thế:** `RentalOrder` chuyển `Canceled` với lý do tương ứng và chuyển sang quy trình hoàn cọc.
  - (CẦN CHỐT) Khách từ chối vì khoang không đúng mô tả thì hoàn 100% cọc, còn từ chối vì lý do cá nhân thì xử lý cọc thế nào? Đây là chính sách của BOM (Flow 4), Flow 2 chỉ thực thi.
  - (CẦN CHỐT) Cho phép khách `Reject` tối đa bao nhiêu lần trên cùng một đơn, tránh việc giữ khoang kéo dài.

- **Xử lý khi khách không đến hẹn (no-show):**
  - Hết khung giờ hẹn mà khách không tới, FS đánh dấu `Appointment.status = NoShow` kèm ghi chú.
  - Hệ thống gửi thông báo/email nhắc khách đặt lại lịch.
  - Khoang chứa **vẫn giữ `Reserved`** cho tới khi chạm ngưỡng xử lý do BOM quy định.
  - (CẦN CHỐT) Sau bao nhiêu lần no-show thì hủy đơn và xử lý tiền cọc ra sao? Chính sách này thuộc Flow 4, hiện chưa có.

#### 2.3 Ký hợp đồng và thanh toán

> (CẦN CHỐT - ranh giới flow) Sơ đồ Flow 1 liệt kê `[Ký hợp đồng] -> [Thanh toán]` là hai bước của Flow 1, nhưng `db-table-draft.md` lại định nghĩa `RentalOrder.status = Done` là *"khách hoàn tất các thủ tục, thanh toán các chi phí cần thiết và đã thiết lập hợp đồng điện tử"*, tức hai bước này diễn ra trong buổi hẹn on-site. Mục 2.3 đang được viết theo hướng **ký hợp đồng và thanh toán nằm trong Flow 2**. Nếu nhóm chốt ngược lại thì toàn bộ mục này sẽ được cắt bỏ.

**Details:**

- **Ký hợp đồng điện tử:**
  - Hệ thống sinh hợp đồng từ mẫu đang hiệu lực, điền sẵn thông tin khách, khoang chứa, giá thuê, thời hạn, số tiền cọc.
  - Khách đọc và xác nhận đồng ý các điều khoản (điều khoản thuê kho, quy định hàng hóa được phép/bị cấm, quy định truy cập, trách nhiệm bảo quản). Nội dung và version điều khoản lấy từ Flow 4.
  - Hệ thống ghi nhận khách đã đồng ý với **version cụ thể** của từng điều khoản, phục vụ đối chiếu về sau.
  - Khách ký điện tử, hệ thống tạo bản ghi `RentalContract` và xuất file PDF lưu trữ.
  - (CẦN CHỐT) Hình thức ký: ký tay trên màn hình thiết bị của FS, hay OTP gửi về điện thoại khách? Ảnh hưởng tới giá trị pháp lý của hợp đồng.

- **Thanh toán các khoản còn lại:**
  - Hệ thống tạo `Invoice` tiền thuê kỳ đầu (prefix `RNT`) theo bảng giá của Flow 4.
  - Tiền cọc đã thu ở Flow 1.3 **không** được trừ vào hóa đơn này, cọc được giữ riêng tới khi trả kho (xem 2.5.3).
  - Khách thanh toán, quy trình thanh toán dùng lại cơ chế đã mô tả ở Flow 1.3.
  - (CẦN CHỐT) Tại cơ sở có cho phép thanh toán tiền mặt hoặc quẹt thẻ qua FS không? `PaymentTransaction` hiện đang ghi chú *"visa card only"* nhưng Flow 1.3 lại mô tả chuyển khoản QR. Nếu có thu tiền mặt thì cần bổ sung `payment_method` và cơ chế FS xác nhận đã thu.
  - Nếu khách không thanh toán được ngay trong buổi hẹn: `Appointment` kết thúc ở trạng thái `Pending Payment`, khoang vẫn giữ `Reserved`, chưa bàn giao khóa.

#### 2.4 Bàn giao khóa và kích hoạt hợp đồng

**Details:**

- **FS:**
  - Chỉ được thực hiện bước này khi hợp đồng đã ký **và** hóa đơn tiền thuê kỳ đầu đã `Paid`.
  - Lập **biên bản bàn giao** (`HandoverRecord`, loại `CheckIn`) ghi nhận:
    - Tình trạng khoang chứa tại thời điểm bàn giao (vệ sinh, hư hỏng sẵn có nếu có).
    - Ảnh chụp hiện trạng khoang chứa.
    - Hình thức truy cập được bàn giao và số lượng.
  - Bàn giao quyền truy cập khoang chứa cho khách:
    - Nếu dùng **khóa cơ**: giao chìa khóa vật lý, ghi nhận số lượng chìa đã giao.
    - Nếu dùng **khóa mã số**: hệ thống sinh mã truy cập và gửi cho khách, FS hướng dẫn khách đổi mã lần đầu.
    - (CẦN CHỐT) Hệ thống dùng khóa cơ hay khóa mã số, hay cho phép cả hai tùy cơ sở? Quyết định này ảnh hưởng trực tiếp tới thiết kế bảng lưu trữ quyền truy cập và tới Flow 7 (mất chìa khóa, lỗi mã truy cập).
  - Hai bên xác nhận biên bản, khách ký nhận.

- **Hệ thống:**
  - `StorageUnit.status`: `Reserved` -> `Rented`.
  - `RentalOrder.status`: chuyển sang trạng thái đang thuê (xem NOTES về xung đột enum bên dưới).
  - `Appointment.status` -> `Completed`.
  - `RentalContract` bắt đầu có hiệu lực, `start_date` được ghi nhận theo thực tế bàn giao.
  - Gửi email xác nhận kèm bản PDF hợp đồng và biên bản bàn giao.
  - Bắn event `RentalOrder.HandoverCompleted` để Flow 3 bắt đầu theo dõi.

**NOTES:**
- (CẦN CHỐT) `start_date` của hợp đồng tính theo ngày khách đăng ký trong `RentalRequest` hay theo ngày bàn giao thực tế? Nếu khách dời lịch nhiều lần thì hai mốc này lệch nhau và ảnh hưởng tới việc tính tiền thuê, ngày hết hạn và phí quá hạn ở Flow 3/Flow 6.

#### Backend flow (chi tiết kỹ thuật)

**Nguyên tắc chung:**
- API của FS/FM yêu cầu đăng nhập và kiểm tra **facility scope**: tài khoản chỉ thao tác được trên `Appointment`/`StorageUnit` thuộc cơ sở mình phụ trách, nếu không trả `403`.
- API của customer áp dụng **ownership check** trên `RentalOrder.customer_id`.
- Mọi bước làm thay đổi `StorageUnit.status` đều phải chạy trong DB transaction và lock theo `unit_id` để không xung đột với luồng đặt cọc của Flow 1.

**a) Lấy slot và đặt lịch (2.1)**
- `GET /api/customer/rental-orders/{id}/appointment-slots`: sinh slot từ giờ hoạt động của cơ sở, loại bỏ slot đã đầy theo số FS khả dụng.
- `POST /api/customer/rental-orders/{id}/appointments`: body `{ appointment_date, slot }`. Validate `Invoice` cọc đã `Paid` trước khi cho đặt lịch. Tạo `Appointment(type=Handover, status=Scheduled)`, bắn event `Appointment.Created` để FM nhận thông báo phân công.
- `PATCH /api/customer/appointments/{id}`: dời lịch, validate số lần dời và thời hạn báo trước.

**b) Lịch trình của FS (2.1, 2.2)**
- `GET /api/staff/appointments?date=...`: trả lịch trong ngày của FS đang đăng nhập, kèm thông tin khách và khoang chứa.
- `POST /api/staff/appointments/{id}/check-in`: ghi nhận khách đến, `status` -> `InProgress`.
- `POST /api/staff/appointments/{id}/no-show`: `status` -> `NoShow`, bắn event `Appointment.NoShow`.

**c) Ghi nhận phản hồi xem khoang (2.2)**
- `POST /api/staff/appointments/{id}/proposal-feedback`: body `{ status: Agree|Reject, note? }`, ghi vào `ProposalFeedback`.
- Khi `Reject`: bắn event `ProposalFeedback.Rejected` để FM xử lý đổi khoang. Việc gán lại `unit_id` chạy trong transaction: trả khoang cũ về `Available` và set khoang mới `Reserved` cùng lúc.

**d) Ký hợp đồng và thanh toán (2.3)**
- `POST /api/customer/rental-orders/{id}/contracts`: sinh `RentalContract` từ mẫu, snapshot version điều khoản đang hiệu lực. Chỉ cho phép khi `ProposalFeedback.status = Agree`.
- `POST /api/customer/contracts/{id}/sign`: ghi nhận chữ ký, xuất PDF lên storage, tạo `Invoice(type=MonthlyRent)`.
- Thanh toán dùng lại `POST /api/invoices/{id}/pay` đã mô tả ở Flow 3.

**e) Bàn giao (2.4)**
- `POST /api/staff/appointments/{id}/handover`: body `{ unit_condition, photos[], access_type, access_quantity, note? }`.
- Backend kiểm tra tuần tự trước khi ghi: hợp đồng đã ký, hóa đơn tiền thuê đã `Paid`, `ProposalFeedback = Agree`. Thiếu bất kỳ điều kiện nào thì trả lỗi rõ ràng, không cho bàn giao.
- Transaction: tạo `HandoverRecord(type=CheckIn)`, tạo bản ghi quyền truy cập, update `StorageUnit.status = Rented`, update `RentalOrder`, `Appointment.status = Completed`, kích hoạt `RentalContract`.
- Bắn event `RentalOrder.HandoverCompleted`.

**Events phát ra từ Flow 2:**

| Event | Consumer |
|---|---|
| `Appointment.Created` | Flow 5 (FM phân công FS) |
| `Appointment.NoShow` | Flow 4 (chính sách xử lý no-show), hệ thống notify khách |
| `ProposalFeedback.Rejected` | Flow 1/Flow 5 (FM gán lại khoang chứa) |
| `RentalOrder.HandoverCompleted` | Flow 3 (bắt đầu theo dõi khoang đang thuê) |

**Schema:**

Các bảng đã có, Flow 2 chỉ đọc hoặc cập nhật trạng thái:
- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được xử lý trong buổi hẹn, `staff_id` là FS được phân công.
- [**Invoice**](./db-table-draft.md#invoice) - hóa đơn tiền thuê kỳ đầu phát sinh tại 2.3.
- [**ProposalFeedback**](./db-table-draft.md#proposalfeedback) - ghi nhận khách đồng ý/từ chối khoang chứa sau khi xem thực tế.

Các bảng Flow 2 cần nhưng **chưa có trong `db-table-draft.md`** (đề xuất, chờ chốt trước khi bổ sung vào file schema chung):
- **Appointment** - lịch hẹn on-site, dùng chung cho cả Flow 2 và Flow 2.5.
  - order_id (N - 1: RentalOrder)
  - staff_id (N - 1: Account, null cho tới khi FM phân công)
  - type (Handover/Return)
  - appointment_date, slot
  - status (Scheduled/Assigned/InProgress/Completed/NoShow/Canceled)
  - note
  - (CẦN CHỐT) `RentalOrder` hiện đã có sẵn `staff_id` và `appointment_date`. Nếu tách bảng `Appointment` riêng thì hai field đó nên bỏ khỏi `RentalOrder` để tránh trùng lặp dữ liệu. Việc sửa `RentalOrder` thuộc phạm vi schema chung nên chưa thực hiện.
- **HandoverRecord** - biên bản bàn giao, dùng chung cho check-in (Flow 2) và check-out (Flow 2.5).
  - order_id (N - 1: RentalOrder)
  - appointment_id (1 - 1: Appointment)
  - staff_id (N - 1: Account)
  - type (CheckIn/CheckOut)
  - unit_condition
  - photos
  - customer_signature
  - note
  - created_at
- **UnitAccessKey** - quyền truy cập khoang chứa đã bàn giao cho khách.
  - unit_id (N - 1: StorageUnit)
  - order_id (N - 1: RentalOrder)
  - access_type (PhysicalKey/AccessCode)
  - quantity hoặc code_hash
  - issued_at, revoked_at
  - status (Active/Revoked/Lost)
  - (CẦN CHỐT) Phụ thuộc hoàn toàn vào quyết định khóa cơ hay khóa mã số ở mục 2.4.
- **RentalContract** - hợp đồng thuê. `Invoice.contract_id` đã tham chiếu tới bảng này nhưng bảng chưa được định nghĩa. Bảng này nằm ở ranh giới Flow 1/Flow 2 nên **chưa đề xuất field**, chờ nhóm chốt chủ sở hữu.

**NOTES**
- (CẦN CHỐT - xung đột enum) `RentalOrder.status` hiện có hai bộ giá trị khác nhau: `db-table-draft.md` ghi `Pending/InProgress/Canceled/Done`, còn Flow 3 dùng `Active/ExpiringSoon/Overdue/PendingReturn/Completed`. Flow 2 là nơi đơn hàng chuyển từ "đang làm thủ tục" sang "đang thuê" nên bị ảnh hưởng trực tiếp. Tài liệu này tạm dùng cách gọi trung tính cho tới khi nhóm gộp hai bộ enum.
- (CẦN CHỐT - state machine dùng chung) Trạng thái `StorageUnit` được set bởi nhiều flow khác nhau nhưng chưa có định nghĩa chung. Flow 2 nắm chuyển đổi `Reserved -> Rented`, Flow 2.5 nắm `Rented -> Maintenance -> Available`. Cần một bảng trạng thái chuẩn cho toàn hệ thống trước khi code.
- Bảng `Appointment` được Flow 3 và Flow 5 gọi tên nhưng chưa ai định nghĩa. Flow 2 đang đề xuất ở trên, nếu nhóm đồng ý thì nên đưa vào `db-table-draft.md` để các flow khác tham chiếu thống nhất.

**Advanced Features (not MVP)**
- Cho khách tự chọn slot theo lịch trống thực tế của từng FS thay vì slot cố định theo giờ hoạt động cơ sở.
- Nhắc lịch hẹn tự động qua email/SMS trước 24h.
- Ký hợp đồng từ xa, khách không cần tới cơ sở mới ký được.
- Khóa thông minh điều khiển qua app, bỏ hẳn bước giao chìa khóa vật lý.

### 2.5 Trả kho và bảo trì

**FLOW:**
```
[Yêu cầu trả kho] -> [Hẹn lịch trả] -> [FS kiểm tra khoang] -> [Xử lý phí phát sinh] -> [Thu hồi quyền truy cập] -> [Hoàn cọc] -> [Bảo trì] -> [Khoang về Available]
```

**Vị trí trong vòng đời thuê kho:** Flow 2.5 nhận đầu vào từ Flow 3.4 (khách bấm yêu cầu trả kho) và xử lý toàn bộ phần on-site. Flow 2.5 kết thúc khi khoang chứa hoàn tất bảo trì và quay về `Available`, sẵn sàng cho một yêu cầu mới ở Flow 1. Đây là điểm đóng vòng đời của một `RentalOrder`.

**Dữ liệu phụ thuộc (input từ các flow khác):**
- Flow 3: yêu cầu trả kho của khách, `RentalOrder` đã chuyển `PendingReturn`, lịch hẹn loại `Return` đã được tạo.
- Flow 2: `HandoverRecord` loại `CheckIn` để đối chiếu hiện trạng khoang lúc nhận và lúc trả.
- Flow 4: mức phí hư hỏng, phí vệ sinh, phí trả trễ và chính sách xử lý tiền cọc.
- Flow 5: FS được phân công xử lý lịch hẹn trả kho.

**Context:** Khách kết thúc nhu cầu thuê và muốn trả lại khoang chứa, cần có người kiểm tra hiện trạng, xử lý các khoản phát sinh và thu hồi quyền truy cập trước khi khoang được cho thuê lại.

**Flow tổng quát:** Khách gửi yêu cầu trả kho từ Flow 3 -> hệ thống tạo lịch hẹn trả -> FM phân công FS -> khách dọn đồ và bàn giao lại khoang -> FS kiểm tra, đối chiếu với biên bản lúc nhận, lập biên bản trả -> xử lý các khoản phí phát sinh và tiền cọc -> thu hồi chìa khóa/vô hiệu hóa mã truy cập -> khoang chuyển `Maintenance` -> hết thời gian bảo trì quay về `Available`.

#### 2.5.1 Tiếp nhận yêu cầu và hẹn lịch trả kho

**Details:**

- **Hệ thống:**
  - Nhận event `RentalOrder.ReturnRequested` từ Flow 3.4, tạo `Appointment(type=Return)` theo ngày khách đề xuất.
  - Thông báo cho FM của cơ sở để phân công FS.
- **FM:**
  - Phân công FS phụ trách buổi trả kho.
  - Có quyền hủy hộ yêu cầu trả kho nếu khách đổi ý (Flow 3 không cho khách tự hủy). Khi hủy, `RentalOrder` quay về trạng thái đang thuê và lịch hẹn chuyển `Canceled`.
  - (CẦN CHỐT) Khi FM hủy hộ có bắt buộc nhập lý do và xác nhận lại với khách không? Đây là câu hỏi đang để mở ở Flow 3.
- **Customer:**
  - Trước ngày hẹn, khách phải tự dọn toàn bộ tài sản ra khỏi khoang chứa.
  - Hệ thống nhắc khách các điều kiện để được nhận lại cọc: khoang trống, không hư hỏng, không còn hóa đơn `Unpaid`.

**NOTES:**
- Flow 2.5 chỉ xử lý trường hợp khách **chủ động đến trả**. Trường hợp khách quá hạn mà không trả, không liên lạc được, hoặc bỏ lại tài sản trong khoang thuộc phạm vi Flow 6 (Xử lý quá hạn). (CẦN CHỐT: Flow 6 hiện chưa ai viết, cần xác nhận ranh giới này để tránh hổng nghiệp vụ.)

#### 2.5.2 Kiểm tra và bàn giao lại khoang chứa

**Details:**

- **FS:**
  - Ghi nhận khách đến (check-in record) giống bước 2.2.
  - Kiểm tra khoang chứa theo checklist và đối chiếu với `HandoverRecord` loại `CheckIn` đã lập ở Flow 2:
    - Khoang đã được dọn trống hoàn toàn chưa.
    - Tình trạng vệ sinh.
    - Hư hỏng về kết cấu, cửa, khóa, thiết bị so với lúc bàn giao.
    - Chụp ảnh hiện trạng để lưu hồ sơ.
  - Lập `HandoverRecord` loại `CheckOut`, khách ký xác nhận.
  - Các trường hợp xử lý:
    - **Đạt yêu cầu:** không phát sinh phí, đi tiếp sang 2.5.3.
    - **Còn tài sản trong khoang:** FS ghi nhận và không hoàn tất trả kho. (CẦN CHỐT: cho khách dọn tiếp trong bao lâu, hay tính phí lưu giữ? Chính sách thuộc Flow 4/Flow 6.)
    - **Khoang bẩn hoặc hư hỏng:** FS ghi nhận chi tiết kèm ảnh, hệ thống tạo hóa đơn phí tương ứng ở 2.5.3.
  - (CẦN CHỐT) Khách không đồng ý với đánh giá hư hỏng của FS thì xử lý thế nào? Đề xuất: cho phép khách ghi ý kiến phản đối vào biên bản và chuyển FM xử lý, chưa tạo hóa đơn ngay.

- **Thu hồi quyền truy cập:**
  - Khóa cơ: FS thu lại chìa khóa, đối chiếu với số lượng đã giao ở 2.4. Thiếu chìa thì tính phí mất chìa theo Flow 4.
  - Khóa mã số: hệ thống vô hiệu hóa mã truy cập của khách ngay khi biên bản được xác nhận.
  - `UnitAccessKey.status` -> `Revoked`, ghi `revoked_at`.

#### 2.5.3 Xử lý phí phát sinh và tiền cọc

**Details:**

- **Các khoản có thể phát sinh tại bước trả kho:**
  - Phí vệ sinh, nếu khoang không đạt yêu cầu khi bàn giao lại.
  - Phí hư hỏng, theo mức độ thiệt hại thực tế.
  - Phí mất chìa khóa hoặc thay khóa.
  - Phí trả kho trễ, nếu ngày trả vượt quá hạn hợp đồng.
  - Các hóa đơn `Unpaid` còn tồn đọng của hợp đồng.
  - Mức phí lấy theo cấu hình của Flow 4, Flow 2.5 **không tự định nghĩa mức phí**.
  - (CẦN CHỐT) Quy ước mã hóa đơn hiện chỉ có các prefix `DEP`, `RNT`, `EXT`. Cần bổ sung prefix cho phí hư hỏng và phí vệ sinh, ví dụ `DMG` và `CLN`, để dễ lọc và đối soát.

- **Xử lý tiền cọc:**
  - Đối trừ tiền cọc với tổng các khoản phát sinh:
    - Cọc lớn hơn tổng phí: hoàn lại phần chênh lệch cho khách.
    - Cọc nhỏ hơn tổng phí: tạo hóa đơn cho phần còn thiếu, khách phải thanh toán trước khi hoàn tất trả kho.
  - (CẦN CHỐT - thiếu ở schema chung) `PaymentTransaction` hiện chỉ mô tả chiều thu tiền, **chưa có cơ chế hoàn tiền**. Cần bổ sung trường phân biệt chiều giao dịch hoặc thêm bảng riêng cho refund, kèm trạng thái xử lý và mã giao dịch hoàn từ cổng thanh toán để đối soát.
  - (CẦN CHỐT - chính sách) Tiền cọc được hoàn lại cho khách, hay mặc định trừ vào kỳ thuê cuối cùng? Hiện không flow nào quy định. Đề xuất hoàn lại để tách bạch với tiền thuê, nhưng đây là quyết định của BOM.
  - (CẦN CHỐT) Thời hạn hoàn cọc là bao lâu kể từ ngày trả kho, và hoàn qua kênh nào?

#### 2.5.4 Bảo trì và mở lại cho thuê

**Details:**

- **Hệ thống:**
  - Sau khi biên bản `CheckOut` được xác nhận và không còn khoản phải thu bắt buộc:
    - `StorageUnit.status` -> `Maintenance`.
    - `RentalOrder` chuyển sang trạng thái kết thúc.
    - `RentalContract` chuyển sang hết hiệu lực.
    - `Appointment.status` -> `Completed`.
  - Ghi lại thời điểm bắt đầu bảo trì để tính thời gian mở lại.

- **Thời gian bảo trì:**
  - Mặc định 1-3 ngày theo ghi chú chung của tài liệu.
  - Hết thời gian bảo trì, khoang tự động chuyển về `Available` và có thể được gán cho yêu cầu mới ở Flow 1.
  - Nếu khoang có hư hỏng cần sửa chữa dài hơn, FM giữ khoang ở `Maintenance` cho tới khi xử lý xong, không để cron tự mở.
  - (CẦN CHỐT - việc mồ côi) Flow 3 ghi cron chuyển `Maintenance -> Available` là "cron job Flow 5", nhưng Flow 5 không hề nhắc tới cron này. Cần xác định flow nào chịu trách nhiệm, nếu không thì khoang sẽ kẹt ở `Maintenance` vĩnh viễn. Flow 2.5 sẵn sàng nhận phần này nếu nhóm đồng ý.
  - (CẦN CHỐT) Thời gian bảo trì cố định hay cấu hình được theo từng cơ sở/loại khoang? Nếu cấu hình được thì thuộc Flow 4 hoặc Flow 5.

#### Backend flow (chi tiết kỹ thuật)

**a) Tiếp nhận yêu cầu trả (2.5.1)**
- Consumer của event `RentalOrder.ReturnRequested`: tạo `Appointment(type=Return, status=Scheduled)`, notify FM.
- `POST /api/manager/appointments/{id}/cancel`: FM hủy hộ yêu cầu trả kho, body `{ reason }`. Transaction: `Appointment.status = Canceled`, `RentalOrder` quay lại trạng thái đang thuê.

**b) Kiểm tra và lập biên bản (2.5.2)**
- `GET /api/staff/appointments/{id}/checkin-record`: lấy `HandoverRecord` loại `CheckIn` để FS đối chiếu hiện trạng.
- `POST /api/staff/appointments/{id}/inspection`: body `{ is_empty, cleanliness, damages[], photos[], returned_key_quantity, note? }`.
- Backend tạo `HandoverRecord(type=CheckOut)` và tính danh sách khoản phí dự kiến dựa trên cấu hình phí của Flow 4, trả về cho FS xem trước khi chốt. Chưa tạo `Invoice` ở bước này.
- `POST /api/staff/appointments/{id}/finalize-return`: chốt biên bản, tạo các `Invoice` phát sinh, thu hồi `UnitAccessKey`.

**c) Đối trừ và hoàn cọc (2.5.3)**
- Tính trong một transaction: tổng phí phát sinh + hóa đơn tồn đọng, so với số tiền cọc đã thu (`Invoice` prefix `DEP` ở trạng thái `Paid`).
- Nếu dư: tạo bản ghi hoàn tiền, gọi API refund của cổng thanh toán, lưu mã giao dịch hoàn để đối soát.
- Nếu thiếu: tạo `Invoice` phần chênh lệch, chặn bước chuyển `Maintenance` cho tới khi thanh toán xong.

**d) Bảo trì và mở lại (2.5.4)**
- Transaction khi hoàn tất: `StorageUnit.status = Maintenance` kèm `maintenance_started_at`, `RentalOrder` và `RentalContract` chuyển trạng thái kết thúc.
- Cron chạy hằng ngày quét các khoang `Maintenance` đã đủ thời gian và không bị FM giữ lại, chuyển về `Available`, bắn event `StorageUnit.BecameAvailable`.
- Lock theo `unit_id` khi chuyển trạng thái để không xung đột với luồng gán khoang của Flow 1.

**Events phát ra từ Flow 2.5:**

| Event | Consumer |
|---|---|
| `Appointment.Created` (type=Return) | Flow 5 (FM phân công FS) |
| `RentalOrder.ReturnCompleted` | Flow 3 (đóng vòng theo dõi), Flow 4 (ghi nhận doanh thu) |
| `Invoice.Created` (phí hư hỏng/vệ sinh) | Flow 4 (theo dõi doanh thu) |
| `StorageUnit.BecameAvailable` | Flow 1 (khoang sẵn sàng cho yêu cầu mới), wishlist nếu có |

**Schema:**

- [**RentalOrder**](./db-table-draft.md#rentalorder) - đơn hàng được đóng lại sau khi trả kho.
- [**Invoice**](./db-table-draft.md#invoice) - các hóa đơn phí phát sinh khi trả kho.
- [**PaymentTransaction**](./db-table-draft.md#paymenttransaction) - cần bổ sung cơ chế hoàn tiền, xem 2.5.3.
- **Appointment**, **HandoverRecord**, **UnitAccessKey** - dùng chung với Flow 2, đề xuất ở phần Schema của Flow 2.

**NOTES**
- Flow 3.4 đã ghi sẵn hành vi mong đợi của Flow 2.5: *"Sau khi FS xác nhận hoàn tất và không phát sinh phí hư hại, khoang chứa chuyển trạng thái MAINTENANCE, RentalOrder chuyển Completed"*. Tài liệu này viết khớp với mô tả đó và bổ sung thêm nhánh **có** phát sinh phí, vốn chưa được mô tả ở đâu.
- Toàn bộ mức phí trong Flow 2.5 phụ thuộc cấu hình của Flow 4. Nếu Flow 4 chưa chốt danh mục phí thì phần này chỉ dừng ở mô tả nghiệp vụ, chưa code được.

**Advanced Features (not MVP)**
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
