import {
  AlertTriangle,
  CalendarClock,
  ClipboardCheck,
  FileWarning,
  Truck,
} from "lucide-react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { TopRightActions } from "./TopRightActions";

const navigation = [
  { label: "Lịch làm việc", path: "/fs/schedule", view: "fs-schedule" as View, icon: CalendarClock },
  { label: "Bàn giao", path: "/fs/appointments/101/handover", view: "fs-handover" as View, icon: ClipboardCheck },
  { label: "Trả kho", path: "/fs/appointments/101/return", view: "fs-return" as View, icon: Truck },
  { label: "Hỗ trợ", path: "/fs/support-requests", view: "fs-support" as View, icon: AlertTriangle },
  { label: "Sự cố", path: "/fs/incidents/new", view: "fs-incidents" as View, icon: FileWarning },
];

export function FsShell({
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
    <div className="fs-app">
      <aside className="fs-sidebar">
        <div className="fs-sidebar-top">
          <Brand onClick={() => navigate("/")} />
          <span className="fs-sidebar-badge">FS</span>
        </div>
        <div className="fs-nav-label">Facility staff</div>
        <nav className="fs-nav" aria-label="Điều hướng facility staff">
          {navigation.map(({ label, path, view: itemView, icon: Icon }) => (
            <button
              key={path}
              type="button"
              className={`fs-nav-item ${view === itemView ? "active" : ""}`}
              onClick={() => navigate(path)}
            >
              <Icon size={17} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <section className="fs-main">
        <header className="fs-topbar">
          <div className="fs-topbar-copy">
            <span className="eyebrow">Facility operations</span>
            <h2>{currentItem.label}</h2>
          </div>
          <TopRightActions
            theme={theme}
            onThemeToggle={onThemeToggle}
            onLogout={() => {
              authGateway.logout();
              navigate("/login");
            }}
            profileRoleLabel="FS"
          />
        </header>

        <div className="fs-content" id="main-content">
          {children}
        </div>
      </section>
    </div>
  );
}
