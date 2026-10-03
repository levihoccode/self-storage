# GET /api/auth/me

Xác minh session hiện tại là ai — FE dùng sau khi login để biết email + role.

> Chạy backend, mở Swagger UI — `http://localhost:8080/swagger-ui/index.html` — bấm **Authorize**
> và dán JWT từ `/api/auth/login` để gọi thử trực tiếp.

## Request

| Header | Giá trị |
|---|---|
| `Authorization` | `Bearer <JWT>` (bắt buộc) |

Không có body/query. Route nằm dưới `/api/auth/**` nhưng được match **trước** rule
`permitAll` trong `SecurityConfig`, nên vẫn yêu cầu token và status ACTIVE.

## Response 200

| Field | Kiểu | Ghi chú |
|---|---|---|
| `email` | string | email của account |
| `role` | string | `ADMIN` · `BOM` · `FM` · `FS` · `CUSTOMER` |

```json
{ "email": "customer1@lemar.vn", "role": "CUSTOMER" }
```

## Lỗi

| Status | Điều kiện | Body |
|---|---|---|
| 401 | Thiếu token / sai chữ ký / hết hạn / account đã bị xóa | Thường không có body (resource server trả trước controller) |
| 403 | Token hợp lệ nhưng account không ACTIVE (`LOCKED` / `BANNED` / `UNVERIFIED`) | Không kèm body lỗi nghiệp vụ (Spring Security mặc định) |

## Ví dụ

```bash
TOKEN='<token từ POST /api/auth/login>'

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/me
# {"email":"customer1@lemar.vn","role":"CUSTOMER"}

curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/auth/me
# 401
```

## Ghi chú

- Role đọc từ DB **mỗi request**: admin đổi role hoặc khóa account thì request kế tiếp
  đã thấy hiệu lực ngay.
- Đây là endpoint chính thức để FE lấy role, thay cho field `role` đã bỏ khỏi response login.
