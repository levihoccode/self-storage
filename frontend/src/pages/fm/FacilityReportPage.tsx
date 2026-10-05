import { AlertTriangle, Loader2, Package, TrendingUp, Warehouse } from "lucide-react";
import { useEffect, useState } from "react";
import type { Navigate } from "../../app/types";
import { formatPrice, unitTypes } from "../../mocks/catalog";
import { FACILITY_OVERDUE_COUNT, fmUnits, type FmUnitStatus } from "../../mocks/fm";
import { DemoNotice } from "../../components/ui/DemoNotice";

type PeriodFilter = "7d" | "30d" | "quarter";

const PERIOD_LABEL: Record<PeriodFilter, string> = {
  "7d": "7 ngày qua",
  "30d": "30 ngày qua",
  quarter: "Quý này",
};

const PERIOD_MULTIPLIER: Record<PeriodFilter, number> = {
  "7d": 0.25,
  "30d": 1,
  quarter: 3,
};

const STATUS_LABEL: Record<FmUnitStatus, string> = {
  Available: "Trống",
  OnHold: "Đang giữ",
  Reserved: "Đã đặt trước",
  Rented: "Đang cho thuê",
  Maintenance: "Bảo trì",
};

const STATUS_BAR_COLOR: Record<FmUnitStatus, string> = {
  Available: "bg-success",
  OnHold: "bg-warning",
  Reserved: "bg-warning",
  Rented: "bg-info",
  Maintenance: "bg-muted",
};

const FILTER_SELECT =
  "min-w-[170px] rounded-sm border border-border bg-surface px-[11px] py-[10px] pr-[30px] text-[12px] font-semibold text-ink";

function unitMonthlyPrice(unitTypeId: string) {
  return unitTypes.find((type) => type.id === unitTypeId)?.monthlyPrice ?? 0;
}

export function FacilityReportPage({ navigate }: { navigate: Navigate }) {
  const [period, setPeriod] = useState<PeriodFilter>("30d");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [loading, setLoading] = useState(true);

  // Chỉ hẹn giờ tắt loading; việc bật loading nằm ở handler đổi filter để
  // tránh setState đồng bộ trong effect (react-hooks/set-state-in-effect).
  useEffect(() => {
    const timer = window.setTimeout(() => setLoading(false), 500);
    return () => window.clearTimeout(timer);
  }, [period, typeFilter]);

  const units = fmUnits.filter((unit) => typeFilter === "all" || unit.unitTypeId === typeFilter);
  const total = units.length;
  const rentedUnits = units.filter((unit) => unit.status === "Rented");
  const availableCount = units.filter((unit) => unit.status === "Available").length;
  const occupancyRate = total === 0 ? 0 : Math.round((rentedUnits.length / total) * 100);
  const estimatedRevenue = Math.round(
    rentedUnits.reduce((sum, unit) => sum + unitMonthlyPrice(unit.unitTypeId), 0) *
      PERIOD_MULTIPLIER[period],
  );

  const statusBreakdown = (
    ["Available", "OnHold", "Reserved", "Rented", "Maintenance"] as const
  ).map((status) => ({
    status,
    count: units.filter((unit) => unit.status === status).length,
  }));

  return (
    <main>
      <div className="mb-8">
        <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Báo cáo cơ sở</h1>
        <p className="mt-2 text-[13px] text-muted">
          Tình hình vận hành của cơ sở bạn phụ trách — không bao gồm số liệu cơ sở khác.
        </p>
      </div>

      <div className="mb-6">
        <DemoNotice tone="pending">
          Số liệu chỉ tính trong phạm vi cơ sở bạn quản lý — kiểm tra facility phải nằm ở tầng API,
          không chỉ ẩn ở giao diện, để tránh rủi ro xem được số liệu cơ sở khác.
        </DemoNotice>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          className={FILTER_SELECT}
          value={period}
          onChange={(event) => {
            setPeriod(event.target.value as PeriodFilter);
            setLoading(true);
          }}
        >
          {(Object.keys(PERIOD_LABEL) as PeriodFilter[]).map((value) => (
            <option key={value} value={value}>
              {PERIOD_LABEL[value]}
            </option>
          ))}
        </select>
        <select
          className={FILTER_SELECT}
          value={typeFilter}
          onChange={(event) => {
            setTypeFilter(event.target.value);
            setLoading(true);
          }}
        >
          <option value="all">Tất cả loại kho</option>
          {unitTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.name}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="flex min-h-[200px] items-center justify-center gap-2 text-[13px] text-muted">
          <Loader2 className="animate-[surface-state-spin_0.9s_linear_infinite]" size={18} />
          Đang tính số liệu…
        </div>
      ) : (
        <>
          <div className="mb-8 grid grid-cols-4 gap-4 max-[900px]:grid-cols-2 max-[520px]:grid-cols-1">
            <button
              className="border border-border bg-surface p-5 text-left transition-colors hover:border-brand"
              onClick={() => navigate("/fm/storage-units")}
            >
              <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
                <Warehouse size={13} /> Tỷ lệ lấp đầy
              </p>
              <p className="m-0 mt-2 text-[26px] font-bold text-ink">{occupancyRate}%</p>
              <p className="mb-0 mt-1 text-[12px] text-muted">
                {rentedUnits.length}/{total} khoang đang cho thuê
              </p>
            </button>

            <button
              className="border border-border bg-surface p-5 text-left transition-colors hover:border-brand"
              onClick={() => navigate("/fm/storage-units")}
            >
              <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
                <Package size={13} /> Khoang trống
              </p>
              <p className="m-0 mt-2 text-[26px] font-bold text-ink">{availableCount}</p>
              <p className="mb-0 mt-1 text-[12px] text-muted">Sẵn sàng cho thuê ngay</p>
            </button>

            <div className="border border-border bg-surface p-5">
              <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-brand">
                <TrendingUp size={13} /> Doanh thu ước tính
              </p>
              <p className="m-0 mt-2 text-[22px] font-bold text-ink">
                {formatPrice(estimatedRevenue)}đ
              </p>
              <p className="mb-0 mt-1 text-[12px] text-muted">Theo {PERIOD_LABEL[period]}</p>
            </div>

            <button
              className="border border-border bg-surface p-5 text-left transition-colors hover:border-brand"
              onClick={() => navigate("/fm/support-requests")}
            >
              <p className="m-0 flex items-center gap-2 font-mono text-[10px] font-bold uppercase tracking-[0.08em] text-danger">
                <AlertTriangle size={13} /> Ca quá hạn
              </p>
              <p className="m-0 mt-2 text-[26px] font-bold text-ink">{FACILITY_OVERDUE_COUNT}</p>
              <p className="mb-0 mt-1 text-[12px] text-muted">Số liệu mẫu, chờ Flow 6</p>
            </button>
          </div>

          <section className="border border-border bg-surface p-6">
            <h2 className="m-0 mb-4 text-[15px] font-bold text-ink">
              Phân bố khoang theo trạng thái
            </h2>
            <div className="grid gap-3">
              {statusBreakdown.map(({ status, count }) => (
                <div key={status} className="grid grid-cols-[120px_1fr_40px] items-center gap-3">
                  <span className="text-[12px] font-bold text-muted">{STATUS_LABEL[status]}</span>
                  <div className="h-2 overflow-hidden rounded-full bg-surface-subtle">
                    <div
                      className={`h-full ${STATUS_BAR_COLOR[status]}`}
                      style={{ width: total === 0 ? "0%" : `${(count / total) * 100}%` }}
                    />
                  </div>
                  <span className="text-right text-[12px] font-bold text-ink">{count}</span>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </main>
  );
}
