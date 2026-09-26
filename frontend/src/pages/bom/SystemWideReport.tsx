import { useMemo, useState } from "react";
import { BarChart3, Building2, CalendarRange } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./SystemWideReport.css";

type MetricKey = "revenue" | "occupancy" | "overdue" | "incidents";
type FacilitySummary = {
  code: string;
  name: string;
  revenue: number;
  occupancy: number;
  overdue: number;
  incidents: number;
};

const facilitySummaries: FacilitySummary[] = [
  { code: "Q7", name: "Kho Q7", revenue: 96, occupancy: 84, overdue: 11, incidents: 4 },
  { code: "Q9", name: "Kho Q9", revenue: 82, occupancy: 76, overdue: 16, incidents: 7 },
  { code: "Q12", name: "Kho Q12", revenue: 114, occupancy: 90, overdue: 9, incidents: 5 },
  { code: "Q15", name: "Kho Q15", revenue: 68, occupancy: 61, overdue: 22, incidents: 9 },
];

const ranges = ["30d", "90d", "6m"]; 

export function SystemWideReport({ navigate }: { navigate: Navigate }) {
  const [range, setRange] = useState("90d");
  const [metric, setMetric] = useState<MetricKey>("revenue");
  const [selectedFacilities, setSelectedFacilities] = useState<string[]>(["Q7", "Q9", "Q12"]);

  const metricLabel: Record<MetricKey, string> = {
    revenue: "Doanh thu",
    occupancy: "Tỷ lệ lấp đầy",
    overdue: "Ca quá hạn",
    incidents: "Sự cố",
  };

  const ranked = useMemo(() => {
    const numericMetricKey: keyof Pick<FacilitySummary, "revenue" | "occupancy" | "overdue" | "incidents"> = metric;

    const list = [...facilitySummaries].sort((a, b) => {
      return Number(b[numericMetricKey]) - Number(a[numericMetricKey]);
    });

    return list.filter((facility) => selectedFacilities.includes(facility.code));
  }, [metric, selectedFacilities]);

  const toggleFacility = (code: string) => {
    setSelectedFacilities((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
    );
  };

  const summary = useMemo(
    () =>
      ranked.reduce(
        (acc, facility) => {
          acc.revenue += facility.revenue;
          acc.occupancy += facility.occupancy;
          acc.overdue += facility.overdue;
          acc.incidents += facility.incidents;
          return acc;
        },
        { revenue: 0, occupancy: 0, overdue: 0, incidents: 0 },
      ),
    [ranked],
  );

  return (
    <main className="system-report-page" style={{ position: "relative" }}>
      <section className="system-report-hero">
        <div className="container system-report-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              System <span>wide report.</span>
            </h1>
            <p>
              So sánh hiệu suất giữa nhiều chi nhánh để BOM theo dõi doanh thu, tỷ lệ lấp đầy và mức độ vận hành toàn hệ thống.
            </p>
          </div>
        </div>
      </section>

      <section className="container system-report-content">
        <div className="system-report-toolbar">
          <div className="system-report-toolbar__group">
            <label className="filter-field">
              <span>Time range</span>
              <select value={range} onChange={(event) => setRange(event.target.value)}>
                {ranges.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>

            <label className="filter-field">
              <span>Metric</span>
              <select value={metric} onChange={(event) => setMetric(event.target.value as MetricKey)}>
                <option value="revenue">Doanh thu</option>
                <option value="occupancy">Lấp đầy</option>
                <option value="overdue">Quá hạn</option>
                <option value="incidents">Sự cố</option>
              </select>
            </label>
          </div>

          <button className="button button-secondary" type="button" onClick={() => navigate("/bom/revenue")}>
            <BarChart3 size={16} /> Revenue
          </button>
        </div>

        <div className="system-report-filters">
          {facilitySummaries.map((facility) => (
            <label key={facility.code} className="facility-toggle">
              <input
                type="checkbox"
                checked={selectedFacilities.includes(facility.code)}
                onChange={() => toggleFacility(facility.code)}
              />
              <span>{facility.code}</span>
            </label>
          ))}
        </div>

        <div className="system-report-cards">
          <div className="report-card">
            <div className="report-card__meta">
              <Building2 size={18} />
              <span>Selected</span>
            </div>
            <strong>{ranked.length}</strong>
            <small>facilities</small>
          </div>

          <div className="report-card">
            <div className="report-card__meta">
              <CalendarRange size={18} />
              <span>Metric</span>
            </div>
            <strong>{metricLabel[metric]}</strong>
            <small>current view</small>
          </div>

          <div className="report-card">
            <div className="report-card__meta">
              <BarChart3 size={18} />
              <span>Summary</span>
            </div>
            <strong>
              {metric === "revenue"
                ? `${summary.revenue}M`
                : metric === "occupancy"
                  ? `${summary.occupancy}%`
                  : metric === "overdue"
                    ? `${summary.overdue} ca`
                    : `${summary.incidents} vụ`}
            </strong>
            <small>{range}</small>
          </div>
        </div>

        <div className="system-report-table-wrap">
          <table className="system-report-table">
            <thead>
              <tr>
                <th>Facility</th>
                <th>Revenue</th>
                <th>Occupancy</th>
                <th>Overdue</th>
                <th>Incidents</th>
              </tr>
            </thead>
            <tbody>
              {ranked.map((facility) => (
                <tr key={facility.code}>
                  <td>{facility.name}</td>
                  <td>{facility.revenue}M</td>
                  <td>{facility.occupancy}%</td>
                  <td>{facility.overdue}</td>
                  <td>{facility.incidents}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
