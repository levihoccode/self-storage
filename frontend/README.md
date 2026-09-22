# Frontend prototype

Presentational React prototype for the self-storage flows. The first slice covers:

- `/` and `/units`: public landing and unit browsing.
- `/rental-requests/new`: mock rental request form.
- `/login`, `/register`, `/verify-email`: auth screens with demo-only states.

## Run

```bash
npm install
npm run dev
```

The TypeScript, HTML, CSS, JSON, Tailwind, and Emmet language servers are project dependencies. The Neovim setup prefers `node_modules/.bin` for this project and falls back to its configured global/Mason binary for other projects.

`npm run build` runs the TypeScript check and Vite production build.

## Docker

```bash
docker compose up --build
```

Open `http://localhost:5173`. The Compose setup mounts the source directory and keeps `node_modules` in a named volume, so Vite hot reload remains available without sharing host dependencies with the container.

Stop the container with:

```bash
docker compose down
```

## Formatting

```bash
npm run format
npm run format:check
```

Prettier formats the frontend source and checks formatting in CI or before review. The shared rules live in `.prettierrc.json` and `.editorconfig`.

## Boundary

Catalog data lives in `src/mocks/catalog.ts`. Form submissions, login, email verification, JWT handling, API calls, persistence, and role-based redirects are intentionally mocked and marked in the UI as TODO work for the backend integration phase.
