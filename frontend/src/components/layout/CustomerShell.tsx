import { Bell, CalendarDays, ChevronDown, LogOut, Menu, PackageOpen, Receipt } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { unreadNotificationCount } from "../../mocks/notifications";
import { NotificationBell } from "../domain/NotificationBell";
import type { Theme } from "../ui/ThemeToggle";
import { ThemeToggle } from "../ui/ThemeToggle";
import { ComingSoonDialog } from "../ui/ComingSoonDialog";
import { WorkspaceShell, type WorkspaceNavItem } from "./WorkspaceShell";

const navigation: (WorkspaceNavItem & { view: View; ready: boolean })[] = [
  {
    label: "Kho của tôi",
    path: "/my-storage",
    view: "my-storage" as View,
    icon: PackageOpen,
    ready: true,
  },
  {
    label: "Hóa đơn & thanh toán",
    path: "/invoices",
    view: "invoices" as View,
    icon: Receipt,
    ready: true,
  },
  {
    label: "Lịch hẹn",
    path: "/appointments/new",
    view: "appointments" as View,
    icon: CalendarDays,
    ready: true,
  },
  {
    label: "Thông báo",
    path: "/notifications",
    view: "notifications" as View,
    icon: Bell,
    ready: true,
  },
];

const SIDEBAR_ICON_BUTTON =
  "hidden h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink max-[760px]:grid";

export function CustomerShell({
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
  const [comingSoonOpen, setComingSoonOpen] = useState(false);
  const session = authGateway.getSession();
  const unreadNotifications = unreadNotificationCount("CUSTOMER", session?.user.email);
  const activeItem = navigation.find((item) => item.view === view);

  const navItems = navigation.map((item) =>
    item.path === "/notifications" ? { ...item, badge: unreadNotifications } : item,
  );

  function logout() {
    authGateway.logout();
    navigate("/login");
  }

  return (
    <>
      <WorkspaceShell
        roleLabel="Không gian của bạn"
        navItems={navItems}
        activePath={activeItem?.path ?? ""}
        onNavigate={(path) => {
          const target = navigation.find((item) => item.path === path);
          if (target?.ready) navigate(path);
          else setComingSoonOpen(true);
        }}
        onBrandClick={() => navigate("/")}
        onLogout={logout}
        contentClassName="mx-auto w-[min(1180px,calc(100%-80px))] pb-[100px] pt-16 max-[760px]:w-[min(100%-32px,600px)] max-[760px]:pb-[70px] max-[760px]:pt-[38px]"
        header={(openMobileMenu) => (
          <CustomerHeader
            session={session}
            theme={theme}
            onThemeToggle={onThemeToggle}
            onOpenMenu={openMobileMenu}
            onLogout={logout}
            onNavigate={navigate}
            onOpenComingSoon={() => setComingSoonOpen(true)}
          />
        )}
      >
        {children}
      </WorkspaceShell>
      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </>
  );
}

/**
 * Customer's topbar is a floating right-aligned pill (not the full-width bar
 * WorkspaceShell renders by default) and its profile menu has an extra "Thông
 * tin của tôi" item — different enough from the other roles that it stays
 * bespoke here instead of living in WorkspaceShell.
 */
function CustomerHeader({
  session,
  theme,
  onThemeToggle,
  onOpenMenu,
  onLogout,
  onNavigate,
  onOpenComingSoon,
}: {
  session: ReturnType<typeof authGateway.getSession>;
  theme: Theme;
  onThemeToggle: () => void;
  onOpenMenu: () => void;
  onLogout: () => void;
  onNavigate: Navigate;
  onOpenComingSoon: () => void;
}) {
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
    <header className="sticky top-0 z-[15] mr-6 ml-auto flex min-h-[58px] w-fit min-w-[280px] items-center justify-end rounded-b-md border border-t-0 border-border bg-surface px-4 py-2.5 max-[760px]:mr-2 max-[760px]:ml-2 max-[760px]:w-[calc(100%-16px)] max-[760px]:min-w-0 max-[760px]:justify-between max-[760px]:px-3">
      <button className={SIDEBAR_ICON_BUTTON} onClick={onOpenMenu} aria-label="Mở menu">
        <Menu size={20} />
      </button>
      <div className="flex items-center gap-6 max-[760px]:gap-3">
        <NotificationBell onNavigate={onNavigate} />
        <ThemeToggle theme={theme} onToggle={onThemeToggle} />
        <div className="relative" ref={profileRef}>
          <button
            className="flex items-center gap-2.5 border-y-0 border-r-0 border-l border-border bg-transparent pl-5 text-left text-ink hover:text-ink max-[760px]:pl-3"
            onClick={() => setProfileOpen((current) => !current)}
            aria-expanded={profileOpen}
            aria-haspopup="menu"
          >
            <span className="grid h-[34px] w-[34px] place-items-center rounded-full bg-brand font-extrabold text-dark">
              {session?.user.name.slice(0, 1)}
            </span>
            <span className="grid gap-px">
              <strong className="text-[12px]">{session?.user.name}</strong>
              <small className="text-[10px] text-muted">Khách hàng</small>
            </span>
            <ChevronDown className="max-[760px]:hidden" size={16} />
          </button>
          {profileOpen && (
            <div
              className="absolute right-0 top-[calc(100%+12px)] z-40 w-[220px] rounded-[6px] border border-border bg-surface p-2 shadow-[var(--shadow-soft),0_0_18px_rgba(53,133,142,0.18)]"
              role="menu"
            >
              <div className="grid gap-0.5 border-b border-border p-2.5">
                <strong className="text-[12px]">{session?.user.name}</strong>
                <span className="overflow-hidden text-ellipsis whitespace-nowrap font-mono text-[10px] font-medium text-muted">
                  {session?.user.email}
                </span>
              </div>
              <button
                className="flex w-full items-center gap-[9px] border-0 bg-transparent px-2.5 py-[11px] text-left text-[12px] font-bold text-ink hover:bg-brand-soft"
                role="menuitem"
                onClick={() => {
                  onOpenComingSoon();
                  setProfileOpen(false);
                }}
              >
                Thông tin của tôi
              </button>
              <button
                className="flex w-full items-center gap-[9px] border-0 bg-transparent px-2.5 py-[11px] text-left text-[12px] font-bold text-danger hover:bg-brand-soft"
                role="menuitem"
                onClick={onLogout}
              >
                <LogOut size={16} /> Đăng xuất
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
