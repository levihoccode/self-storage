---
purpose: Trang ZaloPay chuyển trình duyệt khách về sau thanh toán — chỉ hiển thị, không ghi dữ liệu.
---

# GET /api/payments/zalopay/return

ZaloPay chuyển trình duyệt khách về URL này (`ZALOPAY_REDIRECT_URL`, gửi trong `embed_data.redirecturl`)
sau khi thanh toán. Route chỉ trả lại trạng thái để hiển thị.

- **Actor / quyền:** public — redirect trình duyệt, không mang `Authorization`. Route chỉ tồn tại khi
  `PAYMENT_PROVIDER=zalopay`.
- **Contract chi tiết:** annotation tại `ZaloPayCallbackController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Luồng / hành vi

- Đọc `status` và `apptransid` từ query string rồi trả lại nguyên văn; không kiểm chữ ký, không đọc
  hay ghi DB, không bắn event.

## Ghi chú nghiệp vụ

- **Không dùng để ghi nhận đã trả tiền.** Query string nằm trên thanh địa chỉ, khách sửa được; trạng
  thái thật chỉ đến từ callback hoặc job đối soát (`/v2/query`).

## Ví dụ

```bash
curl -s "http://localhost:8082/api/payments/zalopay/return?appid=553&apptransid=261030_INV999T20261030132000ABCDE&status=1"
# {"status":"1","provider":"zalopay","appTransId":"261030_INV999T20261030132000ABCDE"}
```

## Liên quan

- [zalopay-callback.md](zalopay-callback.md) · [payment.md](../../payment.md) · [routes.md](../../routes.md)
