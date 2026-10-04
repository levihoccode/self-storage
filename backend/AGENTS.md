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

## Docs must follow every feat/API change — hard gate

`backend/docs/` is the living documentation of the backend. **Every feat or API that is created or
changed must be cross-checked against `backend/docs/`; if docs need an update or a new file, that
change is mandatory and goes into the same PR.** A PR with a behavior change and stale docs is not
done.

**Contract chi tiết sống ở annotation Swagger trong code — `docs/routes/*.md` KHÔNG chép lại
contract.** `docs/routes/` ghi usage/actor/luồng/ghi chú; field, status code, message, ví dụ body
nằm ở `@Operation` / `@ApiResponses` / `@Schema` / `@ExampleObject` trên controller.

- New route → add a row to [`docs/routes.md`](docs/routes.md) and a detail file under
  `docs/routes/`, starting from [`docs/templates/route.md`](docs/templates/route.md); annotate the
  controller for Swagger.
- Changed contract — request, response, status codes, error body → update the Swagger annotations
  on the controller (and `docs/index.md › Quy ước data contract` if the shape is cross-route).
- Changed usage / actor / flow — update the route's detail file.
- Changed authorization — route policy, roles, `ACCOUNT_ACTIVE` → update `docs/routes.md` and the
  policy table in `docs/index.md`.
- Changed config/env, seed data, migrations, run/test commands → update the matching section in
  `docs/index.md`.
- Removed route → remove it from `docs/routes.md` and delete its detail file.

Examples inside docs must be **real output from a running app**, never invented. If a change
genuinely needs no docs update, state that explicitly in the PR body with the reason.

`backend/docs/` is **as-built** documentation: behavior authority stays with `specs/` and the
spec-gap gate above — docs never justify behavior the spec does not define.

## Commands

- Build + tests + module boundary check: `./mvnw -B verify` (gồm Checkstyle lint — rule tối thiểu, `backend/checkstyle.xml` — và cổng coverage LINE/BRANCH ≥ 80%)
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
- Schema changes only via migrations; no `ddl-auto`. Seed data lives in `V2__baseline_seed.sql`
  (mirror in `backend/sql/seed.sql`).
- JPA entities use Lombok sparingly: `@Getter` (+ `@Setter` only where needed / via domain methods)
  and `@NoArgsConstructor(access = PROTECTED)`. Do **not** use `@Data`, or default `@ToString` /
  `@EqualsAndHashCode` on entities — lazy associations get loaded by surprise (or recurse infinitely),
  and `hashCode` changes after persist because the generated id is assigned late. `@Builder` /
  `@AllArgsConstructor` must be paired with `@NoArgsConstructor` to keep the constructor JPA requires.
