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

**FLOW:**

```text
[BOM cấu hình chính sách/giá/phí] -> [Hệ thống áp dụng tự động hoặc làm ngưỡng chặn cho thao tác thủ công] -> [BOM theo dõi doanh thu theo chi nhánh/thời gian]
```

**Context:** Flow 4 là nơi tập trung mọi tham số nghiệp vụ (giá, phí, ngưỡng thời gian, điều khoản hợp đồng) mà các flow khác (1, 2, 2.5, 3, 5, 6) đọc để vận hành — không flow nào khác được tự định nghĩa lại các giá trị này. Mục tiêu là để BOM đổi chính sách bằng thao tác trên UI, không phải yêu cầu dev sửa code, trừ khi chính sách đó đòi hỏi hành vi hệ thống hoàn toàn mới.

#### 4.0 Nguyên tắc thiết kế

**2 cấp độ thay đổi chính sách:**

- **Cấp độ 1 — Zero Code Deployment:** chính sách chỉ đổi về dữ liệu/tham số/danh mục. BOM thao tác trực tiếp trên UI (`Policy`/`UnitType`/`ExtraFee`/`Discount`/`RentalTerm`), code chỉ đọc theo key/id, không hard-code giá trị.
- **Cấp độ 2 — Feature Development:** chính sách đòi hỏi hành vi/tích hợp chưa tồn tại trong code (VD: tích hợp Zalo ZNS đòi nợ, tự động đăng flash sale khi trả kho sớm). Không tự cấu hình được trên UI — phải qua Change Request: BOM soạn chính sách → dev phân tích/thiết kế/code → deploy → BOM mới có tham số mới trên UI.

Ranh giới này quyết định toàn bộ thiết kế bên dưới: mục 4.1–4.5 chỉ cung cấp **dữ liệu cấu hình**, không tự chứa logic nghiệp vụ — logic đọc/áp dụng luôn nằm ở flow tiêu thụ (1, 2, 2.5, 3, 5, 6). Flow 4 không tự chạy cron, không tự khóa account, không tự duyệt yêu cầu — đó là việc của flow sở hữu nghiệp vụ tương ứng.

**Phân loại cách một giá trị `Policy` được dùng (`execution_type`):**

- `Automated`: đọc bởi cron/event của flow tiêu thụ, không có con người can thiệp giữa chừng (VD: `unit.maintenance_days`).
- `ManualGuardrail`: là mức trần/sàn cho thao tác thủ công của FS/FM — flow tiêu thụ dùng để **chặn** nếu nhân viên vượt ngưỡng, có thể escalate lên BOM duyệt (Maker-Checker); cơ chế duyệt cụ thể (ai duyệt, trạng thái, thông báo) thuộc **flow tiêu thụ** (VD: Flow 6 cho miễn/giảm phạt quá hạn), Flow 4 chỉ giữ con số ngưỡng.

> **Chưa chốt:** Flow 1 hiện ghi các mốc thời gian của mình (`account.claim_ttl_days`, `invoice.deposit_due_days`...) như số cố định trực tiếp trong bảng tham số riêng, không ghi chú rõ đây là giá trị `Policy` do BOM cấu hình hay là hằng số cố định trong code. Bảng 4.2 dưới đây giả định đây là các giá trị **default seed** cho `Policy`, BOM chỉnh được qua UI như mọi key khác — cần Flow 1 xác nhận lại điều này trước khi merge.

#### 4.1 Quản lý giá thuê theo loại khoang (`UnitType`)

**Context:** Giá thuê được chốt tập trung theo loại khoang (Small/Medium/Large...), không phải theo từng `StorageUnit` riêng lẻ (đã thống nhất với Flow 5 — FM không tự nhập giá, chọn `unit_type_id` có sẵn).

**Flow tổng quát:** BOM xem danh sách `UnitType` → sửa `monthly_price` → giá mới áp dụng ngay cho mọi `StorageUnit` cùng loại kể từ thời điểm cập nhật.

**Details:**
- BOM tạo/sửa `UnitType`: tên, kích thước (width/depth/height/area), mô tả, `monthly_price` — **1 giá trị cố định duy nhất, không phải khung min–max**.
- Đổi `monthly_price` không ảnh hưởng ngược tới các `RentalContract` đã ký (giá đã snapshot ở `RentalContract.monthly_price` tại thời điểm ký) — chỉ ảnh hưởng khách mới đặt hoặc gia hạn sau thời điểm đổi giá.
- `UnitType` đã được dùng bởi ít nhất 1 `StorageUnit` thì không được hard-delete, chỉ chuyển `Inactive`.
- Ghi `AuditLog` mỗi lần đổi giá (old_value/new_value).
- MVP: 1 giá áp dụng toàn hệ thống cho mỗi `unit_type_id`, không phân biệt theo chi nhánh.

**Advanced Features (not MVP):**
- Giá khác nhau theo từng chi nhánh (bảng giá riêng theo `facility_id` + `unit_type_id`).
- FM đề xuất giá riêng cho 1 `StorageUnit` cụ thể (case đặc biệt) — chỉ có hiệu lực sau khi BOM phê duyệt trực tiếp tại đây; Flow 5 chỉ là nơi FM gửi đề xuất kèm lý do, không sở hữu logic tính/áp giá.

#### 4.2 Quản lý chính sách vận hành (`Policy`)

**Context:** Toàn bộ ngưỡng thời gian, mốc tính toán, quy tắc mà các flow khác cần nhưng không nên hard-code, được gom về đây dưới dạng key–value.

**Flow tổng quát:** BOM xem danh sách `Policy` theo nhóm (đặt kho, đặt cọc, bàn giao, gia hạn, quá hạn, bảo trì...) → sửa `value` của 1 key → các flow tiêu thụ đọc giá trị mới ngay từ lần truy vấn tiếp theo, không cần deploy lại.

**Schema:**
```
# Policy
- key (unique)
- value
- value_type (Number/Percent/Text/Boolean)
- execution_type (Automated/ManualGuardrail)
- description
- updated_by
- updated_at
```

**Danh mục key chính thức (tổng hợp từ toàn bộ các flow đã tham chiếu, đối chiếu lại theo bản Flow 1/2/2.5/3/5 mới nhất):**

| Key | value_type | execution_type | Dùng ở | Ý nghĩa | Giá trị mặc định |
|---|---|---|---|---|---:|
| `request.pending_expiry_days` | Number (ngày) | Automated | Flow 1 | Hạn `RentalRequest` còn `Pending` trước khi tự `Expired` | 7 |
| `account.claim_ttl_days` | Number (ngày) | Automated | Flow 1 | Hạn khách tạo tài khoản sau khi `RentalRequest` được duyệt, tính từ `responded_at` | 7 |
| `proposal.response_ttl_days` | Number (ngày) | Automated | Flow 1 | Hạn khách phản hồi 1 `ProposalFeedback` trước khi tự `Expired` | 3 |
| `invoice.deposit_due_days` | Number (ngày) | Automated | Flow 1 | Hạn thanh toán hóa đơn đặt cọc trước khi `Expired` | 3 |
| `appointment.booking_window_days` | Number (ngày) | Automated | Flow 1 | Khách phải chọn lịch check-in trong vòng N ngày kể từ lúc cọc | 7 |
| `appointment.max_days_after_deposit` | Number (ngày) | Automated | Flow 1 | Ngày hẹn check-in tối đa cách lúc cọc bao lâu | 14 |
| `appointment.daily_slot_count` | Number (khung) | Automated | Flow 1 | Số khung giờ cố định mỗi ngày cho lịch check-in/trả kho | 3 |
| `appointment.capacity_mode` | Text | Automated | Flow 1 | Chế độ sinh slot (MVP chỉ có `fixed_windows`) | `fixed_windows` |
| `appointment.checkin_reschedule_enabled` | Boolean | Automated | Flow 1 | Bật/tắt dời lịch check-in | `false` |
| `proposal.max_rejection_count` | Number (lần) | Automated | Flow 1 | Số lần khách được từ chối `ProposalFeedback` **trước khi cọc** trên 1 đơn | 3 |
| `order.deposit_expiry_days` | Number (ngày) | Automated | Flow 1 | Hạn giữ khoang `Reserved` kể từ lúc cọc tới khi bàn giao xong, dùng để set `RentalOrder.expires_at` | 30 |
| `fee.unit_change` | Number | Automated | Flow 1 | Phí phát sinh khi khách đổi sang khoang tương đương sau khi bị từ chối (trước hoặc sau cọc) | chờ BOM |
| `handover.max_rejection_count` | Number (lần) | Automated | Flow 2 | Số lần khách được từ chối khoang **tại chỗ, sau khi đã cọc** trên 1 đơn — tách khỏi `proposal.max_rejection_count` | 2 |
| `handover.payment_grace_hours` | Number (giờ) | Automated | Flow 2 | Thời gian gia hạn thanh toán tháng đầu tại buổi bàn giao trước khi cron hủy hợp đồng | chờ BOM |
| `contract.start_date_rule` | Text | Automated | Flow 2 | Quy tắc tính mốc bắt đầu tính tiền thuê (VD: 1 tuần sau ngày ký, ngày 15 hàng tháng...) | chờ BOM |
| `contract.prepaid_months` | Number (tháng) | Automated | Flow 2 | Số tháng thu trước tại buổi bàn giao | 1 |
| `contract.expiring_soon_days` | Number (ngày) | Automated | Flow 3 | Ngưỡng N ngày trước `end_date` để cảnh báo hợp đồng sắp hết hạn | chờ BOM |
| `extension.invoice_due_days` | Number (ngày) | Automated | Flow 3 | Hạn thanh toán hóa đơn gia hạn, tính từ lúc FM duyệt | chờ BOM |
| `unit.maintenance_days` | Number (ngày) | Automated | Flow 2.5, Flow 5 | Số ngày `StorageUnit.status = Maintenance` (phát sinh từ trả kho, `maintenance_started_at IS NOT NULL`) trước khi cron tự động chuyển về `Available` | 1–3 |
| `report.default_range_months` | Number (tháng) | Automated | Flow 4, Flow 5 | Khoảng thời gian mặc định hiển thị trên dashboard doanh thu của BOM (4.6) và báo cáo cơ sở của FM (Flow 5, mục 5.4) — dùng chung 1 key để 2 dashboard nhất quán hành vi | 2 |
| `overdue.fee_per_day` | Number | Automated | Flow 6 | Phí phạt mỗi ngày quá hạn — nơi **duy nhất** trong hệ thống tính phí trễ hạn (bao gồm cả trễ trả kho, xem NOTES bên dưới) | chờ BOM |
| `overdue.lock_after_days` | Number (ngày) | Automated | Flow 6 | Số ngày nợ phí quá hạn trước khi cron khóa hợp đồng/quyền truy cập | chờ BOM |
| `overdue.waive_max_percent` | Percent | ManualGuardrail | Flow 6 | Mức % nhân viên được tự quyết miễn giảm phạt, vượt mức phải chuyển BOM duyệt | chờ BOM |
| `overdue.waive_max_amount` | Number | ManualGuardrail | Flow 6 | Mức tiền tối đa nhân viên được tự quyết miễn giảm phạt | chờ BOM |
| `deposit.type` | Text (`Fixed`/`Percent`) | Automated | Flow 1 (`Invoice.amount`, type=Deposit) | Hình thức tính tiền cọc | `Percent` |
| `deposit.value` | Number | Automated | Flow 1 | Số tiền cố định hoặc % `UnitType.monthly_price` dùng để tính cọc | 50 |

**Details:**
- BOM tạo/sửa `value` cho từng key qua UI dạng danh sách + filter theo nhóm (prefix key: `request.*`, `account.*`, `proposal.*`, `appointment.*`, `invoice.*`, `order.*`, `handover.*`, `contract.*`, `extension.*`, `unit.*`, `overdue.*`, `report.*`...) và theo `execution_type` để tách rõ "tham số hệ thống tự chạy" với "ngưỡng cho nhân viên".
- Validate theo `value_type` khi lưu (Number phải parse được số, Percent phải trong [0,100], Boolean chỉ nhận true/false).
- Ghi `AuditLog` mỗi lần đổi giá trị policy.
- MVP không version hoá theo khoảng thời gian hiệu lực (không `effective_from`/`effective_to` cho từng bộ policy) — mỗi key chỉ có 1 giá trị hiện hành; lịch sử tra qua `AuditLog`. Việc này không tạo rủi ro cho hợp đồng cũ vì các giá trị đã "chốt" cho khách luôn được snapshot ở nơi phát sinh (`Invoice.amount`, `RentalContract.monthly_price`, `RentalContract.terms_version`, `RentalOrder.expires_at`) — không phụ thuộc giá trị `Policy` hiện tại.
- Với các tham số cần đi cùng nhau (như `overdue.waive_max_percent` + `overdue.waive_max_amount`), tách thành 2 key phẳng riêng thay vì gộp 1 giá trị JSON — giữ nhất quán với toàn bộ hệ thống, không cần logic parse JSON ở nơi tiêu thụ.
- Thêm key mới không cần đổi schema, nhưng **code đọc key đó phải được lập trình sẵn** — thêm 1 dòng dữ liệu không tự sinh hành vi mới nếu chưa có code đọc (đúng ranh giới Cấp độ 1 vs Cấp độ 2).
- `Percent`: `deposit_amount = UnitType.monthly_price (hoặc StorageUnit.monthly_price nếu có override) × deposit.value / 100`.
- `Fixed`: `deposit_amount = deposit.value` (không nhân với giá thuê) — giữ chỗ cho trường hợp BOM muốn cọc cố định không theo % (VD: khoang nhỏ cọc tối thiểu 500k dù giá thuê thấp hơn).
- `Invoice.amount` (Flow 1 sở hữu) vẫn đúng như thiết kế hiện tại: chỉ lưu **kết quả snapshot**, không lưu công thức — công thức nằm ở `Policy`, chỉ áp dụng tại thời điểm tạo hóa đơn.
- **Cơ chế Maker-Checker cho các key `ManualGuardrail`** (VD: nhân viên xin giảm phạt vượt `overdue.waive_max_percent`) là quy trình duyệt (approval queue, trạng thái, thông báo) thuộc **flow tiêu thụ** (Flow 6), không thiết kế ở đây — Flow 4 chỉ đảm bảo giá trị ngưỡng luôn sẵn có và đúng kiểu dữ liệu.

**NOTES:**
- **`fee.late_return_per_day` đã bị loại khỏi danh mục.** Flow 3 (mục 3.6, A4) đã chốt: Flow 6 là nơi duy nhất tính phí trễ hạn qua `overdue.fee_per_day`, tính từ `end_date` tới thời điểm chốt biên bản trả kho; Flow 2.5 không tự tính phí trả trễ riêng để tránh 2 flow cùng thu phí trùng khoảng ngày. Nếu Flow 2.5 vẫn còn tham chiếu `fee.late_return_per_day` ở đâu đó, đó là phần Flow 2.5 cần sửa theo, không phải việc thêm lại key này ở Flow 4.
- **`order.auto_cancel_days` đã bị loại khỏi danh mục.** Flow 2 đã xác nhận không còn cần key này — hạn giữ kho sau khi cọc mà chưa bàn giao nay dùng thẳng field `RentalOrder.expires_at` (set = `now + order.deposit_expiry_days` ngay lúc cọc thành công), không tra `Policy` mỗi lần cron chạy.
- **Các key `deposit.type`/`deposit.value`/`deposit.due_hours`/`request.account_timeout_hours`/`appointment.no_show_limit`/`appointment.reject_limit` đã bị loại khỏi danh mục** vì không khớp với bất kỳ field nào Flow 1/2 thực sự dùng trong bản mới nhất — đây là tên key sót lại từ bản nháp cũ, dùng sai đơn vị hoặc gộp nhầm 2 khái niệm khác nhau thành 1 key.
- `appointment.checkin_reschedule_enabled = false` là giá trị mặc định của MVP; khi bật dời lịch (Advanced Feature) mới cần thêm `appointment.reschedule_limit`/`appointment.reschedule_notice_hours`, xem Advanced Features bên dưới.

**Advanced Features (not MVP):**
- Version hoá theo bộ chính sách (nhiều bộ `effective_from`/`effective_to`, chỉ 1 bộ active tại 1 thời điểm).
- Validate chéo giữa các key liên quan (VD: `invoice.deposit_due_days` quy đổi giờ phải nhỏ hơn `order.deposit_expiry_days`).
- Bổ sung `appointment.reschedule_limit` (số lần dời lịch tối đa) và `appointment.reschedule_notice_hours` (giờ báo trước tối thiểu) khi Flow 1 bật tính năng dời lịch check-in.

---

**Schema (do Flow 4 sở hữu):** `UnitType`, `Policy`, `ExtraFee`, `Discount`, `RentalTerm`.

**Cross-reference (ai đọc gì từ Flow 4):**

| Flow | Đọc từ Flow 4 |
|---|---|
| Flow 1 | `request.pending_expiry_days`, `account.claim_ttl_days`, `proposal.response_ttl_days`, `invoice.deposit_due_days`, `appointment.booking_window_days`, `appointment.max_days_after_deposit`, `appointment.daily_slot_count`, `appointment.capacity_mode`, `appointment.checkin_reschedule_enabled`, `proposal.max_rejection_count`, `order.deposit_expiry_days`, `fee.unit_change` |
| Flow 2 | `UnitType.monthly_price`, `RentalTerm` (Active), `contract.start_date_rule`, `contract.prepaid_months`, `handover.payment_grace_hours`, `handover.max_rejection_count` |
| Flow 2.5 | `ExtraFee` (DMG/CLN/LOST-KEY...), `unit.maintenance_days` |
| Flow 3 | `contract.expiring_soon_days`, `extension.invoice_due_days`, `Discount` (nếu áp dụng cho gia hạn) |
| Flow 5 | `UnitType` (chỉ đọc khi FM chọn `unit_type_id`, không đọc/ghi giá), `unit.maintenance_days` (tham chiếu để giải thích cơ chế `maintenance_started_at`), `report.default_range_months` |
| Flow 6 | `overdue.fee_per_day`, `overdue.lock_after_days`, `overdue.waive_max_percent`, `overdue.waive_max_amount` |

**NOTES:**

- Mục 4.1–4.5 chỉ là **nguồn dữ liệu**, không tự chứa logic áp dụng — logic luôn nằm ở flow tiêu thụ. Nếu 1 flow cần hành vi mới mà dữ liệu ở đây không đủ diễn tả (Cấp độ 2), đó là Change Request, không phải thêm 1 dòng `Policy`.
- Cơ chế khóa account do quá hạn (`Account disabled`) **không thuộc Flow 4** — là hệ quả nghiệp vụ của Flow 6. Flow 4 chỉ cung cấp `overdue.lock_after_days`.
- Cơ chế Maker-Checker cho các key `ManualGuardrail` (approval queue, ai duyệt, thông báo) **không thuộc Flow 4** — thiết kế cụ thể thuộc Flow 6 (miễn/giảm phạt) hoặc Flow 2.5 (phí trả kho đặc biệt).
- Bỏ hẳn bảng `Payment` từng được phác thảo trong bản nháp cũ — đã được thay thế hoàn toàn bởi cặp `Invoice` + `PaymentTransaction` (Flow 1/2/3).
- Bỏ hẳn bảng `RentalPolicy` (bundle, versioned) từng được đề xuất — mô hình `Policy` key–value đã là hợp đồng ngầm giữa Flow 1/2/3/5, không đổi được mà không sửa lại các flow đó; nhu cầu "giữ nguyên chính sách cho hợp đồng cũ" đã được giải quyết bằng snapshot ở `Invoice`/`RentalContract`/`RentalOrder.expires_at`.

**Advanced Features chung của Flow 4 (not MVP):**
- Giao diện xây dựng "công thức" tính phí phức tạp hơn 4 loại `calculation_type` cố định.
- Workflow duyệt nội bộ trước khi 1 thay đổi `Policy`/giá có hiệu lực (hiện tại BOM sửa là áp dụng ngay, không qua duyệt).

#### 4.3 Quản lý các khoản phí (`ExtraFee`)

**Context:** Danh mục phí phát sinh thủ công ngoài tiền thuê định kỳ (làm lại chìa khóa, vệ sinh, hư hỏng...), dùng khi FS/FM lập biên bản có phát sinh phí (Flow 2.5/Flow 7) và tạo `Invoice(type=Penalty/Service)`.

**Flow tổng quát:** BOM thêm/sửa/vô hiệu hoá `ExtraFee` → FS/FM chọn đúng khoản phí từ danh mục khi lập biên bản, hệ thống tự tính `amount` theo `calculation_type` và tạo `Invoice`.

**Schema:**
```
# ExtraFee
- name
- category           -- mã ngắn, VD: LOST-KEY, CLN, DMG
- amount
- calculation_type (Fixed/Daily/Monthly/Percent)
- trigger_type (ManualIncident)     -- MVP chỉ có 1 giá trị, giữ chỗ mở rộng sau
- description
- status (Active/Inactive)
```

**Details:**
- Cách tính theo `calculation_type`:
  - `Fixed`: `amount × số lần phát sinh`.
  - `Daily`: `amount × số ngày phát sinh` (VD: phí lưu giữ tài sản theo ngày).
  - `Monthly`: `amount` áp dụng mỗi tháng.
  - `Percent`: `amount × giá thuê hiện hành của hợp đồng`.
- **`trigger_type` luôn là `ManualIncident` cho MVP** — Rental Fee (tiền thuê định kỳ) và Penalty do quá hạn **không phải bản ghi trong `ExtraFee`**, chúng dùng cơ chế snapshot riêng đã có (`RentalContract.monthly_price`, `Policy.overdue.fee_per_day`). Field `trigger_type` chỉ giữ chỗ cho trường hợp tương lai thật sự cần 1 loại phí tự động theo catalog (VD: phí wifi hàng tháng tách khỏi tiền thuê chính).
- Không cho xoá 1 `ExtraFee` đã từng được dùng để tạo `Invoice` — chỉ chuyển `Inactive`.
- Khi tạo hóa đơn phí, hệ thống tra `ExtraFee` theo `category` để lấy `amount`/`calculation_type`, ghi `category` vào `Invoice.desc` để đối soát.

**Advanced Features (not MVP):**
- Ước tính phí hư hỏng tự động theo danh mục mức độ thiệt hại có sẵn, thay vì FS chọn phí tương ứng thủ công.
- Phí khác nhau theo từng chi nhánh (`facility_id` riêng trên `ExtraFee`) — MVP dùng chung 1 mức phí toàn hệ thống, nhất quán với cách xử lý giá thuê ở 4.1.

#### 4.4 Quản lý khuyến mãi (`Discount`)

**Context:** Các chương trình giảm giá áp dụng cho hóa đơn đặt cọc/tiền thuê/gia hạn.

**Flow tổng quát:** BOM tạo `Discount` (mã, loại giảm, giá trị, phạm vi áp dụng, thời hạn) → hệ thống tự áp dụng khi tạo `Invoice` thuộc loại và trong khoảng thời gian được cấu hình.

**Schema:**
```
# Discount
- code (unique)
- name
- discount_type (Percent/Fixed)
- value
- apply_to (Deposit/Rental/Extension/All)
- min_months (nullable)
- start_at
- end_at
- is_active
```

**Details:**
- Tại thời điểm tạo `Invoice`, hệ thống tìm `Discount` đang `is_active` và còn hiệu lực (`start_at <= now <= end_at`) khớp `apply_to` và `min_months` (nếu có) → ghi `Invoice.discount_amount`, `Invoice.amount = giá gốc - discount_amount`.
- MVP: mỗi hóa đơn chỉ áp dụng tối đa 1 `Discount`, không cộng dồn.
- **Coi là MVP** (không phải Advanced Feature) — schema đã đầy đủ và đã được các flow khác tham chiếu, không có lý do kỹ thuật để lùi lại.

**Advanced Features (not MVP):**
- Cộng dồn nhiều discount theo thứ tự ưu tiên.
- Mã giảm giá khách tự nhập (thay vì hệ thống tự động áp).

#### 4.5 Quản lý điều khoản hợp đồng (`RentalTerm`)

**Context:** `RentalContract.terms_version` (Flow 2) tham chiếu tới văn bản điều khoản khách đã đồng ý — Flow 4 sở hữu bảng lưu nội dung/version của điều khoản này. Đây cũng là nơi chứa các quy định dạng văn bản cho khách đọc (chính sách hủy, trả kho, gia hạn, quá hạn...), thay vì tách thành các field text riêng lẻ.

**Flow tổng quát:** BOM tải lên 1 phiên bản điều khoản mới → hệ thống đặt phiên bản đó `Active`, phiên bản cũ tự động `Inactive` → Flow 2 dùng đúng version `Active` khi sinh `RentalContract` mới, snapshot `version` vào `RentalContract.terms_version`.

**Schema:**
```
# RentalTerm
- version (unique, VD: "v2.0")
- file_url          -- PDF điều khoản
- content           -- tuỳ chọn, text hiển thị inline (bao gồm chính sách hủy/trả/gia hạn/quá hạn mô tả cho khách)
- status (Active/Inactive)
- created_by
- created_at
```

**Details:**
- Ràng buộc: tại một thời điểm chỉ có đúng 1 `RentalTerm` ở trạng thái `Active`. Khi BOM kích hoạt version mới, version đang `Active` tự chuyển `Inactive` trong cùng transaction.
- Hợp đồng đã ký giữ nguyên `terms_version` đã snapshot — đổi điều khoản mới không ảnh hưởng ngược tới hợp đồng cũ.

#### 4.6 Dashboard theo dõi doanh thu chi nhánh

**Context:** BOM cần so sánh doanh thu/hiệu suất giữa các chi nhánh, loại kho, khoảng thời gian.

**Flow tổng quát:** BOM chọn bộ lọc (thời gian, chi nhánh, loại kho) → hệ thống tổng hợp từ `PaymentTransaction` → `Invoice` → `RentalContract` → `StorageUnit` → `Facility` → trả về số liệu dạng bảng/biểu đồ, xem song song 2 khoảng thời gian để so sánh.

**Chỉ số chính:**
- **Collected Revenue**: tổng tiền thanh toán thành công (`PaymentTransaction.status = Success`).
- **Outstanding**: tổng tiền hóa đơn còn `Unpaid`.
- **Refunded**: tổng tiền đã hoàn (khi Flow 2.5/6 có cơ chế hoàn tiền).
- **Gross Billed**: tổng giá trị đã lập hóa đơn, bất kể đã thu hay chưa.

**Details:**
- Nhóm theo `Invoice.type` (Deposit/Rental/Extension/Penalty/Service) để tách doanh thu theo nguồn.
- Bộ lọc: thời gian (mặc định 2 tháng gần nhất), chi nhánh, loại kho, khách hàng.
- Chế độ so sánh: chọn 1 biểu đồ làm gốc → hệ thống trả thêm dữ liệu kỳ trước liền kề (hoặc kỳ do BOM chọn) để hiển thị % tăng/giảm.
- Không lưu số liệu tổng hợp sẵn cho MVP — tính trực tiếp mỗi lần truy vấn; tối ưu (cache/index) là việc của giai đoạn sau, không phải vấn đề thiết kế MVP.

**Advanced Features (not MVP):**
- Cache/pre-aggregate số liệu doanh thu theo ngày.
- Dự báo xu hướng doanh thu, gợi ý chi nhánh tiềm năng để mở rộng.
- Custom dashboard widget theo nhu cầu từng BOM.

---

**Schema (do Flow 4 sở hữu):** `UnitType`, `Policy`, `ExtraFee`, `Discount`, `RentalTerm`.

**Cross-reference (ai đọc gì từ Flow 4):**

| Flow | Đọc từ Flow 4 |
|---|---|
| Flow 1 | `deposit.*`, `request.account_timeout_hours`, `fee.unit_change` |
| Flow 2 | `UnitType.monthly_price`, `RentalTerm` (Active), `contract.start_date_rule`, `handover.payment_grace_hours`, `order.auto_cancel_days`, `appointment.*` |
| Flow 2.5 | `ExtraFee` (DMG/CLN/LOST-KEY...), `unit.maintenance_days` |
| Flow 3 | `contract.expiring_soon_days`, `extension.invoice_due_days`, `Discount` (nếu áp dụng cho gia hạn) |
| Flow 5 | `UnitType` (chỉ đọc khi FM chọn `unit_type_id`, không đọc/ghi giá) |
| Flow 6 | `overdue.fee_per_day`, `overdue.lock_after_days`, `overdue.waive_max_percent`, `overdue.waive_max_amount` |

**NOTES:**

- Mục 4.1–4.5 chỉ là **nguồn dữ liệu**, không tự chứa logic áp dụng — logic luôn nằm ở flow tiêu thụ. Nếu 1 flow cần hành vi mới mà dữ liệu ở đây không đủ diễn tả (Cấp độ 2), đó là Change Request, không phải thêm 1 dòng `Policy`.
- Cơ chế khóa account do quá hạn (`Account disabled`) **không thuộc Flow 4** — là hệ quả nghiệp vụ của Flow 6. Flow 4 chỉ cung cấp `overdue.lock_after_days`.
- Cơ chế Maker-Checker cho các key `ManualGuardrail` (approval queue, ai duyệt, thông báo) **không thuộc Flow 4** — thiết kế cụ thể thuộc Flow 6 (miễn/giảm phạt) hoặc Flow 2.5 (phí trả kho đặc biệt).
- Bỏ hẳn bảng `Payment` từng được phác thảo trong bản nháp cũ — đã được thay thế hoàn toàn bởi cặp `Invoice` + `PaymentTransaction` (Flow 1/2/3).
- Bỏ hẳn bảng `RentalPolicy` (bundle, versioned) từng được đề xuất — mô hình `Policy` key–value đã là hợp đồng ngầm giữa Flow 2/3/5, không đổi được mà không sửa lại 3 flow đó; nhu cầu "giữ nguyên chính sách cho hợp đồng cũ" đã được giải quyết bằng snapshot ở `Invoice`/`RentalContract`.

**Advanced Features chung của Flow 4 (not MVP):**
- Giao diện xây dựng "công thức" tính phí phức tạp hơn 4 loại `calculation_type` cố định.
- Workflow duyệt nội bộ trước khi 1 thay đổi `Policy`/giá có hiệu lực (hiện tại BOM sửa là áp dụng ngay, không qua duyệt).

#### 5.0 Quản lý tài khoản & phân quyền (System Administrator)
### 6. Xử lý quá hạn/gia hạn (BOM & FM)

NOTE: sau khi trả hợp đồng, status của kho là MAINTANANCE trong vòng 1-3 ngày trước khi cho người khác thuê.

### 7. Yêu cầu hỗ trợ và xử lý sự cố
