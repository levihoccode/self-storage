import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, BadgeDollarSign, Plus, Search } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./FeeManagement.css";

type FeeStatus = "Active" | "Inactive" | "Archived";
type FeeCalculation = "Fixed" | "Daily" | "Monthly" | "Percent" | "Lock";
type FeeCategory = "Service" | "Penalty" | "Utility" | "Security";

type FeeRecord = {
  id: string;
  name: string;
  category: FeeCategory;
  amount: number;
  calculation: FeeCalculation;
  description: string;
  status: FeeStatus;
  createdBy: string;
  createdAt: string;
};

const initialFees: FeeRecord[] = [
  {
    id: "fee-101",
    name: "Mất chìa khóa",
    category: "Penalty",
    amount: 50000,
    calculation: "Fixed",
    description: "Phí thay mới chìa khóa cho unit đã hết hạn khoán và cần cấp lại khóa.",
    status: "Active",
    createdBy: "BOM - Lê Thị Lan",
    createdAt: "2026-08-28",
  },
  {
    id: "fee-102",
    name: "Wifi",
    category: "Utility",
    amount: 2000000,
    calculation: "Monthly",
    description: "Phí dịch vụ internet hàng tháng cho đơn vị lưu trữ có cài đặt internet.",
    status: "Active",
    createdBy: "BOM - Lê Thị Lan",
    createdAt: "2026-09-05",
  },
  {
    id: "fee-103",
    name: "Khóa tài khoản tạm thời",
    category: "Security",
    amount: 3,
    calculation: "Lock",
    description: "Phí/logic khóa tài khoản do vi phạm quy định bảo mật hoặc thanh toán chậm.",
    status: "Inactive",
    createdBy: "BOM - Trần Minh Khang",
    createdAt: "2026-09-11",
  },
];

const statusOptions: Array<"all" | FeeStatus> = ["all", "Active", "Inactive", "Archived"];
const calculationOptions: FeeCalculation[] = ["Fixed", "Daily", "Monthly", "Percent", "Lock"];

const emptyFee: FeeRecord = {
  id: "",
  name: "",
  category: "Service",
  amount: 0,
  calculation: "Fixed",
  description: "",
  status: "Inactive",
  createdBy: "BOM - System",
  createdAt: new Date().toISOString().slice(0, 10),
};

export function FeeManagement({ navigate }: { navigate: Navigate }) {
  const [fees, setFees] = useState<FeeRecord[]>(initialFees);
  const [selectedId, setSelectedId] = useState(initialFees[0]?.id ?? "");
  const [statusFilter, setStatusFilter] = useState<"all" | FeeStatus>("all");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<FeeRecord>(initialFees[0] ?? emptyFee);

  const filteredFees = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return fees.filter((fee) => {
      const matchesStatus = statusFilter === "all" || fee.status === statusFilter;
      const matchesSearch =
        !keyword ||
        fee.name.toLowerCase().includes(keyword) ||
        fee.category.toLowerCase().includes(keyword) ||
        fee.description.toLowerCase().includes(keyword);
      return matchesStatus && matchesSearch;
    });
  }, [fees, query, statusFilter]);

  const selectedFee = filteredFees.find((fee) => fee.id === selectedId) ?? filteredFees[0] ?? null;

  useEffect(() => {
    if (selectedFee && selectedFee.id !== draft.id) {
      setDraft(selectedFee);
    }
  }, [selectedFee, draft.id]);

  function updateSelected(fee: FeeRecord) {
    setSelectedId(fee.id);
    setDraft(fee);
  }

  function applyStatus(feeId: string, nextStatus: FeeStatus) {
    const updated = fees.map((fee) => (fee.id === feeId ? { ...fee, status: nextStatus } : fee));
    setFees(updated);
    const nextDraft = updated.find((fee) => fee.id === feeId) ?? draft;
    setDraft({ ...nextDraft, status: nextStatus });
  }

  function createNewFee() {
    const newFee: FeeRecord = {
      ...emptyFee,
      id: `fee-${Date.now()}`,
      name: "New Fee",
      createdBy: "BOM - System",
      createdAt: new Date().toISOString().slice(0, 10),
    };
    setFees((current) => [newFee, ...current]);
    setSelectedId(newFee.id);
    setDraft(newFee);
  }

  const currentFee = draft;

  return (
    <main className="fee-page" style={{ position: "relative" }}>
      <section className="fee-hero">
        <div className="container fee-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Fee <span>management.</span>
            </h1>
            <p>
              Quản lý danh sách phí phát sinh, phụ phí và khoản xử lý theo từng loại hoạt động của cơ sở lưu trữ.
            </p>
          </div>
        </div>
      </section>

      <section className="container fee-content">
        <div className="fee-topbar">
          <div className="fee-topbar__group">
            <button className="button button-primary" type="button" onClick={createNewFee}>
              <Plus size={16} /> Thêm fee
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/bom/policies")}>
              <BadgeDollarSign size={16} /> Chính sách
            </button>
          </div>
        </div>

        <div className="fee-layout">
          <div className="fee-panel">
            <div className="fee-panel__header">
              <div className="fee-panel__title">Danh sách fee</div>
              <span>{filteredFees.length} items</span>
            </div>

            <div className="fee-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm kiếm</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tên hoặc mô tả"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Status</span>
                <select
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value as "all" | FeeStatus)}
                >
                  {statusOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="fee-table-wrap">
              <table className="fee-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Category</th>
                    <th>Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredFees.map((fee) => {
                    const isSelected = selectedFee?.id === fee.id;
                    return (
                      <tr
                        key={fee.id}
                        className={isSelected ? "fee-row is-selected" : "fee-row"}
                        onClick={() => updateSelected(fee)}
                      >
                        <td>
                          <div className="fee-name-block">
                            <strong>{fee.name}</strong>
                            <span>{fee.createdBy}</span>
                          </div>
                        </td>
                        <td>{fee.category}</td>
                        <td>{fee.amount.toLocaleString("vi-VN")}đ</td>
                        <td>
                          <span className={`account-status-tag status-${fee.status.toLowerCase()}`}>
                            {fee.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {!filteredFees.length && <div className="empty-state">Không có fee nào phù hợp.</div>}
            </div>
          </div>

          <aside className="fee-panel fee-panel--detail">
            <div className="fee-detail-header">
              <div>
                <p className="eyebrow">Fee detail</p>
                <h2>{currentFee.name || "New fee"}</h2>
              </div>
              <span className={`account-status-tag status-${currentFee.status.toLowerCase()}`}>
                {currentFee.status}
              </span>
            </div>

            <div className="fee-form-grid">
              <label className="fee-field">
                <span>Fee name</span>
                <input
                  value={currentFee.name}
                  onChange={(event) => setDraft({ ...currentFee, name: event.target.value })}
                  placeholder="Ví dụ: Mất chìa khóa"
                />
              </label>

              <div className="fee-field-row">
                <label className="fee-field">
                  <span>Category</span>
                  <select
                    value={currentFee.category}
                    onChange={(event) =>
                      setDraft({ ...currentFee, category: event.target.value as FeeCategory })
                    }
                  >
                    <option value="Service">Service</option>
                    <option value="Penalty">Penalty</option>
                    <option value="Utility">Utility</option>
                    <option value="Security">Security</option>
                  </select>
                </label>

                <label className="fee-field">
                  <span>Calculation</span>
                  <select
                    value={currentFee.calculation}
                    onChange={(event) =>
                      setDraft({ ...currentFee, calculation: event.target.value as FeeCalculation })
                    }
                  >
                    {calculationOptions.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <label className="fee-field">
                <span>Amount</span>
                <input
                  type="number"
                  value={currentFee.amount}
                  onChange={(event) =>
                    setDraft({ ...currentFee, amount: Number(event.target.value || 0) })
                  }
                />
              </label>

              <label className="fee-field">
                <span>Description</span>
                <textarea
                  rows={4}
                  value={currentFee.description}
                  onChange={(event) => setDraft({ ...currentFee, description: event.target.value })}
                />
              </label>
            </div>

            {currentFee.calculation === "Lock" && (
              <div className="fee-warning">
                <AlertTriangle size={14} />
                Lock fee được dùng cho trạng thái khóa tài khoản; hệ thống lưu trong bảng quyền khóa riêng.
              </div>
            )}

            <div className="fee-actions">
              <button
                className="button button-primary button-full"
                type="button"
                onClick={() => {
                  const nextFees = fees.some((fee) => fee.id === draft.id)
                    ? fees.map((fee) => (fee.id === draft.id ? draft : fee))
                    : [draft, ...fees];
                  setFees(nextFees);
                  setSelectedId(draft.id || `fee-${Date.now()}`);
                }}
              >
                Lưu fee
              </button>

              <button
                className="button button-secondary button-full"
                type="button"
                onClick={() => applyStatus(currentFee.id, "Active")}
              >
                Kích hoạt
              </button>

              <button
                className="button button-full fee-secondary-button"
                type="button"
                onClick={() => applyStatus(currentFee.id, "Inactive")}
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
