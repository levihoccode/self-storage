import {
  Bell,
  CalendarDays,
  Menu,
  PackageOpen,
  Receipt,
  X,
} from "lucide-react";
import { useState } from "react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { ComingSoonDialog } from "../ui/ComingSoonDialog";
import { TopRightActions } from "./TopRightActions";

const navigation = [
  { label: "Kho của tôi", path: "/my-storage", view: "my-storage" as View, icon: PackageOpen },
  { label: "Hóa đơn & thanh toán", path: "/invoices", view: "my-storage" as View, icon: Receipt },
  { label: "Lịch hẹn", path: "/appointments", view: "my-storage" as View, icon: CalendarDays },
  { label: "Thông báo", path: "/notifications", view: "my-storage" as View, icon: Bell },
];

export function CustomerShell({
  view,
  navigate,
  theme,
  onThemeToggle,
  isScrolled,
  children,
}: {
  view: View;
  navigate: Navigate;
  theme: Theme;
  onThemeToggle: () => void;
  isScrolled: boolean;
  children: React.ReactNode;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [comingSoonOpen, setComingSoonOpen] = useState(false);

  function logout() {
    authGateway.logout();
    navigate("/login");
  }

  return (
    <div className="customer-app">
      <aside className={`customer-sidebar ${menuOpen ? "is-open" : ""}`}>
        <div className="customer-sidebar-top">
          <Brand onClick={() => navigate("/")} />
          <button
            className="icon-button customer-close"
            onClick={() => setMenuOpen(false)}
            aria-label="Đóng menu"
          >
            <X size={18} />
          </button>
        </div>
        <div className="customer-nav-label">Không gian của bạn</div>
        <nav className="customer-nav" aria-label="Điều hướng tài khoản">
          {navigation.map(({ label, path, icon: Icon }) => {
            const isActive = window.location.pathname === path;

            return (
              <button
                key={path}
                className={`customer-nav-item ${isActive ? "active" : ""}`}
                onClick={() => {
                  setMenuOpen(false);
                  if (path === "/my-storage" || path === "/notifications") navigate(path);
                  else setComingSoonOpen(true);
                }}
              >
                <Icon size={18} />
                <span>{label}</span>
                {path === "/notifications" && <span className="notification-count">2</span>}
              </button>
            );
          })}
        </nav>
        <div className="customer-sidebar-bottom">
          <div className="demo-warning" role="note">
            <strong>DEMO ONLY</strong>
            <span>XÓA NGAY KHI BACKEND LOGIN XONG</span>
          </div>
        </div>
      </aside>
      {menuOpen && (
        <button
          className="customer-sidebar-backdrop"
          onClick={() => setMenuOpen(false)}
          aria-label="Đóng menu"
        />
      )}
      <section className="customer-main">
        <header className={`customer-topbar ${isScrolled ? "is-scrolled" : ""}`}>
          <button
            className="icon-button customer-menu"
            onClick={() => setMenuOpen(true)}
            aria-label="Mở menu"
          >
            <Menu size={20} />
          </button>
          <TopRightActions
            theme={theme}
            onThemeToggle={onThemeToggle}
            onLogout={logout}
            onViewProfile={() => setComingSoonOpen(true)}
            profileRoleLabel="Khách hàng"
          />
        </header>
        <div className="customer-content" id="main-content">
          {children}
        </div>
      </section>
      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </div>
  );
}
