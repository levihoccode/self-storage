import {
  BadgeDollarSign,
  ClipboardList,
  Database,
  FileChartColumn,
  Landmark,
  ShieldCheck,
  Store,
} from "lucide-react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { Brand } from "../ui/Brand";
import type { Theme } from "../ui/ThemeToggle";
import { TopRightActions } from "./TopRightActions";

const navigation = [
  { label: "Chính sách", path: "/bom/policies", view: "bom-policies" as View, icon: ShieldCheck },
  { label: "Phí & thu", path: "/bom/fees", view: "bom-fees" as View, icon: BadgeDollarSign },
  { label: "Khuyến mãi", path: "/bom/discounts", view: "bom-discounts" as View, icon: ClipboardList },
  { label: "Doanh thu", path: "/bom/revenue", view: "bom-revenue" as View, icon: FileChartColumn },
  { label: "Cơ sở", path: "/bom/facilities", view: "bom-facilities" as View, icon: Store },
  {
    label: "Yêu cầu nhân sự",
    path: "/bom/staff-requests",
    view: "bom-staff-requests" as View,
    icon: Database,
  },
  { label: "Loại kho", path: "/bom/unit-types", view: "bom-unit-types" as View, icon: Landmark },
  { label: "Báo cáo", path: "/bom/reports", view: "bom-reports" as View, icon: FileChartColumn },
];

export function BomShell({
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
    <div className="bom-app">
      <aside className="bom-sidebar">
        <div className="bom-sidebar-top">
          <Brand onClick={() => navigate("/")} />
          <span className="bom-sidebar-badge">BOM</span>
        </div>

        <div className="bom-nav-label">Business Operations</div>
        <nav className="bom-nav" aria-label="Điều hướng BOM">
          {navigation.map(({ label, path, view: itemView, icon: Icon }) => (
            <button
              key={path}
              type="button"
              className={`bom-nav-item ${view === itemView ? "active" : ""}`}
              onClick={() => navigate(path)}
            >
              <Icon size={17} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <section className="bom-main">
        <header className="bom-topbar">
          <div className="bom-topbar-copy">
            <span className="eyebrow">Operations control</span>
            <h2>{currentItem.label}</h2>
          </div>
          <TopRightActions
            theme={theme}
            onThemeToggle={onThemeToggle}
            onLogout={() => {
              authGateway.logout();
              navigate("/login");
            }}
            profileRoleLabel="BOM"
          />
        </header>

        <div className="bom-content" id="main-content">
          {children}
        </div>
      </section>
    </div>
  );
}
