import { useMemo, useState } from "react";
import { ArrowUpRight, BarChart3, CalendarRange, TrendingUp } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./RevenueDashboard.css";

type TimeRange = "30d" | "90d" | "6m" | "12m";
type RevenuePoint = {
  label: string;
  value: number;
};

const revenueSeries: Record<TimeRange, RevenuePoint[]> = {
  "30d": [
    { label: "W1", value: 44 },
    { label: "W2", value: 58 },
    { label: "W3", value: 70 },
    { label: "W4", value: 64 },
  ],
  "90d": [
    { label: "Jan", value: 52 },
    { label: "Feb", value: 68 },
    { label: "Mar", value: 78 },
    { label: "Apr", value: 90 },
    { label: "May", value: 84 },
    { label: "Jun", value: 100 },
  ],
  "6m": [
    { label: "Jan", value: 50 },
    { label: "Feb", value: 60 },
    { label: "Mar", value: 66 },
    { label: "Apr", value: 72 },
    { label: "May", value: 81 },
    { label: "Jun", value: 94 },
  ],
  "12m": [
    { label: "Jan", value: 40 },
    { label: "Feb", value: 48 },
    { label: "Mar", value: 52 },
    { label: "Apr", value: 65 },
    { label: "May", value: 71 },
    { label: "Jun", value: 75 },
    { label: "Jul", value: 82 },
    { label: "Aug", value: 86 },
    { label: "Sep", value: 92 },
    { label: "Oct", value: 99 },
    { label: "Nov", value: 108 },
    { label: "Dec", value: 114 },
  ],
};

const facilityOptions = ["Tất cả", "Q7", "Q9", "Q12"];
const unitTypeOptions = ["Tất cả", "Kho nhỏ", "Kho trung", "Kho lớn"];
const customerOptions = ["Tất cả", "Khách hàng tổ chức", "Khách hàng cá nhân"];

export function RevenueDashboard({ navigate }: { navigate: Navigate }) {
  const [timeRange, setTimeRange] = useState<TimeRange>("90d");
  const [facility, setFacility] = useState("Tất cả");
  const [unitType, setUnitType] = useState("Tất cả");
  const [customer, setCustomer] = useState("Tất cả");
  const [compareMode, setCompareMode] = useState(true);

  const chartData = revenueSeries[timeRange];
  const totalRevenue = useMemo(
    () => chartData.reduce((sum, point) => sum + point.value, 0),
    [chartData],
  );

  const averageRevenue = Math.round(totalRevenue / chartData.length);
  const growth = 18.4;

  return (
    <main className="revenue-page" style={{ position: "relative" }}>
      <section className="revenue-hero">
        <div className="container revenue-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Revenue <span>dashboard.</span>
            </h1>
            <p>
              Theo dõi xu hướng doanh thu, so sánh theo chi nhánh, loại kho và nhóm khách hàng theo thời gian thực.
            </p>
          </div>
        </div>
      </section>

      <section className="container revenue-content">
        <div className="revenue-toolbar">
          <div className="revenue-toolbar__group">
            <label className="filter-field">
              <span>Time range</span>
              <select value={timeRange} onChange={(event) => setTimeRange(event.target.value as TimeRange)}>
                <option value="30d">30 days</option>
                <option value="90d">90 days</option>
                <option value="6m">6 months</option>
                <option value="12m">12 months</option>
              </select>
            </label>

            <label className="filter-field">
              <span>Facility</span>
              <select value={facility} onChange={(event) => setFacility(event.target.value)}>
                {facilityOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <label className="filter-field">
              <span>Unit type</span>
              <select value={unitType} onChange={(event) => setUnitType(event.target.value)}>
                {unitTypeOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <label className="filter-field">
              <span>Customer</span>
              <select value={customer} onChange={(event) => setCustomer(event.target.value)}>
                {customerOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <button
            className="button button-secondary"
            type="button"
            onClick={() => setCompareMode((current) => !current)}
          >
            {compareMode ? "Tắt so sánh" : "Bật so sánh"}
          </button>
        </div>

        <div className="revenue-cards">
          <div className="revenue-card">
            <div className="revenue-card__meta">
              <BarChart3 size={18} />
              <span>Total revenue</span>
            </div>
            <strong>{totalRevenue.toLocaleString("vi-VN")}M</strong>
            <small>VND</small>
          </div>

          <div className="revenue-card">
            <div className="revenue-card__meta">
              <TrendingUp size={18} />
              <span>Average</span>
            </div>
            <strong>{averageRevenue.toLocaleString("vi-VN")}M</strong>
            <small>VND / period</small>
          </div>

          <div className="revenue-card">
            <div className="revenue-card__meta">
              <ArrowUpRight size={18} />
              <span>Growth</span>
            </div>
            <strong>{growth.toFixed(1)}%</strong>
            <small>vs previous period</small>
          </div>
        </div>

        <div className="revenue-grid">
          <div className="revenue-panel">
            <div className="revenue-panel__header">
              <div className="revenue-panel__title">Revenue trend</div>
              <div className="revenue-panel__badge">
                <CalendarRange size={14} />
                {timeRange}
              </div>
            </div>

            <div className="chart" aria-label="Revenue chart">
              {chartData.map((point) => (
                <div key={point.label} className="chart-column">
                  <span className="chart-column__value">{point.value}M</span>
                  <div className="chart-column__bar" style={{ height: `${point.value}%` }} />
                  <span className="chart-column__label">{point.label}</span>
                </div>
              ))}
            </div>
          </div>

          <aside className="revenue-panel">
            <div className="revenue-panel__header">
              <div className="revenue-panel__title">Comparison</div>
              <div className="revenue-panel__badge accent">{compareMode ? "On" : "Off"}</div>
            </div>

            <div className="comparison-list">
              <div className="comparison-item">
                <span>Q7</span>
                <strong>42.6M</strong>
              </div>
              <div className="comparison-item">
                <span>Q9</span>
                <strong>39.8M</strong>
              </div>
              <div className="comparison-item">
                <span>Q12</span>
                <strong>46.3M</strong>
              </div>
            </div>

            <div className="revenue-note">
              Dữ liệu đang được tính toán theo bộ lọc đã chọn. Vùng này có thể bổ sung biểu đồ tròn hoặc bảng drill-down khi BE xác nhận schema chi tiết.
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
