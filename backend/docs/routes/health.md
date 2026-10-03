# GET /api/health

Endpoint hạ tầng (không thuộc module nghiệp vụ nào) để xác nhận ứng dụng chạy được.

> Chạy backend rồi mở Swagger UI — `http://localhost:8080/swagger-ui/index.html` — để gọi thử
> trực tiếp.

## Request

Không tham số, không cần xác thực.

## Response 200

| Field | Kiểu | Giá trị |
|---|---|---|
| `status` | string | `ok` |
| `service` | string | `self-storage` |

```json
{"status":"ok","service":"self-storage"}
```

## Ví dụ

```bash
curl http://localhost:8080/api/health
# {"status":"ok","service":"self-storage"}
```

## Ghi chú

- Không chạm database/Redis — chỉ xác nhận HTTP layer sống.
