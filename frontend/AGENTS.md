# AGENTS.md — frontend

React 19 · TypeScript 5.8 · Vite 6 · Prettier

## Commands

- Dev: `npm run dev`
- Build (includes typecheck): `npm run build`
- Format: `npm run format` · check: `npm run format:check`

There is no lint or test script yet. If you add one, wire it into CI in the same change.

## Layout

- `src/pages/**` — one page per spec screen (`fe-pages/`).
- `src/app/routes.ts` — route table; adding a screen means updating this file.
- `src/app/types.ts` — shared types. `src/app/layout.ts` — nav/layout config.
- `src/components/{ui,layout,domain}/**` — shared components.
- `src/mocks/**` — fake data, so the app runs without a backend.

## Rules

- **Never infer business rules.** Valid states and actions come from the API (e.g.
  `available_actions`); the FE only renders what it receives.
- Every screen needs three states: loading · empty · error. Missing one means it is not done.
- User-facing text is **Vietnamese**; money `1.500.000 đ`; dates `dd/MM/yyyy`.
- No hard-coded endpoints or permissions — share one API client.
- Types live in `src/app/types.ts`; do not add new `any`.
- Styling: check `package.json` and the file you are editing first. Tailwind branches carry
  `src/tailwind.css`, plain-CSS branches carry `src/styles.css` + `src/theme.css`, and some pages
  have their own `.css`. Never mix both systems in one file.
- FE validation is UX only; the real rules live in the backend — do not duplicate business rules.
- Do not call the real API at build time; never commit `.env`.
