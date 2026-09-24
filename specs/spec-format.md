# Spec Format

Tài liệu này quy định cách viết business flow trong thư mục `specs/`.
Mục tiêu là để các flow có cùng cấu trúc, cùng mức độ chi tiết và dễ review.

## 1. Cấu trúc một flow

Mỗi flow lớn gồm nhiều sub-flow. Mỗi sub-flow là một mục nhỏ có số thứ tự và có đầy đủ `Context`, `Flow tổng quát`, `Details`, `Schema`, `NOTES` và `Advanced Features` khi cần.

Ví dụ:

```md
### 2. Check-in và bàn giao kho

#### 2.1 Tiếp nhận lịch hẹn
...

#### 2.2 Kiểm tra khoang
...

#### 2.3 Ký hợp đồng và thanh toán
...

#### 2.4 Bàn giao khoang
...
```

Không viết toàn bộ flow dài trong một mục duy nhất. Không gộp nhiều sub-flow có actor, điều kiện bắt đầu hoặc trạng thái kết thúc khác nhau vào cùng một mục.

Mỗi flow nên dùng thứ tự sau:

```md
#### 1.x Tên nghiệp vụ

**Context:** Bối cảnh và điều kiện bắt đầu.

**Flow tổng quát:** Một câu hoặc một dòng mô tả luồng chính từ đầu đến cuối.

**Details:**
- **Customer:**
- **FM:**
- **FS:**
- **Hệ thống:**

**Schema có trong phần này:**
- [**Entity**](./db-table-draft.md#entity)

**NOTES:**
- Các quy tắc, giả định hoặc quyết định ảnh hưởng tới cách đọc flow.

**Advanced Features (not MVP):**
- Các phần chưa thuộc MVP.
```

Không phải mục nào cũng cần đủ mọi actor. Chỉ giữ actor có hành động trong mục đó.

## 2. Context

`Context` trả lời ba câu hỏi:

- Ai đang ở trong quy trình?
- Quy trình bắt đầu từ trạng thái nào?
- Điều kiện nào phải đúng trước khi bắt đầu?

Viết ngắn và dùng tên trạng thái chính xác.

Ví dụ:

```md
**Context:** Khách đã đặt cọc thành công (`RentalOrder.status = Deposited`), khoang đang `Reserved` và chưa có lịch hẹn check-in.
```

Không đưa quyết định kỹ thuật dài hoặc danh sách API vào `Context`.

## 3. FLOW và Flow tổng quát

`FLOW` mô tả toàn bộ flow ở mức cao. Nó chỉ liệt kê thứ tự và tên các sub-flow.

`Flow tổng quát` nằm trong từng sub-flow. Nó mô tả happy path của riêng sub-flow đó bằng một dòng hoặc một đoạn ngắn.

Ví dụ `FLOW` ở cấp flow lớn:

```md
**FLOW:**
```

```text
[2.1 Tiếp nhận lịch hẹn]
          │
          ▼
[2.2 Kiểm tra khoang]
          │
          ▼
[2.3 Ký hợp đồng và thanh toán]
          │
          ▼
[2.4 Bàn giao khoang]
```

Các tên trong sơ đồ phải khớp với tiêu đề các sub-flow bên dưới.

Ví dụ `Flow tổng quát` trong một sub-flow:

```md
**Flow tổng quát:**
FS xác nhận khách đã đến -> hệ thống cập nhật `Appointment` -> Flow 2 mở phần kiểm tra `HandoverRecord`.
```

Chỉ đưa các bước chính của sub-flow. Chi tiết điều kiện và nhánh ngoại lệ nằm trong `Details` của chính sub-flow đó.

## 4. Details

Viết theo actor và thứ tự thời gian:

```md
**Details:**
- **Customer:**
  - Chọn ngày và khung giờ.
  - Xác nhận lịch hẹn.
- **FM:**
  - Xem các lịch chưa được phân công.
  - Chọn FS phụ trách.
- **FS:**
  - Xác nhận khách đã đến.
- **Hệ thống:**
  - Tạo hoặc cập nhật các bản ghi liên quan.
  - Ghi audit cho thay đổi trạng thái hoặc tiền.
```

Quy tắc viết:

- Dùng câu chủ động.
- Mỗi bullet là một hành động hoặc một điều kiện.
- Ghi rõ actor thực hiện thay đổi.
- Ghi trạng thái trước và sau khi có transition.
- Nhánh ngoại lệ đặt ngay sau happy path liên quan.
- Không lặp cùng một transition ở nhiều section.

### 4.1 Nhánh điều kiện

Dùng cấu trúc giống Flow 1:

```md
- Khách chọn một trong hai hướng:
  - **Đồng ý:** ...
  - **Từ chối:** ...
```

Với mỗi nhánh, phải ghi:

- Bản ghi nào được cập nhật.
- Trạng thái mới.
- Actor hoặc flow chịu trách nhiệm bước tiếp theo.
- Có tạo bản ghi mới hay không.

### 4.2 Transaction và side effect

Chỉ ghi transaction khi nó bảo vệ một invariant nghiệp vụ:

```md
- Trong cùng transaction, hệ thống cập nhật `RentalOrder`, `StorageUnit` và `Invoice`.
- Sau transaction, hệ thống gửi email và tạo thông báo trên website. Lỗi email không rollback thay đổi nghiệp vụ.
```

Không biến phần business flow thành tài liệu implementation. Chi tiết lock, query, endpoint và retry nên nằm trong tài liệu kỹ thuật riêng.

## 5. Schema có trong phần này

Chỉ liệt kê entity được tạo, đọc hoặc cập nhật trong mục hiện tại.

```md
**Schema có trong phần này:**
- [**RentalOrder**](./db-table-draft.md#rentalorder)
- [**Appointment**](./db-table-draft.md#appointment)
```

Schema chi tiết, field, enum, constraint và audit catalog chỉ viết ở `db-table-draft.md`. Không copy lại bảng schema vào flow.

## 6. Điều kiện và dữ liệu đầu vào

Mỗi flow lớn phải nêu rõ các điều kiện cần và đủ để bắt đầu. Nhóm các điều kiện theo flow hoặc hệ thống cung cấp dữ liệu đó:

```md
#### Điều kiện và dữ liệu đầu vào
- **Flow 1:**
  - `RentalOrder.status = InProgress`.
  - Có `Appointment` hợp lệ.
- **Flow 5:**
  - FS đã được phân công.
- **Chưa xác định nguồn gốc:**
  - Điều kiện chưa có owner rõ ràng.
```

Quy tắc:

- Chỉ bắt đầu flow khi tất cả điều kiện bắt buộc đã đầy đủ.
- Nếu thiếu một điều kiện, flow không được thực thi.
- Không tự tạo dữ liệu thay thế để vượt qua điều kiện đầu vào.
- Không đưa cách sửa lỗi, retry hoặc phân công xử lý lỗi vào business flow nếu chưa có contract liên flow.
- Nếu chưa xác định được flow hoặc hệ thống cung cấp điều kiện, ghi rõ `Chưa xác định nguồn gốc` thay vì tự suy đoán.
- Dữ liệu chỉ được dùng trong một sub-flow thì ghi tại sub-flow đó, không đưa vào điều kiện bắt đầu của flow lớn.

Phần này là contract đầu vào của flow. Không lặp lại nó trong `Context`, `Details` hoặc `NOTES` của các sub-flow.

## 7. NOTES

`NOTES` dùng cho:

- Quyết định đã thống nhất.
- Ranh giới ownership giữa các flow.
- Giả định cần nhớ khi đọc nghiệp vụ.
- Quy tắc không thể hiện tự nhiên trong happy path.

Ví dụ:

```md
**NOTES:**
- Flow 1 sở hữu vòng đời `Appointment`.
- Flow 2 chỉ ghi nhận khách đến và xử lý phần on-site.
- Appointment đã `Done` giữ nguyên để bảo toàn lịch sử.
```

Không dùng `NOTES` để giấu một bước nghiệp vụ bắt buộc. Bước đó phải nằm trong `Details`.

## 8. Advanced Features

Chỉ đưa các ý tưởng đã xác định là ngoài MVP.

```md
**Advanced Features (not MVP):**
- Reschedule lịch hẹn.
- Ký hợp đồng online và tự sinh PDF.
```

Không đưa việc chưa quyết định vào đây nếu nó ảnh hưởng tới MVP. Khi đó phải ghi rõ câu hỏi hoặc blocker trong `NOTES`.

## 9. API, event và audit

Business flow ưu tiên mô tả hành động, không ưu tiên tên endpoint.

- Endpoint chỉ ghi khi nó làm rõ actor hoặc quyền sở hữu.
- Event chỉ ghi khi flow khác cần biết để tiếp tục xử lý.
- Audit action không lập catalog trong flow; thêm action vào catalog `AuditLog` trong `db-table-draft.md`.
- Không lặp lại cùng một API, event hoặc audit action ở nhiều section.

## 10. Quy tắc đồng bộ giữa các flow

Trước khi sửa một flow:

1. Xác định flow sở hữu entity và transition.
2. Dùng đúng tên bảng, field và enum trong `db-table-draft.md`.
3. Không tạo bản sao contract đã có ở flow khác.
4. Nếu flow khác tạo bản ghi, flow hiện tại chỉ đọc hoặc cập nhật theo contract đã thống nhất.
5. Khi thay đổi ownership hoặc trạng thái, rà lại các flow liên quan.

## 11. Checklist review

- [ ] Có `Context` và điều kiện bắt đầu.
- [ ] Có `Flow tổng quát` ngắn.
- [ ] `Details` được viết theo actor và thứ tự thời gian.
- [ ] Happy path và nhánh ngoại lệ không bị trộn.
- [ ] Mỗi transition ghi rõ trạng thái trước và sau.
- [ ] Rõ flow nào sở hữu từng entity và bước tiếp theo.
- [ ] Schema chỉ được link từ `db-table-draft.md`.
- [ ] Audit action nằm trong catalog `AuditLog`.
- [ ] Không lặp nghiệp vụ ở workflow, backend và notes.
- [ ] Advanced Features không chứa quyết định MVP chưa chốt.
