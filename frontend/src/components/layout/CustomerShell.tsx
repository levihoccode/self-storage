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
    <div className="flex min-h-screen bg-background">
      <aside
        className={`fixed inset-y-0 left-0 z-20 flex w-[268px] flex-col border-r border-border bg-surface px-[18px] pb-[18px] pt-7 transition-transform duration-200 max-[760px]:z-30 max-[760px]:w-[min(300px,88vw)] max-[760px]:-translate-x-full ${
          menuOpen ? "max-[760px]:translate-x-0" : ""
        }`}
      >
        <div className="flex items-center justify-between px-2 pb-11">
          <Brand onClick={() => navigate("/")} />
          <button
            className={SIDEBAR_ICON_BUTTON}
            onClick={() => setMenuOpen(false)}
            aria-label="Đóng menu"
          >
            <X size={18} />
          </button>
        </div>
        <div className="mx-3 mb-3 font-mono text-[10px] font-medium uppercase leading-[1.4] tracking-[0.08em] text-muted">
          Không gian của bạn
        </div>
        <nav className="grid gap-1" aria-label="Điều hướng tài khoản">
          {navigation.map(({ label, path, view: itemView, icon: Icon }) => {
            const isActive = view === itemView && path === "/my-storage";
            return (
              <button
                key={path}
                className={`flex w-full items-center gap-3 rounded-sm border-0 px-3 py-[13px] text-left text-[13px] font-bold hover:bg-brand-soft hover:text-ink ${
                  isActive
                    ? "bg-brand-soft text-ink shadow-[inset_3px_0_var(--brand)]"
                    : "bg-transparent text-muted"
                }`}
                onClick={() => {
                  setMenuOpen(false);
                  if (path === "/my-storage") navigate(path);
                  else setComingSoonOpen(true);
                }}
              >
                <Icon size={18} />
                <span>{label}</span>
                {path === "/notifications" && (
                  <span className="ml-auto grid h-5 w-5 place-items-center rounded-full bg-brand text-[11px] text-dark shadow-[0_0_10px_rgba(194,208,153,0.8),0_0_22px_rgba(53,133,142,0.45)]">
                    2
                  </span>
                )}
              </button>
            );
          })}
        </nav>
        <div className="mt-auto grid gap-4 px-2 pt-[18px]">
          <div
            className="grid gap-1 border-2 border-danger bg-[rgba(255,155,115,0.13)] p-3 font-mono text-[10px] font-bold leading-[1.35] tracking-[0.04em] text-danger shadow-[0_0_14px_rgba(255,155,115,0.45),inset_0_0_18px_rgba(255,155,115,0.08)]"
            role="note"
          >
            <strong className="text-[15px] tracking-[0.08em]">DEMO ONLY</strong>
            <span>XÓA NGAY KHI BACKEND LOGIN XONG</span>
          </div>
          <button
            className="inline-flex items-center gap-[9px] border-0 bg-transparent px-3 py-2 text-left text-[13px] font-bold text-muted hover:text-danger"
            onClick={logout}
          >
            <LogOut size={17} /> Đăng xuất
          </button>
        </div>
      </aside>
      {menuOpen && (
        <button
          className="fixed inset-0 z-20 block border-0 bg-[rgba(0,0,0,0.52)]"
          onClick={() => setMenuOpen(false)}
          aria-label="Đóng menu"
        />
      )}
      <section className="min-w-0 flex-1 ml-[268px] max-[760px]:ml-0">
        <header className="sticky top-0 z-[15] mr-6 ml-auto flex min-h-[58px] w-fit min-w-[280px] items-center justify-end rounded-b-md border border-t-0 border-border bg-surface px-4 py-2.5 max-[760px]:mr-2 max-[760px]:ml-2 max-[760px]:w-[calc(100%-16px)] max-[760px]:min-w-0 max-[760px]:justify-between max-[760px]:px-3">
          <button
            className={SIDEBAR_ICON_BUTTON}
            onClick={() => setMenuOpen(true)}
            aria-label="Mở menu"
          >
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-6 max-[760px]:gap-3">
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
                      setComingSoonOpen(true);
                      setProfileOpen(false);
                    }}
                  >
                    Thông tin của tôi
                  </button>
                  <button
                    className="flex w-full items-center gap-[9px] border-0 bg-transparent px-2.5 py-[11px] text-left text-[12px] font-bold text-danger hover:bg-brand-soft"
                    role="menuitem"
                    onClick={logout}
                  >
                    <LogOut size={16} /> Đăng xuất
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <div
          className="mx-auto w-[min(1180px,calc(100%-80px))] pb-[100px] pt-16 max-[760px]:w-[min(100%-32px,600px)] max-[760px]:pb-[70px] max-[760px]:pt-[38px]"
          id="main-content"
        >
          {children}
        </div>
      </section>
      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </div>
  );
}
