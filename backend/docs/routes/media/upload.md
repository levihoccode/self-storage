---
purpose: Upload file (ảnh/PDF) dùng chung cho các flow — trả URL để submit vào flow sau.
---

# POST /api/media/uploads

FE upload ảnh hiện trạng / scan hợp đồng / ảnh giấy tờ trước, rồi dùng URL trả về khi submit flow.

- **Actor / quyền:** mọi tài khoản đã đăng nhập (`ACCOUNT_ACTIVE`) — không giới hạn role.
- **Contract chi tiết:** annotation tại `MediaController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Nhận `multipart/form-data` field `file`; validate loại (JPEG/PNG/WebP/PDF — header kèm tham số
  như `; charset=binary` vẫn nhận) và kích thước ≤ 10MB — sai trả 400 với message cụ thể.
- Lưu lên Cloudinary với `resource_type=image` → Cloudinary kiểm nội dung thật; file giả mạo
  content-type bị từ chối **400** ("File không đọc được hoặc không đúng định dạng ảnh/PDF"),
  không lọt thành asset `raw`.
- File nằm trong folder riêng của account đang đăng nhập: `uploads/acc-{accountId}` — upload mồ côi
  (flow không hoàn tất) vẫn truy vết được chủ sở hữu trong console storage.
- Servlet multipart nhận tới 12MB (`application.yml › spring.servlet.multipart`) để file 10–12MB
  rơi vào validator và có message 400 riêng.
- **Không ghi DB, không audit** — audit nằm ở flow gắn URL vào entity.
- Lỗi provider/network → 500 chung, chi tiết chỉ vào log.

## Ghi chú nghiệp vụ

- Giới hạn 10MB khớp cap upload của Cloudinary free plan.
- Ảnh giấy tờ sẽ cần chế độ private — chờ spec-gap #133 (chưa chốt cơ chế delivery).
- File mồ côi chưa có job dọn — xem [follow-up.md](../follow-up.md).

## Ví dụ

```bash
curl -s -X POST http://localhost:8080/api/media/uploads \
  -H "Authorization: Bearer <JWT>" -F "file=@anh.png"
{"message":"Tải file lên thành công","data":{"url":"https://res.cloudinary.com/dhbcghtis/image/upload/v1791894055/uploads/acc-6/xwxjx6efqwmmrlw11gy9.png","publicId":"uploads/acc-6/xwxjx6efqwmmrlw11gy9","bytes":70,"format":"png"}}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
