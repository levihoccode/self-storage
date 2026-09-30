# FE còn lại — trạng thái 4 nhánh role & checklist xử lý

> Kiểm tra ngày 2026-09-30. Refs đã kiểm tra:
> `fe/feat/admin` @ `166b5da`, `fe/feat/auth` @ `ae44ac8`, `fe/feat/bom` @ `8b0f995`, `fe/feat/fs` @ `ad0d7f4`.
> Bối cảnh: `fe/feat/cus` (#23) và `fe/feat/fm` (#22) đã được approve, đang chờ resolve conflict với `main`.

## Tóm tắt

| Nhánh | Pages của role | Wire vào App.tsx | CI | PR |
|---|---|---|---|---|
| `fe/feat/bom` | 8 BOM pages (mỗi page + css) + `BomShell` | ❌ không render | ✅ pass | #28 |
| `fe/feat/admin` | 5 admin pages (+2 css) | ⚠️ có, nhưng thiếu module | ❌ fail (Prettier 13 files) | chưa có |
| `fe/feat/auth` | auth pages + `NotificationsCenter` (+css) | ⚠️ có, nhưng thiếu module | ❌ fail (Prettier 11 files) | chưa có |
| `fe/feat/fs` | 5 FS pages | ⚠️ có, nhưng thiếu module | ❌ fail (Prettier 13 files) | chưa có |

**Điểm chung cần xử lý trước khi merge:**

- **Era CSS cũ (pre-Tailwind)**: `frontend/src/styles.css` 45–56KB (main: 4.6KB); shell dùng class CSS riêng
  (vd `frontend/src/components/layout/FsShell.tsx` dùng `fs-app`, `fs-sidebar`) thay vì wrap `WorkspaceShell`
  như `FmShell`/`CustomerShell` hiện tại.
- **Lệch main rất lớn**: merge-base với main là `ba74f39` (admin) / `330534d` (auth, bom, fs).
  Diff so với main: admin ~219 files, auth ~224 files, fs ~227 files, bom ~235 files
  (`git diff --stat origin/main <branch>`).
- **Trùng lặp chéo**: `NotificationsCenter` (auth/bom) vs `NotificationsPage` (#23); 3 shell riêng
  (AdminShell/BomShell/FsShell); 2 cơ chế demo login khác nhau (email-keyword ở auth vs demo account
  cố định ở #22/#23).

## fe/feat/bom — PR #28

Vấn đề:

1. **PR #28 target sai base**: đang merge vào `specs/fe-pages` (docs branch) thay vì `main`
   (`gh pr view 28 --json baseRefName` → `specs/fe-pages`) → hiển thị CLEAN/20 files; khi retarget về
   main sẽ thành conflict lớn.
2. **Pages không được render**: `frontend/src/App.tsx` (91 dòng) không import `BomShell` hay bất kỳ
   `pages/bom/*` nào; `frontend/src/app/routes.ts` đã map `/bom/*` → view `bom-*` nhưng App không có
   nhánh render → UI không truy cập được pages.

Checklist:

- [ ] Retarget PR #28 về `main`
- [ ] Wire `BomShell` + 8 pages vào `App.tsx`/routes
- [ ] Bổ sung demo login cho role BOM (hiện chưa có cơ chế nào)
- [ ] Đưa vào kế hoạch migration Tailwind + rebase

## fe/feat/admin

Vấn đề:

1. CI fail ở bước Prettier: `Code style issues found in 13 files` (run 2026-09-27).
2. `App.tsx` import **15 module không tồn tại**: `pages/auth/AuthPage`, `pages/auth/NotificationsCenter`,
   8× `pages/bom/*`, 5× `pages/fs/*` → `tsc -b` sẽ fail kể cả sau khi fix format.
3. Lịch sử 1 commit gộp 219 files trên nền `init` → cần rebase/cherry-pick thay vì merge trực tiếp.

Checklist:

- [ ] Rebase nhánh lên `main` (hoặc tách commit)
- [ ] Fix Prettier 13 files; chạy `npm run format:check` + `npm run build`
- [ ] Quyết định phạm vi App.tsx: chỉ giữ admin views hay là nhánh integration (hiện import cả bom/fs/auth)
- [ ] Migration Tailwind cho 5 admin pages + AdminShell
- [ ] Mở draft PR để CI chạy liên tục

## fe/feat/auth

Vấn đề:

1. CI fail Prettier 11 files.
2. `App.tsx` import **18 module không tồn tại** (5× admin, 8× bom, 5× fs).
3. **Demo login khác cơ chế**: `resolveRoleFromEmail()` suy role từ keyword trong email + password ≥6 ký tự
   (`frontend/src/app/auth.ts`, `frontend/src/pages/auth/LoginForm.tsx`) — trong khi #22/#23 dùng demo
   account cố định. Cần thống nhất một cơ chế.
4. `NotificationsCenter` trùng chức năng với `NotificationsPage` của #23 — chỉ giữ một bản.
5. `getRoleRedirectPath()` có nhánh tạm đáng ngờ: `case "fm" → /bom/facilities`.

Checklist:

- [ ] Fix Prettier 11 files
- [ ] Chốt lại App.tsx chỉ giữ auth views, bỏ import các role khác
- [ ] Thống nhất demo auth + NotificationsCenter với #22/#23
- [ ] Sửa redirect `fm`
- [ ] Migration Tailwind + rebase `main`

## fe/feat/fs

Vấn đề:

1. CI fail Prettier 13 files.
2. `App.tsx` import **14 module không tồn tại** (`NotificationsCenter` + 5× admin + 8× bom).
3. CSS thủ công: commit FS thêm +2.407 dòng vào `styles.css`; shell dùng class `fs-*`.

Checklist:

- [ ] Fix Prettier 13 files
- [ ] Chốt App.tsx chỉ giữ FS views
- [ ] Migration Tailwind cho 5 FS pages + FsShell (wrap `WorkspaceShell`)
- [ ] Rebase `main`

## Việc chung (làm sau khi #22/#23 merge)

- [ ] Rebase 4 nhánh lên `main` mới; migrate Tailwind theo pattern đã áp dụng ở #22/#23
- [ ] Dedupe shell: AdminShell/BomShell/FsShell → wrap `WorkspaceShell`
- [ ] Thống nhất NotificationsCenter/NotificationsPage
- [ ] Thống nhất cơ chế demo auth (email-keyword vs demo account)
- [ ] Mở PR cho admin/auth/fs; retarget #28

## Cách xác minh nhanh (lệnh đã dùng)

- CI: `gh run list --repo levihoccode/self-storage --branch fe/feat/admin` → `failure`;
  `gh run view <run-id> --log-failed` → `Code style issues found in 13 files`.
- Import thiếu: so `git show <branch>:frontend/src/App.tsx` (các `from "./pages/*"`) với
  `git ls-tree -r <branch> frontend/src/pages`.
- Wiring bom: `git show <branch>:frontend/src/App.tsx` tìm `bom` (case-insensitive) → không có kết quả.
- PR #28 base: `gh pr view 28 --repo levihoccode/self-storage --json baseRefName` → `specs/fe-pages`.
