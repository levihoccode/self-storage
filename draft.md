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
ssss#### 1.1 Yêu cầu đặt kho
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
### 4. Quản lý business rules, các khoản phí và theo dõi doanh thu (BOM)
**Context:** Thiết lập môi trường để quản lý rules doanh nghiệp, khách hàng, đồng theo dõi doanh thu, khách hàng tiềm năng, chi nhánh tiềm năng giúp mở rộng chi nhánh, ...
**Các điều cần lưu ý khi thực hiện code ở flow này:**
- Các chính sách về mặt hình thức đều là các con chữ, cần có một chiến thuật rõ ràng để hạn thay đổi code khi chính sách bị thay đổi nhiều nhất có thể. Các giải pháp được đề xuất:
  - Cố định các cơ chế chính sách sẽ được hỗ trợ tự động bởi hệ thống như các chức năng (ví dụ: tự động khóa hợp đồng khi quá hạn, tự động tạo bảng tính phí khi một hợp đồng quá hạn)
  - Cho phép xử lý thủ công khi hệ thống chưa hỗ trợ cơ chế cần thiết (ví dụ: thanh lý tài sản khi quá hạn > 90 ngày, bồi thường thiệt hại đặc biệt không có trong danh mục cố định -> FM/BOM thực hiện thủ công ngoài đời với tác vụ là "Xử lý sự cố" rồi bấm nút 'Ghi nhận xử lý' trên hệ thống, chứ code không tự chạy).

- Khi thực hiện phần "Thêm chính sách", cần phân chia rõ thành 2 cấp độ:
#### Cấp độ 1: Thêm chính sách dựa trên CƠ CHẾ CÓ SẴN (Không sửa Code - Zero Code Deployment)
Áp dụng cho các chính sách chỉ thay đổi về mặt dữ liệu, tham số hoặc danh mục lựa chọn. Cơ chế xử lý đã được lập trình sẵn trong code.

* **Thao tác của BOM:** Thực hiện trực tiếp trên giao diện quản trị (Web UI).
* **Các trường hợp hỗ trợ:**
  - **Thêm loại phụ phí mới:** Thêm mục phí vào danh mục (VD: Thêm *"Phí mượn xe đẩy hàng quá giờ: 50.000đ/lần"*). Hệ thống tự cập nhật danh sách phụ phí để nhân viên cơ sở (FS/FM) tick chọn khi lập biên bản nghiệm thu kho.
  - **Thêm bậc cấu hình/khuyến mãi:** Thêm rule tính toán dựa trên khung có sẵn (VD: Hợp đồng thuê trên 6 tháng -> Giảm 5% tiền cọc).
  - **Ban hành văn bản pháp lý mới:** Tải lên nội dung/file PDF điều khoản phiên bản mới (`RentalTerm v2.0`) để áp dụng cho các hợp đồng tiếp theo.
* **Bản chất kỹ thuật:** Thao tác tạo bản ghi mới (INSERT) vào Database (`FeeConfig`, `RentalTerm`, `PromotionRule`). Code hệ thống tự động đọc và áp dụng ngay lập tức.


#### Cấp độ 2: Thêm chính sách mang HÀNH VI HOÀN TOÀN MỚI (Bắt buộc sửa Code - Feature Development)
Áp dụng khi BOM ban hành một chính sách đòi hỏi hệ thống phải thực hiện một hành vi, quy trình hoặc tích hợp kỹ thuật chưa từng tồn tại trong mã nguồn.

* **Thao tác của BOM:** Không thể tự cấu hình trên Web UI. Chính sách phải đi qua quy trình tiếp nhận yêu cầu thay đổi (Change Request).
* **Ví dụ thực tế:**
  - *"Khách quá hạn 30 ngày thì tự động gửi tin nhắn đòi nợ qua Zalo ZNS và đẩy hồ sơ sang đơn vị thu hồi nợ."*
  - *"Nếu khách trả kho trước hạn 15 ngày, tự động đăng tin khoang trống lên website ở chế độ flash sale."*
* **Tại sao không thể tự cấu hình trên UI?** Vì hệ thống chưa có tích hợp API bên thứ ba (Zalo Gateway, Đơn vị thu nợ), chưa có Cronjob quét mốc 30 ngày, và chưa có logic state machine tương ứng.
* **Quy trình triển khai:**
  1. **BOM / Nghiệp vụ:** Soạn thảo văn bản quy định chính sách mới.
  2. **Kỹ thuật (Tech Lead / Dev):** Phân tích tác động (Impact Analysis), thiết kế Database/API và viết code bổ sung cơ chế mới.
  3. **Kiểm thử & Triển khai (Deploy):** Đẩy phiên bản phần mềm mới lên môi trường production.
  4. **Cung cấp giao diện quản trị:** Lúc này BOM mới có thêm các tham số mới trên Web UI để bật/tắt hoặc điều chỉnh ngưỡng (VD: đổi mốc 30 ngày thành 45 ngày).

**FLOW:**
```
                        [Trang Quản Lý Của BOM]
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
         ▼                        ▼                        ▼
[Quản Lý Chính Sách      [Quản Lý Chính Sách      [Dashboard Theo Dõi
     Nghiệp Vụ]                 Phí]              Doanh Thu Chi Nhánh]
 (Business Rules &       (Fee Configuration &      (Revenue Analytics &
 Operational Limits)         Surcharges)             Cash Inflow)
```
- Những table cần được cung cấp cho Flow 4 (dựk tính): Unit, Facility, Users, Reservation, Contrasct, Rental History, Payment.
- Các table Flow 4 tạo ra: Business rules, Fee, Account disabled
#### 4.1 Quản lý business rules
**Context:**BOM quản lý các chính sách nghiệp vụ được áp dụng trong hệ thống. Các chính sách được cấu hình thông qua giao diện quản trị thay vì thay đổi trực tiếp trong source
**Flow tổng quát:** 
    [Bom đăng nhập]->[Page quản lý rules]->[CRUD-Status-Active]->[kiểm tra hợp lệ]->[Save]
 **Details:**
  Business Rule quản lý các inf liên quan đến: deposit, policy, effective, status và version của từng policy
 **Schema**
 RentalPolicy
  - id
  - name
  - deposit_type 
  - deposit_value 
  - cancel_policy 
  - return_policy 
  - renewal_policy 
  - overdue_policy 
  - lock_policy
  - version 
  - effective_from 
  - effective_to 
  - status 
  - created_by 
  - created_at 
  - updated_at

**Validetion**
  - efective from < efective to
  - Nếu depotit type = % thì deposit value thuộc [0,100];
  - không cho phép các version đang active bị overlap về thời gian
  - các Business rule đã được sử dụng không được hảd delete
**Rule áp dụng**
  - Deposit type chỉ có 2 loại $ hoặc %
  - Nếu là $: sử dụng trực tiếp Deposit value tiền cọc
  - Nếu là %: tính tiền cọc = deposit value* unit-price;
  - Chính sách được áp dụng cho khách hàng phải được các định tại thời điểm khách hàng chập nhận đề xuất thuê kho
  - vesion đã áp dụng cho hợp đồng phải được lưu lại để tránh việc thay đổi policy mới ảnh hưởng để hợp đồng cũ 

  -**note** 
    + Ngoài ra có thể phát triển thêm bảng [discounts] hỗ trở khách hàng dùng giả giá, và [active] giúp admin quản lý hoạn động của BOM
    + Chưa có idea làm version rule
  
  **Các chính sách liên quan**
    - Bảo trì:
      - Thời gian bảo trì, refresh kho cein 1 tháng kể từ khi trả kho và 1 năm kể từ lần bảo trì gần nhất. Data được lưu ở units
    - Hình thức ký: Ký điện tử + Hợp đồng giấy khi bàn giao kho
    - Hợp đồng sinh ra mỗi khi bắt đầu và kết thúc thuê kho
    - 100% Thanh toán online
    - Hiện tại chưa có chính sách hoàn tiền. Đang cân nhắc đến việc lập bảng giảm giá  [discounts];
    - Đối với các chính sách liên quan đến khóa tài khoản. Các tài bị khóa được lưu trong table [Account disabled]
      - id
      - Cus-id
      - time (tính theo ngày, time<0 -> khóa vĩnh viễn)
      - effective_from 
      - description

#### 4.2 Các loại kho và phí
  **Context**
    BOM quản lý loại kho, giá thuê và các khoản phí được áp dụng trong hệ thống. Flow 4 là nơi cấu hình dữ liệu giá để các flow khác sử dụng trong quá trình đặt, thuê, gia hạn và trả kho.

##### 4.2.1 Quản lý loại kho
**Flow**
  [BOM đăng nhập]->[Page Unit Type]->[CRUD/ kích hoạt-vô hiệu]->[kiểm tra hợp lệ]->[Save]

**Details**
  BOM quản lý inf unit type thuê theo tháng
**Schema**
  UnitType 
    - id 
    - name 
    - size 
    - default-price (min-price/max-price) 
    - description
    - status 
    - created_by 
    - created_at 
    - updated_at

**Validation**
  - default-price >0;
  - Unit type đã được sử dụng không được hảd delete
  - Unit muốn check giá phải tham chiếu qua Unit type
  - Unit type không sử dụng có thể để qua inactive

##### 4.2.2 Quản lý fee
  **Flow** 
    [Bom đăng nhập]->[Page quản lý Fee]->[CRUD/ Kích hoạt-vô hiệu]->[kiểm tra hợp lệ]->[Save]
  **Detail**
    Fee dùng để định nghĩ các khoản phí phát sinh trong quá trình sử dụng hệ thống như: Booking fee, rental fee, renewal fee, return fee, penalty fee, v.v.
  **Schema**
    Fee:
      - id
      - code
      - name
      - category
      - amount
      - caculation type
      - facility-id
      - effective_from 
      - effective_to 
      - status 
      - created_by 
      - created_at 
      - updated_at

  **Calculation**
    - Fixed:	amount * số lần /tháng     VD: mất chìa khóa 63 lần/tháng
    - Daily:	amount * số ngày /tháng    VD: thuê máy kéo 13 ngày/ tháng
    - Monthly:	amount /tháng            VD: wifi
    - Percentage:	price * amount%        VD: Hư hỏng kho 36%

  **Validation**
    - amount >0;
    - nếu caculation-type = % thì amount thuộc 0-100;
    - effective_from < effective_to 
    - Fee đã được sử dụng không được hard delete.
    - Fee hết hiệu lực được chuyển sang Inactive/Expired. 

  **Fee phát sinh**
    [Business Transaction]->[Check fee]->[Tính fee]->[Tạo bill]->[Invoice - Payment]->[Thông báo Cus]
    ** Sau bước tính phí áp dụng mô hình của tạo hóa đơn đặt cọc.

**Note** 

**Payment** 
  - Có thể ghép chung với phần tạo hóa đơn, ở đây chỉ lên idea payment cho fee
  - Table: id, cus-id, contract-id(hình như ở flow2), (reservation-id), payment-type(deposit/rental/renewal/Fee/...), amount, payment-method, code, status, paid-at, created-at.
  - Khi FM/FS tạo yêu cầu về phí: Sys dựa vào type + caculaiton + asc created-at để lấy data. Đối các phí daily/monthly/... Sys sẽ lưu hóa đơn và sẽ thông báo cho Cus trước khi đến kỳ hạn (Flow đoạn này giống phần thanh toán bàn giao kho)
 
#### 4.3 Dashboard theo dõi doanh thu chi nhánh

**Context:** 
  - BOM sử dụng Dashboard để theo dõi doanh thu của các Facility/chi nhánh dựa trên dữ liệu giao dịch thực tế của hệ thống.

**Flow tổng quát:**
  [Bom đăng nhập]->[DB doanh thu]->[Chọn thời gian]->[Lọc Facility/unit type/Cus]->[Tổng hợp dữ liệu]->[Hiển thị báo cáo]

**Details:**
  - Theo dõi doanh thu theo thời gian, facility, unit type, cus.
  - So sánh doanh thu giữ các khoảng thời gian
  - Theo dõi các khoản đã thanh hóa, chờ thanh toán
**Data source**
  Payment -> Invoice -> Contract -> StorageUnit -> Facility

**Các chỉ số chính**
  - Collected Revenue: tổng tiền thanh toán thành công.
  - Outstanding: tổng tiền còn phải thanh toán.
  - Refunded: tổng tiền đã hoàn.
  - Gross Billed: tổng giá trị đã lập hóa đơn.

**VD**  Hiển thị dữ liệu trong 2 tháng gần nhất và cho phép BOM thay đổi khoảng thời gian.

#### Features
  - Facility-specific pricing override.
  - Discount/Promotion management.
  - Dynamic pricing.
  - Advanced revenue analytics.
  - Automatic revenue forecasting.
  - Custom dashboard widgets.
Advanced policy rule engine.

### 5. Quản lý chi nhánh và nhân sự (BOM & FM)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)
NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.
### 7. Yêu cầu hỗ trợ và xử lý sự cố
