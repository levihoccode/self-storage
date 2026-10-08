---
purpose: Mô hình phân quyền — authorities, ba kiểu ràng buộc quyền, 401 vs 403; chi tiết từng route xem routes/.
---
# Authorization Model

Phân quyền backend: **ai được làm gì** sau khi đã xác thực. Phần danh tính/token nằm ở
[authentication.md](authentication.md).

## Authorities

`AccountJwtAuthenticationConverter` cấp authorities từ DB cho mỗi request:

| Authority | Nguồn | Ý nghĩa |
|---|---|---|
| `ROLE_<role>` | `Account.role.name` | `ADMIN` · `BOM` · `FM` · `FS` · `CUSTOMER` |
| `ACCOUNT_ACTIVE` | `status = ACTIVE` | account đang hoạt động |

Account `BANNED` không được cấp authority nào → mọi route yêu cầu quyền đều 403.

## Models — ba kiểu ràng buộc quyền

Mỗi chức năng chọn đúng **một** trong ba model; mỗi model một công cụ:

| Model | Câu hỏi kiểm | Công cụ | Ví dụ |
|---|---|---|---|
| **Role-bound** | Chức năng thuộc về role nào? | Route namespace trong `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`) | Quản trị tài khoản / role / permission |
| **Permission-bound** | Role này có capability X? | `Access.can(permission, facilityId)` + dữ liệu `role_permissions` | Duyệt yêu cầu; tạo notification `OTHER` |
| **Ownership** | Actor có phải chủ tài nguyên? | Check ở service, không thuộc RBAC | Khách xem/hủy đơn của mình |

Quy tắc chọn:

- Gán cho role khác là **vô nghĩa hoặc nguy hiểm** (quản trị hệ thống, tự nâng quyền) → role-bound.
  Không tạo permission; guard nằm một chỗ duy nhất ở `SecurityConfig`.
- Gán cho role khác **có thể có lý do nghiệp vụ** (FM/BOM cùng đọc, BOM tạo notification…) → permission.
- Phụ thuộc vào **instance dữ liệu** → ownership.

`can()` có hai dạng gọi — không có ngữ nghĩa “null = bỏ scope”:

- `can(permission)` — hành động global.
- `can(permission, facilityId)` — hành động theo cơ sở; `facilityId` bắt buộc (từ chối null).
  ADMIN/BOM bỏ qua scope; FM/FS kiểm `fm_account_id` / `account_facility_assignments`.

Catalog và quy ước đặt code: [permission-catalog.md](permission-catalog.md).

## 401 vs 403

Mọi 401/403 trả body `{message, timestamp}` (401 kèm header `WWW-Authenticate`): 401 message là mô
tả lỗi token từ Spring. 403 có các message sau:

| Nguồn | Message |
|---|---|
| Sai role cho route (SecurityConfig) | `"Bạn không có quyền truy cập"` |
| Account `BANNED` | `"Tài khoản đã bị chặn"` |
| `Access.can()` — role thiếu permission | `"Bạn không có quyền thực hiện thao tác này"` |
| `Access.can(permission, facilityId)` — ngoài phạm vi cơ sở | `"Bạn không được gán vào cơ sở này"` |

Chi tiết: [error-handling.md](error-handling.md).

## Điều kiện nghiệp vụ: xác minh email

- Khách chưa xác minh (`email_verified_at = null`) **vẫn đăng nhập được**, nhưng không được thực hiện
  thao tác nghiệp vụ thuộc luồng thuê kho (claim/liên kết đơn, đặt cọc, ký hợp đồng, nhận/trả kho…).
- Đây là điều kiện authorization **theo hành động**, không theo route — sẽ enforce trong service khi
  flow tương ứng được làm (Flow 1 §1.2 là case đầu tiên).
- Spec: [db-table-draft.md › Account](../../specs/db-table-draft.md#account);
  [draft.md › Business workflow](../../specs/draft.md#business-workflow).

## Chưa có

- UI quản trị gán permission cho role (Flow 5): mapping hiện sửa qua seed/SQL.
- Phần lớn namespace role-bound (`/api/fm/**`, `/api/bom/**`…) chưa có endpoint nghiệp vụ nào.
