import { useMemo, useState } from "react";
import { AlertCircle, ArrowRight, CalendarRange, Search, ShieldCheck } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./AccountManagement.css";

type AuditAction =
  | "Account.UpdateRole"
  | "Account.Lock"
  | "RolePermission.Update"
  | "RentalRequest.Approve"
  | "Facility.Assign"
  | "Login.Failed";

type AuditLogEntry = {
  id: string;
  accountId: string;
  action: AuditAction;
  entityType: string;
  entityId: string;
  oldValue: Record<string, string | number | boolean | null> | null;
  newValue: Record<string, string | number | boolean | null> | null;
  createdAt: string;
};

const initialLogs: AuditLogEntry[] = [
  {
    id: "log-1",
    accountId: "acc-103",
    action: "Account.UpdateRole",
    entityType: "Account",
    entityId: "acc-105",
    oldValue: { role: "FS", facility: "Kho Mộc Quận 7" },
    newValue: { role: "FM", facility: "Kho Mộc Quận 7" },
    createdAt: "2026-09-24 08:10",
  },
  {
    id: "log-2",
    accountId: "acc-101",
    action: "Facility.Assign",
    entityType: "FacilityAssignment",
    entityId: "fa-14",
    oldValue: { assignedTo: "null", status: "Unassigned" },
    newValue: { assignedTo: "acc-102", status: "Assigned" },
    createdAt: "2026-09-24 08:35",
  },
  {
    id: "log-3",
    accountId: "acc-104",
    action: "RolePermission.Update",
    entityType: "RolePermission",
    entityId: "role-fm",
    oldValue: { "invoice.read": true },
    newValue: { "invoice.read": false },
    createdAt: "2026-09-24 09:02",
  },
  {
    id: "log-4",
    accountId: "acc-101",
    action: "RentalRequest.Approve",
    entityType: "RentalRequest",
    entityId: "rr-55",
    oldValue: { status: "Pending" },
    newValue: { status: "Approved" },
    createdAt: "2026-09-24 09:40",
  },
  {
    id: "log-5",
    accountId: "acc-105",
    action: "Login.Failed",
    entityType: "LoginHistory",
    entityId: "lh-105",
    oldValue: { attempts: 1 },
    newValue: { attempts: 2 },
    createdAt: "2026-09-24 09:45",
  },
];

const entityOptions = ["all", "Account", "RolePermission", "FacilityAssignment", "RentalRequest", "LoginHistory"] as const;

export function AuditLog({ navigate }: { navigate: Navigate }) {
  const [logs, setLogs] = useState<AuditLogEntry[]>(initialLogs);
  const [accountId, setAccountId] = useState("");
  const [entityType, setEntityType] = useState<(typeof entityOptions)[number]>("all");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(initialLogs[0]?.id ?? "");

  const filteredLogs = useMemo(() => {
    const keyword = query.trim().toLowerCase();

    return logs.filter((log) => {
      const matchesAccount = !accountId || log.accountId.toLowerCase().includes(accountId.toLowerCase());
      const matchesEntity = entityType === "all" || log.entityType === entityType;
      const matchesQuery =
        !keyword ||
        log.action.toLowerCase().includes(keyword) ||
        log.entityId.toLowerCase().includes(keyword) ||
        log.entityType.toLowerCase().includes(keyword);

      return matchesAccount && matchesEntity && matchesQuery;
    });
  }, [accountId, entityType, logs, query]);

  const selectedLog = filteredLogs.find((log) => log.id === selectedId) ?? filteredLogs[0] ?? null;

  const formatValue = (value: Record<string, string | number | boolean | null> | null) => {
    if (!value) return "-";
    return Object.entries(value)
      .map(([key, item]) => `${key}: ${String(item)}`)
      .join(" | ");
  };

  return (
    <main className="admin-account-page">
      <section className="admin-account-hero">
        <div className="container admin-account-hero__inner">
          <div>
            <p className="eyebrow">System Administrator</p>
            <h1>
              Audit <span>log.</span>
            </h1>
            <p>
              Theo dõi toàn bộ thay đổi nhạy cảm của nhân viên và thực thể trong hệ thống để phục vụ kiểm tra, điều tra và báo cáo.
            </p>
          </div>
        </div>
      </section>

      <section className="container admin-account-content">
        <div className="admin-account-actions">
          <div className="admin-account-action-group">
            <button className="button button-primary" type="button" onClick={() => navigate("/admin/accounts")}>
              <ShieldCheck size={16} /> Accounts
            </button>
            <button className="button button-secondary" type="button" onClick={() => navigate("/admin/rbac")}>
              <AlertCircle size={16} /> RBAC
            </button>
          </div>

          <button className="button button-quiet" type="button" onClick={() => navigate("/admin/login-history")}>
            <CalendarRange size={16} /> Login history
          </button>
        </div>

        <div className="admin-account-layout">
          <div className="admin-account-panel admin-account-panel--list">
            <div className="admin-account-toolbar">
              <div className="admin-account-toolbar__title">
                <AlertCircle size={18} />
                <strong>Audit trail</strong>
              </div>
              <span className="admin-account-toolbar__count">{filteredLogs.length} sự kiện</span>
            </div>

            <div className="admin-account-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm theo action</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Action, entity, ID"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Account</span>
                <input
                  value={accountId}
                  onChange={(event) => setAccountId(event.target.value)}
                  placeholder="acc-101"
                />
              </label>

              <label className="filter-field">
                <span>Entity</span>
                <select value={entityType} onChange={(event) => setEntityType(event.target.value as typeof entityType)}>
                  {entityOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="account-table-wrapper">
              <table className="account-table">
                <thead>
                  <tr>
                    <th>Account</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Thời gian</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredLogs.map((log) => {
                    const isSelected = selectedLog?.id === log.id;
                    return (
                      <tr
                        key={log.id}
                        className={isSelected ? "account-row is-selected" : "account-row"}
                        onClick={() => setSelectedId(log.id)}
                      >
                        <td>
                          <div className="account-user">
                            <strong>{log.accountId}</strong>
                            <span>{log.entityType}</span>
                          </div>
                        </td>
                        <td>
                          <span className="account-role-tag role-admin">{log.action}</span>
                        </td>
                        <td>
                          <div className="account-user">
                            <strong>{log.entityId}</strong>
                            <span>{log.entityType}</span>
                          </div>
                        </td>
                        <td className="account-facility">{log.createdAt}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {!filteredLogs.length && (
                <div className="empty-state">Không có audit log nào phù hợp với bộ lọc.</div>
              )}
            </div>
          </div>

          <aside className="admin-account-panel admin-account-panel--detail">
            {selectedLog ? (
              <>
                <div className="admin-account-detail-header">
                  <div>
                    <p className="eyebrow">Log detail</p>
                    <h2>{selectedLog.action}</h2>
                  </div>
                  <span className="account-status-tag status-active">Recorded</span>
                </div>

                <div className="admin-account-detail-grid">
                  <div className="detail-field">
                    <span>Account</span>
                    <strong>{selectedLog.accountId}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Entity type</span>
                    <strong>{selectedLog.entityType}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Entity id</span>
                    <strong>{selectedLog.entityId}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Created at</span>
                    <strong>{selectedLog.createdAt}</strong>
                  </div>
                </div>

                <div className="admin-account-detail-grid">
                  <div className="detail-field">
                    <span>Old value</span>
                    <strong>{formatValue(selectedLog.oldValue)}</strong>
                  </div>
                  <div className="detail-field">
                    <span>New value</span>
                    <strong>{formatValue(selectedLog.newValue)}</strong>
                  </div>
                </div>

                <div className="admin-account-actions-panel">
                  <button className="button button-primary button-full" type="button" onClick={() => navigate("/admin/accounts")}>
                    Xem account liên quan <ArrowRight size={16} />
                  </button>
                </div>
              </>
            ) : (
              <div className="empty-state">Không có bản ghi audit log.</div>
            )}
          </aside>
        </div>
      </section>
    </main>
  );
}
