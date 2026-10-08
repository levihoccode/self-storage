import { useMemo, useState } from "react";
import { AlertTriangle, CalendarRange, Search, ShieldCheck, UserRound, Wifi } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./AccountManagement.css";

type LoginStatus = "Success" | "Failed";

type LoginHistoryEntry = {
  id: string;
  accountId: string | null;
  email: string;
  ipAddress: string;
  userAgent: string;
  status: LoginStatus;
  failureReason: string | null;
  createdAt: string;
};

const initialHistory: LoginHistoryEntry[] = [
  {
    id: "lh-101",
    accountId: "acc-101",
    email: "anh.nguyen@khomoc.vn",
    ipAddress: "10.24.18.11",
    userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    status: "Success",
    failureReason: null,
    createdAt: "2026-09-24 08:15",
  },
  {
    id: "lh-102",
    accountId: "acc-104",
    email: "huy.pham@khomoc.vn",
    ipAddress: "14.186.23.92",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
    status: "Failed",
    failureReason: "Sai mật khẩu",
    createdAt: "2026-09-24 08:21",
  },
  {
    id: "lh-103",
    accountId: null,
    email: "ghost.user@khomoc.vn",
    ipAddress: "43.156.72.14",
    userAgent: "curl/8.0.1",
    status: "Failed",
    failureReason: "Email không tồn tại",
    createdAt: "2026-09-24 08:27",
  },
  {
    id: "lh-104",
    accountId: "acc-102",
    email: "khang.tran@khomoc.vn",
    ipAddress: "10.24.18.11",
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)",
    status: "Success",
    failureReason: null,
    createdAt: "2026-09-24 09:10",
  },
  {
    id: "lh-105",
    accountId: "acc-105",
    email: "tan.vo@khomoc.vn",
    ipAddress: "118.70.52.44",
    userAgent: "Mozilla/5.0 (Android 14; Mobile)",
    status: "Failed",
    failureReason: "Sai mật khẩu",
    createdAt: "2026-09-24 09:14",
  },
  {
    id: "lh-106",
    accountId: "acc-105",
    email: "tan.vo@khomoc.vn",
    ipAddress: "118.70.52.44",
    userAgent: "Mozilla/5.0 (Android 14; Mobile)",
    status: "Failed",
    failureReason: "Sai mật khẩu",
    createdAt: "2026-09-24 09:18",
  },
];

const statusOptions: Array<"all" | LoginStatus> = ["all", "Success", "Failed"];

export function LoginHistory({ navigate }: { navigate: Navigate }) {
  const [entries, setEntries] = useState<LoginHistoryEntry[]>(initialHistory);
  const [statusFilter, setStatusFilter] = useState<"all" | LoginStatus>("all");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(initialHistory[0]?.id ?? "");

  const filteredEntries = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return entries.filter((entry) => {
      const matchesStatus = statusFilter === "all" || entry.status === statusFilter;
      const matchesQuery =
        !keyword ||
        entry.email.toLowerCase().includes(keyword) ||
        entry.ipAddress.toLowerCase().includes(keyword) ||
        entry.userAgent.toLowerCase().includes(keyword) ||
        (entry.failureReason ?? "").toLowerCase().includes(keyword);

      return matchesStatus && matchesQuery;
    });
  }, [entries, query, statusFilter]);

  const selectedEntry =
    filteredEntries.find((entry) => entry.id === selectedId) ?? filteredEntries[0] ?? null;

  const suspiciousFailed = filteredEntries.filter(
    (entry) => entry.status === "Failed" && entry.email === selectedEntry?.email,
  ).length;

  return (
    <main className="admin-account-page">
      <section className="admin-account-hero">
        <div className="container admin-account-hero__inner">
          <div>
            <p className="eyebrow">System Administrator</p>
            <h1>
              Login <span>history.</span>
            </h1>
            <p>
              Theo dõi đăng nhập thành công và thất bại theo email, IP, thiết bị để phát hiện dấu
              hiệu bất thường.
            </p>
          </div>
        </div>
      </section>

      <section className="container admin-account-content">
        <div className="admin-account-actions">
          <div className="admin-account-action-group">
            <button
              className="button button-primary"
              type="button"
              onClick={() => navigate("/admin/accounts")}
            >
              <UserRound size={16} /> Tài khoản
            </button>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => navigate("/admin/audit-log")}
            >
              <ShieldCheck size={16} /> Audit log
            </button>
          </div>

          <button
            className="button button-quiet"
            type="button"
            onClick={() => navigate("/admin/login-history")}
          >
            <CalendarRange size={16} /> Refresh
          </button>
        </div>

        <div className="admin-account-layout">
          <div className="admin-account-panel admin-account-panel--list">
            <div className="admin-account-toolbar">
              <div className="admin-account-toolbar__title">
                <Wifi size={18} />
                <strong>Lịch sử đăng nhập</strong>
              </div>
              <span className="admin-account-toolbar__count">{filteredEntries.length} kết quả</span>
            </div>

            <div className="admin-account-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm kiếm</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Email, IP, user agent, reason"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Status</span>
                <select
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}
                >
                  {statusOptions.map((option) => (
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
                    <th>Email</th>
                    <th>IP</th>
                    <th>Status</th>
                    <th>Thời gian</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredEntries.map((entry) => {
                    const isSelected = selectedEntry?.id === entry.id;
                    return (
                      <tr
                        key={entry.id}
                        className={isSelected ? "account-row is-selected" : "account-row"}
                        onClick={() => setSelectedId(entry.id)}
                      >
                        <td>
                          <div className="account-user">
                            <strong>{entry.email}</strong>
                            <span>
                              {entry.accountId
                                ? `Account: ${entry.accountId}`
                                : "Account: Không xác định"}
                            </span>
                          </div>
                        </td>
                        <td>
                          <div className="account-user">
                            <strong>{entry.ipAddress}</strong>
                            <span>{entry.userAgent}</span>
                          </div>
                        </td>
                        <td>
                          <span
                            className={`account-status-tag status-${entry.status.toLowerCase()}`}
                          >
                            {entry.status}
                          </span>
                        </td>
                        <td className="account-facility">{entry.createdAt}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {!filteredEntries.length && (
                <div className="empty-state">
                  Không có lịch sử đăng nhập nào phù hợp với bộ lọc.
                </div>
              )}
            </div>
          </div>

          <aside className="admin-account-panel admin-account-panel--detail">
            {selectedEntry ? (
              <>
                <div className="admin-account-detail-header">
                  <div>
                    <p className="eyebrow">Login detail</p>
                    <h2>{selectedEntry.email}</h2>
                  </div>
                  <span
                    className={`account-status-tag status-${selectedEntry.status.toLowerCase()}`}
                  >
                    {selectedEntry.status}
                  </span>
                </div>

                <div className="admin-account-detail-grid">
                  <div className="detail-field">
                    <span>Account</span>
                    <strong>{selectedEntry.accountId ?? "Không xác định"}</strong>
                  </div>
                  <div className="detail-field">
                    <span>IP address</span>
                    <strong>{selectedEntry.ipAddress}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Device</span>
                    <strong>{selectedEntry.userAgent}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Thời gian</span>
                    <strong>{selectedEntry.createdAt}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Failure reason</span>
                    <strong>{selectedEntry.failureReason ?? "-"}</strong>
                  </div>
                </div>

                {selectedEntry.status === "Failed" && suspiciousFailed >= 2 && (
                  <div className="admin-account-warning">
                    <AlertTriangle size={14} style={{ marginRight: 6, verticalAlign: "middle" }} />
                    Dấu hiệu bất thường: cùng email/IP đã có nhiều lần login thất bại liên tiếp.
                  </div>
                )}
              </>
            ) : (
              <div className="empty-state">Không có dữ liệu lịch sử đăng nhập.</div>
            )}
          </aside>
        </div>
      </section>
    </main>
  );
}
