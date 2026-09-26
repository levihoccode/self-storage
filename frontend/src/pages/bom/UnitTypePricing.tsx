import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, PackagePlus, TrendingUp } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./UnitTypePricing.css";

type UnitTypeRecord = {
  id: string;
  name: string;
  width: number;
  depth: number;
  height: number;
  area: number;
  description: string;
  monthlyPrice: number;
  updatedBy: string;
  updatedAt: string;
};

const initialUnitTypes: UnitTypeRecord[] = [
  {
    id: "unit-101",
    name: "Small",
    width: 2.5,
    depth: 3,
    height: 2.6,
    area: 7.5,
    description: "Kho nhỏ, phù hợp lưu trữ tài liệu và đồ gia dụng cỡ vừa.",
    monthlyPrice: 4500000,
    updatedBy: "BOM - Lê Thị Lan",
    updatedAt: "2026-09-20",
  },
  {
    id: "unit-102",
    name: "Medium",
    width: 4,
    depth: 4,
    height: 3,
    area: 16,
    description: "Kho trung bình cho máy móc, đồ nội thất và hàng hóa lưu kho chuyên dụng.",
    monthlyPrice: 8200000,
    updatedBy: "BOM - Lê Thị Lan",
    updatedAt: "2026-09-18",
  },
  {
    id: "unit-103",
    name: "Large",
    width: 5,
    depth: 6,
    height: 3.5,
    area: 30,
    description: "Kho lớn, tối ưu cho hàng hóa quy mô lớn và công suất cao.",
    monthlyPrice: 14500000,
    updatedBy: "BOM - Trần Minh Khang",
    updatedAt: "2026-09-15",
  },
];

const emptyUnitType: UnitTypeRecord = {
  id: "",
  name: "",
  width: 0,
  depth: 0,
  height: 0,
  area: 0,
  description: "",
  monthlyPrice: 0,
  updatedBy: "BOM - System",
  updatedAt: new Date().toISOString().slice(0, 10),
};

export function UnitTypePricing({ navigate }: { navigate: Navigate }) {
  const [unitTypes, setUnitTypes] = useState<UnitTypeRecord[]>(initialUnitTypes);
  const [selectedId, setSelectedId] = useState(initialUnitTypes[0]?.id ?? "");
  const [draft, setDraft] = useState<UnitTypeRecord>(initialUnitTypes[0] ?? emptyUnitType);

  const selectedUnitType =
    unitTypes.find((unitType) => unitType.id === selectedId) ?? unitTypes[0] ?? null;

  useEffect(() => {
    if (selectedUnitType && selectedUnitType.id !== draft.id) {
      setDraft(selectedUnitType);
    }
  }, [selectedUnitType, draft.id]);

  const totalMonthlyValue = useMemo(
    () => unitTypes.reduce((sum, unitType) => sum + unitType.monthlyPrice, 0),
    [unitTypes],
  );

  function updateSelected(unitType: UnitTypeRecord) {
    setSelectedId(unitType.id);
    setDraft(unitType);
  }

  function createNewUnitType() {
    const newUnitType: UnitTypeRecord = {
      ...emptyUnitType,
      id: `unit-${Date.now()}`,
      name: "New Type",
      updatedBy: "BOM - System",
      updatedAt: new Date().toISOString().slice(0, 10),
    };
    setUnitTypes((current) => [newUnitType, ...current]);
    setSelectedId(newUnitType.id);
    setDraft(newUnitType);
  }

  function saveCurrent() {
    const nextUnitTypes = unitTypes.some((unitType) => unitType.id === draft.id)
      ? unitTypes.map((unitType) => (unitType.id === draft.id ? { ...draft, updatedAt: new Date().toISOString().slice(0, 10) } : unitType))
      : [draft, ...unitTypes];

    setUnitTypes(nextUnitTypes);
    setSelectedId(draft.id || `unit-${Date.now()}`);
  }

  const currentUnitType = draft;

  return (
    <main className="unit-type-page" style={{ position: "relative" }}>
      <section className="unit-type-hero">
        <div className="container unit-type-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Unit type <span>pricing.</span>
            </h1>
            <p>
              Định nghĩa loại kho và giá thuê áp dụng cho toàn hệ thống, đồng thời cảnh báo khi thay đổi giá ảnh hưởng tới hợp đồng đã ký.
            </p>
          </div>
        </div>
      </section>

      <section className="container unit-type-content">
        <div className="unit-type-summary">
          <div className="summary-card">
            <span>Unit types</span>
            <strong>{unitTypes.length}</strong>
          </div>
          <div className="summary-card">
            <span>Base price</span>
            <strong>{totalMonthlyValue.toLocaleString("vi-VN")}đ</strong>
          </div>
          <div className="summary-card">
            <span>Active</span>
            <strong>{unitTypes.length}</strong>
          </div>
        </div>

        <div className="unit-type-topbar">
          <button className="button button-primary" type="button" onClick={createNewUnitType}>
            <PackagePlus size={16} /> Tạo loại kho mới
          </button>
          <button className="button button-secondary" type="button" onClick={() => navigate("/bom/revenue")}>
            <TrendingUp size={16} /> Doanh thu
          </button>
        </div>

        <div className="unit-type-layout">
          <div className="unit-type-panel">
            <div className="unit-type-panel__header">
              <div className="unit-type-panel__title">Danh sách unit type</div>
              <span>{unitTypes.length} items</span>
            </div>

            <div className="unit-type-table-wrap">
              <table className="unit-type-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Area</th>
                    <th>Monthly price</th>
                    <th>Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {unitTypes.map((unitType) => {
                    const isSelected = selectedUnitType?.id === unitType.id;
                    return (
                      <tr
                        key={unitType.id}
                        className={isSelected ? "unit-type-row is-selected" : "unit-type-row"}
                        onClick={() => updateSelected(unitType)}
                      >
                        <td>{unitType.name}</td>
                        <td>{unitType.area} m²</td>
                        <td>{unitType.monthlyPrice.toLocaleString("vi-VN")}đ</td>
                        <td>{unitType.updatedAt}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <aside className="unit-type-panel unit-type-panel--detail">
            <div className="unit-type-detail-header">
              <div>
                <p className="eyebrow">Unit type detail</p>
                <h2>{currentUnitType.name || "New type"}</h2>
              </div>
            </div>

            <div className="unit-type-form-grid">
              <div className="unit-type-field-row">
                <label className="unit-type-field">
                  <span>Name</span>
                  <input
                    value={currentUnitType.name}
                    onChange={(event) => setDraft({ ...currentUnitType, name: event.target.value })}
                  />
                </label>

                <label className="unit-type-field">
                  <span>Monthly price</span>
                  <input
                    type="number"
                    value={currentUnitType.monthlyPrice}
                    onChange={(event) =>
                      setDraft({
                        ...currentUnitType,
                        monthlyPrice: Number(event.target.value || 0),
                      })
                    }
                  />
                </label>
              </div>

              <div className="unit-type-field-row">
                <label className="unit-type-field">
                  <span>Width (m)</span>
                  <input
                    type="number"
                    value={currentUnitType.width}
                    onChange={(event) => setDraft({ ...currentUnitType, width: Number(event.target.value || 0) })}
                  />
                </label>

                <label className="unit-type-field">
                  <span>Depth (m)</span>
                  <input
                    type="number"
                    value={currentUnitType.depth}
                    onChange={(event) => setDraft({ ...currentUnitType, depth: Number(event.target.value || 0) })}
                  />
                </label>
              </div>

              <div className="unit-type-field-row">
                <label className="unit-type-field">
                  <span>Height (m)</span>
                  <input
                    type="number"
                    value={currentUnitType.height}
                    onChange={(event) => setDraft({ ...currentUnitType, height: Number(event.target.value || 0) })}
                  />
                </label>

                <label className="unit-type-field">
                  <span>Area (m²)</span>
                  <input
                    type="number"
                    value={currentUnitType.area}
                    onChange={(event) => setDraft({ ...currentUnitType, area: Number(event.target.value || 0) })}
                  />
                </label>
              </div>

              <label className="unit-type-field">
                <span>Description</span>
                <textarea
                  rows={4}
                  value={currentUnitType.description}
                  onChange={(event) => setDraft({ ...currentUnitType, description: event.target.value })}
                />
              </label>
            </div>

            <div className="unit-type-warning">
              <AlertTriangle size={14} />
              Thay đổi giá chỉ áp dụng cho khoang chưa ký hợp đồng. Hợp đồng đã ký giữ nguyên giá đã chốt lúc ký.
            </div>

            <div className="unit-type-actions">
              <button className="button button-primary button-full" type="button" onClick={saveCurrent}>
                Lưu thay đổi
              </button>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
