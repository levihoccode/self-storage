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

**FLOW:**
```
[BOM tạo Facility] -> [BOM chỉ định role (FM/FS) cho nhân sự] -> [Admin thực thi tạo/cập nhật Account] -> [BOM gán FM vào Facility] -> [FM setup khoang chứa tại Facility] -> [FM điều phối Facility Staff] -> [FM theo dõi báo cáo cơ sở]
```

#### 5.0 Quản lý tài khoản & phân quyền (System Administrator)

**Context:** System Administrator là người duy nhất có quyền **thực thi kỹ thuật** việc tạo tài khoản và cập nhật role trong hệ thống. Admin không quyết định ai giữ role gì — quyết định đó thuộc về BOM (xem mục 5.1). Admin chỉ đảm bảo thao tác tạo/sửa account được thực hiện đúng, an toàn và có audit trail.

**Flow tổng quát:** BOM chỉ định role (FM/FS) cho 1 người → gửi `AccountRoleRequest` lên Admin → Admin tra cứu email, tạo account mới hoặc cập nhật role account đã tồn tại → account đủ điều kiện hoạt động theo đúng phạm vi quyền hạn.

**Details:**

- **Admin:**
  - Tạo Account cho nhân sự nội bộ (FM, FS, BOM) theo 2 cách: tạo thủ công từng account, hoặc import hàng loạt qua file Excel/Sheets (validate từng dòng, trả báo cáo lỗi). Riêng Customer tự đăng ký tài khoản (Flow 1, mục 1.2), Admin không tạo hộ.
  - Xử lý `AccountRoleRequest` do BOM gửi lên (xem mục 5.1) — mỗi dòng kèm **role đã được BOM chỉ định trực tiếp**, không phải đề xuất chờ duyệt:
    - Tra cứu email trong dòng đó xem đã có account chưa.
    - **Chưa có account:** tạo mới account, gán `role_id` tương ứng.
    - **Đã có account:** chỉ **cập nhật `role_id`** thành role BOM chỉ định — không tạo account trùng.
    - Nếu role là **FS**: sau khi tạo/cập nhật, Admin gán luôn account vào facility dự kiến (tạo dòng `AccountFacilityAssignment`).
    - Nếu role là **FM**: Admin chỉ tạo/cập nhật account, **không** gán facility — việc gán FM vào Facility thuộc quyền BOM (xem mục 5.1), vì `Facility.fm_account_id` là field BOM trực tiếp quản lý.
    - Chuyển status dòng đó sang `Done`. Admin không có quyền `Reject` vì đây là lệnh thực thi, không phải đề xuất chờ xét duyệt — nếu dữ liệu dòng đó lỗi (email sai định dạng...), Admin báo lỗi lại cho BOM chứ không tự ý đổi role khác.
  - **Đổi role account đã tồn tại đang giữ facility assignment (bắt buộc, trong 1 transaction):**
    - Nếu account đang là FM của 1 Facility (`Facility.fm_account_id = account_id`) và bị đổi sang role khác: hệ thống **tự động clear** `Facility.fm_account_id` về `null`.
    - Nếu account đang là FS có dòng `AccountFacilityAssignment` và bị đổi sang role khác: hệ thống **tự động xoá** dòng đó.
    - Toàn bộ thao tác đổi role + clear/xoá assignment phải nằm trong cùng 1 transaction, tránh để lại data-scope "mồ côi".
  - Ghi `AuditLog` cho mọi hành động nhạy cảm: tạo account, đổi role, gán/xoá facility assignment (cấu trúc bảng dùng chung với Flow 3 — xem Schema).
  - Theo dõi lịch sử đăng nhập của từng account qua bảng `LoginHistory` (xem Schema).
  - **Thiết lập quyền truy cập dữ liệu theo model RBAC:** Admin quản lý bảng `Role`/`Permission`/`RolePermission` — gán tập permission cho từng role qua giao diện quản trị (data-driven, không hard-code trong source code). Đây chính là cách Admin thực hiện nhiệm vụ *"Thiết lập quyền truy cập dữ liệu cho các role dựa trên model RBAC"* đã mô tả ở phần Actors.

- **RBAC — Bảng phân quyền tổng quát (tham chiếu, áp dụng xuyên suốt mọi flow):**

| Role | Phạm vi dữ liệu (data scope) | Nhóm quyền chính |
|---|---|---|
| Customer | Chỉ dữ liệu của chính mình (account_id = self) | Xem kho, đặt kho, thanh toán, quản lý kho đã thuê, gửi support request |
| Facility Staff | Chỉ dữ liệu thuộc facility được gán (qua `AccountFacilityAssignment`) | Check-in/out, cập nhật trạng thái unit, xử lý sự cố on-site |
| Facility Manager | Chỉ dữ liệu thuộc đúng 1 facility mà `Facility.fm_account_id` trỏ tới mình | Duyệt request, gán unit, quản lý FS, xem report của facility mình |
| Business Operation Manager | Toàn bộ dữ liệu mọi facility | Set chính sách, giá/phí, xem report toàn hệ thống, chỉ định role nhân sự |
| System Administrator | Toàn bộ account + config, KHÔNG thao tác nghiệp vụ thuê kho | Tạo/cập nhật account, quản lý Role/Permission, xem audit log & login history |

  - Bảng trên là dữ liệu **khởi tạo mặc định** (seed data) cho `Role`/`RolePermission` ở MVP, không phải giới hạn cứng trong code — Admin có thể điều chỉnh permission chi tiết hơn qua bảng dữ liệu khi cần, không cần deploy lại.

- **Cross-reference:**
  - `Facility.fm_account_id` là nguồn duy nhất lưu quan hệ FM–Facility (1–1) — Account không lưu `facility_id` cho FM; cần biết FM phụ trách facility nào thì truy vấn ngược từ `Facility.fm_account_id`.
  - Facility assignment cho FS liên kết với mục 5.3, qua `AccountFacilityAssignment` do Admin quản lý.
  - Việc Customer tự tạo account thuộc Flow 1 (mục 1.2), không thuộc phạm vi Admin.
  - Cấu trúc `AuditLog` dùng chung với quyết định ở Flow 3 (mục 3.6), không tự định nghĩa bản riêng ở Flow 5.

#### 5.1 Quản lý cơ sở (Facility)

**Context:** BOM là người duy nhất có quyền tạo mới và quản lý danh sách toàn bộ cơ sở/chi nhánh, đồng thời là người **quyết định role** (FM/FS) cho nhân sự và gán FM phụ trách cho từng cơ sở. Admin chỉ thực thi kỹ thuật (mục 5.0), không quyết định nghiệp vụ ai giữ role gì.

**Flow tổng quát:** BOM tạo mới một Facility → điền thông tin cơ bản → chỉ định role (FM/FS) cho nhân sự cần thiết và gửi danh sách lên Admin thực thi → sau khi Admin xử lý xong, BOM gán FM vào Facility → Facility đủ điều kiện chuyển `Active` để FM tiếp tục setup khoang chứa.

**Details:**

- **BOM:**
  - Tạo/sửa/vô hiệu hoá Facility (không xoá cứng vì còn liên kết StorageUnit, RentalOrder...) với thông tin: code (mã chi nhánh, dùng trong Invoice.code/RentalContract.code, ví dụ "Q7", "TD"), tên cơ sở, địa chỉ, số điện thoại, giờ hoạt động, cấu hình loại khóa hỗ trợ (enabledKeyAccess/enabledCodeAccess, ít nhất 1 cờ phải bật), trạng thái (Active/Inactive).
  - **Ràng buộc bắt buộc:** Facility mới tạo mặc định `Inactive`, chỉ được BOM chuyển sang `Active` **sau khi** `fm_account_id` đã có giá trị. Facility `Inactive` không hiển thị cho khách ở Flow 1 và FM không tạo được `StorageUnit` cho tới khi Facility `Active`.
  - Gán/thay đổi FM phụ trách cho từng Facility bằng cách set trực tiếp `Facility.fm_account_id`. **Đã chốt: quan hệ FM–Facility là 1–1**, và đây là field duy nhất lưu quan hệ này trong toàn hệ thống.
  - Nếu Facility chưa có FM:
    - **Đã có sẵn account FM rảnh:** BOM chọn trực tiếp account đó, set `fm_account_id`.
    - **Chưa có account FM phù hợp:** BOM chỉ định role FM cho 1 người và gửi vào danh sách `AccountRoleRequest`.
  - **Tạo danh sách chỉ định nhân sự (`AccountRoleRequest` — dạng batch/list):** mỗi dòng: họ tên, email, role đã quyết định (FM hoặc FS), facility dự kiến → gửi lên Admin thực thi.
  - Sau khi Admin xử lý xong danh sách:
    - Dòng **FS**: Admin đã tạo/cập nhật account và gán facility xong — BOM không cần làm gì thêm.
    - Dòng **FM**: Admin chỉ tạo/cập nhật account — BOM tự thực hiện bước "Gán/thay đổi FM phụ trách" ở trên.

**Schema `AccountRoleRequest`** *(thay thế `AccountCreationRequest`)*:
- batch_id (nullable) — nhóm các dòng cùng 1 lần BOM gửi lên.
- requested_by (BOM)
- target_name
- target_email
- role (FM hoặc FS)
- target_facility_id
- status (Pending/Done)
- account_id (nullable) — gán sau khi Admin xử lý xong dòng đó.
- created_at
- expires_at

**Phụ thuộc chưa chốt (không thuộc phạm vi thiết kế của Flow 5):**
- Giá thuê theo `UnitType` — **thuộc Flow 4**, Flow 5 chỉ đọc `UnitType.monthly_price` khi tạo `StorageUnit`, không tự định nghĩa lại khung giá.
- `SupportRequest` mà 5.3 cần dùng để phân công FS xử lý sự cố — **thuộc Flow 3/7**, Flow 5 chỉ đọc/ghi field `assigned_staff_id`, không tự định nghĩa lại cấu trúc bảng.

#### 5.2 Quản lý khoang chứa (Storage Unit) tại cơ sở

**Context:** FM khai báo và duy trì dữ liệu các khoang chứa vật lý tại cơ sở mình phụ trách — chỉ thực hiện được sau khi Facility đã `Active`.

**Flow tổng quát:** FM tạo mới/cập nhật StorageUnit (chọn loại, vị trí, trạng thái) → khoang chứa hiển thị cho khách xem và để FM chỉ định khi duyệt RentalRequest.

**Đã chốt (B1 — Flow 5 là owner của enum `StorageUnit.status`, dùng chung toàn hệ thống):**
```
Available |Reserved | Rented | Maintenance
```
- `Available`: sẵn sàng cho thuê.
- `Reserved`: đã đặt cọc, giữ tới khi bàn giao (Flow 1 → Flow 2).
- `Rented`: đã bàn giao, đang có hợp đồng `Active` (Flow 2).
- `Maintenance`: đang bảo trì hoặc đang sửa sự cố (Flow 2.5, hoặc FM chuyển thủ công ở mục này).

**Đã chốt (A4 — owner mở lại khoang sau Maintenance):** việc tự động chuyển `Maintenance -> Available` sau khi trả kho **thuộc cron của Flow 2.5**, không phải Flow 5. Flow 5 (mục này) **chỉ** xử lý các trường hợp FM chuyển trạng thái **thủ công** cho sự cố ngoài luồng trả kho (khoang hư hỏng đột xuất khi đang `Available`/`Rented`...) — không đụng vào cron tự động của Flow 2.5. **Cơ chế kỹ thuật:** khi FM chuyển `Maintenance` do sự cố, **không set `StorageUnit.maintenance_started_at`** — field này chỉ do Flow 2.5 set khi `Maintenance` phát sinh từ luồng trả kho, vì cron của Flow 2.5 chỉ quét các bản ghi có field này khác null để tự động mở lại theo `Policy.unit.maintenance_days`. Để trống field này là cách duy nhất đảm bảo cron không tự ý mở lại 1 khoang đang bị FM giữ vì sự cố.

**Details:**

- **FM:**
  - Tạo `StorageUnit`: `unit_code`, chọn `unit_type_id` (tham chiếu tới `UnitType` do BOM quản lý ở Flow 4), `location`, trạng thái ban đầu (mặc định `Available`).
  - **Không tự nhập giá thuê cho từng khoang.** Giá thuê là thuộc tính của `UnitType` (`UnitType.monthly_price`), do BOM cập nhật trực tiếp ở Flow 4 — mọi `StorageUnit` cùng `unit_type_id` tại một thời điểm dùng chung một mức giá. FM chỉ chọn đúng loại khoang, không có bước validate khung giá vì FM không nhập số tiền.
  - Chuyển khoang sang `Maintenance` thủ công:
    - `Available` → chuyển trực tiếp sang `Maintenance`.
    - `Reserved` → không chuyển trực tiếp, phải xử lý request/order liên quan và thông báo khách trước.
    - `Rented` → không tự ý chuyển; tạo yêu cầu xử lý sự cố, thông báo khách, thực hiện phương án di chuyển/tạm ngưng theo nghiệp vụ đã duyệt.
    - Đang `Maintenance` → không xuất hiện trong danh sách khoang có thể đặt/assign.
    - Sau khi xử lý xong sự cố, FM chuyển khoang về `Available` thủ công (không set lại `maintenance_started_at`, field này giữ null suốt vòng đời của case sự cố).
  - Mọi thay đổi trạng thái thủ công phải ghi `AuditLog` (người thực hiện, thời điểm, lý do).
  - Cross-reference: trạng thái StorageUnit dùng chung xuyên suốt Flow 1 (`Reserved` khi duyệt/đặt cọc), Flow 2 (`Rented` sau bàn giao), Flow 2.5 (`Maintenance` khi trả kho).

**Advanced Features (not MVP):**

- FM đề xuất mức giá riêng cho từng `StorageUnit` (khác với giá mặc định của `UnitType`) khi có lý do đặc biệt (vị trí xấu, hư hao 1 phần...); đề xuất chỉ **có hiệu lực sau khi BOM phê duyệt trên Flow 4** — FM không được tự áp giá, kể cả ở dạng "chờ duyệt". Khi triển khai, đây là quyết định và thao tác của BOM (Flow 4), Flow 5 chỉ là nơi FM gửi đề xuất kèm lý do, không sở hữu logic tính/áp giá.

#### 5.3 Quản lý & điều phối Facility Staff

**Context:** FM điều phối các FS tại cơ sở để hỗ trợ check-in, check-out, kiểm tra khoang chứa và xử lý sự cố on-site.

**Flow tổng quát:** FM xem danh sách FS thuộc cơ sở mình (qua `AccountFacilityAssignment`) → phân công FS trực tiếp trên Appointment.staff_id... (bảng do Flow 1 sở hữu — xem specs/flow-1/db-table-draft.md, không phải Flow 2 như ghi trước đó)

**Details:**

- **FM:**
  - Xem danh sách FS được gán vào cơ sở của mình.
  - Xem `Appointment` của cơ sở theo ngày, lọc `staff_id IS NULL` để thấy lịch chưa phân công, và gán FS: set `Appointment.staff_id`.
    **Đã chốt (A6, thống nhất với Flow 2):** `staff_id`/`appointment_date` **không** còn nằm trên `RentalOrder` — chuyển hẳn sang bảng Appointment (Flow 1 sở hữu), Flow 5 chỉ đọc/ghi `staff_id`..
  - Phân công FS xử lý 1 `SupportRequest`: set `SupportRequest.assigned_staff_id` (bảng do Flow 3/7 sở hữu — xem 5.1).
  - Theo dõi tiến độ qua status của `Appointment`/`SupportRequest` tương ứng — MVP không dùng bảng `StaffAssignment` riêng.
  - MVP scope: chỉ "phân công theo task/appointment", chưa quản lý ca làm việc (shift) chi tiết.
  - Cross-reference: cần `Appointment` (Flow 1 sở hữu — check-in, bàn giao, trả kho) và `SupportRequest` (Flow 3/7).

#### 5.4 Báo cáo cơ sở

**Context:** FM cần theo dõi tình hình vận hành của riêng cơ sở mình phụ trách.

**Details:**

- **FM:** xem dashboard/report theo filter thời gian và loại khoang: số khoang trống/đã thuê, tỷ lệ lấp đầy, doanh thu, số ca quá hạn tại cơ sở.
- Truy vấn report **bắt buộc lọc theo facility mà `Facility.fm_account_id` trỏ tới FM đang đăng nhập** ở tầng API (không chỉ ẩn ở FE) — tránh rủi ro IDOR.

**Cross-reference:**
- Doanh thu/phí phụ thuộc chính sách giá từ Flow 4.
- Số ca quá hạn phụ thuộc dữ liệu từ Flow 6.
- Report toàn hệ thống/so sánh nhiều cơ sở thuộc quyền BOM (Flow 4).

**Schema (liên quan, tổng hợp toàn Flow 5):**

- `Account` — `role_id (N-1: Role)`, không có field `facility_id` (xem 5.0 Cross-reference).
- `Role`, `Permission`, `RolePermission` — mô hình RBAC data-driven do Admin quản lý (xem 5.0), thay cho phương án hard-code permission trong code.
- `Facility` — có `code` (unique, dùng cho mã hóa đơn/hợp đồng), `fm_account_id` (1–1, nguồn duy nhất), mặc định `Inactive` khi tạo mới.
- `AccountFacilityAssignment` — mapping FS–Facility (1–n), do Admin quản lý.
- `AccountRoleRequest` — thay thế `AccountCreationRequest`, chỉ còn status `Pending`/`Done`.
- `StorageUnit` — thuộc 1 Facility, tham chiếu `unit_type_id` (không tự lưu giá), trạng thái dùng chung Flow 1/2/2.5.
- `UnitType` — **thuộc Flow 4**, Flow 5 chỉ đọc `monthly_price` khi tạo `StorageUnit`.
- `AuditLog` — dùng chung cấu trúc với Flow 3 (`account_id`, `action`, `entity_type`, `entity_id`, `old_value`, `new_value`, `created_at`), không tự định nghĩa bản tối giản riêng.
- `LoginHistory` — thuộc phạm vi Admin (5.0): `account_id` (nullable), `email`, `ip_address`, `user_agent`, `status`, `failure_reason`, `created_at`.
- ~~`PricingPolicy`~~ — loại bỏ, xung đột với mô hình `UnitType.monthly_price` đã dùng ở Flow 1/2/3/4.
- ~~`StaffAssignment`~~ — loại khỏi phạm vi MVP, dùng field trực tiếp trên `RentalOrder`/`SupportRequest`.
- `SupportRequest` — **không định nghĩa lại ở đây**, bảng thuộc Flow 3/7; Flow 5 chỉ đọc/ghi `assigned_staff_id`.

**Advanced Features (not MVP):**
- Import hàng loạt account qua Excel/Sheets.
- `PricingPolicy` override riêng theo từng facility (nếu sau này cần giá khác nhau theo chi nhánh, thay vì `UnitType.monthly_price` áp dụng toàn hệ thống).
- Unique constraint DB-level cho quan hệ 1–1 FM–Facility (MVP validate ở application layer).
- Quản lý ca làm việc (shift scheduling) chi tiết cho FS.
- Tự động phân công FS dựa trên khối lượng công việc/vị trí.
- Cảnh báo tự động khi tỷ lệ lấp đầy cơ sở quá thấp/cao.
### 6. Xử lý quá hạn/gia hạn (BOM & FM)

NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.

### 7. Yêu cầu hỗ trợ và xử lý sự cố
