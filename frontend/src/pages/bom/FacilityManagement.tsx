import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Building2, Plus, Search } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./FacilityManagement.css";

type FacilityStatus = "Active" | "Inactive";

type FacilityRecord = {
  id: string;
  code: string;
  name: string;
  address: string;
  phone: string;
  operatingHours: string;
  fmAccountId: string | null;
  status: FacilityStatus;
};

const initialFacilities: FacilityRecord[] = [
  {
    id: "fac-101",
    code: "Q7",
    name: "Kho Q7 - Tân Phú",
    address: "Lô 7, KCN Tân Phú, TP.HCM",
    phone: "028-3990-1122",
    operatingHours: "08:00 - 20:00",
    fmAccountId: "fm-01",
    status: "Active",
  },
  {
    id: "fac-102",
    code: "Q9",
    name: "Kho Q9 - Bình Dương",
    address: "Đường 30, Khu công nghiệp Bình Dương",
    phone: "0274-555-9090",
    operatingHours: "08:00 - 18:00",
    fmAccountId: "fm-02",
    status: "Active",
  },
  {
    id: "fac-103",
    code: "Q12",
    name: "Kho Q12 - Long An",
    address: "Đường Hòa Bình, tỉnh Long An",
    phone: "0272-840-3311",
    operatingHours: "09:00 - 21:00",
    fmAccountId: null,
    status: "Inactive",
  },
];

const emptyFacility: FacilityRecord = {
  id: "",
  code: "",
  name: "",
  address: "",
  phone: "",
  operatingHours: "",
  fmAccountId: null,
  status: "Inactive",
};

const activeFmOptions = [
  { value: "fm-01", label: "FM-01 · Nguyễn Văn An · Kho Q7" },
  { value: "fm-02", label: "FM-02 · Trần Thị Bình · Kho Q9" },
  { value: "fm-03", label: "FM-03 · Lê Hoàng Cường · Kho Q12" },
  { value: "fm-04", label: "FM-04 · Phạm Mai Duyên · Kho HCM" },
];

export function FacilityManagement({ navigate }: { navigate: Navigate }) {
  const [facilities, setFacilities] = useState<FacilityRecord[]>(initialFacilities);
  const [selectedId, setSelectedId] = useState(initialFacilities[0]?.id ?? "");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<FacilityRecord>(initialFacilities[0] ?? emptyFacility);

  const filteredFacilities = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return facilities.filter((facility) => {
      const matchesSearch =
        !keyword ||
        facility.code.toLowerCase().includes(keyword) ||
        facility.name.toLowerCase().includes(keyword) ||
        facility.address.toLowerCase().includes(keyword);
      return matchesSearch;
    });
  }, [facilities, query]);

  const selectedFacility =
    filteredFacilities.find((facility) => facility.id === selectedId) ?? filteredFacilities[0] ?? null;

  useEffect(() => {
    if (selectedFacility && selectedFacility.id !== draft.id) {
      setDraft(selectedFacility);
    }
  }, [selectedFacility, draft.id]);

  function updateSelected(facility: FacilityRecord) {
    setSelectedId(facility.id);
    setDraft(facility);
  }

  function createNewFacility() {
    const newFacility: FacilityRecord = {
      ...emptyFacility,
      id: `fac-${Date.now()}`,
      code: `NEW-${Date.now().toString().slice(-4)}`,
      name: "New Facility",
      status: "Inactive",
    };
    setFacilities((current) => [newFacility, ...current]);
    setSelectedId(newFacility.id);
    setDraft(newFacility);
  }

  const currentFacility = draft;

  return (
    <main className="facility-page" style={{ position: "relative" }}>
      <section className="facility-hero">
        <div className="container facility-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Facility <span>management.</span>
            </h1>
            <p>
              Quản lý cơ sở/chi nhánh, gán FM phụ trách và mở/đóng trạng thái hoạt động theo điều kiện nghiệp vụ.
            </p>
          </div>
        </div>
      </section>

      <section className="container facility-content">
        <div className="facility-topbar">
          <div className="facility-topbar__group">
            <button className="button button-primary" type="button" onClick={createNewFacility}>
              <Plus size={16} /> Tạo cơ sở mới
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/bom/revenue")}>
              <Building2 size={16} /> Doanh thu
            </button>
          </div>
        </div>

        <div className="facility-layout">
          <div className="facility-panel">
            <div className="facility-panel__header">
              <div className="facility-panel__title">Danh sách cơ sở</div>
              <span>{filteredFacilities.length} facilities</span>
            </div>

            <label className="filter-field filter-field--search">
              <span>Tìm kiếm</span>
              <div className="filter-input-wrap">
                <Search size={14} />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Mã, tên hoặc địa chỉ"
                />
              </div>
            </label>

            <div className="facility-table-wrap">
              <table className="facility-table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Name</th>
                    <th>FM</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredFacilities.map((facility) => {
                    const isSelected = selectedFacility?.id === facility.id;
                    return (
                      <tr
                        key={facility.id}
                        className={isSelected ? "facility-row is-selected" : "facility-row"}
                        onClick={() => updateSelected(facility)}
                      >
                        <td>{facility.code}</td>
                        <td>{facility.name}</td>
                        <td>{facility.fmAccountId ?? "Unassigned"}</td>
                        <td>
                          <span className={`account-status-tag status-${facility.status.toLowerCase()}`}>
                            {facility.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {!filteredFacilities.length && <div className="empty-state">Không có cơ sở nào phù hợp.</div>}
            </div>
          </div>

          <aside className="facility-panel facility-panel--detail">
            <div className="facility-detail-header">
              <div>
                <p className="eyebrow">Facility detail</p>
                <h2>{currentFacility.name || "New facility"}</h2>
              </div>
              <span className={`account-status-tag status-${currentFacility.status.toLowerCase()}`}>
                {currentFacility.status}
              </span>
            </div>

            <div className="facility-form-grid">
              <div className="facility-field-row">
                <label className="facility-field">
                  <span>Code</span>
                  <input
                    value={currentFacility.code}
                    onChange={(event) => setDraft({ ...currentFacility, code: event.target.value })}
                  />
                </label>

                <label className="facility-field">
                  <span>Name</span>
                  <input
                    value={currentFacility.name}
                    onChange={(event) => setDraft({ ...currentFacility, name: event.target.value })}
                  />
                </label>
              </div>

              <label className="facility-field">
                <span>Address</span>
                <input
                  value={currentFacility.address}
                  onChange={(event) => setDraft({ ...currentFacility, address: event.target.value })}
                />
              </label>

              <div className="facility-field-row">
                <label className="facility-field">
                  <span>Phone</span>
                  <input
                    value={currentFacility.phone}
                    onChange={(event) => setDraft({ ...currentFacility, phone: event.target.value })}
                  />
                </label>

                <label className="facility-field">
                  <span>Operating hours</span>
                  <input
                    value={currentFacility.operatingHours}
                    onChange={(event) =>
                      setDraft({ ...currentFacility, operatingHours: event.target.value })
                    }
                  />
                </label>
              </div>

              <label className="facility-field">
                <span>FM account</span>
                <select
                  value={currentFacility.fmAccountId ?? ""}
                  onChange={(event) =>
                    setDraft({ ...currentFacility, fmAccountId: event.target.value || null })
                  }
                >
                  <option value="">Chọn FM đang hoạt động</option>
                  {activeFmOptions.map((fm) => (
                    <option key={fm.value} value={fm.value}>
                      {fm.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {!currentFacility.fmAccountId && (
              <div className="facility-warning">
                <AlertTriangle size={14} />
                Không thể kích hoạt cơ sở khi chưa có FM phụ trách.
              </div>
            )}

            <div className="facility-actions">
              <button
                className="button button-primary button-full"
                type="button"
                onClick={() => {
                  const nextFacilities = facilities.some((facility) => facility.id === draft.id)
                    ? facilities.map((facility) => (facility.id === draft.id ? draft : facility))
                    : [draft, ...facilities];
                  setFacilities(nextFacilities);
                  setSelectedId(draft.id || `fac-${Date.now()}`);
                }}
              >
                Lưu thay đổi
              </button>

              <button
                className="button button-secondary button-full"
                type="button"
                disabled={!currentFacility.fmAccountId}
                onClick={() => {
                  setDraft({ ...currentFacility, status: "Active" });
                  setFacilities((current) =>
                    current.map((facility) =>
                      facility.id === currentFacility.id ? { ...facility, status: "Active" } : facility,
                    ),
                  );
                }}
              >
                Kích hoạt
              </button>

              <button
                className="button button-full facility-secondary-button"
                type="button"
                onClick={() => {
                  setDraft({ ...currentFacility, status: "Inactive" });
                  setFacilities((current) =>
                    current.map((facility) =>
                      facility.id === currentFacility.id ? { ...facility, status: "Inactive" } : facility,
                    ),
                  );
                }}
              >
                Vô hiệu hóa
              </button>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
