# Backend — self-storage

Bộ khung modular monolith theo [issue #15](https://github.com/levihoccode/self-storage/issues/15). Chưa có nghiệp vụ, chỉ có cấu trúc để mỗi người vào code flow của mình.

## Stack

Spring Boot 3.3.5 · Java 17 · Maven · Spring Modulith 1.2.5 · PostgreSQL 16 · Redis 7

## Chạy

```bash
docker compose up --build          # từ thư mục gốc repo
curl http://localhost:8080/api/health
# {"status":"ok","service":"self-storage"}
```

Chỉ chạy test, không cần Docker:

```bash
cd backend && ./mvnw verify
```

## Cấu trúc

Mỗi flow là một package con của `vn.lemar.selfstorage`, Spring Modulith coi mỗi package đó là một module:

| Module | Flow | Module | Flow |
|---|---|---|---|
| `booking` | F1 Đặt kho | `facility` | F5 Chi nhánh & nhân sự |
| `handover` | F2 Check-in & bàn giao | `overdue` | F6 Quá hạn |
| `checkout` | F2.5 Trả kho & bảo trì | `support` | F7 Hỗ trợ & sự cố |
| `tenancy` | F3 Kho đã thuê | `identity` | Tài khoản & RBAC |
| `pricing` | F4 Business rules & phí | `payment` `notification` `scheduler` | Hạ tầng dùng chung |

Trong mỗi module có sẵn bốn tầng theo issue #15 mục 2:

```
controller → application → domain → repository
```

## Ranh giới giữa các module

Chiều phụ thuộc khai báo ở `package-info.java` của từng module:

- `tenancy → handover → booking`
- `checkout → handover, tenancy`
- Mọi module đọc được từ `facility`, `pricing`, `identity`

`ModuleStructureTest` chạy `ApplicationModules.verify()` để chặn gọi sai chiều **ngay lúc build**. Viết code trong `booking` mà gọi thẳng sang `handover` là build đỏ.

Cần đọc dữ liệu của module khác thì thêm interface `XxxQueryService` trả DTO ở module sở hữu, **không** expose Entity hay Repository. Cần báo cho module khác biết một thay đổi trạng thái thì bắn domain event.

## Chưa làm

- Authentication: `SecurityConfig` mới khai báo năm nhóm route theo role, tất cả đang `permitAll`. Làm xong `identity` thì đổi sang `hasRole` và bật facility-scope ở tầng method.
- Chưa có entity nào, nên `ddl-auto` để `validate` và chưa có migration. Khi bắt đầu viết entity thì thêm Flyway trước.
- ShedLock (issue #15 mục 5) chưa thêm vì chưa có cron nào.
- `overdue`, `support`, `scheduler` chưa khai báo `allowedDependencies` vì issue #15 chưa chốt chiều phụ thuộc của ba module này.
