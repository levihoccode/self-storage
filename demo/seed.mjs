// Một lần cho mỗi máy: nạp dữ liệu demo rồi chụp lại thành snapshot để reset tức thì về sau.
//
//   node demo/seed.mjs
//
// Yêu cầu: `docker compose up -d`, backend đã chạy ít nhất một lần (Flyway dựng schema).
// Chạy lại lần hai trên cùng DB sẽ báo lỗi — DB phải sạch, vì `demo-seed.sql` chỉ nạp được một lần.
import path from "node:path";
import { REPO_ROOT, flywayReady, psqlFile, scalar, dumpTo } from "./lib/db.mjs";

const SNAPSHOT = path.join(REPO_ROOT, "demo", ".snapshot", "base.dump");
const DEMO_SEED = "backend/sql/dev/demo-seed.sql";

const migrations = await flywayReady();
console.log(`Flyway: ${migrations} migration đã áp dụng`);

const already = await scalar("select count(*) from facilities where code = 'BT'");
if (Number(already) > 0) {
  console.error(
    [
      "DB này đã có dữ liệu demo (cơ sở BT tồn tại).",
      "`demo-seed.sql` không nạp lại được. Reset về trạng thái sạch:",
      "",
      "  docker compose down -v && docker compose up -d",
      "  cd backend && ./mvnw spring-boot:run      # đợi Flyway xong",
      "  node demo/seed.mjs",
    ].join("\n"),
  );
  process.exit(1);
}

console.log(`Nạp ${DEMO_SEED} …`);
await psqlFile(DEMO_SEED);

const summary = await scalar(`
  select 'facilities=' || (select count(*) from facilities)
      || ' accounts=' || (select count(*) from accounts)
      || ' contracts=' || (select count(*) from rental_contracts)
      || ' invoices=' || (select count(*) from invoices)
      || ' units=' || (select count(*) from storage_units)
`);
console.log(`Dữ liệu: ${summary}`);

const bytes = await dumpTo(SNAPSHOT);
console.log(`Snapshot: ${path.relative(REPO_ROOT, SNAPSHOT)} (${(bytes / 1024).toFixed(0)} KB)`);
console.log("Xong. Giờ dùng: node demo/live.mjs 7");
