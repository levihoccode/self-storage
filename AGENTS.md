# AGENTS.md — self-storage

## Source of truth

- Business rules: `specs/draft.md`, `specs/db-table-draft.md`. Doc format: `specs/spec-format.md`.
- Code must match the spec. If spec and code disagree: **report it, do not pick a side**.
- `specs/*.md` is shared across flows: edit only your flow's section, leave the rest alone.

## Workflow — one issue behind every PR

Work starts from an issue, and the PR links back to it. There is no PR without an issue.

| Issue form | What it is | Relation to a PR |
|---|---|---|
| `be-api`, `be-server`, `fe-page` | a unit of work | the PR ships it; `Closes #N` closes it on merge |
| `bug` | behavior that runs against the spec | the PR fixes it; `Closes #N` |
| `spec-gap` | the spec is silent, contradicts itself, or you want to deviate | no PR of its own — it is the permission for the current PR |
| `proposal` | you want to change something already defined | no PR of its own — approve first, then it becomes work |

```text
issue (task) → branch → PR "Closes #N" → CI green + 1 approval → merge → issue closes
                              │
                              └─ spec unclear → open spec-gap #M → STOP
                                 → approved → spec + code in the same PR
```

- One issue may produce several PRs. One PR closes **one** issue; link any others with `#N`.
- Never `Closes` an epic. Roadmap `#30` is an index of three packages (`#33` A · `#34` B · `#35` C) —
  `Closes #33` is correct, `Closes #30` is not. Tick individual items inside the package issue.
- Every PR needs one approval from the code owner. All CI gates must be green: **PR gate**,
  **Guardrails**, **Backend CI**, **Frontend CI**. Never disable or skip tests to make a check
  pass — ask `@levihoccode` for the `waiver` label, with a stated reason, if a gate really must be
  bypassed.
- Issues created with `gh issue create` skip the form — fill the form's fields by hand.
- The PR body is the end-of-task report below. Its sections are not optional.

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
- Commit messages are in **English** — subject and body. PRs are squash-merged, so the PR title
  becomes the commit subject: keep PR titles English too; the PR body stays Vietnamese.
- Branch names follow the pattern already in use: `specs/<topic>`, `be/feat/<topic>`, `fe/feat/<topic>`, `docs/<topic>`, `chore/<topic>`.
- One commit = one logical change. Do not commit unless asked. Never force-push.
- Never commit `.env`, credentials, or tokens — sandbox keys only.

## Per layer

- Backend: `backend/AGENTS.md`
- Frontend: `frontend/AGENTS.md`
