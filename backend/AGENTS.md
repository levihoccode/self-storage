Dockerfile:5

--------------------

   3 |     WORKDIR /app

   4 |     COPY pom.xml .

   5 | >>> RUN mvn -B -q dependency:go-offline

   6 |     COPY src ./src


--------------------

failed to solve: process "/bin/sh -c mvn -B -q dependency:go-offline" did not complete successfully: exit code: 1
# AGENTS.md — backend

Spring Boot 3.3.5 · Java 17 · Spring Modulith · Maven wrapper · Postgres · Redis

> Read the root [`AGENTS.md`](../AGENTS.md) first. The evidence rules, the spec-gap gate and the
> issue ↔ PR workflow apply here too.

## Spec is the contract — hard gate

Code is a 1:1 transfer of `specs/` into working software. Everything the spec defines — states,
transitions, fields, money rules, deadlines, permissions — must match it **exactly**. No
"improvements", no renamed states, no extra flags, no silent defaults.

This covers spec-visible behavior only. Package names, DTO field casing, log wording and other
code-internal choices are yours to make.

When you hit any of the following, **stop and open an issue**:

- an edge case or path the spec does not cover,
- a spec that contradicts itself, or contradicts `db-table-draft.md`,
- a change you want to make to spec-defined behavior,
- something you cannot implement as written (missing table/field, ambiguous rule).

**No issue → no implementation of that behavior.** Do not improvise a fix, do not leave a TODO and
keep going, do not silently pick one reading of the spec.

Issue requirements:

- Title `spec-gap: <module> — <one line>`, label `spec-gap`, assignee `@levihoccode`.
- Body: exact spec section (`specs/draft.md` / `specs/db-table-draft.md` + heading), a quote of
  what the spec says, what is undefined or contradictory, the options with trade-offs, and what
  stays blocked. The UI form is `.github/ISSUE_TEMPLATE/spec-gap.yml`; if you open the issue with
  `gh issue create` instead, fill those same fields.
- Approval = a comment from `@levihoccode` on that issue. Check it before continuing:
  `gh issue view <n> --repo levihoccode/self-storage --json state,comments`.
- Approved change → edit the spec and the code in the **same PR**, so the spec stays the source of
  truth.
- Report the issue number under "What was not verified".

Only the ambiguous behavior is frozen. The rest of the task continues.

## Commands

- Build + tests + module boundary check: `./mvnw -B verify` (gồm Checkstyle lint — rule tối thiểu, `backend/checkstyle.xml`)
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
