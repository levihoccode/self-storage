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

## Policy route (SecurityConfig)

| Route | Yêu cầu |
|---|---|
| `/api/health`, `/api/auth/**` (trừ `/me`) | public |
| `/api/auth/me` | `ACCOUNT_ACTIVE` |
| `/api/customer/**` | `ROLE_CUSTOMER` |
| `/api/staff/**` | `ROLE_FS` |
| `/api/fm/**` | `ROLE_FM` |
| `/api/bom/**` | `ROLE_BOM` |
| `/api/admin/**` | `ROLE_ADMIN` |
| `/api/**` (còn lại) | `ACCOUNT_ACTIVE` |

`POST /api/notifications` đi qua policy `/api/**`; quyền tạo `OTHER` được kiểm tra tại method
bằng `Access.can(RoleName.ADMIN, RoleName.BOM)`.

## Guard tại method (A3b)

Endpoint demo `/api/facility-access/ping/{facilityId}` đi qua policy `/api/**` ở trên,
sau đó `Access` kiểm tra role và facility scope:

| Role | Phạm vi được phép |
|---|---|
| `ADMIN`, `BOM` | Mọi facility |
| `FM` | Facility có `fm_account_id` là account hiện tại |
| `FS` | Facility có assignment của account hiện tại trong `account_facility_assignments` |
| Role khác | Bị từ chối (`403`) |

## 401 vs 403

Mọi 401/403 trả body `{message, timestamp}` (401 kèm header `WWW-Authenticate`): 401 message là mô
tả lỗi token từ Spring. 403 có các message sau:

| Nguồn | Message |
|---|---|
| Sai role cho route (SecurityConfig) | `"Bạn không có quyền truy cập"` |
| Account `BANNED` | `"Tài khoản đã bị chặn"` |
| Guard `Access.can()` sai role | `"Bạn không có quyền thực hiện thao tác này"` |
| Guard `Access.canAccessFacility()` ngoài phạm vi cơ sở | `"Bạn không được gán vào cơ sở này"` |

Chi tiết: [error-handling.md](error-handling.md).

## Điều kiện nghiệp vụ: xác minh email

- Khách chưa xác minh (`email_verified_at = null`) **vẫn đăng nhập được**, nhưng không được thực hiện
  thao tác nghiệp vụ thuộc luồng thuê kho (claim/liên kết đơn, đặt cọc, ký hợp đồng, nhận/trả kho…).
- Đây là điều kiện authorization **theo hành động**, không theo route — sẽ enforce trong service khi
  flow tương ứng được làm (Flow 1 §1.2 là case đầu tiên).
- Spec: [db-table-draft.md › Account](../../specs/db-table-draft.md#account);
  [draft.md › Business workflow](../../specs/draft.md#business-workflow).

## Chưa có

- Permission-based (role → permissions): mới có schema, chưa code nào đọc.
