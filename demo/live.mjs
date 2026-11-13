// Mở các cửa sổ demo theo flow, đã đăng nhập sẵn. Bấm số để reset DB + nhảy sang cảnh đó.
//
//   node demo/live.mjs 7            # mở Flow 7 rồi chờ phím
//   node demo/live.mjs 7 --auto     # chỉ mở + chụp ảnh rồi thoát (dùng để kiểm tra)
//   node demo/live.mjs 7 --no-reset # mở cảnh, không đụng DB
//
// Chuẩn bị một lần: `node demo/seed.mjs`
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";
import { REPO_ROOT, truncateAll, restoreFrom, flushRedis, psqlFile, scalar } from "./lib/db.mjs";
import { SCENES, TALKING_POINTS, FLOW_NAMES } from "./scenes.mjs";
import { detectScreen, geometryFor } from "./lib/layout.mjs";

const argv = process.argv.slice(2);
const AUTO = argv.includes("--auto");
const NO_RESET = argv.includes("--no-reset");
const startFlow = Number(argv.find((a) => /^[1-7]$/.test(a)) ?? "7");

const APP = process.env.DEMO_APP ?? "http://localhost:5173";
const API = process.env.DEMO_API ?? "http://localhost:8081";
const PASSWORD = "Test@1234";
const STORAGE_KEY = "kho-moc-auth-session";
const SNAPSHOT = path.join(REPO_ROOT, "demo", ".snapshot", "base.dump");
const SHOT_DIR = path.join(REPO_ROOT, "demo", ".snapshot", "shots");

function resolveChromium() {
  const candidates = [
    process.env.DEMO_CHROME,
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/usr/bin/google-chrome",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error(
    "Không tìm thấy Chromium/Chrome/Edge. Đặt biến DEMO_CHROME trỏ tới file thực thi,\n" +
      "hoặc chạy: npx playwright install chromium (rồi đặt DEMO_CHROME theo đường dẫn nó in ra).",
  );
}

async function sessionFor(email) {
  const loginRes = await fetch(`${API}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  const login = await loginRes.json();
  if (!loginRes.ok) throw new Error(`login ${email}: ${login.message ?? JSON.stringify(login)}`);

  const meRes = await fetch(`${API}/api/auth/me`, {
    headers: { Authorization: `Bearer ${login.data.token}` },
  });
  const me = await meRes.json();
  if (!meRes.ok) throw new Error(`me ${email}: ${JSON.stringify(me)}`);

  return {
    user: {
      id: String(login.data.accountId),
      name: me.data.name ?? email.split("@")[0],
      email: me.data.email,
      phone: me.data.phone ?? undefined,
      role: me.data.role,
    },
    issuedAt: new Date().toISOString(),
    token: login.data.token,
  };
}

// Wayland không cho client đặt vị trí/kích thước cửa sổ (compositor quyết định).
// Ép XWayland thì --window-position/--window-size mới có tác dụng; tắt bằng DEMO_OZONE=native.
const OZONE =
  process.env.DEMO_OZONE ??
  (process.platform === "linux" && process.env.WAYLAND_DISPLAY ? "x11" : null);
const OZONE_ARGS = OZONE ? [`--ozone-platform=${OZONE}`] : [];

const executablePath = resolveChromium();
const browser = { slots: new Map() };

async function closeSlot(index) {
  const slot = browser.slots.get(index);
  if (!slot) return;
  await slot.context.close().catch(() => {});
  browser.slots.delete(index);
}

async function openSlot(index, spec, geometry) {
  await closeSlot(index);
  const profile = path.join(REPO_ROOT, "demo", ".snapshot", `profile-${index}`);
  fs.rmSync(profile, { recursive: true, force: true });

  const session = await sessionFor(spec.email);
  const context = await chromium.launchPersistentContext(profile, {
    executablePath,
    headless: false,
    // null = khung nhìn bám theo cửa sổ thật. Đặt số cứng ở đây là lý do trang bị "fixed size".
    viewport: null,
    args: [
      `--window-position=${geometry.at[0]},${geometry.at[1]}`,
      `--window-size=${geometry.size[0]},${geometry.size[1]}`,
      "--no-first-run",
      "--no-default-browser-check",
      ...OZONE_ARGS,
    ],
  });
  await context.addInitScript(
    ([key, payload]) => {
      try {
        window.localStorage.setItem(key, payload);
      } catch {
        /* about:blank */
      }
    },
    [STORAGE_KEY, JSON.stringify(session)],
  );

  const page = context.pages()[0] ?? (await context.newPage());
  await page.goto(`${APP}${spec.route}`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);

  browser.slots.set(index, { context, page, email: spec.email, route: spec.route });

  return browser.slots.get(index);
}

async function showScene(flow) {
  const scene = SCENES[flow];
  if (!scene) throw new Error(`Chưa định nghĩa cảnh cho flow ${flow}`);

  browser.screen ??= await detectScreen(executablePath, APP, OZONE_ARGS);
  const geometry = geometryFor(scene, browser.screen);

  for (const [index, spec] of scene.entries()) {
    const slot = browser.slots.get(index);
    if (slot && slot.email === spec.email) {
      slot.route = spec.route;
      await slot.page.goto(`${APP}${spec.route}`, { waitUntil: "domcontentloaded" });
      await slot.page.waitForTimeout(800);
    } else {
      await openSlot(index, spec, geometry[index]);
    }
  }
  for (const index of [...browser.slots.keys()]) {
    if (index >= scene.length) await closeSlot(index);
  }

  console.log(`\n── Flow ${flow} · ${FLOW_NAMES[flow]} ──`);
  for (const line of TALKING_POINTS[flow] ?? []) console.log(`   • ${line}`);
  console.log(`   Cửa sổ: ${scene.map((s) => `${s.email.split("@")[0]} → ${s.route}`).join("  |  ")}`);
  console.log(
    `   Màn hình ${browser.screen.width}×${browser.screen.height} → ${geometry
      .map((g) => `${g.size[0]}×${g.size[1]} @${g.at[0]},${g.at[1]}`)
      .join("  ")}  (window manager có thể xếp lại)`,
  );
}

async function reset(flow) {
  if (NO_RESET) return;
  if (!fs.existsSync(SNAPSHOT)) {
    throw new Error(`Chưa có snapshot. Chạy trước: node demo/seed.mjs`);
  }
  const started = Date.now();
  await flushRedis();
  await truncateAll();
  await restoreFrom(SNAPSHOT);
  const overlay = path.join(REPO_ROOT, "demo", "sql", `flow-${flow}.sql`);
  if (fs.existsSync(overlay)) await psqlFile(`demo/sql/flow-${flow}.sql`);

  // Tự kiểm: restore im lặng thất bại là kiểu hỏng tệ nhất — cảnh mở ra trống mà không ai biết.
  const accounts = Number(await scalar("select count(*) from accounts"));
  const support = Number(await scalar("select count(*) from support_requests"));
  if (accounts < 30) {
    throw new Error(
      `Reset xong nhưng DB chỉ có ${accounts} account (cần ≥ 30). Snapshot hỏng hoặc volume bị xoá.\n` +
        `Chạy lại: node demo/seed.mjs`,
    );
  }
  console.log(`   [reset ${Date.now() - started}ms] accounts=${accounts} support=${support}`);
}

async function shoot(flow) {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const files = [];
  for (const [index, slot] of browser.slots) {
    const file = path.join(SHOT_DIR, `flow-${flow}-slot${index}.png`);
    await slot.page.screenshot({ path: file });
    files.push(path.relative(REPO_ROOT, file));
  }
  console.log(`   Ảnh: ${files.join(", ")}`);
  return files;
}

await (async () => {
  await reset(startFlow);
  await showScene(startFlow);

  if (AUTO) {
    await new Promise((resolve) => setTimeout(resolve, 2500));
    await shoot(startFlow);
    for (const index of [...browser.slots.keys()]) await closeSlot(index);
    process.exit(0);
  }

  console.log("\nBấm 1–7 để reset + đổi flow, q để thoát.\n");
  process.stdin.setRawMode(true);
  process.stdin.resume();
  process.stdin.on("data", async (buffer) => {
    const key = buffer.toString();
    if (key === "q" || key === "\u0003") {
      for (const index of [...browser.slots.keys()]) await closeSlot(index);
      process.exit(0);
    }
    if (/^[1-7]$/.test(key)) {
      try {
        await reset(Number(key));
        await showScene(Number(key));
        await shoot(Number(key));
      } catch (error) {
        console.error(`Lỗi: ${error.message}`);
      }
    }
  });
})();
