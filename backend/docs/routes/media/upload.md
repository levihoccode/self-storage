---
purpose: Upload file (ảnh/PDF) dùng chung cho các flow — trả URL để submit vào flow sau.
---

# POST /api/media/uploads

FE upload ảnh hiện trạng / scan hợp đồng / ảnh giấy tờ trước, rồi dùng URL trả về khi submit flow.

- **Actor / quyền:** mọi tài khoản đã đăng nhập (`ACCOUNT_ACTIVE`) — không giới hạn role.
- **Contract chi tiết:** annotation tại `MediaController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html` (bấm **Authorize** và dán JWT).

## Luồng / hành vi

- Nhận `multipart/form-data` field `file`; validate loại (JPEG/PNG/WebP/PDF) và kích thước
  ≤ 10MB — sai trả 400 với message cụ thể.
- Servlet multipart nhận tới 12MB: file 10–12MB rơi vào validator để có message 400 riêng,
  > 12MB bị chặn ở tầng multipart với cùng message.
- Lưu qua `FileStorage` (impl Cloudinary, `resource_type=auto`); **không ghi DB, không audit** —
  audit nằm ở flow gắn URL vào entity.
- Lỗi provider → 500 chung, chi tiết chỉ vào log.

## Ghi chú nghiệp vụ

- Giới hạn 10MB khớp cap upload của Cloudinary free plan.
- Ảnh giấy tờ sẽ cần chế độ private — chờ spec-gap #133 (chưa chốt cơ chế delivery).

## Ví dụ

```bash
curl -s -X POST http://localhost:8080/api/media/uploads \
  -H "Authorization: Bearer <JWT>" -F "file=@anh.png"
{"message":"Tải file lên thành công","data":{"url":"https://res.cloudinary.com/dhbcghtis/image/upload/v1791881725/xxodnf2c998bbcfgwdpv.png","publicId":"xxodnf2c998bbcfgwdpv","bytes":70,"format":"png"}}
```

## Liên quan

- Xem thêm: [routes.md](../routes.md) · [index.md](../index.md)
