import { useState } from "react";
import { ArrowRight, ChevronDown, Filter, MapPin } from "lucide-react";
import { Navigate } from "../app/types";
import { PAGE_CONTAINER } from "../app/layout";
import { facilities, formatPrice, UnitType, unitTypes } from "../mocks/catalog";
import { UnitCard } from "../components/domain/UnitCard";
import { UnitDetailsDialog } from "../components/domain/UnitDetailsDialog";

const EYEBROW = "m-0 mb-[18px] font-mono text-mono uppercase tracking-[0.08em] text-brand";
const PRIMARY_BUTTON =
  "inline-flex min-h-11 w-full items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] py-0 text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] py-0 text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background";
const COMPACT_SELECT =
  "relative flex min-h-11 min-w-[190px] items-center gap-[10px] rounded-sm border border-border bg-surface px-3 py-[10px] focus-within:border-brand focus-within:shadow-[0_0_0_3px_var(--brand-soft)] max-[760px]:min-w-0";
const COMPACT_SELECT_ELEMENT =
  "w-full min-w-0 appearance-none border-0 bg-transparent pr-[15px] text-[12px] font-bold text-ink outline-0 invalid:text-muted";

export function BrowsePage({ navigate }: { navigate: Navigate }) {
  const params = new URLSearchParams(window.location.search);
  const [facilityFilter, setFacilityFilter] = useState(params.get("facility") ?? "");
  const [typeFilter, setTypeFilter] = useState(params.get("type") ?? "");
  const [selectedUnit, setSelectedUnit] = useState<UnitType | null>(null);
  const filteredTypes = typeFilter ? unitTypes.filter((item) => item.id === typeFilter) : unitTypes;
  const lowestPrice = filteredTypes.length
    ? Math.min(...filteredTypes.map((item) => item.monthlyPrice))
    : 0;

  return (
    <>
      <section className="border-b border-border bg-background pb-[52px] pt-[62px] text-ink min-[761px]:pb-[68px] min-[761px]:pt-[78px]">
        <div className={PAGE_CONTAINER}>
          <div>
            <h1 className="m-0 text-[clamp(42px,6vw,66px)] font-bold leading-[1.03] tracking-[-0.045em] text-ink">
              Chọn phương án
              <br />
              <span className="text-accent">cho hàng hóa.</span>
            </h1>
            <p className="m-0 mt-6 max-w-[450px] text-[14px] leading-[1.65] text-muted">
              So sánh quy mô, giá và điểm tiếp nhận.
            </p>
          </div>
        </div>
      </section>
      <section className="bg-background py-[clamp(80px,10vw,120px)]">
        <div className={PAGE_CONTAINER}>
          <div className="flex items-center justify-between gap-[25px] border-b border-border pb-5 max-[760px]:block">
            <div className="flex items-center gap-2 text-[12px] font-extrabold text-ink">
              <Filter size={17} className="text-accent" />
              <span>Lọc phương án</span>
            </div>
            <div className="flex gap-[10px] max-[760px]:mt-[15px] max-[760px]:grid max-[760px]:grid-cols-2">
              <label className={COMPACT_SELECT}>
                <span className="text-[10px] text-muted">Điểm kho</span>
                <select
                  className={COMPACT_SELECT_ELEMENT}
                  value={facilityFilter}
                  onChange={(event) => setFacilityFilter(event.target.value)}
                >
                  <option value="">Tất cả điểm kho</option>
                  {facilities.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.district}
                    </option>
                  ))}
                </select>
                <ChevronDown
                  size={15}
                  className="pointer-events-none absolute right-[10px] text-muted"
                />
              </label>
              <label className={COMPACT_SELECT}>
                <span className="text-[10px] text-muted">Quy mô</span>
                <select
                  className={COMPACT_SELECT_ELEMENT}
                  value={typeFilter}
                  onChange={(event) => setTypeFilter(event.target.value)}
                >
                  <option value="">Tất cả quy mô</option>
                  {unitTypes.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.capacity} · {item.name}
                    </option>
                  ))}
                </select>
                <ChevronDown
                  size={15}
                  className="pointer-events-none absolute right-[10px] text-muted"
                />
              </label>
            </div>
          </div>
          <div className="grid grid-cols-[minmax(0,1fr)_290px] gap-[35px] pt-[33px] max-[760px]:grid-cols-1">
            <div>
              <div className="mb-[17px] flex items-center justify-between gap-4">
                <p className="m-0 text-[12px] font-extrabold text-ink">
                  {filteredTypes.length} phương án kho
                </p>
                <span className="text-[11px] text-muted">
                  {lowestPrice
                    ? `Tham khảo từ ${formatPrice(lowestPrice)}đ/tháng`
                    : "Không có lựa chọn phù hợp"}
                </span>
              </div>
              {filteredTypes.length ? (
                <div className="grid grid-cols-2 gap-6 max-[760px]:grid-cols-1">
                  {filteredTypes.map((unit) => (
                    <UnitCard
                      key={unit.id}
                      unit={unit}
                      onDetails={() => setSelectedUnit(unit)}
                      navigate={navigate}
                    />
                  ))}
                </div>
              ) : (
                <div className="rounded-md border border-border bg-surface p-10">
                  <p className={EYEBROW}>Thử lại lựa chọn</p>
                  <h2 className="m-0 mb-5 text-[24px] font-bold text-ink">Chưa có quy mô này.</h2>
                  <button className={SECONDARY_BUTTON} onClick={() => setTypeFilter("")}>
                    Xem tất cả phương án <ArrowRight size={16} />
                  </button>
                </div>
              )}
            </div>
            <aside className="self-start rounded-md border border-border bg-surface-subtle p-[25px] max-[760px]:order-first">
              <p className={EYEBROW}>Điểm kho</p>
              <h2 className="m-0 mb-[15px] text-[30px] font-bold leading-[1.08] tracking-[-0.04em] text-ink">
                Chọn điểm kho.
              </h2>
              <p className="m-0 mb-[25px] text-[12px] leading-[1.6] text-muted">
                Địa chỉ và giờ tiếp nhận.
              </p>
              <div className="mb-6 grid gap-[18px]">
                {facilities
                  .filter((item) => !facilityFilter || item.id === facilityFilter)
                  .map((item) => (
                    <div className="flex items-start gap-[10px]" key={item.id}>
                      <span className="grid h-7 w-7 shrink-0 place-items-center rounded-sm bg-brand-soft text-brand">
                        <MapPin size={15} />
                      </span>
                      <div className="grid gap-[2px]">
                        <strong className="text-[11px] text-ink">{item.name}</strong>
                        <span className="text-[10px] text-muted">{item.address}</span>
                        <small className="text-[10px] text-brand">{item.hours}</small>
                      </div>
                    </div>
                  ))}
              </div>
              <button className={PRIMARY_BUTTON} onClick={() => navigate("/rental-requests/new")}>
                Gửi nhu cầu lưu trữ <ArrowRight size={16} />
              </button>
            </aside>
          </div>
        </div>
      </section>
      {selectedUnit && (
        <UnitDetailsDialog
          unit={selectedUnit}
          onClose={() => setSelectedUnit(null)}
          navigate={navigate}
        />
      )}
    </>
  );
}
