import { ChevronDown, LogOut, Menu, X } from "lucide-react";
import type { ComponentType, ReactNode } from "react";
import { useEffect, useRef, useState } from "react";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { ThemeToggle } from "../ui/ThemeToggle";

export type WorkspaceNavItem = {
  label: string;
  path: string;
  icon: ComponentType<{ size?: number }>;
  badge?: number;
};

/**
 * Generic authenticated shell for internal roles (Facility Staff, Facility
 * Manager, Business Operation Manager, System Administrator). Customer keeps
 * its own CustomerShell — different nav vocabulary — but every internal role
 * shares this same sidebar/topbar/profile-menu/mobile-nav pattern instead of
 * each page rebuilding it.
 */
export function WorkspaceShell({
  roleLabel,
  navItems,
  activePath,
  onNavigate,
  onBrandClick,
  user,
  onLogout,
  theme,
  onThemeToggle,
  children,
}: {
  roleLabel: string;
  navItems: WorkspaceNavItem[];
  activePath: string;
  onNavigate: (path: string) => void;
  onBrandClick: () => void;
  user: { name: string; email: string };
  onLogout: () => void;
  theme: Theme;
  onThemeToggle: () => void;
  children: ReactNode;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);

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

  return (
    <div className="workspace-app">
      <aside className={`workspace-sidebar ${menuOpen ? "is-open" : ""}`}>
        <div className="workspace-sidebar-top">
          <Brand onClick={onBrandClick} />
          <button
            className="workspace-close grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink"
            onClick={() => setMenuOpen(false)}
            aria-label="Đóng menu"
          >
            <X size={18} />
          </button>
        </div>
        <div className="workspace-nav-label">{roleLabel}</div>
        <nav className="workspace-nav" aria-label={`Điều hướng ${roleLabel}`}>
          {navItems.map(({ label, path, icon: Icon, badge }) => (
            <button
              key={path}
              className={`workspace-nav-item ${activePath === path ? "active" : ""}`}
              onClick={() => {
                setMenuOpen(false);
                onNavigate(path);
              }}
            >
              <Icon size={18} />
              <span>{label}</span>
              {typeof badge === "number" && badge > 0 && (
                <span className="notification-count">{badge}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="workspace-sidebar-bottom">
          <button className="workspace-logout" onClick={onLogout}>
            <LogOut size={17} /> Đăng xuất
          </button>
        </div>
      </aside>
      {menuOpen && (
        <button
          className="workspace-sidebar-backdrop"
          onClick={() => setMenuOpen(false)}
          aria-label="Đóng menu"
        />
      )}
      <section className="workspace-main">
        <header className="workspace-topbar">
          <button
            className="workspace-menu grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink"
            onClick={() => setMenuOpen(true)}
            aria-label="Mở menu"
          >
            <Menu size={20} />
          </button>
          <div className="workspace-topbar-actions">
            <ThemeToggle theme={theme} onToggle={onThemeToggle} />
            <div className="workspace-profile" ref={profileRef}>
              <button
                className={`workspace-user ${profileOpen ? "is-open" : ""}`}
                onClick={() => setProfileOpen((current) => !current)}
                aria-expanded={profileOpen}
                aria-haspopup="menu"
              >
                <span className="workspace-avatar">{user.name.slice(0, 1)}</span>
                <span className="workspace-user-copy">
                  <strong>{user.name}</strong>
                  <small>{roleLabel}</small>
                </span>
                <ChevronDown size={16} />
              </button>
              {profileOpen && (
                <div className="workspace-profile-menu" role="menu">
                  <div className="workspace-profile-heading">
                    <strong>{user.name}</strong>
                    <span>{user.email}</span>
                  </div>
                  <button role="menuitem" className="profile-logout" onClick={onLogout}>
                    <LogOut size={16} /> Đăng xuất
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <div className="workspace-content" id="main-content">
          {children}
        </div>
      </section>
    </div>
  );
}
