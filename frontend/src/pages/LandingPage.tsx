import { useState } from "react";
import {
  ArrowRight,
  Box,
  Check,
  ChevronDown,
  ChevronRight,
  Clock3,
  MapPin,
  Search,
} from "lucide-react";
import { Navigate } from "../app/types";
import { facilities, formatPrice, unitTypes } from "../mocks/catalog";

const CONTAINER = "mx-auto w-[min(1200px,calc(100%-48px))] max-[760px]:w-[min(100%-32px,600px)]";
const THEME_TRANSITION =
  "transition-[background-color,color,border-color,box-shadow] duration-[280ms] ease";
const LANDING_BUTTON =
  "inline-flex min-h-[45px] items-center justify-center gap-[9px] rounded-sm border border-transparent px-[17px] text-[13px] font-[750] transition-colors duration-[180ms] ease";
const PRIMARY_BUTTON = `${LANDING_BUTTON} bg-brand text-background hover:bg-brand-strong hover:text-background`;
const SECONDARY_BUTTON = `${LANDING_BUTTON} border-border bg-transparent text-brand hover:border-brand hover:bg-brand-soft hover:text-ink`;
const TEXT_LINK =
  "inline-flex items-center gap-2 border-0 bg-transparent p-0 text-[13px] font-[750] text-brand hover:text-ink";
const ROW_LINK = `${TEXT_LINK} whitespace-nowrap text-[11px] max-[760px]:col-start-3 max-[760px]:row-[1/span_2]`;
const SECTION_TITLE = "m-0 max-w-[15ch] text-ink text-title";
const LIST_HEADING =
  "flex items-center justify-between gap-3.5 font-mono text-mono uppercase tracking-[0.05em] text-muted";

function SectionHeading({ title, copy }: { title: string; copy: string }) {
  return (
    <div className="mb-12 grid grid-cols-[minmax(0,1fr)_minmax(260px,340px)] items-end gap-[50px] max-[900px]:gap-10 max-[760px]:block">
      <div>
        <h2 className={SECTION_TITLE}>{title}</h2>
      </div>
      <p className="m-0 mb-[3px] text-[13px] leading-[1.65] text-muted max-[760px]:mt-[22px] max-[760px]:max-w-[45ch]">
        {copy}
      </p>
    </div>
  );
}

export function LandingPage({ navigate }: { navigate: Navigate }) {
  const [facilityId, setFacilityId] = useState("");
  const [typeId, setTypeId] = useState("");
  const [selectedFacilityId, setSelectedFacilityId] = useState(facilities[0]?.id ?? "");
  const selectedFacility =
    facilities.find((item) => item.id === selectedFacilityId) ?? facilities[0];
  const selectedUnits = selectedFacility
    ? unitTypes.filter((unit) => selectedFacility.availableUnitIds.includes(unit.id))
    : [];

  function browseFromFinder(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (facilityId) params.set("facility", facilityId);
    if (typeId) params.set("type", typeId);
    const query = params.toString();
    navigate(`/units${query ? `?${query}` : ""}`);
  }

  return (
    <>
      <section className={`border-b border-border bg-background ${THEME_TRANSITION}`}>
        <div
          className={`${CONTAINER} grid grid-cols-[minmax(0,1fr)_minmax(360px,420px)] items-center gap-[clamp(48px,8vw,120px)] py-24 pb-20 max-[900px]:grid-cols-[minmax(0,1fr)_minmax(330px,390px)] max-[900px]:gap-[42px] max-[760px]:block max-[760px]:py-[72px] max-[760px]:pb-[58px]`}
        >
          <div className="max-w-[620px]">
            <h1 className="m-0 max-w-[10ch] text-ink text-display max-[760px]:text-[clamp(3.1rem,14vw,5rem)]">
              Tìm đúng chỗ chứa
              <span className="block text-brand">cho nhịp hàng.</span>
            </h1>
            <p className="mt-7 max-w-[47ch] text-[16px] leading-[1.65] text-muted">
              Chọn điểm kho và quy mô để xem phương án phù hợp trước khi gửi nhu cầu. Không cần đăng
              nhập để bắt đầu.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-6">
              <a className={TEXT_LINK} href="#how-it-works">
                Xem quy trình <ArrowRight size={16} />
              </a>
            </div>
          </div>

          <form
            className={`rounded-md border border-border bg-surface p-7 shadow-[0_24px_60px_rgba(0,0,0,0.22)] max-[760px]:mt-12 ${THEME_TRANSITION}`}
            onSubmit={browseFromFinder}
          >
            <div className="flex items-center gap-[13px]">
              <div
                className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-brand-soft text-brand"
                aria-hidden="true"
              >
                <Search size={17} />
              </div>
              <div>
                <p className="m-0 mb-1 font-mono text-mono uppercase tracking-[0.05em] text-brand">
                  Bắt đầu từ điều bạn biết
                </p>
                <h2 className="m-0 text-[25px] leading-[1.1] tracking-[-0.035em] text-ink">
                  Tìm phương án kho
                </h2>
              </div>
            </div>
            <p className="mb-6 mt-5 text-[13px] leading-[1.55] text-muted">
              Chọn một hoặc cả hai tiêu chí. Bạn có thể xem toàn bộ lựa chọn sau đó.
            </p>

            <label
              className="mb-4 block text-[12px] font-[750] text-ink"
              htmlFor="landing-facility"
            >
              <span>Điểm kho</span>
              <div className="mt-[7px] flex min-h-12 items-center gap-2.5 rounded-sm border border-border bg-background px-[13px] text-muted focus-within:border-brand focus-within:shadow-[0_0_0_3px_var(--brand-soft)] [&>svg:last-child]:pointer-events-none [&>svg:last-child]:shrink-0">
                <MapPin size={16} aria-hidden="true" />
                <select
                  className="w-full min-w-0 appearance-none border-0 bg-transparent text-[13px] text-ink outline-0 invalid:text-muted"
                  id="landing-facility"
                  value={facilityId}
                  onChange={(event) => setFacilityId(event.target.value)}
                >
                  <option value="">Tất cả điểm kho</option>
                  {facilities.map((facility) => (
                    <option key={facility.id} value={facility.id}>
                      {facility.district}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} aria-hidden="true" />
              </div>
            </label>

            <label
              className="mb-4 block text-[12px] font-[750] text-ink"
              htmlFor="landing-unit-type"
            >
              <span>Quy mô lưu trữ</span>
              <div className="mt-[7px] flex min-h-12 items-center gap-2.5 rounded-sm border border-border bg-background px-[13px] text-muted focus-within:border-brand focus-within:shadow-[0_0_0_3px_var(--brand-soft)] [&>svg:last-child]:pointer-events-none [&>svg:last-child]:shrink-0">
                <Box size={16} aria-hidden="true" />
                <select
                  className="w-full min-w-0 appearance-none border-0 bg-transparent text-[13px] text-ink outline-0 invalid:text-muted"
                  id="landing-unit-type"
                  value={typeId}
                  onChange={(event) => setTypeId(event.target.value)}
                >
                  <option value="">Tất cả quy mô</option>
                  {unitTypes.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unit.capacity} · {unit.name}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} aria-hidden="true" />
              </div>
            </label>

            <button className={`${PRIMARY_BUTTON} mt-1 w-full`} type="submit">
              Xem phương án kho <ArrowRight size={16} />
            </button>
            <p className="mt-[15px] flex items-center justify-center gap-1.5 text-[11px] text-muted [&>svg]:text-success">
              <Check size={14} aria-hidden="true" /> Xem trước không cần đăng nhập
            </p>
          </form>
        </div>

        <div
          className={`${CONTAINER} grid grid-cols-3 border-t border-border max-[760px]:grid-cols-1`}
          aria-label="Điều bạn có thể làm trên Kho Mộc"
        >
          {[
            { mark: "01", title: "Chọn trước", copy: "Điểm kho và quy mô" },
            { mark: "02", title: "So sánh rõ", copy: "Kích thước, giá tham khảo" },
            { mark: "03", title: "Gửi khi sẵn sàng", copy: "Không cần cam kết ngay" },
          ].map((item) => (
            <div
              key={item.mark}
              className={`flex min-h-[88px] items-center gap-3 border-r border-border px-6 py-4 first:pl-0 last:border-r-0 last:pr-0 max-[760px]:min-h-[63px] max-[760px]:border-r-0 max-[760px]:border-b max-[760px]:px-0 max-[760px]:py-[14px] max-[760px]:last:border-b-0 ${THEME_TRANSITION}`}
            >
              <span className="font-mono text-mono tracking-[0.04em] text-brand" aria-hidden="true">
                {item.mark}
              </span>
              <span className="grid gap-0.5">
                <strong className="text-[12px] text-ink">{item.title}</strong>
                <small className="text-[11px] text-muted">{item.copy}</small>
              </span>
            </div>
          ))}
        </div>
      </section>

      <section
        className={`border-b border-border bg-surface py-28 max-[760px]:py-[76px] ${THEME_TRANSITION}`}
        id="support"
      >
        <div className={CONTAINER}>
          <SectionHeading
            title="Xem nơi hàng đến, rồi chọn quy mô."
            copy="Thông tin địa chỉ, giờ tiếp nhận và các phương án mẫu được đặt cạnh nhau để bạn kiểm tra nhanh."
          />

          <div
            className={`grid grid-cols-[minmax(250px,0.7fr)_minmax(0,1.3fr)] border border-border bg-background max-[760px]:block ${THEME_TRANSITION}`}
          >
            <div
              className="border-r border-border py-5 max-[760px]:border-b max-[760px]:border-r-0 max-[760px]:border-border"
              aria-label="Chọn điểm kho"
            >
              <div className={`${LIST_HEADING} px-[22px] pb-3.5`}>
                <span>Điểm kho</span>
                <span>{String(facilities.length).padStart(2, "0")}</span>
              </div>
              {facilities.map((facility, index) => {
                const isSelected = facility.id === selectedFacility?.id;
                return (
                  <button
                    key={facility.id}
                    className={`group grid w-full grid-cols-[25px_minmax(0,1fr)_16px] items-center gap-3 border-0 border-t border-border px-[22px] py-[18px] text-left transition-[color,background-color] duration-[180ms] ease hover:bg-surface-subtle hover:text-ink ${
                      isSelected ? "bg-surface-subtle text-ink" : "bg-transparent text-muted"
                    }`}
                    type="button"
                    aria-pressed={isSelected}
                    onClick={() => setSelectedFacilityId(facility.id)}
                  >
                    <span
                      className={`font-mono text-mono group-hover:text-brand ${
                        isSelected ? "text-brand" : "text-muted"
                      }`}
                    >
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <span className="grid min-w-0 gap-[3px]">
                      <strong className="truncate text-[13px] font-bold text-current">
                        {facility.district}
                      </strong>
                      <small className="truncate text-[11px] text-muted">{facility.address}</small>
                    </span>
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                );
              })}
            </div>

            {selectedFacility && (
              <article
                className="min-w-0 p-[31px] max-[760px]:p-[25px_20px_22px]"
                aria-live="polite"
              >
                <div className="flex items-start justify-between gap-[18px]">
                  <div>
                    <span className="font-mono text-mono uppercase tracking-[0.05em] text-brand">
                      Đang xem
                    </span>
                    <h3 className="m-0 max-w-[17ch] text-[clamp(1.6rem,3vw,2.35rem)] leading-[1.08] tracking-[-0.04em] text-ink">
                      {selectedFacility.name}
                    </h3>
                  </div>
                  <span className="shrink-0 rounded-full border border-[rgba(140,221,166,0.25)] px-2 py-[5px] font-mono text-[9px] tracking-[0.03em] text-success">
                    Đang hoạt động
                  </span>
                </div>
                <p className="mt-4 max-w-[48ch] text-[13px] leading-[1.6] text-muted">
                  {selectedFacility.note}
                </p>
                <div className="mt-[21px] flex flex-wrap gap-x-5 gap-y-2 text-[11px] text-ink [&>span]:inline-flex [&>span]:items-center [&>span]:gap-[7px] [&>svg]:text-brand">
                  <span>
                    <MapPin size={15} /> {selectedFacility.address}
                  </span>
                  <span>
                    <Clock3 size={15} /> {selectedFacility.hours.replace("Tiếp nhận hàng: ", "")}
                  </span>
                </div>

                <div className="mt-[33px] border-t border-border">
                  <div className={`${LIST_HEADING} px-0 py-3.5`}>
                    <span>Quy mô tham khảo</span>
                    <span>{String(selectedUnits.length).padStart(2, "0")}</span>
                  </div>
                  {selectedUnits.map((unit) => (
                    <div
                      className="grid grid-cols-[26px_minmax(0,1fr)_auto_auto] items-center gap-3 border-t border-border py-4 max-[760px]:grid-cols-[26px_minmax(0,1fr)_auto]"
                      key={unit.id}
                    >
                      <div
                        className="grid h-[26px] w-[26px] place-items-center rounded-sm bg-brand-soft text-brand"
                        aria-hidden="true"
                      >
                        <Box size={17} />
                      </div>
                      <div className="grid min-w-0 gap-[3px]">
                        <strong className="text-[13px] text-ink">{unit.name}</strong>
                        <span className="text-[11px] text-muted">
                          {unit.capacity} · {unit.size}
                        </span>
                      </div>
                      <div className="grid justify-items-end gap-0.5 max-[760px]:col-start-2 max-[760px]:justify-items-start">
                        <strong className="text-[12px] text-brand">
                          {formatPrice(unit.monthlyPrice)}đ
                        </strong>
                        <span className="text-[11px] text-muted">/ tháng</span>
                      </div>
                      <button
                        className={ROW_LINK}
                        type="button"
                        onClick={() =>
                          navigate(`/units?facility=${selectedFacility.id}&type=${unit.id}`)
                        }
                      >
                        Chi tiết <ArrowRight size={14} />
                      </button>
                    </div>
                  ))}
                </div>

                <div className="mt-[22px] flex justify-end">
                  <button
                    className={SECONDARY_BUTTON}
                    type="button"
                    onClick={() => navigate(`/units?facility=${selectedFacility.id}`)}
                  >
                    Xem tất cả phương án <ArrowRight size={16} />
                  </button>
                </div>
              </article>
            )}
          </div>
        </div>
      </section>

      <section
        className={`border-b border-border bg-background py-28 max-[760px]:py-[76px] ${THEME_TRANSITION}`}
        id="how-it-works"
      >
        <div
          className={`${CONTAINER} grid grid-cols-[minmax(260px,0.75fr)_minmax(0,1.25fr)] gap-[clamp(50px,10vw,140px)] max-[900px]:gap-10 max-[760px]:block`}
        >
          <div>
            <h2 className={`${SECTION_TITLE} max-w-[10ch]`}>Từ lựa chọn đến nhu cầu rõ ràng.</h2>
            <p className="mt-6 max-w-[32ch] text-[13px] leading-[1.65] text-muted max-[760px]:mt-[22px]">
              Bạn luôn biết mình đang ở bước nào và cần chuẩn bị điều gì tiếp theo.
            </p>
          </div>
          <div className="border-t border-border">
            {[
              {
                number: "01",
                title: "Xác định nhu cầu",
                copy: "Chọn khu vực và quy mô lưu trữ gần với kế hoạch hàng hóa của bạn.",
              },
              {
                number: "02",
                title: "Gửi thông tin",
                copy: "Cho Kho Mộc biết lô hàng, thời gian và nhu cầu vận hành dự kiến.",
              },
              {
                number: "03",
                title: "Chốt phương án",
                copy: "Đội ngũ phản hồi, thống nhất lịch và các bước tiếp theo.",
              },
            ].map((step) => (
              <article
                key={step.number}
                className="grid grid-cols-[42px_minmax(0,1fr)] gap-3.5 border-b border-border py-[22px]"
              >
                <span className="pt-1 font-mono text-mono tracking-[0.04em] text-brand">
                  {step.number}
                </span>
                <div>
                  <h3 className="m-0 text-[17px] tracking-[-0.02em] text-ink">{step.title}</h3>
                  <p className="mt-[7px] max-w-[47ch] text-[13px] leading-[1.6] text-muted">
                    {step.copy}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className={`bg-surface-subtle py-[72px] ${THEME_TRANSITION}`}>
        <div className={`${CONTAINER} flex items-center justify-between gap-8 max-[760px]:block`}>
          <div>
            <h2 className="m-0 text-[clamp(2rem,4vw,3rem)] font-[750] leading-[1.03] tracking-[-0.045em] text-ink">
              Đã biết quy mô?
            </h2>
            <p className="mt-2 text-[13px] text-muted">Gửi nhu cầu lưu trữ khi bạn đã sẵn sàng.</p>
          </div>
          <button
            className={`${PRIMARY_BUTTON} shrink-0 max-[760px]:mt-[26px]`}
            type="button"
            onClick={() => navigate("/rental-requests/new")}
          >
            Gửi nhu cầu lưu trữ <ArrowRight size={16} />
          </button>
        </div>
      </section>
    </>
  );
}
