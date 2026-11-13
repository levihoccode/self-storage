// Truy cập Postgres của docker compose từ Node — chạy được cả Linux và Windows.
// Không dùng shell redirect (`<`, `|`) vì cmd.exe và bash xử lý khác nhau: mọi thứ đi qua stdin pipe.
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

export const REPO_ROOT = path.resolve(import.meta.dirname, "..", "..");

const SERVICE = "postgres";
const REDIS = "redis";
const DB = process.env.DEMO_DB ?? "self_storage";
const USER = process.env.DEMO_DB_USER ?? "self_storage";

function docker(args, { stdin, cwd = REPO_ROOT } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn("docker", args, { cwd });
    const out = [];
    const err = [];
    child.stdout.on("data", (chunk) => out.push(chunk));
    child.stderr.on("data", (chunk) => err.push(chunk));
    child.on("error", (error) =>
      reject(new Error(`không chạy được docker: ${error.message}\nDocker Desktop đã bật chưa?`)),
    );
    child.on("close", (code) => {
      const stdout = Buffer.concat(out);
      const stderr = Buffer.concat(err).toString().trim();
      if (code === 0) resolve(stdout);
      else reject(new Error(`docker ${args.slice(0, 3).join(" ")} → exit ${code}\n${stderr.slice(-1500)}`));
    });
    child.stdin.end(stdin ?? undefined);
  });
}

function compose(service, args, options) {
  return docker(["compose", "exec", "-T", service, ...args], options);
}

/** Chạy SQL, trả stdout dạng text. */
export async function psql(sql) {
  const out = await compose(SERVICE, ["psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", USER, "-d", DB], {
    stdin: Buffer.from(sql, "utf8"),
  });
  return out.toString();
}

/** Chạy một file .sql trên host: đọc nội dung rồi đẩy vào stdin của psql. */
export async function psqlFile(file) {
  return psql(fs.readFileSync(path.resolve(REPO_ROOT, file), "utf8"));
}

/** Một dòng kết quả, không padding. */
export async function scalar(sql) {
  const out = await compose(SERVICE, ["psql", "-tA", "-v", "ON_ERROR_STOP=1", "-U", USER, "-d", DB], {
    stdin: Buffer.from(sql, "utf8"),
  });
  return out.toString().trim();
}

/** Truncate mọi bảng nghiệp vụ, giữ flyway_schema_history để Flyway không dựng lại schema. */
export async function truncateAll() {
  await psql(`
    DO $$ DECLARE r record; BEGIN
      FOR r IN SELECT tablename FROM pg_tables
               WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
      LOOP
        EXECUTE format('TRUNCATE TABLE public.%I RESTART IDENTITY CASCADE', r.tablename);
      END LOOP;
    END $$;
  `);
}

/** pg_dump data-only (bỏ flyway_schema_history) ra file nhị phân. */
export async function dumpTo(file) {
  const out = await compose(SERVICE, [
    "pg_dump", "-U", USER, "-d", DB,
    "--data-only", "--exclude-table=public.flyway_schema_history", "-Fc",
  ]);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out);
  return fs.statSync(file).size;
}

/**
 * Nạp lại dump. `--disable-triggers` là bắt buộc: pg_dump cảnh báo vòng FK giữa
 * storage_units và rental_orders, không tắt trigger thì restore chết giữa đường.
 */
export async function restoreFrom(file) {
  const dump = fs.readFileSync(file);
  await compose(SERVICE, [
    "pg_restore", "-U", USER, "-d", DB, "--data-only", "--disable-triggers",
  ], { stdin: dump });
}

/** Xoá cache phiên thanh toán và bộ đếm rate limit trong Redis. */
export async function flushRedis() {
  try {
    await compose(REDIS, ["redis-cli", "FLUSHALL"]);
  } catch {
    // Không có Redis trong compose là chấp nhận được khi demo.
  }
}

/** Flyway đã chạy xong chưa — chờ tới khi có bản ghi migration cuối. */
export async function flywayReady(timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const ok = await scalar(
        "select count(*) from flyway_schema_history where success and installed_rank > 0",
      );
      if (Number(ok) > 30) return Number(ok);
    } catch {
      // container hoặc bảng chưa có — thử lại
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error("Flyway chưa xong sau 3 phút. Bật backend trước rồi chạy lại.");
}
