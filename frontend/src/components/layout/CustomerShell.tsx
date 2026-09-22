import {
  Bell,
  CalendarDays,
  ChevronDown,
  LogOut,
  Menu,
  PackageOpen,
  Receipt,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { ThemeToggle } from "../ui/ThemeToggle";
import { ComingSoonDialog } from "../ui/ComingSoonDialog";

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
  const [profileOpen, setProfileOpen] = useState(false);
  const [comingSoonOpen, setComingSoonOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const session = authGateway.getSession();

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setProfileOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

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
          {navigation.map(({ label, path, view: itemView, icon: Icon }) => (
            <button
              key={path}
              className={`customer-nav-item ${view === itemView && path === "/my-storage" ? "active" : ""}`}
              onClick={() => {
                setMenuOpen(false);
                if (path === "/my-storage") navigate(path);
                else setComingSoonOpen(true);
              }}
            >
              <Icon size={18} />
              <span>{label}</span>
              {path === "/notifications" && <span className="notification-count">2</span>}
            </button>
          ))}
        </nav>
        <div className="customer-sidebar-bottom">
          <div className="demo-warning" role="note">
            <strong>DEMO ONLY</strong>
            <span>XÓA NGAY KHI BACKEND LOGIN XONG</span>
          </div>
          <button className="customer-logout" onClick={logout}>
            <LogOut size={17} /> Đăng xuất
          </button>
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
          <div className="customer-topbar-actions">
            <ThemeToggle theme={theme} onToggle={onThemeToggle} />
            <div className="customer-profile" ref={profileRef}>
              <button
                className={`customer-user ${profileOpen ? "is-open" : ""}`}
                onClick={() => setProfileOpen((current) => !current)}
                aria-expanded={profileOpen}
                aria-haspopup="menu"
              >
                <span className="customer-avatar">{session?.user.name.slice(0, 1)}</span>
                <span className="customer-user-copy">
                  <strong>{session?.user.name}</strong>
                  <small>Khách hàng</small>
                </span>
                <ChevronDown size={16} />
              </button>
              {profileOpen && (
                <div className="customer-profile-menu" role="menu">
                  <div className="customer-profile-heading">
                    <strong>{session?.user.name}</strong>
                    <span>{session?.user.email}</span>
                  </div>
                  <button
                    role="menuitem"
                    onClick={() => {
                      setComingSoonOpen(true);
                      setProfileOpen(false);
                    }}
                  >
                    Thông tin của tôi
                  </button>
                  <button role="menuitem" className="profile-logout" onClick={logout}>
                    <LogOut size={16} /> Đăng xuất
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <div className="customer-content" id="main-content">
          {children}
        </div>
      </section>
      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </div>
  );
}
