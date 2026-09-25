import { ArrowRight, CalendarDays, MapPin, ReceiptText } from "lucide-react";
import { useMemo, useState } from "react";
import type { Navigate } from "../app/types";
import { rentedStorage, StorageStatus } from "../mocks/customer";

const statusLabel: Record<StorageStatus, string> = {
  active: "Đang thuê",
  expiring: "Sắp hết hạn",
  overdue: "Đã quá hạn",
};

const STATUS_BADGE: Record<StorageStatus, string> = {
  active: "bg-[rgba(168,207,154,0.18)] text-success",
  expiring: "bg-[rgba(230,238,201,0.18)] text-warning",
  overdue: "bg-[rgba(255,155,115,0.18)] text-danger",
};

const PRIMARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-transparent bg-brand px-[18px] text-[14px] font-[750] text-background transition-colors duration-[180ms] ease hover:bg-brand-strong";
const SECONDARY_BUTTON =
  "inline-flex min-h-11 items-center justify-center gap-2.5 rounded-sm border border-brand bg-transparent px-[18px] text-[14px] font-[750] text-brand transition-colors duration-[180ms] ease hover:bg-brand hover:text-background";
const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink max-[760px]:w-full";

export function MyStoragePage({ navigate }: { navigate: Navigate }) {
  const [facility, setFacility] = useState("all");
  const [sort, setSort] = useState("nearest");
  const facilities = useMemo(
    () => [...new Set(rentedStorage.map((storage) => storage.facility))],
    [],
  );
  const filteredStorage = useMemo(() => {
    const result = rentedStorage.filter(
      (storage) => facility === "all" || storage.facility === facility,
    );
    return [...result].sort((left, right) => {
      const leftDate = Date.parse(left.endDate.split("/").reverse().join("-"));
      const rightDate = Date.parse(right.endDate.split("/").reverse().join("-"));
      return sort === "nearest" ? leftDate - rightDate : rightDate - leftDate;
    });
  }, [facility, sort]);

  return (
    <main>
      <section aria-label="Kho đang thuê">
        <div className="mb-[22px] flex items-center justify-between gap-6 max-[760px]:flex-col max-[760px]:items-stretch">
          <button
            className={`${PRIMARY_BUTTON} mr-auto max-[760px]:self-start`}
            onClick={() => navigate("/rental-requests/new")}
          >
            Thuê thêm kho <ArrowRight size={17} />
          </button>
          <div className="flex items-center gap-2.5 max-[760px]:flex-col max-[760px]:items-stretch">
            <label className="grid gap-[5px] font-mono text-[10px] font-medium uppercase leading-[1.4] text-muted">
              Chi nhánh
              <select
                className={FILTER_SELECT}
                value={facility}
                onChange={(event) => setFacility(event.target.value)}
              >
                <option value="all">Tất cả chi nhánh</option>
                {facilities.map((item) => (
                  <option key={item} value={item}>
                    {item.replace("Kho Mộc — ", "")}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-[5px] font-mono text-[10px] font-medium uppercase leading-[1.4] text-muted">
              Sắp xếp
              <select
                className={FILTER_SELECT}
                value={sort}
                onChange={(event) => setSort(event.target.value)}
              >
                <option value="nearest">Sắp hết hạn trước</option>
                <option value="farthest">Hạn xa nhất trước</option>
              </select>
            </label>
          </div>
        </div>
        <div className="grid gap-3.5">
          {filteredStorage.map((storage) => (
            <StorageCard key={storage.id} storage={storage} navigate={navigate} />
          ))}
        </div>
      </section>
    </main>
  );
}

function StorageCard({
  storage,
  navigate,
}: {
  storage: (typeof rentedStorage)[number];
  navigate: Navigate;
}) {
  return (
    <article className="grid grid-cols-[minmax(0,1fr)_290px] overflow-hidden border border-border bg-surface max-[760px]:grid-cols-1">
      <div className="p-6">
        <div className="flex items-center justify-between gap-5">
          <div>
            <p className="m-0 flex items-center gap-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
              Khoang {storage.unitCode}
            </p>
            <h3 className="mb-0 mt-1 text-[18px] tracking-[-0.02em]">{storage.facility}</h3>
          </div>
          <span
            className={`whitespace-nowrap rounded-full px-[9px] py-[6px] text-[11px] font-extrabold ${STATUS_BADGE[storage.status]}`}
          >
            {statusLabel[storage.status]}
          </span>
        </div>
        <p className="mb-6 mt-3.5 flex items-center gap-[7px] text-[13px] text-muted">
          <MapPin size={16} /> {storage.district}
        </p>
        <div className="flex items-center gap-10 max-[760px]:flex-wrap max-[760px]:gap-[18px_30px]">
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] font-medium uppercase text-muted">
              Diện tích
            </span>
            <strong className="text-[13px]">{storage.size}</strong>
          </div>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] font-medium uppercase text-muted">
              Thời hạn đến
            </span>
            <strong className="text-[13px]">{storage.endDate}</strong>
          </div>
          <div className="grid gap-[3px]">
            <span className="font-mono text-[10px] font-medium uppercase text-muted">Giá thuê</span>
            <strong className="text-[13px]">{storage.monthlyPrice}</strong>
          </div>
        </div>
      </div>
      <div className="flex flex-col justify-between gap-7 border-l border-border bg-surface-subtle p-6 shadow-[inset_1px_0_var(--border)] max-[760px]:gap-5 max-[760px]:border-l-0 max-[760px]:border-t max-[760px]:border-border">
        <div className="flex items-start gap-2 text-[12px] leading-[1.4] text-muted">
          <ReceiptText size={16} />
          <span>{storage.paymentLabel}</span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            className={SECONDARY_BUTTON}
            onClick={() => navigate(`/my-storage/${storage.id}`)}
          >
            Xem chi tiết <ArrowRight size={16} />
          </button>
          <button
            className="border-0 bg-transparent px-0 py-2 text-[12px] text-muted hover:text-brand"
            onClick={() => navigate("/appointments")}
          >
            <CalendarDays size={16} /> Lịch hẹn
          </button>
        </div>
      </div>
    </article>
  );
}
