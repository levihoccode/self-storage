import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  CalendarRange,
  CheckCircle2,
  CircleDollarSign,
  Plus,
  Search,
  ShieldCheck,
} from "lucide-react";
import type { Navigate } from "../../app/types";
import "./BusinessRulesPolicy.css";

type PolicyStatus = "Active" | "Inactive" | "Expired";
type DepositType = "%" | "$";

type PolicyRecord = {
  id: string;
  name: string;
  depositType: DepositType;
  depositValue: number;
  cancelPolicy: string;
  returnPolicy: string;
  renewalPolicy: string;
  overduePolicy: string;
  effectiveFrom: string;
  effectiveTo: string;
  status: PolicyStatus;
  createdBy: string;
  createdAt: string;
};

const initialPolicies: PolicyRecord[] = [
  {
    id: "pol-101",
    name: "Rental Policy - Q4 2026",
    depositType: "%",
    depositValue: 30,
    cancelPolicy: "Hủy trước 7 ngày: hoàn 70% tiền cọc; trong 7 ngày: giữ 50% cọc.",
    returnPolicy: "Trả kho đúng hẹn, không phạt; chậm 1-3 ngày: +5%/ngày theo đơn vị lưu trữ.",
    renewalPolicy: "Gia hạn tự động khi khách đặt trước 3 ngày nếu chưa đầy slot.",
    overduePolicy: "Phí quá hạn 1,5%/ngày trên giá thuê cơ sở theo từng loại kho.",
    effectiveFrom: "2026-09-01",
    effectiveTo: "2026-12-31",
    status: "Active",
    createdBy: "BOM - Lê Thị Lan",
    createdAt: "2026-08-28",
  },
  {
    id: "pol-102",
    name: "Rental Policy - Q1 2027",
    depositType: "$",
    depositValue: 5000000,
    cancelPolicy: "Hủy trước 15 ngày: hoàn 100% tiền cọc; sau 15 ngày: giữ 40%.",
    returnPolicy: "Hết hạn kho, khách có thể gia hạn tối đa 2 lần trong vòng 7 ngày.",
    renewalPolicy: "Gia hạn cần xác nhận BOM trước 48 giờ.",
    overduePolicy: "Phụ thu 800.000đ/ngày cho kho nhóm A và 500.000đ/ngày cho kho nhóm B.",
    effectiveFrom: "2027-01-01",
    effectiveTo: "2027-03-31",
    status: "Inactive",
    createdBy: "BOM - Lê Thị Lan",
    createdAt: "2026-09-15",
  },
  {
    id: "pol-103",
    name: "Policy Pilot - Seasonal",
    depositType: "%",
    depositValue: 20,
    cancelPolicy: "Hủy theo quy định mùa cao điểm; hoàn 60% nếu hủy trước 14 ngày.",
    returnPolicy: "Trả kho đúng ngày, kiểm tra chất lượng trước khi ký xác nhận trả kho.",
    renewalPolicy: "Mỗi đơn hàng tối đa 1 lần gia hạn theo mùa lưu trữ.",
    overduePolicy: "Phụ thu 2%/ngày đối với kho theo mùa.",
    effectiveFrom: "2026-06-01",
    effectiveTo: "2026-08-31",
    status: "Expired",
    createdBy: "BOM - Trần Minh Khang",
    createdAt: "2026-05-10",
  },
];

const statusOptions: Array<"all" | PolicyStatus> = ["all", "Active", "Inactive", "Expired"];

const emptyPolicy: PolicyRecord = {
  id: "",
  name: "",
  depositType: "%",
  depositValue: 0,
  cancelPolicy: "",
  returnPolicy: "",
  renewalPolicy: "",
  overduePolicy: "",
  effectiveFrom: "",
  effectiveTo: "",
  status: "Inactive",
  createdBy: "BOM - System",
  createdAt: new Date().toISOString().slice(0, 10),
};

export function BusinessRulesPolicy({ navigate }: { navigate: Navigate }) {
  const [policies, setPolicies] = useState<PolicyRecord[]>(initialPolicies);
  const [selectedId, setSelectedId] = useState(initialPolicies[0]?.id ?? "");
  const [statusFilter, setStatusFilter] = useState<"all" | PolicyStatus>("all");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<PolicyRecord>(initialPolicies[0] ?? emptyPolicy);

  const filteredPolicies = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return policies.filter((policy) => {
      const matchesStatus = statusFilter === "all" || policy.status === statusFilter;
      const matchesSearch =
        !keyword ||
        policy.name.toLowerCase().includes(keyword) ||
        policy.cancelPolicy.toLowerCase().includes(keyword) ||
        policy.returnPolicy.toLowerCase().includes(keyword) ||
        policy.overduePolicy.toLowerCase().includes(keyword);

      return matchesStatus && matchesSearch;
    });
  }, [policies, query, statusFilter]);

  const selectedPolicy =
    filteredPolicies.find((policy) => policy.id === selectedId) ?? filteredPolicies[0] ?? null;

  useEffect(() => {
    if (selectedPolicy && selectedPolicy.id !== draft.id) {
      setDraft(selectedPolicy);
    }
  }, [selectedPolicy, draft.id]);

  function updateSelected(policy: PolicyRecord) {
    setSelectedId(policy.id);
    setDraft(policy);
  }

  function applyStatus(policyId: string, nextStatus: PolicyStatus) {
    const updatedPolicies = policies.map((policy) =>
      policy.id === policyId ? { ...policy, status: nextStatus } : policy,
    );

    setPolicies(updatedPolicies);

    const nextDraft = updatedPolicies.find((policy) => policy.id === policyId) ?? draft;
    setDraft({ ...nextDraft, status: nextStatus });
  }

  function createNewPolicy() {
    const newItem: PolicyRecord = {
      ...emptyPolicy,
      id: `pol-${Date.now()}`,
      name: "New Policy",
      createdBy: "BOM - System",
      createdAt: new Date().toISOString().slice(0, 10),
    };
    setPolicies((current) => [newItem, ...current]);
    setDraft(newItem);
    setSelectedId(newItem.id);
  }

  const currentPolicy = draft;

  return (
    <main className="bom-policy-page" style={{ position: "relative" }}>
      <section className="bom-policy-hero">
        <div className="container bom-policy-hero__inner">
          <div>
            <p className="eyebrow">Business Operation Manager</p>
            <h1>
              Business <span>rules.</span>
            </h1>
            <p>
              Quản lý chính sách đặt cọc, hủy, trả kho, gia hạn và quá hạn theo nguyên tắc vận hành của hệ thống.
            </p>
          </div>
        </div>
      </section>

      <section className="container bom-policy-content">
        <div className="bom-policy-actions">
          <div className="bom-policy-action-group">
            <button className="button button-primary" type="button" onClick={createNewPolicy}>
              <Plus size={16} /> Thêm policy
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/bom/fees")}>
              <CircleDollarSign size={16} /> Quản lý phí
            </button>
          </div>

          <button className="button button-quiet" type="button" onClick={() => navigate("/notifications")}>
            <ShieldCheck size={16} /> Thông báo
          </button>
        </div>

        <div className="bom-policy-layout">
          <div className="bom-policy-panel bom-policy-panel--list">
            <div className="bom-policy-toolbar">
              <div className="bom-policy-toolbar__title">
                <CalendarRange size={18} />
                <strong>Danh sách chính sách</strong>
              </div>
              <span className="bom-policy-toolbar__count">{filteredPolicies.length} policy</span>
            </div>

            <div className="bom-policy-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm kiếm</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tên, nội dung policy"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Status</span>
                <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}>
                  {statusOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="bom-policy-table-wrapper">
              <table className="bom-policy-table">
                <thead>
                  <tr>
                    <th>Policy</th>
                    <th>Effective</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredPolicies.map((policy) => {
                    const isSelected = selectedPolicy?.id === policy.id;
                    return (
                      <tr
                        key={policy.id}
                        className={isSelected ? "bom-policy-row is-selected" : "bom-policy-row"}
                        onClick={() => updateSelected(policy)}
                      >
                        <td>
                          <div className="bom-policy-user">
                            <strong>{policy.name}</strong>
                            <span>{policy.createdBy}</span>
                          </div>
                        </td>
                        <td className="bom-policy-date">
                          {policy.effectiveFrom} → {policy.effectiveTo}
                        </td>
                        <td>
                          <span className={`account-status-tag status-${policy.status.toLowerCase()}`}>
                            {policy.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {!filteredPolicies.length && (
                <div className="empty-state">Không có policy nào phù hợp với bộ lọc hiện tại.</div>
              )}
            </div>
          </div>

          <aside className="bom-policy-panel bom-policy-panel--detail">
            <div className="bom-policy-detail-header">
              <div>
                <p className="eyebrow">Policy detail</p>
                <h2>{currentPolicy.name || "New policy"}</h2>
              </div>
              <span className={`account-status-tag status-${currentPolicy.status.toLowerCase()}`}>
                {currentPolicy.status}
              </span>
            </div>

            <div className="bom-policy-form-grid">
              <label className="bom-policy-field">
                <span>Policy name</span>
                <input
                  value={currentPolicy.name}
                  onChange={(event) => setDraft({ ...currentPolicy, name: event.target.value })}
                  placeholder="Ví dụ: Rental Policy - Q4 2026"
                />
              </label>

              <div className="bom-policy-field-row">
                <label className="bom-policy-field">
                  <span>Deposit type</span>
                  <select
                    value={currentPolicy.depositType}
                    onChange={(event) =>
                      setDraft({
                        ...currentPolicy,
                        depositType: event.target.value as DepositType,
                      })
                    }
                  >
                    <option value="%">%</option>
                    <option value="$">$</option>
                  </select>
                </label>

                <label className="bom-policy-field">
                  <span>Deposit value</span>
                  <input
                    type="number"
                    value={currentPolicy.depositValue}
                    onChange={(event) =>
                      setDraft({
                        ...currentPolicy,
                        depositValue: Number(event.target.value || 0),
                      })
                    }
                  />
                </label>
              </div>

              <label className="bom-policy-field">
                <span>Effective from</span>
                <input
                  type="date"
                  value={currentPolicy.effectiveFrom}
                  onChange={(event) => setDraft({ ...currentPolicy, effectiveFrom: event.target.value })}
                />
              </label>

              <label className="bom-policy-field">
                <span>Effective to</span>
                <input
                  type="date"
                  value={currentPolicy.effectiveTo}
                  onChange={(event) => setDraft({ ...currentPolicy, effectiveTo: event.target.value })}
                />
              </label>

              <label className="bom-policy-field">
                <span>Cancel policy</span>
                <textarea
                  value={currentPolicy.cancelPolicy}
                  onChange={(event) => setDraft({ ...currentPolicy, cancelPolicy: event.target.value })}
                  rows={3}
                />
              </label>

              <label className="bom-policy-field">
                <span>Return policy</span>
                <textarea
                  value={currentPolicy.returnPolicy}
                  onChange={(event) => setDraft({ ...currentPolicy, returnPolicy: event.target.value })}
                  rows={3}
                />
              </label>

              <label className="bom-policy-field">
                <span>Renewal policy</span>
                <textarea
                  value={currentPolicy.renewalPolicy}
                  onChange={(event) => setDraft({ ...currentPolicy, renewalPolicy: event.target.value })}
                  rows={2}
                />
              </label>

              <label className="bom-policy-field">
                <span>Overdue policy</span>
                <textarea
                  value={currentPolicy.overduePolicy}
                  onChange={(event) => setDraft({ ...currentPolicy, overduePolicy: event.target.value })}
                  rows={2}
                />
              </label>
            </div>

            {currentPolicy.effectiveFrom && currentPolicy.effectiveTo &&
              currentPolicy.effectiveFrom >= currentPolicy.effectiveTo && (
                <div className="bom-policy-warning">
                  <AlertTriangle size={14} />
                  Cảnh báo: effective_from phải nhỏ hơn effective_to.
                </div>
              )}

            {currentPolicy.depositType === "%" && (currentPolicy.depositValue < 0 || currentPolicy.depositValue > 100) && (
              <div className="bom-policy-warning">
                <AlertTriangle size={14} />
                Cảnh báo: deposit value khi kiểu % phải nằm trong khoảng 0 đến 100.
              </div>
            )}

            <div className="bom-policy-actions-panel">
              <button
                className="button button-primary button-full"
                type="button"
                onClick={() => {
                  const nextPolicies = policies.some((policy) => policy.id === draft.id)
                    ? policies.map((policy) => (policy.id === draft.id ? draft : policy))
                    : [draft, ...policies];
                  setPolicies(nextPolicies);
                  setSelectedId(draft.id || String(Date.now()));
                }}
              >
                <CheckCircle2 size={16} /> Lưu policy
              </button>

              <button className="button button-secondary button-full" type="button" onClick={() => applyStatus(currentPolicy.id, "Active")}>
                Kích hoạt
              </button>

              <button className="button button-full bom-policy-secondary-button" type="button" onClick={() => applyStatus(currentPolicy.id, "Inactive")}>
                Vô hiệu hóa
              </button>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
