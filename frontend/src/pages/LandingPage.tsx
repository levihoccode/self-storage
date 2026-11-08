import { useEffect, useState } from "react";
import { ArrowRight, RotateCcw, RotateCw } from "lucide-react";
import { Navigate } from "../app/types";
import { PAGE_CONTAINER as CONTAINER } from "../app/layout";
import {
  fetchFacilities,
  fetchUnitTypes,
  formatArea,
  formatDimensions,
  formatVnd,
  type Facility,
  type UnitType,
} from "../app/catalog";
import { SurfaceState } from "../components/ui/SurfaceState";
import { UnitBox3D, boxMetrics } from "./landing/UnitBox3D";
import "./landing/landing.css";

/** Nội dung tĩnh: API loại khoang chưa trả mô tả (khoảng trống G2 trong data contract). */
const FIT_BY_TYPE: Record<string, string> = {
  Small: "Hồ sơ, vật tư nhỏ, hàng mẫu",
  Medium: "Hàng tồn của cửa hàng nhỏ",
  Large: "Hàng tồn nhiều, thiết bị văn phòng",
};

/**
 * Nội dung tĩnh: các giá trị nằm trong bảng policies, chưa có API công khai (khoảng trống G1).
 * invoice.deposit_months, invoice.deposit_due_days, proposal.max_rejection_count, request.pending_expiry_days.
 */
const DEPOSIT_MONTHS = 1;
const RULES = [
  { big: "1 tháng", text: "Tiền cọc bằng 1 tháng giá thuê của khoang được đề xuất." },
  {
    big: "3 ngày",
    text: "Hạn thanh toán cọc, tính từ lúc bạn đồng ý khoang. Khoang chỉ được giữ sau khi cọc thành công.",
  },
  {
    big: "Tối đa 3 lần",
    text: "Số lần từ chối đề xuất. Lần thứ 3 đơn bị huỷ; cọc đã trả (nếu có) không được hoàn.",
  },
  { big: "7 ngày", text: "Yêu cầu chưa được xử lý quá 7 ngày sẽ tự hết hạn." },
];

const STEPS = [
  {
    n: "01",
    title: "Gửi yêu cầu",
    who: "Bạn",
    detail: "Cơ sở, loại khoang, ngày bắt đầu, thời hạn. Không cần tài khoản.",
    limit: "Hết hạn sau 7 ngày nếu chưa xử lý",
  },
  {
    n: "02",
    title: "Duyệt và chọn khoang",
    who: "Quản lý cơ sở",
    detail: "Chọn khoang trống đúng loại và tạo đề xuất cho bạn.",
    limit: "—",
  },
  {
    n: "03",
    title: "Phản hồi đề xuất",
    who: "Bạn",
    detail: "Đồng ý, hoặc từ chối kèm lý do để nhận khoang khác.",
    limit: "Từ chối tối đa 3 lần",
  },
  {
    n: "04",
    title: "Đặt cọc",
    who: "Bạn",
    detail: "Thanh toán hoá đơn cọc. Khoang chỉ được giữ sau khi cọc thành công.",
    limit: "3 ngày",
  },
  {
    n: "05",
    title: "Hẹn lịch nhận kho",
    who: "Bạn",
    detail:
      "Chọn một khung giờ: 08:00–10:00, 13:00–15:00 hoặc 17:00–19:00. Mỗi khung một lịch mỗi cơ sở.",
    limit: "—",
  },
  {
    n: "06",
    title: "Nhận kho",
    who: "Nhân viên kho",
    detail: "Tiếp đón, xác nhận danh tính và bàn giao khoang.",
    limit: "Theo lịch đã hẹn",
  },
];

const VIEWER_SCALE = 62;
const CARD_SCALE = 30;

type CatalogData = {
  facilities: Facility[];
  unitTypes: UnitType[];
  typesBySite: Record<number, UnitType[]>;
};
type LoadResult = { ok: true; data: CatalogData } | { ok: false; message: string };

/** Hàm tải thuần (không setState): effect chỉ ghi state sau `await`. */
async function loadCatalog(): Promise<LoadResult> {
  try {
    const facilities = await fetchFacilities();
    const perSite = await Promise.all(facilities.map((facility) => fetchUnitTypes(facility.id)));
    const typesBySite: Record<number, UnitType[]> = {};
    const byId = new Map<number, UnitType>();
    facilities.forEach((facility, index) => {
      typesBySite[facility.id] = perSite[index];
      perSite[index].forEach((unit) => byId.set(unit.id, unit));
    });
    const unitTypes = [...byId.values()].sort((left, right) => left.id - right.id);
    return { ok: true, data: { facilities, unitTypes, typesBySite } };
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : "Không tải được danh sách cơ sở.",
    };
  }
}

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function SectionHeading({ label, title, lead }: { label: string; title: string; lead?: string }) {
  return (
    <div className="mb-9">
      <p className="lp-eyebrow text-[var(--lp-teal)]">{label}</p>
      <h2 className="mt-2.5 max-w-[22ch] text-balance text-[clamp(1.875rem,4vw,2.875rem)] leading-[1.05] tracking-[-0.03em]">
        {title}
      </h2>
      {lead && (
        <p className="mt-3.5 max-w-[62ch] text-[1.0625rem] leading-relaxed text-[var(--lp-text-2)]">
          {lead}
        </p>
      )}
    </div>
  );
}

function SiteChips({ codes }: { codes: string[] }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="text-[0.8125rem] text-[var(--lp-muted)]">Cung cấp tại</span>
      {codes.map((code) => (
        <span
          key={code}
          className="lp-mono rounded border border-[var(--lp-teal)] px-2 py-0.5 text-xs"
        >
          {code}
        </span>
      ))}
    </div>
  );
}

const SELECT_CLASS =
  "min-h-[46px] w-full rounded-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)] px-3 text-base text-[var(--lp-ink)]";

export function LandingPage({ navigate }: { navigate: Navigate }) {
  const [load, setLoad] = useState<
    { kind: "loading" } | { kind: "error"; message: string } | { kind: "ready"; data: CatalogData }
  >({ kind: "loading" });
  const [siteId, setSiteId] = useState<number | null>(null);
  const [typeId, setTypeId] = useState<number | null>(null);
  const [period, setPeriod] = useState(6);
  const [angle, setAngle] = useState(-35);
  const [autoRotate, setAutoRotate] = useState(true);

  // setState chỉ chạy sau await; `active` chặn cập nhật khi đã rời trang.
  useEffect(() => {
    let active = true;
    async function loadInitial() {
      const result = await loadCatalog();
      if (!active) return;
      if (result.ok) {
        setLoad({ kind: "ready", data: result.data });
        setSiteId(result.data.facilities[0]?.id ?? null);
      } else {
        setLoad({ kind: "error", message: result.message });
      }
    }
    void loadInitial();
    return () => {
      active = false;
    };
  }, []);

  // Nút "Thử lại" — setState nằm ở event handler, không phải effect.
  async function retry() {
    setLoad({ kind: "loading" });
    const result = await loadCatalog();
    if (result.ok) {
      setLoad({ kind: "ready", data: result.data });
      setSiteId(result.data.facilities[0]?.id ?? null);
    } else {
      setLoad({ kind: "error", message: result.message });
    }
  }

  if (load.kind === "loading") {
    return (
      <main className={`${CONTAINER} py-16`}>
        <SurfaceState variant="loading" title="Đang tải cơ sở và loại khoang…" />
      </main>
    );
  }
  if (load.kind === "error") {
    return (
      <main className={`${CONTAINER} py-16`}>
        <SurfaceState
          variant="error"
          title="Không tải được danh sách cơ sở"
          description={load.message}
          action={{ label: "Thử lại", onClick: () => void retry() }}
        />
      </main>
    );
  }

  const { facilities, unitTypes, typesBySite } = load.data;
  if (facilities.length === 0 || unitTypes.length === 0) {
    return (
      <main className={`${CONTAINER} py-16`}>
        <SurfaceState
          variant="empty"
          title="Chưa có cơ sở nào đang mở"
          description="Bạn vẫn có thể gửi yêu cầu, đội ngũ vận hành sẽ liên hệ tư vấn."
          action={{ label: "Gửi yêu cầu", onClick: () => navigate("/rental-requests/new") }}
        />
      </main>
    );
  }

  const currentSiteId = siteId ?? facilities[0].id;
  const availableTypes = typesBySite[currentSiteId] ?? [];
  const current = availableTypes.find((unit) => unit.id === typeId) ?? availableTypes[0];
  const viewerUnit = unitTypes.find((unit) => unit.id === typeId) ?? unitTypes[1] ?? unitTypes[0];
  const areas = unitTypes.map((unit) => unit.area);
  const hoursSet = new Set(facilities.map((facility) => facility.operatingHours));
  const hours = hoursSet.size === 1 ? [...hoursSet][0].split(" ")[0] : "Xem từng cơ sở";
  const codesOf = (unit: UnitType) =>
    facilities.filter((facility) => facility.unitTypeIds.includes(unit.id)).map((f) => f.code);
  const viewerShadow = boxMetrics(viewerUnit, VIEWER_SCALE).shadow;

  function rotate(delta: number) {
    setAutoRotate(false);
    setAngle((value) => value + delta);
  }

  function pickSite(id: number) {
    setSiteId(id);
    scrollToSection("estimate");
  }

  /** Chọn loại khoang; nếu cơ sở đang chọn không có loại này thì chuyển sang cơ sở có. */
  function pickType(unit: UnitType, scrollTo?: string) {
    const site = typesBySite[currentSiteId]?.some((item) => item.id === unit.id)
      ? currentSiteId
      : facilities.find((facility) => typesBySite[facility.id]?.some((item) => item.id === unit.id))
          ?.id;
    if (site !== undefined) setSiteId(site);
    setTypeId(unit.id);
    if (scrollTo) scrollToSection(scrollTo);
  }

  return (
    <div className="lp">
      <section id="top" className="lp-grid-bg scroll-mt-24 bg-[var(--lp-hero)]">
        <div
          className={`${CONTAINER} grid grid-cols-[minmax(0,1.05fr)_minmax(360px,1fr)] items-center gap-[clamp(32px,5vw,64px)] py-[clamp(40px,6vw,72px)] max-[960px]:grid-cols-1`}
        >
          <div className="min-w-0">
            <p className="lp-eyebrow text-[var(--lp-teal)]">Kho tự quản · TP. Hồ Chí Minh</p>
            <h1 className="mt-4 text-[clamp(2.375rem,5.6vw,4.25rem)] leading-[1.02] tracking-[-0.035em]">
              Thấy đúng cỡ kho
              <span className="block text-[var(--lp-teal)]">trước khi thuê.</span>
            </h1>
            <p className="mt-5 max-w-[50ch] text-lg leading-relaxed text-[var(--lp-text-2)]">
              Mỗi cỡ khoang có mô hình 3D theo kích thước thật, giá và điều khoản ghi rõ. Dành cho
              cửa hàng, đơn vị bán hàng và văn phòng cần chỗ chứa hàng tồn, hồ sơ, vật tư.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <button
                type="button"
                className="lp-btn lp-btn-primary px-6"
                onClick={() => navigate("/rental-requests/new")}
              >
                Gửi yêu cầu thuê kho
              </button>
              <button
                type="button"
                className="lp-btn lp-btn-ghost px-6"
                onClick={() => scrollToSection("estimate")}
              >
                Ước tính chi phí
              </button>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-11 gap-y-5">
              <div>
                <strong className="lp-mono block text-3xl">
                  {String(facilities.length).padStart(2, "0")}
                </strong>
                <span className="text-[0.8125rem] text-[var(--lp-muted)]">cơ sở đang mở</span>
              </div>
              <div>
                <strong className="lp-mono block text-3xl">
                  {`${Math.min(...areas)}–${Math.max(...areas)} m²`.replace(/\./g, ",")}
                </strong>
                <span className="text-[0.8125rem] text-[var(--lp-muted)]">diện tích khoang</span>
              </div>
              <div>
                <strong className="lp-mono block text-3xl">{hours}</strong>
                <span className="text-[0.8125rem] text-[var(--lp-muted)]">giờ tiếp nhận</span>
              </div>
            </div>
          </div>

          <div className="min-w-0 rounded-xl border border-[var(--lp-line)] bg-[var(--lp-surface)] p-5">
            <div
              className="flex flex-wrap gap-2"
              role="group"
              aria-label="Chọn cỡ khoang để xem 3D"
            >
              {unitTypes.map((unit) => {
                const on = unit.id === viewerUnit.id;
                return (
                  <button
                    key={unit.id}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setTypeId(unit.id)}
                    className={`min-h-[44px] rounded-md border px-4 py-2 text-left text-[0.9375rem] font-bold ${
                      on
                        ? "border-[var(--brand)] bg-[var(--brand)] text-[var(--signal-ink)]"
                        : "border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[var(--lp-ink)]"
                    }`}
                  >
                    {unit.name}
                    <span className="lp-mono block text-[0.6875rem] font-medium opacity-85">
                      {formatArea(unit.area)}
                    </span>
                  </button>
                );
              })}
            </div>

            <div
              className="relative mt-2.5 flex h-[340px] items-center justify-center overflow-hidden [perspective:1000px]"
              aria-hidden="true"
            >
              <div
                className={`flex items-center justify-center [transform-style:preserve-3d] ${
                  autoRotate ? "lp-spin" : ""
                }`}
              >
                <UnitBox3D unit={viewerUnit} scale={VIEWER_SCALE} angle={angle} />
              </div>
              <div className="absolute inset-x-0 bottom-3.5 flex justify-center">
                <div
                  className="h-3.5 rounded-full bg-[var(--lp-shade)] blur-[6px]"
                  style={{ width: viewerShadow }}
                />
              </div>
            </div>

            <div className="mt-1.5 flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                aria-label="Xoay sang trái"
                onClick={() => rotate(-30)}
                className="grid h-11 w-11 place-items-center rounded-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)]"
              >
                <RotateCcw size={18} />
              </button>
              <button
                type="button"
                aria-label="Xoay sang phải"
                onClick={() => rotate(30)}
                className="grid h-11 w-11 place-items-center rounded-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)]"
              >
                <RotateCw size={18} />
              </button>
              <button
                type="button"
                aria-pressed={autoRotate}
                onClick={() => setAutoRotate((value) => !value)}
                className="min-h-[44px] rounded-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)] px-4 text-sm font-semibold"
              >
                {autoRotate ? "Dừng xoay" : "Tự xoay"}
              </button>
              <span className="min-w-[160px] flex-1 text-xs text-[var(--lp-muted)]">
                Mô phỏng 3D đúng tỷ lệ kích thước thật. Dáng cửa mang tính minh hoạ, không phải ảnh
                chụp khoang.
              </span>
            </div>

            <div className="mt-3.5 flex flex-wrap items-end gap-x-7 gap-y-3 border-t border-[var(--lp-line)] pt-3.5">
              <div className="min-w-[200px] flex-1">
                <span className="lp-eyebrow text-[var(--lp-teal)]">Kích thước</span>
                <p className="lp-mono mt-1 text-base">{formatDimensions(viewerUnit)}</p>
                <p className="mt-0.5 text-[0.8125rem] text-[var(--lp-muted)]">
                  rộng × sâu × cao · {formatArea(viewerUnit.area)}
                </p>
              </div>
              <div>
                <span className="lp-eyebrow text-[var(--lp-teal)]">Giá / tháng</span>
                <p className="lp-mono mt-1 text-2xl">{formatVnd(viewerUnit.monthlyPrice)}</p>
              </div>
            </div>
            <div className="mt-3">
              <SiteChips codes={codesOf(viewerUnit)} />
            </div>
          </div>
        </div>
      </section>

      <section id="unit-types" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(52px,7vw,84px)]">
        <div className={CONTAINER}>
          <SectionHeading
            label="Các cỡ kho"
            title="Ba cỡ, cùng một tỷ lệ."
            lead="Cả ba vẽ cùng một tỷ lệ, để bạn thấy khác biệt thật giữa 2 m² và 7,5 m². Số khoang trống không hiển thị công khai; quản lý cơ sở chọn khoang phù hợp sau khi nhận yêu cầu."
          />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-5">
            {unitTypes.map((unit) => (
              <div
                key={unit.id}
                className="flex min-w-0 flex-col overflow-hidden rounded-xl border border-[var(--lp-line)] bg-[var(--lp-surface)]"
              >
                <div className="lp-grid-bg flex h-[210px] items-center justify-center bg-[var(--lp-hero)] [perspective:700px]">
                  <UnitBox3D unit={unit} scale={CARD_SCALE} angle={-38} tilt={-22} />
                </div>
                <div className="flex flex-1 flex-col gap-2.5 p-5">
                  <div className="flex items-baseline justify-between gap-3">
                    <h3 className="text-2xl tracking-[-0.02em]">{unit.name}</h3>
                    <span className="lp-mono text-[0.9375rem] text-[var(--lp-teal)]">
                      {formatArea(unit.area)}
                    </span>
                  </div>
                  <p className="lp-mono text-[0.8125rem] text-[var(--lp-muted)]">
                    {formatDimensions(unit)} (rộng × sâu × cao)
                  </p>
                  <p className="text-[0.9375rem] leading-normal text-[var(--lp-text-2)]">
                    {FIT_BY_TYPE[unit.name] ?? "—"}
                  </p>
                  <div className="mt-auto flex items-baseline justify-between border-t border-[var(--lp-line)] pt-3">
                    <span className="text-[0.8125rem] text-[var(--lp-muted)]">Giá / tháng</span>
                    <strong className="lp-mono text-xl">{formatVnd(unit.monthlyPrice)}</strong>
                  </div>
                  <SiteChips codes={codesOf(unit)} />
                  <button
                    type="button"
                    className="lp-btn lp-btn-solid"
                    onClick={() => {
                      pickType(unit);
                      scrollToSection("top");
                    }}
                  >
                    Xem cỡ này ở khung 3D
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section
        id="estimate"
        className="scroll-mt-24 border-y border-[var(--lp-line)] bg-[var(--lp-bg-alt)] py-[clamp(52px,7vw,84px)]"
      >
        <div className={`${CONTAINER} flex flex-wrap items-start gap-x-10 gap-y-8`}>
          <div className="min-w-[300px] flex-[1_1_360px]">
            <p className="lp-eyebrow text-[var(--lp-teal)]">Ước tính</p>
            <h2 className="mt-2.5 text-[clamp(1.875rem,4vw,2.875rem)] leading-[1.05] tracking-[-0.03em]">
              Tính chi phí trong 10 giây.
            </h2>
            <p className="mt-3.5 max-w-[46ch] text-[1.0625rem] leading-relaxed text-[var(--lp-text-2)]">
              Chọn cơ sở, loại khoang và số tháng. Đây là ước tính từ bảng giá hiện tại, không phải
              báo giá. Giá có thể khác giá chốt trong hợp đồng tại thời điểm ký.
            </p>
          </div>
          <div className="flex min-w-[300px] flex-[1_1_440px] flex-col gap-[18px] rounded-xl border border-[var(--lp-line)] bg-[var(--lp-surface)] p-6">
            <div>
              <label
                htmlFor="estimate-site"
                className="lp-eyebrow mb-1.5 block text-[var(--lp-teal)]"
              >
                Cơ sở
              </label>
              <select
                id="estimate-site"
                className={SELECT_CLASS}
                value={String(currentSiteId)}
                onChange={(event) => setSiteId(Number(event.target.value))}
              >
                {facilities.map((facility) => (
                  <option key={facility.id} value={facility.id}>
                    {facility.code} · {facility.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label
                htmlFor="estimate-type"
                className="lp-eyebrow mb-1.5 block text-[var(--lp-teal)]"
              >
                Loại khoang
              </label>
              <select
                id="estimate-type"
                className={SELECT_CLASS}
                value={current ? String(current.id) : ""}
                onChange={(event) => setTypeId(Number(event.target.value))}
              >
                {availableTypes.map((unit) => (
                  <option key={unit.id} value={unit.id}>
                    {unit.name} · {formatArea(unit.area)} · {formatVnd(unit.monthlyPrice)}/tháng
                  </option>
                ))}
              </select>
              <p className="mt-1.5 text-[0.8125rem] text-[var(--lp-muted)]">
                Cơ sở này cung cấp {availableTypes.length}/{unitTypes.length} loại khoang.
              </p>
            </div>
            <div>
              <span id="estimate-months" className="lp-eyebrow mb-1.5 block text-[var(--lp-teal)]">
                Thời hạn thuê dự kiến
              </span>
              <div className="flex items-center" role="group" aria-labelledby="estimate-months">
                <button
                  type="button"
                  aria-label="Giảm một tháng"
                  onClick={() => setPeriod((value) => Math.max(1, value - 1))}
                  className="h-12 w-12 rounded-l-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[1.375rem] font-bold"
                >
                  −
                </button>
                <div
                  className="lp-mono flex h-12 min-w-[130px] items-center justify-center border-y border-[var(--lp-line)] text-lg"
                  aria-live="polite"
                >
                  {period} tháng
                </div>
                <button
                  type="button"
                  aria-label="Tăng một tháng"
                  onClick={() => setPeriod((value) => Math.min(24, value + 1))}
                  className="h-12 w-12 rounded-r-md border border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[1.375rem] font-bold"
                >
                  +
                </button>
              </div>
            </div>
            {current && (
              <div className="overflow-hidden rounded-lg border border-[var(--lp-line)] text-[0.9375rem]">
                <div className="flex justify-between gap-3 border-b border-[var(--lp-line)] px-4 py-3">
                  <span className="text-[var(--lp-muted)]">Giá niêm yết / tháng</span>
                  <strong className="lp-mono">{formatVnd(current.monthlyPrice)}</strong>
                </div>
                <div className="flex justify-between gap-3 border-b border-[var(--lp-line)] px-4 py-3">
                  <span className="text-[var(--lp-muted)]">
                    Thuê {period} tháng (giá × số tháng)
                  </span>
                  <strong className="lp-mono">{formatVnd(current.monthlyPrice * period)}</strong>
                </div>
                <div className="flex justify-between gap-3 bg-[var(--lp-surface-2)] px-4 py-3">
                  <span>Tiền cọc dự kiến ({DEPOSIT_MONTHS} tháng giá thuê)</span>
                  <strong className="lp-mono">
                    {formatVnd(current.monthlyPrice * DEPOSIT_MONTHS)}
                  </strong>
                </div>
              </div>
            )}
            <button
              type="button"
              className="lp-btn lp-btn-primary min-h-[50px] w-full"
              onClick={() => navigate("/rental-requests/new")}
            >
              Gửi yêu cầu thuê kho <ArrowRight size={16} />
            </button>
          </div>
        </div>
      </section>

      <section id="terms" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(52px,7vw,84px)]">
        <div className={CONTAINER}>
          <SectionHeading
            label="Điều khoản đang áp dụng"
            title="Quy tắc nằm ngay trên trang."
            lead="Không cần gọi hỏi. Thuế và phí khác, điều kiện hoàn cọc sẽ được bổ sung khi nhóm chốt."
          />
          <div className="grid grid-cols-4 gap-3.5 max-[1000px]:grid-cols-2 max-[600px]:grid-cols-1">
            {RULES.map((rule) => (
              <div
                key={rule.big}
                className="rounded-xl border border-[var(--lp-line)] bg-[var(--lp-surface)] p-5"
              >
                <strong className="lp-mono block text-3xl leading-tight">{rule.big}</strong>
                <span className="mt-2.5 block text-[0.9375rem] leading-relaxed text-[var(--lp-text-2)]">
                  {rule.text}
                </span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section
        id="how-it-works"
        className="scroll-mt-24 border-y border-[var(--lp-line)] bg-[var(--lp-bg-alt)] py-[clamp(52px,7vw,84px)]"
      >
        <div className={CONTAINER}>
          <SectionHeading label="Quy trình" title="Sáu bước, ai làm gì, hạn bao lâu." />
          <ol className="grid list-none grid-cols-[repeat(auto-fit,minmax(170px,1fr))] gap-3.5 p-0">
            {STEPS.map((step) => (
              <li
                key={step.n}
                className="flex min-w-0 flex-col gap-1.5 border-t-[3px] border-[var(--lp-teal)] pt-3.5"
              >
                <span className="lp-mono text-[1.375rem] text-[var(--lp-teal)]">{step.n}</span>
                <strong className="text-[1.0625rem] leading-tight">{step.title}</strong>
                <span className="lp-eyebrow text-[var(--lp-muted)]">{step.who}</span>
                <span className="text-sm leading-normal text-[var(--lp-text-2)]">
                  {step.detail}
                </span>
                <span className="lp-mono mt-0.5 text-xs">{step.limit}</span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section id="locations" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(52px,7vw,84px)]">
        <div className={CONTAINER}>
          <SectionHeading label="Cơ sở" title="Chọn nơi hàng đến." />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-5">
            {facilities.map((facility) => (
              <div
                key={facility.id}
                className="flex min-w-0 flex-col gap-3 rounded-xl border border-[var(--lp-line)] bg-[var(--lp-surface)] p-5"
              >
                <div>
                  <span className="lp-eyebrow text-[var(--lp-teal)]">{facility.code}</span>
                  <h3 className="mt-1 text-2xl tracking-[-0.02em]">{facility.name}</h3>
                </div>
                <p className="text-[0.9375rem] text-[var(--lp-text-2)]">{facility.address}</p>
                <p className="text-[0.9375rem]">
                  Giờ tiếp nhận: <strong>{facility.operatingHours}</strong>
                </p>
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-[0.8125rem] text-[var(--lp-muted)]">Loại khoang</span>
                  {(typesBySite[facility.id] ?? []).map((unit) => (
                    <span
                      key={unit.id}
                      className="lp-mono rounded border border-[var(--lp-teal)] px-2 py-0.5 text-xs"
                    >
                      {unit.name}
                    </span>
                  ))}
                </div>
                <button
                  type="button"
                  className="lp-btn lp-btn-ghost mt-auto"
                  onClick={() => pickSite(facility.id)}
                >
                  Chọn cơ sở này
                </button>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="bg-[var(--brand)] py-[clamp(44px,6vw,64px)] text-[var(--signal-ink)]">
        <div className={`${CONTAINER} flex flex-wrap items-center justify-between gap-6`}>
          <h2 className="min-w-[280px] max-w-[30ch] flex-[1_1_440px] text-[clamp(1.75rem,3.8vw,2.75rem)] leading-[1.05] tracking-[-0.03em]">
            Chưa chắc cần bao nhiêu? Gửi nhu cầu, quản lý cơ sở sẽ đề xuất khoang phù hợp.
          </h2>
          <button
            type="button"
            className="lp-btn bg-[#0c1512] px-7 text-white"
            onClick={() => navigate("/rental-requests/new")}
          >
            Gửi yêu cầu đặt kho <ArrowRight size={16} />
          </button>
        </div>
      </section>
    </div>
  );
}
