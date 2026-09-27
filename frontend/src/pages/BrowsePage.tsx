import { useState } from "react";
import { ArrowRight, ChevronDown, Filter, MapPin } from "lucide-react";
import { Navigate } from "../app/types";
import { facilities, formatPrice, UnitType, unitTypes } from "../mocks/catalog";
import { UnitCard } from "../components/domain/UnitCard";
import { UnitDetailsDialog } from "../components/domain/UnitDetailsDialog";

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
      <section className="page-intro">
        <div className="container page-intro-inner">
          <div>
            <h1>
              Chọn phương án
              <br />
              <span>cho hàng hóa.</span>
            </h1>
            <p>So sánh quy mô, giá và điểm tiếp nhận.</p>
          </div>
        </div>
      </section>
      <section className="section browse-section">
        <div className="container">
          <div className="browse-toolbar">
            <div className="toolbar-title">
              <Filter size={17} />
              <span>Lọc phương án</span>
            </div>
            <div className="toolbar-fields">
              <label className="compact-select">
                <span>Điểm kho</span>
                <select
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
                <ChevronDown size={15} />
              </label>
              <label className="compact-select">
                <span>Quy mô</span>
                <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
                  <option value="">Tất cả quy mô</option>
                  {unitTypes.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.capacity} · {item.name}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} />
              </label>
            </div>
          </div>
          <div className="browse-layout">
            <div className="browse-results">
              <div className="results-heading">
                <p>{filteredTypes.length} phương án kho</p>
                <span>
                  {lowestPrice
                    ? `Tham khảo từ ${formatPrice(lowestPrice)}đ/tháng`
                    : "Không có lựa chọn phù hợp"}
                </span>
              </div>
              {filteredTypes.length ? (
                <div className="unit-preview-grid browse-grid">
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
                <div className="empty-state">
                  <p className="eyebrow">Thử lại lựa chọn</p>
                  <h2>Chưa có quy mô này.</h2>
                  <button className="button button-secondary" onClick={() => setTypeFilter("")}>
                    Xem tất cả phương án <ArrowRight size={16} />
                  </button>
                </div>
              )}
            </div>
            <aside className="facility-filter-card">
              <p className="eyebrow">Điểm kho</p>
              <h2>Chọn điểm kho.</h2>
              <p>Địa chỉ và giờ tiếp nhận.</p>
              <div className="facility-list">
                {facilities
                  .filter((item) => !facilityFilter || item.id === facilityFilter)
                  .map((item) => (
                    <div className="facility-list-item" key={item.id}>
                      <span className="facility-pin">
                        <MapPin size={15} />
                      </span>
                      <div>
                        <strong>{item.name}</strong>
                        <span>{item.address}</span>
                        <small>{item.hours}</small>
                      </div>
                    </div>
                  ))}
              </div>
              <button
                className="button button-primary button-full"
                onClick={() => navigate("/rental-requests/new")}
              >
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
