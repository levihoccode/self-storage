---
purpose: Kiểm tra ứng dụng còn sống (endpoint hạ tầng).
---
# GET /api/health

Endpoint hạ tầng (không thuộc module nghiệp vụ nào) để xác nhận ứng dụng chạy được.

- **Actor / quyền:** public.
- **Contract chi tiết:** annotation tại `HealthController.java`; Swagger UI —
  `http://localhost:8080/swagger-ui/index.html`.

## Hành vi

- Không chạm database/Redis — chỉ xác nhận HTTP layer sống.

## Ví dụ

```bash
curl -s http://localhost:8080/api/health
{"message":"Server còn sống tốt!","data":{"service":"self-storage","status":"ok"}}
```
