# AGENTS.md — self-storage

## Source of truth

- Business rules: `specs/draft.md`, `specs/db-table-draft.md`. Doc format: `specs/spec-format.md`.
- Code must match the spec. If spec and code disagree: **report it, do not pick a side**.
- `specs/*.md` is shared across flows: edit only your flow's section, leave the rest alone.

## Evidence is required

"Probably works" is not acceptable. Every claim needs **one of**:

- real command output (build / test / lint / curl), or
- a specific `path/to/file:line`.

Banned: "should work", "probably", "likely", "I think". If you cannot verify something, say so
plainly: **"not verified, because &lt;reason&gt;"**.

## Before writing code

1. **Read before you write.** Open the actual file/class/component to read its signature — never
   guess an API.
2. **Plan before you edit.** More than 2 files, or touching schema / migration / API contract /
   payment → present the plan and the risks, then wait for approval.
3. **Do not expand scope.** No refactoring beyond the request. Ask before adding a dependency.
4. **Never edit a test to make it pass.** Fix the cause; changing a test needs a stated reason and
   approval.

## Definition of done

- Backend: `cd backend && ./mvnw -B verify` green.
- Frontend: `cd frontend && npm run build && npm run format:check` green.
- Runtime changes (DB, API, payment, cron) must be actually run, not just compiled.

## Required end-of-task report — all four sections

1. Files changed.
2. Commands run — with results.
3. **What was not verified.**
4. Remaining risks + what the reviewer should look at.

## Self-challenge

Before saying "done", name **one weakness** in your own solution: an untested path, an unchecked
assumption, a fragile spot. No counter-argument means it is not a conclusion yet.

## Git

- Conventional commits with a scope: `feat(booking): …`, `fix(payment): …`, `docs(flow-2): …`.
- One commit = one logical change. Do not commit unless asked. Never force-push.
- Never commit `.env`, credentials, or tokens — sandbox keys only.

## Per layer

- Backend: `backend/AGENTS.md`
- Frontend: `frontend/AGENTS.md`
