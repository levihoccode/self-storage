import { useMemo, useState } from "react";
import {
  ArrowRight,
  Download,
  Lock,
  PencilLine,
  Search,
  ShieldCheck,
  UserPlus,
  Users,
} from "lucide-react";
import type { Navigate } from "../../app/types";
import "./AccountManagement.css";

type AccountRole = "Customer" | "FM" | "FS" | "BOM" | "Admin";
type AccountStatus = "Active" | "Inactive" | "Locked";

type Account = {
  id: string;
  name: string;
  email: string;
  role: AccountRole;
  status: AccountStatus;
  facility: string;
  lastActive: string;
};

const initialAccounts: Account[] = [
  {
    id: "acc-101",
    name: "Nguyễn Hoàng Anh",
    email: "anh.nguyen@khomoc.vn",
    role: "FM",
    status: "Active",
    facility: "Kho Mộc Thảo Điền",
    lastActive: "2 giờ trước",
  },
  {
    id: "acc-102",
    name: "Trần Minh Khang",
    email: "khang.tran@khomoc.vn",
    role: "FS",
    status: "Active",
    facility: "Kho Mộc Tân Bình",
    lastActive: "Hôm qua",
  },
  {
    id: "acc-103",
    name: "Lê Thị Lan",
    email: "lan.le@khomoc.vn",
    role: "BOM",
    status: "Active",
    facility: "Toàn hệ thống",
    lastActive: "1 ngày trước",
  },
  {
    id: "acc-104",
    name: "Phạm Quốc Huy",
    email: "huy.pham@khomoc.vn",
    role: "Admin",
    status: "Locked",
    facility: "Toàn hệ thống",
    lastActive: "3 ngày trước",
  },
  {
    id: "acc-105",
    name: "Võ Nhật Tân",
    email: "tan.vo@khomoc.vn",
    role: "FS",
    status: "Inactive",
    facility: "Kho Mộc Quận 7",
    lastActive: "1 tuần trước",
  },
  {
    id: "acc-106",
    name: "Nguyễn Minh Anh",
    email: "customer@kho-moc.demo",
    role: "Customer",
    status: "Active",
    facility: "Khách hàng tự đăng ký",
    lastActive: "Hôm nay",
  },
];

const roleOptions: Array<"all" | AccountRole> = ["all", "Customer", "FM", "FS", "BOM", "Admin"];
const statusOptions: Array<"all" | AccountStatus> = ["all", "Active", "Inactive", "Locked"];

export function AccountManagement({ navigate }: { navigate: Navigate }) {
  const [accounts, setAccounts] = useState<Account[]>(initialAccounts);
  const [roleFilter, setRoleFilter] = useState<"all" | AccountRole>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | AccountStatus>("all");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(initialAccounts[0]?.id ?? "");

  const filteredAccounts = useMemo(() => {
    const keyword = query.trim().toLowerCase();

    return accounts.filter((account) => {
      const matchesRole = roleFilter === "all" || account.role === roleFilter;
      const matchesStatus = statusFilter === "all" || account.status === statusFilter;
      const matchesSearch =
        !keyword ||
        account.name.toLowerCase().includes(keyword) ||
        account.email.toLowerCase().includes(keyword) ||
        account.facility.toLowerCase().includes(keyword);

      return matchesRole && matchesStatus && matchesSearch;
    });
  }, [accounts, query, roleFilter, statusFilter]);

  const selectedAccount =
    filteredAccounts.find((account) => account.id === selectedId) ?? filteredAccounts[0] ?? null;

  function toggleAccountStatus(account: Account) {
    const nextStatus = account.status === "Locked" ? "Active" : "Locked";
    setAccounts((currentAccounts) =>
      currentAccounts.map((item) =>
        item.id === account.id ? { ...item, status: nextStatus } : item,
      ),
    );
  }

  return (
    <main className="admin-account-page">
      <section className="admin-account-hero">
        <div className="container admin-account-hero__inner">
          <div>
            <p className="eyebrow">System Administrator</p>
            <h1>
              Quản lý <span>tài khoản.</span>
            </h1>
            <p>
              Tạo, cập nhật role, khóa/mở tài khoản và duy trì audit trail cho hệ thống nhân sự.
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
              <UserPlus size={16} /> Tạo tài khoản
            </button>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => navigate("/admin/rbac")}
            >
              <ShieldCheck size={16} /> Quản lý RBAC
            </button>
          </div>

          <button
            className="button button-quiet"
            type="button"
            onClick={() => navigate("/admin/audit-log")}
          >
            <Download size={16} /> Xuất báo cáo
          </button>
        </div>

        <div className="admin-account-layout">
          <div className="admin-account-panel admin-account-panel--list">
            <div className="admin-account-toolbar">
              <div className="admin-account-toolbar__title">
                <Users size={18} />
                <strong>Danh sách tài khoản</strong>
              </div>
              <span className="admin-account-toolbar__count">
                {filteredAccounts.length} tài khoản
              </span>
            </div>

            <div className="admin-account-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm kiếm</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tên, email, facility"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Role</span>
                <select
                  value={roleFilter}
                  onChange={(event) => setRoleFilter(event.target.value as typeof roleFilter)}
                >
                  {roleOptions.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "Tất cả" : option}
                    </option>
                  ))}
                </select>
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
                    <th>Tài khoản</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Facility</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAccounts.map((account) => {
                    const isSelected = selectedAccount?.id === account.id;
                    return (
                      <tr
                        key={account.id}
                        className={isSelected ? "account-row is-selected" : "account-row"}
                        onClick={() => setSelectedId(account.id)}
                      >
                        <td>
                          <div className="account-user">
                            <strong>{account.name}</strong>
                            <span>{account.email}</span>
                          </div>
                        </td>
                        <td>
                          <span className={`account-role-tag role-${account.role.toLowerCase()}`}>
                            {account.role}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`account-status-tag status-${account.status.toLowerCase()}`}
                          >
                            {account.status}
                          </span>
                        </td>
                        <td className="account-facility">{account.facility}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {!filteredAccounts.length && (
                <div className="empty-state">
                  Không có tài khoản nào phù hợp với bộ lọc hiện tại.
                </div>
              )}
            </div>
          </div>

          <aside className="admin-account-panel admin-account-panel--detail">
            {selectedAccount ? (
              <>
                <div className="admin-account-detail-header">
                  <div>
                    <p className="eyebrow">Account detail</p>
                    <h2>{selectedAccount.name}</h2>
                  </div>
                  <span
                    className={`account-status-tag status-${selectedAccount.status.toLowerCase()}`}
                  >
                    {selectedAccount.status}
                  </span>
                </div>

                <div className="admin-account-detail-grid">
                  <div className="detail-field">
                    <span>Email</span>
                    <strong>{selectedAccount.email}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Vai trò</span>
                    <strong
                      className={`detail-role detail-role--${selectedAccount.role.toLowerCase()}`}
                    >
                      {selectedAccount.role}
                    </strong>
                  </div>
                  <div className="detail-field">
                    <span>Facility</span>
                    <strong>{selectedAccount.facility}</strong>
                  </div>
                  <div className="detail-field">
                    <span>Hoạt động cuối</span>
                    <strong>{selectedAccount.lastActive}</strong>
                  </div>
                </div>

                {(selectedAccount.role === "FM" || selectedAccount.role === "FS") && (
                  <div className="admin-account-warning">
                    Cảnh báo: tài khoản đang giữ Facility assignment. Khi đổi role, hệ thống sẽ tự
                    động gỡ khỏi facility hiện tại.
                  </div>
                )}

                <div className="admin-account-actions-panel">
                  <button className="button button-primary button-full" type="button">
                    <PencilLine size={16} /> Đổi role
                  </button>
                  <button
                    className="button button-secondary button-full"
                    type="button"
                    onClick={() => toggleAccountStatus(selectedAccount)}
                  >
                    <Lock size={16} />{" "}
                    {selectedAccount.status === "Locked" ? "Mở khóa" : "Khóa tài khoản"}
                  </button>
                  <button
                    className="button button-full admin-account-secondary-button"
                    type="button"
                    onClick={() => navigate("/admin/audit-log")}
                  >
                    Xem audit log <ArrowRight size={16} />
                  </button>
                </div>
              </>
            ) : (
              <div className="empty-state">Không có dữ liệu tài khoản.</div>
            )}
          </aside>
        </div>
      </section>
    </main>
  );
}
