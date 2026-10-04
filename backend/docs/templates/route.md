<!--
Template tài liệu route. Cách dùng:
1. Copy file này thành routes/<nhóm>/<tên>.md — ví dụ routes/auth/login.md.
2. Điền các mục bên dưới, xoá các comment hướng dẫn.
3. Thêm một dòng vào routes.md: method, path, quyền, mô tả, link.
4. Contract chi tiết (field, status code, message, ví dụ body) nằm ở annotation Swagger trong code —
   KHÔNG chép lại vào đây. Ví dụ curl trong tài liệu phải là output THẬT, không chế.
-->

# <METHOD> /<path>

<Một câu mô tả route làm gì, dùng trong tình huống nào.>

- **Actor / quyền:** <public / role nào / chủ tài nguyên / nội bộ>
- **Contract chi tiết:** annotation tại `<Controller>.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (route cần token: bấm **Authorize** và dán JWT).

## Luồng / hành vi

- <các bước chính, side effect, transaction, idempotent, giới hạn…>

## Ghi chú nghiệp vụ

- <ràng buộc từ spec, điều kiện đặc biệt…>

## Ví dụ

```bash
curl -s http://localhost:8080/<path>
# <output thật>
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
