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
import { UnitBox3D } from "./landing/UnitBox3D";
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
const THUMB_SCALE = 16;

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

function SectionHeading({ title }: { title: string }) {
  return (
    <h2 className="mb-7 text-[clamp(1.75rem,3.6vw,2.5rem)] leading-[1.08] tracking-[-0.02em]">
      {title}
    </h2>
  );
}

function SiteChips({ codes }: { codes: string[] }) {
  return (
    <span className="flex flex-wrap gap-1.5">
      {codes.map((code) => (
        <span key={code} className="border border-[var(--lp-line)] px-2 py-0.5 text-sm font-bold">
          {code}
        </span>
      ))}
    </span>
  );
}

const FIELD_LABEL = "mb-1.5 block text-sm font-semibold text-[var(--lp-text-2)]";
const SELECT_CLASS =
  "min-h-[46px] w-full rounded-[2px] border-2 border-[var(--lp-line)] bg-[var(--lp-surface-2)] px-3 text-base text-[var(--lp-ink)]";
const ICON_BUTTON =
  "grid h-11 w-11 place-items-center border-2 border-[var(--lp-line)] bg-[var(--lp-surface-2)]";
const BLOCK = "border border-[var(--lp-line)] bg-[var(--lp-surface)]";

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
  const codesOf = (unit: UnitType) =>
    facilities.filter((facility) => facility.unitTypeIds.includes(unit.id)).map((f) => f.code);

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
      <section id="top" className="scroll-mt-24 bg-[var(--lp-hero)]">
        <div
          className={`${CONTAINER} grid grid-cols-[minmax(0,1fr)_minmax(360px,1fr)] items-center gap-[clamp(32px,5vw,64px)] py-[clamp(40px,6vw,72px)] max-[960px]:grid-cols-1`}
        >
          <div className="min-w-0">
            <h1 className="text-[clamp(2.25rem,5vw,3.75rem)] leading-[1.05] tracking-[-0.03em]">
              Thuê kho tự quản theo diện tích thật
            </h1>
            <p className="mt-5 max-w-[46ch] text-lg leading-relaxed text-[var(--lp-text-2)]">
              Cho cửa hàng, đơn vị bán hàng và văn phòng cần chỗ chứa hàng tồn, hồ sơ, vật tư.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <button
                type="button"
                className="lp-btn lp-btn-primary"
                onClick={() => navigate("/rental-requests/new")}
              >
                Gửi yêu cầu thuê kho
              </button>
              <button
                type="button"
                className="lp-btn lp-btn-ghost"
                onClick={() => scrollToSection("estimate")}
              >
                Ước tính chi phí
              </button>
            </div>
          </div>

          <div className={`min-w-0 p-5 ${BLOCK}`}>
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
                    className={`min-h-[44px] border-2 px-4 py-1.5 text-left text-[0.9375rem] font-bold ${
                      on
                        ? "border-[var(--brand)] bg-[var(--brand)] text-[var(--signal-ink)]"
                        : "border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[var(--lp-ink)]"
                    }`}
                  >
                    {unit.name}
                    <span className="lp-num block text-xs font-medium">
                      {formatArea(unit.area)}
                    </span>
                  </button>
                );
              })}
            </div>

            <div
              className="lp-grid-bg relative mt-3 flex h-[320px] items-center justify-center overflow-hidden border border-[var(--lp-line)] [perspective:1000px]"
              role="img"
              aria-label={`Mô hình 3D khoang ${viewerUnit.name}, ${formatDimensions(viewerUnit)}`}
            >
              <div
                className={`flex items-center justify-center [transform-style:preserve-3d] ${
                  autoRotate ? "lp-spin" : ""
                }`}
              >
                <UnitBox3D unit={viewerUnit} scale={VIEWER_SCALE} angle={angle} />
              </div>
            </div>

            <div className="mt-3 flex flex-wrap items-center gap-2">
              <button
                type="button"
                aria-label="Xoay sang trái"
                onClick={() => rotate(-30)}
                className={ICON_BUTTON}
              >
                <RotateCcw size={18} />
              </button>
              <button
                type="button"
                aria-label="Xoay sang phải"
                onClick={() => rotate(30)}
                className={ICON_BUTTON}
              >
                <RotateCw size={18} />
              </button>
              <button
                type="button"
                aria-pressed={autoRotate}
                onClick={() => setAutoRotate((value) => !value)}
                className="min-h-[44px] border-2 border-[var(--lp-line)] bg-[var(--lp-surface-2)] px-4 text-sm font-semibold"
              >
                {autoRotate ? "Dừng xoay" : "Tự xoay"}
              </button>
              <span className="text-xs text-[var(--lp-muted)]">
                Mô hình minh hoạ, không phải ảnh khoang.
              </span>
            </div>

            <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 border-t border-[var(--lp-line)] pt-4 max-[480px]:grid-cols-1">
              <div>
                <dt className="text-sm text-[var(--lp-muted)]">Rộng × sâu × cao</dt>
                <dd className="lp-num mt-0.5 text-base">{formatDimensions(viewerUnit)}</dd>
              </div>
              <div>
                <dt className="text-sm text-[var(--lp-muted)]">Giá / tháng</dt>
                <dd className="lp-num mt-0.5 text-xl">{formatVnd(viewerUnit.monthlyPrice)}</dd>
              </div>
              <div>
                <dt className="text-sm text-[var(--lp-muted)]">Diện tích</dt>
                <dd className="lp-num mt-0.5 text-base">{formatArea(viewerUnit.area)}</dd>
              </div>
              <div>
                <dt className="text-sm text-[var(--lp-muted)]">Cung cấp tại</dt>
                <dd className="mt-0.5">
                  <SiteChips codes={codesOf(viewerUnit)} />
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

      <section id="unit-types" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(48px,6vw,76px)]">
        <div className={CONTAINER}>
          <SectionHeading title="Các cỡ kho" />
          <div className="grid gap-2">
            {unitTypes.map((unit) => (
              <div
                key={unit.id}
                className={`grid grid-cols-[120px_minmax(0,1.1fr)_minmax(0,1.3fr)_minmax(0,1fr)_auto] items-center gap-5 p-4 max-[900px]:grid-cols-[120px_minmax(0,1fr)] ${BLOCK}`}
              >
                <div className="lp-grid-bg flex h-[104px] items-center justify-center border border-[var(--lp-line)] [perspective:500px]">
                  <UnitBox3D unit={unit} scale={THUMB_SCALE} angle={-38} tilt={-22} />
                </div>
                <div className="min-w-0">
                  <h3 className="text-xl">{unit.name}</h3>
                  <p className="lp-num mt-1 text-sm">
                    {formatArea(unit.area)} · {formatDimensions(unit)}
                  </p>
                </div>
                <p className="text-[0.9375rem] text-[var(--lp-text-2)]">
                  {FIT_BY_TYPE[unit.name] ?? "—"}
                </p>
                <div>
                  <strong className="lp-num block text-lg">{formatVnd(unit.monthlyPrice)}</strong>
                  <span className="text-sm text-[var(--lp-muted)]">/ tháng</span>
                  <div className="mt-1.5">
                    <SiteChips codes={codesOf(unit)} />
                  </div>
                </div>
                <button
                  type="button"
                  className="lp-btn lp-btn-ghost max-[900px]:col-span-2"
                  onClick={() => pickType(unit, "top")}
                >
                  Xem 3D
                </button>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section
        id="estimate"
        className="scroll-mt-24 bg-[var(--lp-bg-alt)] py-[clamp(48px,6vw,76px)]"
      >
        <div className={`${CONTAINER} max-w-[640px]`}>
          <SectionHeading title="Ước tính chi phí thuê" />
          <div className={`flex flex-col gap-5 p-6 ${BLOCK}`}>
            <div>
              <label htmlFor="estimate-site" className={FIELD_LABEL}>
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
              <label htmlFor="estimate-type" className={FIELD_LABEL}>
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
            </div>
            <div>
              <span id="estimate-months" className={FIELD_LABEL}>
                Thời hạn thuê dự kiến
              </span>
              <div className="flex items-center" role="group" aria-labelledby="estimate-months">
                <button
                  type="button"
                  aria-label="Giảm một tháng"
                  onClick={() => setPeriod((value) => Math.max(1, value - 1))}
                  className="h-12 w-12 border-2 border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[1.375rem] font-bold"
                >
                  −
                </button>
                <div
                  className="lp-num flex h-12 min-w-[130px] items-center justify-center border-y-2 border-[var(--lp-line)] text-lg"
                  aria-live="polite"
                >
                  {period} tháng
                </div>
                <button
                  type="button"
                  aria-label="Tăng một tháng"
                  onClick={() => setPeriod((value) => Math.min(24, value + 1))}
                  className="h-12 w-12 border-2 border-[var(--lp-line)] bg-[var(--lp-surface-2)] text-[1.375rem] font-bold"
                >
                  +
                </button>
              </div>
            </div>
            {current && (
              <div className="border border-[var(--lp-line)] text-[0.9375rem]">
                <div className="flex justify-between gap-3 px-4 py-3">
                  <span className="text-[var(--lp-muted)]">Giá / tháng</span>
                  <strong className="lp-num">{formatVnd(current.monthlyPrice)}</strong>
                </div>
                <div className="flex justify-between gap-3 px-4 py-3">
                  <span className="text-[var(--lp-muted)]">Thuê {period} tháng</span>
                  <strong className="lp-num">{formatVnd(current.monthlyPrice * period)}</strong>
                </div>
                <div className="flex justify-between gap-3 bg-[var(--lp-surface-2)] px-4 py-3">
                  <span>Tiền cọc dự kiến ({DEPOSIT_MONTHS} tháng giá thuê)</span>
                  <strong className="lp-num">
                    {formatVnd(current.monthlyPrice * DEPOSIT_MONTHS)}
                  </strong>
                </div>
              </div>
            )}
            <p className="text-sm text-[var(--lp-muted)]">
              Ước tính theo bảng giá hiện tại, không phải báo giá.
            </p>
            <button
              type="button"
              className="lp-btn lp-btn-primary w-full"
              onClick={() => navigate("/rental-requests/new")}
            >
              Gửi yêu cầu thuê kho <ArrowRight size={16} />
            </button>
          </div>
        </div>
      </section>

      <section id="terms" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(48px,6vw,76px)]">
        <div className={CONTAINER}>
          <SectionHeading title="Điều khoản" />
          <div className="grid gap-2">
            {RULES.map((rule) => (
              <div
                key={rule.big}
                className={`grid grid-cols-[160px_minmax(0,1fr)] items-baseline gap-5 p-4 max-[600px]:grid-cols-1 max-[600px]:gap-1 ${BLOCK}`}
              >
                <strong className="lp-num text-lg">{rule.big}</strong>
                <span className="text-[0.9375rem] text-[var(--lp-text-2)]">{rule.text}</span>
              </div>
            ))}
          </div>
          <p className="mt-3 text-sm text-[var(--lp-muted)]">
            Thuế, phí khác và điều kiện hoàn cọc: chưa công bố.
          </p>
        </div>
      </section>

      <section
        id="how-it-works"
        className="scroll-mt-24 bg-[var(--lp-bg-alt)] py-[clamp(48px,6vw,76px)]"
      >
        <div className={CONTAINER}>
          <SectionHeading title="Quy trình thuê kho" />
          <ol className="grid list-none gap-2 p-0">
            {STEPS.map((step) => (
              <li
                key={step.n}
                className={`grid grid-cols-[48px_minmax(0,1.1fr)_minmax(0,2fr)_minmax(0,1.2fr)] items-baseline gap-5 p-4 max-[900px]:grid-cols-[48px_minmax(0,1fr)] max-[900px]:gap-y-1 ${BLOCK}`}
              >
                <span className="lp-num text-lg text-[var(--lp-teal)]">{step.n}</span>
                <div>
                  <strong>{step.title}</strong>
                  <span className="block text-sm text-[var(--lp-muted)]">{step.who}</span>
                </div>
                <span className="text-[0.9375rem] text-[var(--lp-text-2)] max-[900px]:col-start-2">
                  {step.detail}
                </span>
                <span className="lp-num text-sm max-[900px]:col-start-2">{step.limit}</span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section id="locations" className="scroll-mt-24 bg-[var(--lp-bg)] py-[clamp(48px,6vw,76px)]">
        <div className={CONTAINER}>
          <SectionHeading title="Cơ sở" />
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-2">
            {facilities.map((facility) => (
              <div key={facility.id} className={`flex min-w-0 flex-col gap-2.5 p-5 ${BLOCK}`}>
                <h3 className="text-xl">
                  {facility.code} · {facility.name}
                </h3>
                <p className="text-[0.9375rem] text-[var(--lp-text-2)]">{facility.address}</p>
                <p className="text-[0.9375rem]">
                  Giờ tiếp nhận: <strong>{facility.operatingHours}</strong>
                </p>
                <p className="flex flex-wrap items-center gap-1.5 text-[0.9375rem]">
                  Loại khoang:
                  <SiteChips codes={(typesBySite[facility.id] ?? []).map((unit) => unit.name)} />
                </p>
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

      <section className="bg-[var(--dark)] py-[clamp(40px,5vw,60px)] text-[var(--on-dark-ink)]">
        <div className={`${CONTAINER} flex flex-wrap items-center justify-between gap-5`}>
          <h2 className="max-w-[34ch] text-[clamp(1.5rem,3vw,2rem)] leading-[1.15] tracking-[-0.02em] text-[var(--on-dark-ink)]">
            Chưa chắc chọn cỡ nào? Gửi yêu cầu, quản lý cơ sở sẽ đề xuất khoang phù hợp.
          </h2>
          <button
            type="button"
            className="lp-btn lp-btn-primary"
            onClick={() => navigate("/rental-requests/new")}
          >
            Gửi yêu cầu thuê kho <ArrowRight size={16} />
          </button>
        </div>
      </section>
    </div>
  );
}
