import { useMemo, useState } from "react";
import { AlertTriangle, Check, Database, KeyRound, Search, ShieldCheck } from "lucide-react";
import type { Navigate } from "../../app/types";
import "./AccountManagement.css";

type RoleName = "Admin" | "BOM" | "FM" | "FS" | "Customer";

type Permission = {
  id: string;
  code: string;
  description: string;
  group: string;
};

type RolePermissionMap = Record<string, Record<string, boolean>>;

const roles: RoleName[] = ["Admin", "BOM", "FM", "FS", "Customer"];

const permissions: Permission[] = [
  { id: "perm-1", code: "account.read", description: "Xem danh sách tài khoản", group: "Account" },
  {
    id: "perm-2",
    code: "account.write",
    description: "Tạo và cập nhật tài khoản",
    group: "Account",
  },
  { id: "perm-3", code: "account.lock", description: "Khóa/mở khóa tài khoản", group: "Account" },
  { id: "perm-4", code: "role.read", description: "Xem cấu hình vai trò", group: "RBAC" },
  { id: "perm-5", code: "role.write", description: "Chỉnh sửa quyền của vai trò", group: "RBAC" },
  { id: "perm-6", code: "facility.read", description: "Xem danh sách kho", group: "Facility" },
  { id: "perm-7", code: "facility.write", description: "Quản lý thông tin kho", group: "Facility" },
  { id: "perm-8", code: "rental_request.read", description: "Xem yêu cầu thuê", group: "Rental" },
  {
    id: "perm-9",
    code: "rental_request.approve",
    description: "Duyệt yêu cầu thuê",
    group: "Rental",
  },
  { id: "perm-10", code: "invoice.read", description: "Xem hóa đơn", group: "Invoice" },
  {
    id: "perm-11",
    code: "invoice.write",
    description: "Cập nhật trạng thái thanh toán",
    group: "Invoice",
  },
  {
    id: "perm-12",
    code: "policy.update",
    description: "Chỉnh sửa chính sách hệ thống",
    group: "Policy",
  },
];

const initialMatrix: RolePermissionMap = {
  Admin: {
    "account.read": true,
    "account.write": true,
    "account.lock": true,
    "role.read": true,
    "role.write": true,
    "facility.read": true,
    "facility.write": true,
    "rental_request.read": true,
    "rental_request.approve": true,
    "invoice.read": true,
    "invoice.write": true,
    "policy.update": true,
  },
  BOM: {
    "account.read": true,
    "account.write": true,
    "account.lock": false,
    "role.read": true,
    "role.write": false,
    "facility.read": true,
    "facility.write": true,
    "rental_request.read": true,
    "rental_request.approve": true,
    "invoice.read": true,
    "invoice.write": false,
    "policy.update": true,
  },
  FM: {
    "account.read": true,
    "account.write": false,
    "account.lock": false,
    "role.read": true,
    "role.write": false,
    "facility.read": true,
    "facility.write": true,
    "rental_request.read": true,
    "rental_request.approve": true,
    "invoice.read": true,
    "invoice.write": true,
    "policy.update": false,
  },
  FS: {
    "account.read": false,
    "account.write": false,
    "account.lock": false,
    "role.read": false,
    "role.write": false,
    "facility.read": true,
    "facility.write": false,
    "rental_request.read": true,
    "rental_request.approve": false,
    "invoice.read": false,
    "invoice.write": false,
    "policy.update": false,
  },
  Customer: {
    "account.read": false,
    "account.write": false,
    "account.lock": false,
    "role.read": false,
    "role.write": false,
    "facility.read": false,
    "facility.write": false,
    "rental_request.read": true,
    "rental_request.approve": false,
    "invoice.read": true,
    "invoice.write": false,
    "policy.update": false,
  },
};

export function RolePermissionManagement({ navigate }: { navigate: Navigate }) {
  const [matrix, setMatrix] = useState<RolePermissionMap>(initialMatrix);
  const [query, setQuery] = useState("");
  const [selectedRole, setSelectedRole] = useState<RoleName>("FM");

  const visiblePermissions = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return permissions.filter((permission) => {
      if (!keyword) return true;
      return (
        permission.code.toLowerCase().includes(keyword) ||
        permission.description.toLowerCase().includes(keyword) ||
        permission.group.toLowerCase().includes(keyword)
      );
    });
  }, [query]);

  const highRiskPermissions = ["invoice.read", "rental_request.approve", "policy.update"];

  function togglePermission(role: RoleName, permissionCode: string) {
    setMatrix((current) => ({
      ...current,
      [role]: {
        ...current[role],
        [permissionCode]: !current[role][permissionCode],
      },
    }));
  }

  function saveChanges() {
    const risky = visiblePermissions.filter(
      (permission) =>
        highRiskPermissions.includes(permission.code) &&
        matrix[selectedRole][permission.code] === false,
    );

    if (risky.length > 0) {
      window.alert(
        `Cảnh báo: bạn đang gỡ quyền quan trọng khỏi role ${selectedRole}: ${risky
          .map((item) => item.code)
          .join(", ")}. Vui lòng xác nhận lại trước khi lưu.`,
      );
      return;
    }

    window.alert(`Đã lưu thay đổi cho role ${selectedRole}.`);
  }

  return (
    <main className="admin-account-page">
      <section className="admin-account-hero">
        <div className="container admin-account-hero__inner">
          <div>
            <p className="eyebrow">System Administrator</p>
            <h1>
              Role & <span>permission.</span>
            </h1>
            <p>
              Quản lý quyền truy cập theo mô hình RBAC, với bảng quyền theo role và cảnh báo khi gỡ
              quyền nhạy cảm.
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
              <ShieldCheck size={16} /> Quản lý tài khoản
            </button>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => navigate("/admin/audit-log")}
            >
              <Database size={16} /> Audit log
            </button>
          </div>

          <button className="button button-quiet" type="button" onClick={() => saveChanges()}>
            <KeyRound size={16} /> Lưu thay đổi
          </button>
        </div>

        <div className="admin-account-layout">
          <div className="admin-account-panel admin-account-panel--list">
            <div className="admin-account-toolbar">
              <div className="admin-account-toolbar__title">
                <ShieldCheck size={18} />
                <strong>Ma trận quyền</strong>
              </div>
              <span className="admin-account-toolbar__count">
                {visiblePermissions.length} permissions
              </span>
            </div>

            <div className="admin-account-filters">
              <label className="filter-field filter-field--search">
                <span>Tìm quyền</span>
                <div className="filter-input-wrap">
                  <Search size={14} />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Mã quyền, mô tả, group"
                  />
                </div>
              </label>

              <label className="filter-field">
                <span>Role đang xem</span>
                <select
                  value={selectedRole}
                  onChange={(event) => setSelectedRole(event.target.value as RoleName)}
                >
                  {roles.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="account-table-wrapper">
              <table className="account-table">
                <thead>
                  <tr>
                    <th>Permission</th>
                    {roles.map((role) => (
                      <th key={role}>{role}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visiblePermissions.map((permission) => (
                    <tr key={permission.id} className="account-row">
                      <td>
                        <div className="account-user">
                          <strong>{permission.code}</strong>
                          <span>{permission.description}</span>
                        </div>
                      </td>
                      {roles.map((role) => {
                        const enabled = matrix[role][permission.code];
                        return (
                          <td key={`${role}-${permission.code}`}>
                            <label style={{ display: "flex", justifyContent: "center" }}>
                              <input
                                type="checkbox"
                                checked={Boolean(enabled)}
                                onChange={() => togglePermission(role, permission.code)}
                              />
                            </label>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <aside className="admin-account-panel admin-account-panel--detail">
            <div className="admin-account-detail-header">
              <div>
                <p className="eyebrow">Role detail</p>
                <h2>{selectedRole}</h2>
              </div>
              <span className="account-status-tag status-active">Active</span>
            </div>

            <div className="admin-account-detail-grid">
              <div className="detail-field">
                <span>Role description</span>
                <strong>
                  {selectedRole === "Admin" && "Quản trị hệ thống đầy đủ"}
                  {selectedRole === "BOM" && "Quản lý nghiệp vụ và chính sách tổng thể"}
                  {selectedRole === "FM" && "Quản lý facility và yêu cầu vận hành"}
                  {selectedRole === "FS" && "Hỗ trợ vận hành thực tế trên kho"}
                  {selectedRole === "Customer" && "Người dùng cuối, chỉ truy cập dữ liệu cá nhân"}
                </strong>
              </div>

              <div className="detail-field">
                <span>Granted permissions</span>
                <strong>
                  {Object.values(matrix[selectedRole]).filter(Boolean).length}/
                  {visiblePermissions.length}
                </strong>
              </div>
            </div>

            <div className="admin-account-warning">
              <AlertTriangle size={14} style={{ marginRight: 6, verticalAlign: "middle" }} />
              Cảnh báo bảo mật: việc gỡ quyền nhạy cảm khỏi role có nhiều account có thể ảnh hưởng
              tới toàn bộ hệ thống.
            </div>

            <div className="admin-account-actions-panel">
              <button
                className="button button-primary button-full"
                type="button"
                onClick={saveChanges}
              >
                <Check size={16} /> Lưu cấu hình RBAC
              </button>
              <button
                className="button button-full admin-account-secondary-button"
                type="button"
                onClick={() => navigate("/admin/accounts")}
              >
                Xem account liên quan
              </button>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
