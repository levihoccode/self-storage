<!--
Template tài liệu route. Cách dùng:
1. Copy file này thành routes/<nhóm>/<tên>.md — ví dụ routes/auth/login.md.
2. Điền các mục bên dưới, xoá các comment hướng dẫn.
3. Thêm một dòng vào routes.md: method, path, quyền, mô tả, link.
4. Ví dụ trong tài liệu phải là output THẬT từ app đang chạy — không chế.
-->

# <METHOD> /<path>

> Chạy backend, mở Swagger UI — `http://localhost:8080/swagger-ui/index.html` — để gọi thử trực
> tiếp (route cần token: bấm **Authorize** và dán JWT).

<Một câu mô tả route làm gì, dùng trong tình huống nào.>

## Request

| Header | Giá trị |
|---|---|
| `Authorization` | `Bearer <JWT>` — xoá dòng này nếu route public |

| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `<field>` | <kiểu> | ✔ / — | <ràng buộc validate, chuẩn hoá nếu có> |

```json
{ "<field>": "<giá trị ví dụ>" }
```

## Response 200

<Đổi 200 thành 201/204 nếu route tạo mới / xoá. Xoá mục này nếu không có body.>

| Field | Kiểu | Ghi chú |
|---|---|---|
| `<field>` | <kiểu> | <ý nghĩa, đơn vị nếu có> |

```json
{ "<field>": "<giá trị ví dụ>" }
```

## Lỗi

| Status | Điều kiện | Body |
|---|---|---|
| 400 | <validate fail> | Format lỗi mặc định của Spring Boot |
| 401 | <thiếu / sai / hết hạn token, account bị xoá> | `{"message":"...","timestamp":"..."}` |
| 403 | <sai role / status không ACTIVE> | <mô tả body> |
| 404 | <không tìm thấy tài nguyên> | <mô tả body> |
| 409 | <xung đột trạng thái> | <mô tả body> |

<Xoá các dòng không áp dụng.>

## Ví dụ

```bash
# 200
curl -s http://localhost:8080/<path>
# <output thật>
```

## Ghi chú

- <side effect, transaction, idempotent, phân quyền theo facility, giới hạn…>
- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
