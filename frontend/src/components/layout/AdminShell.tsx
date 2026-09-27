import {
  ClipboardList,
  Database,
  History,
  ShieldCheck,
  Users,
} from "lucide-react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { TopRightActions } from "./TopRightActions";

const navigation = [
  { label: "Tài khoản", path: "/admin/accounts", view: "admin-accounts" as View, icon: Users },
  {
    label: "Yêu cầu nhân sự",
    path: "/admin/staff-requests",
    view: "admin-staff-requests" as View,
    icon: ClipboardList,
  },
  { label: "RBAC", path: "/admin/rbac", view: "admin-rbac" as View, icon: ShieldCheck },
  {
    label: "Lịch sử đăng nhập",
    path: "/admin/login-history",
    view: "admin-login-history" as View,
    icon: History,
  },
  {
    label: "Audit log",
    path: "/admin/audit-log",
    view: "admin-audit-log" as View,
    icon: Database,
  },
];

export function AdminShell({
  view,
  navigate,
  theme,
  onThemeToggle,
  children,
}: {
  view: View;
  navigate: Navigate;
  theme: Theme;
  onThemeToggle: () => void;
  children: React.ReactNode;
}) {
  const currentItem = navigation.find((item) => item.view === view) ?? navigation[0];

  return (
    <div className="admin-app">
      <aside className="admin-sidebar">
        <div className="admin-sidebar-top">
          <Brand onClick={() => navigate("/")} />
          <span className="admin-sidebar-badge">Admin</span>
        </div>

        <div className="admin-nav-label">Quản trị</div>
        <nav className="admin-nav" aria-label="Điều hướng quản trị">
          {navigation.map(({ label, path, view: itemView, icon: Icon }) => (
            <button
              key={path}
              type="button"
              className={`admin-nav-item ${view === itemView ? "active" : ""}`}
              onClick={() => navigate(path)}
            >
              <Icon size={17} />
              <span>{label}</span>
            </button>
          ))}
        </nav>

      </aside>

      <section className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-copy">
            <span className="eyebrow">System control</span>
            <h2>{currentItem.label}</h2>
          </div>
          <TopRightActions
            theme={theme}
            onThemeToggle={onThemeToggle}
            onLogout={() => {
              authGateway.logout();
              navigate("/login");
            }}
            profileRoleLabel="Admin"
          />
        </header>

        <div className="admin-content" id="main-content">
          {children}
        </div>
      </section>
    </div>
  );
}
