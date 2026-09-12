# Bản nháp phân tích

## Techstack

- Platform: Web Only

## Overview

- Một ứng dụng quản lý việc cho thuê các chuỗi kho tự chứa (thuê xong muốn chứa gì chứa)

## Terms

**FM:** Facility Manager - Quản lý cơ sở \
**FS:** Facility Staff - Nhân viên vận hành kho \
**BOM:** Business Operation Manager - Quản lý tổng các chuỗi

## Actors

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
    - customer_email
    - customer_phone
    - unit_type
    - facility
    - start_date (MM/DD/YYYY)
    - period - số tháng thuê
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

### 5. Quản lý chi nhánh và nhân sự (BOM & FM)

FLOW tổng quát:

[BOM tạo/quản lý Facility] -> [FM setup khoang chứa tại Facility] -> [FM điều phối Facility Staff] -> [FM theo dõi báo cáo cơ sở]
### 5.1 Quản lý cơ sở (Facility)

**Context: BOM là người duy nhất có quyền tạo mới và quản lý danh sách toàn bộ cơ sở/chi nhánh trong hệ thống, đồng thời gán FM phụ trách cho từng cơ sở.**

Flow tổng quát: BOM tạo mới một Facility → điền thông tin cơ bản của cơ sở → gán một FM (đã có account + role) phụ trách cơ sở đó → cơ sở đủ điều kiện để FM tiếp tục setup khoang chứa.

Details:

BOM:
Tạo/sửa/vô hiệu hóa Facility (không nên xóa cứng vì còn liên kết StorageUnit, RentalOrder... của cơ sở) với các thông tin: tên cơ sở, địa chỉ, số điện thoại, giờ hoạt động, trạng thái (Active/Inactive).
Gán/thay đổi FM phụ trách cho từng Facility.
Vấn đề cần chốt: một FM chỉ phụ trách 1 facility (1–1) hay có thể phụ trách nhiều facility cùng lúc (1–n)? Cái này ảnh hưởng đến toàn bộ RBAC và các query filter theo facility ở các flow khác.
Cross-reference: Việc tạo account và gán role FM cho một người dùng thuộc phạm vi System Administrator. Ở đây BOM chỉ chọn/gán một account FM có sẵn cho facility, chứ không tạo account mới.
### 5.2 Quản lý khoang chứa (Storage Unit) tại cơ sở

**Context: FM khai báo và duy trì dữ liệu các khoang chứa vật lý tại cơ sở mình phụ trách — đây là nguồn dữ liệu nền cho khách xem/đặt kho và cho FS thao tác khi bàn giao/trả kho.**

Flow tổng quát: FM tạo mới/cập nhật StorageUnit (loại, kích thước, vị trí, giá thuê, trạng thái) → khoang chứa hiển thị cho khách xem và để FM chỉ định (assign) khi duyệt RentalRequest.

Details:

FM:
Tạo StorageUnit: unit_code, unit_type, size, location (khu/tầng/dãy), rental_price, trạng thái ban đầu (mặc định Available).
Sửa thông tin khoang chứa; chuyển trạng thái thủ công trong các trường hợp đặc biệt (VD: chuyển sang Maintenance khi phát hiện sự cố ngoài quy trình trả kho thông thường).
Xem danh sách khoang chứa theo filter: loại, trạng thái, khu vực.
Ràng buộc: giá thuê FM nhập vào phải nằm trong khung giá đã được BOM cấu hình (validate ở BE) — không được tự set giá vượt khung.
Cross-reference (quan trọng — trạng thái StorageUnit là dữ liệu dùng chung xuyên suốt các flow):
Flow 1 (Đặt kho): FM đọc các unit có trạng thái Available để chỉ định unit_id khi Approve một RentalRequest; hệ thống chuyển trạng thái sang OnHold/Reserved khi được duyệt/đặt cọc.
Flow 2 (Check-in/Handover): trạng thái chuyển sang Rented sau khi FS bàn giao thành công.
Flow 3 (Trả kho): trạng thái chuyển sang Maintenance khi khách trả kho (ghi chú gốc: giữ MAINTENANCE 1–3 ngày trước khi cho thuê lại).
Flow 4 (Business rules): khung giá thuê tham chiếu chính sách giá do BOM quản lý — Flow 5 chỉ được dùng khung giá này, không định nghĩa lại.
### 5.3 Quản lý & điều phối Facility Staff

**Context: FM điều phối các FS tại cơ sở để hỗ trợ check-in, check-out, kiểm tra khoang chứa và xử lý sự cố on-site.**

Flow tổng quát: FM xem danh sách FS thuộc cơ sở mình → phân công FS cho một lịch hẹn hoặc một sự cố cụ thể → theo dõi tiến độ xử lý của FS.

Details:

FM:
Xem danh sách FS được gán vào cơ sở của mình.
Phân công FS phụ trách một appointment cụ thể để hỗ trợ khách check-in/bàn giao.
Phân công FS xử lý một SupportRequest/sự cố tại kho.
Theo dõi trạng thái công việc đã giao (đang xử lý / hoàn thành / quá hạn).
MVP scope: chỉ cần "phân công theo task/appointment", chưa cần quản lý ca làm việc (shift) chi tiết kiểu HRM.
Cross-reference:
Cần dữ liệu lịch hẹn on-site (appointment) được tạo ra ở Flow 1 (mục 1.1/1.3, sau khi khách đặt cọc thành công) và dùng trong Flow 2 để biết cần phân công FS vào thời điểm nào.
Cần dữ liệu SupportRequest từ Flow 7 (Yêu cầu hỗ trợ và xử lý sự cố) để phân công FS xử lý.
Việc gán một FS vào một facility cụ thể (account–facility mapping, phục vụ RBAC) thuộc phạm vi System Administrator — đây là điều kiện tiên quyết để FM có danh sách FS để chọn, dù không nằm trong 5 flow chính.
### 5.4 Báo cáo cơ sở

**Context: FM cần theo dõi tình hình vận hành của riêng cơ sở mình phụ trách.**

Details:

FM: xem dashboard/report theo filter thời gian và loại khoang: số khoang trống/đã thuê, tỷ lệ lấp đầy, doanh thu, số ca quá hạn tại cơ sở.
Cross-reference:
Số liệu doanh thu và phí phụ thuộc chính sách giá/phí từ Flow 4.
Số ca quá hạn phụ thuộc dữ liệu xử lý từ Flow 6 (Xử lý quá hạn/gia hạn).
Report so sánh nhiều cơ sở / toàn hệ thống thuộc quyền BOM (Flow 4) — FM chỉ xem được report của cơ sở mình phụ trách (ràng buộc theo RBAC).
Schema (liên quan)
Facility — cơ sở/chi nhánh, có FM phụ trách.
StorageUnit — khoang chứa thuộc một Facility, trạng thái dùng chung với Flow 1/2/3.
Account (role FM/FS) — được tạo/gán role bởi System Administrator, gán vào Facility để phục vụ RBAC.
(Tùy chọn, chưa MVP) StaffAssignment — bản ghi phân công FS cho appointment/sự cố cụ thể.


###### NOTES
Chưa chốt quan hệ FM–Facility là 1–1 hay 1–n.
Flow 5 phụ thuộc dữ liệu tài khoản & phân quyền theo facility vốn thuộc trách nhiệm System Administrator — nên coi đây là pre-condition, không tự làm trong Flow 5.
Trạng thái StorageUnit là điểm giao thoa nhiều nhất với các flow khác (1, 2, 3) — cần định nghĩa rõ state machine của StorageUnit chung cho toàn hệ thống trước khi thiết kế chi tiết Flow 5, tránh mỗi flow tự ý set trạng thái gây conflict.
Khung giá của StorageUnit phải validate theo chính sách của Flow 4, không để FM nhập tự do.
Advanced Features (not MVP)
Quản lý ca làm việc (shift scheduling) chi tiết cho FS.
Tự động phân công FS dựa trên khối lượng công việc hiện tại hoặc vị trí trong kho.
Cảnh báo tự động cho FM khi tỷ lệ lấp đầy cơ sở quá thấp hoặc quá cao (gần full).

### 6. Xử lý quá hạn/gia hạn (BOM & FM)

NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.

### 7. Yêu cầu hỗ trợ và xử lý sự cố
