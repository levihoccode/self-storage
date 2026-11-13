// Chia cửa sổ theo màn hình thật, không dùng toạ độ viết tay.
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";

/**
 * Đọc vùng làm việc của màn hình chính (đã trừ taskbar/dock).
 * Phải chạy headed: chế độ headless báo kích thước mô phỏng, không phải màn hình thật.
 * Cửa sổ dò được đặt ngoài màn hình để không nháy lên trước mặt người đang demo.
 */
export async function detectScreen(executablePath, url, extraArgs = []) {
  // Dùng persistent context, không phải chromium.launch(): bản không-persistent báo
  // kích thước màn hình mặc định (1280×720) thay vì màn hình thật (1920×1080).
  const profile = path.join(process.cwd(), ".snapshot", ".probe");
  fs.rmSync(profile, { recursive: true, force: true });
  const context = await chromium.launchPersistentContext(profile, {
    executablePath,
    headless: false,
    viewport: null,
    args: ["--window-size=200,150", ...extraArgs],
  });
  try {
    const page = context.pages()[0] ?? (await context.newPage());
    // Phải nạp một trang thật: about:blank báo kích thước mặc định của trình duyệt,
    // không phải của màn hình (đo được 1280×720 trong khi màn hình là 1920×1080).
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 15_000 });
    const size = await page.evaluate(() => ({
      width: window.screen.availWidth,
      height: window.screen.availHeight,
    }));
    if (!size.width || !size.height) throw new Error("màn hình trả về 0");
    return size;
  } finally {
    await context.close().catch(() => {});
    fs.rmSync(profile, { recursive: true, force: true });
  }
}

/**
 * Chia `count` cửa sổ cạnh nhau, phủ kín bề ngang.
 * Cửa sổ cuối nhận phần dư để không hở một dải vài pixel.
 */
export function tile({ width, height }, count, { margin = 0 } = {}) {
  const usableWidth = width - margin * 2;
  const usableHeight = height - margin;
  if (count <= 1) return [{ at: [margin, margin], size: [usableWidth, usableHeight] }];

  const columnWidth = Math.floor(usableWidth / count);
  return Array.from({ length: count }, (_, index) => {
    const last = index === count - 1;
    const w = last ? usableWidth - columnWidth * index : columnWidth;
    return { at: [margin + columnWidth * index, margin], size: [w, usableHeight] };
  });
}

/** Cảnh khai báo `at`/`size` thì tôn trọng; không khai báo thì chia theo màn hình. */
export function geometryFor(scene, screen) {
  const auto = tile(screen, scene.length, { margin: 0 });
  return scene.map((spec, index) => ({
    at: spec.at ?? auto[index].at,
    size: spec.size ?? auto[index].size,
  }));
}
