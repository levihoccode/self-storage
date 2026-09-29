# AGENTS.md — backend

Spring Boot 3.3.5 · Java 17 · Spring Modulith · Maven wrapper · Postgres · Redis

## Commands

- Build + tests + module boundary check: `./mvnw -B verify`
- Single test class: `./mvnw -B test -Dtest=<ClassName>`
- Run the app: `./mvnw spring-boot:run` (needs `docker compose up -d` for Postgres + Redis)

## Module boundaries — CI fails on violations

- Modules: `booking`, `payment`, `facility`, `handover`, `checkout`, `identity`,
  `notification`, `overdue`, `pricing`, `scheduler`, `support`, `tenancy`.
- Call another module **only through its public API** (`application/`). Never import another
  module's `domain/` or `repository/`. No dependency cycles.
- `ModuleStructureTest` is the gate → run `./mvnw -B verify` after any structural change.

## Layout inside a module

```text
<module>/
  domain/       entities, enums, invariants
  repository/   data access
  application/  services + transaction boundaries
  controller/   HTTP API
```

## Code rules

- Transactions belong in `application`, not in controllers.
- Every state transition goes through a service; never set status fields ad hoc.
- Money: `BigDecimal` (never `double`). Time: `Instant`/`OffsetDateTime` in UTC, formatted at the
  API layer.
- Every change to state / ownership / money writes an `AuditLog` — **one record per entity**.
- Notifications: send email **and** create an in-app notification; a failed email must not roll
  back the business transaction.
- Payment/IPN: idempotent on `vnp_txn_ref`. Any change to `StorageUnit.status` must lock by
  `unit_id`.
- Authorization goes through `can(actor, action, resource)` and
  `canAccessFacility(actor, facilityId)` — no scattered `if (role == …)`.
- Errors: dedicated exceptions + one handler returning a consistent JSON error body; never return
  200 with an error payload.
- Schema changes only via migrations; no `ddl-auto`. Sample data lives in `mock-data.sql`.
