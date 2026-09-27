import {
  BarChart3,
  CalendarClock,
  CalendarDays,
  Inbox,
  LifeBuoy,
  Receipt,
  Undo2,
  UserSquare2,
  Users,
  Warehouse,
} from "lucide-react";
import { useState } from "react";
import { authGateway } from "../../app/auth";
import type { Navigate, View } from "../../app/types";
import { ComingSoonDialog } from "../ui/ComingSoonDialog";
import type { Theme } from "../ui/ThemeToggle";
import { WorkspaceShell, type WorkspaceNavItem } from "./WorkspaceShell";

const navigation: (WorkspaceNavItem & { view: View; ready: boolean })[] = [
  {
    label: "Yêu cầu đặt kho",
    path: "/fm/rental-requests",
    view: "fm-rental-requests",
    icon: Inbox,
    ready: true,
  },
  {
    label: "Lịch hẹn & phân công FS",
    path: "/fm/appointments",
    view: "home",
    icon: CalendarDays,
    ready: false,
  },
  {
    label: "Yêu cầu trả kho",
    path: "/fm/return-requests",
    view: "home",
    icon: Undo2,
    ready: false,
  },
  {
    label: "Yêu cầu gia hạn",
    path: "/fm/extend-requests",
    view: "home",
    icon: CalendarClock,
    ready: false,
  },
  {
    label: "Yêu cầu hỗ trợ",
    path: "/fm/support-requests",
    view: "home",
    icon: LifeBuoy,
    ready: false,
  },
  {
    label: "Quản lý khoang chứa",
    path: "/fm/storage-units",
    view: "home",
    icon: Warehouse,
    ready: false,
  },
  { label: "Nhân viên FS", path: "/fm/staff", view: "home", icon: Users, ready: false },
  { label: "Báo cáo cơ sở", path: "/fm/reports", view: "home", icon: BarChart3, ready: false },
  { label: "Hoá đơn cơ sở", path: "/fm/invoices", view: "home", icon: Receipt, ready: false },
  {
    label: "Khách hàng & hợp đồng",
    path: "/fm/contracts",
    view: "home",
    icon: UserSquare2,
    ready: false,
  },
];

export function FmShell({
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
  const activeItem = navigation.find((item) => item.ready && item.view === view);

  function logout() {
    authGateway.logout();
    navigate("/login");
  }

  return (
    <>
      <WorkspaceShell
        roleLabel="Facility Manager"
        navItems={navigation}
        activePath={activeItem?.path ?? ""}
        onNavigate={(path) => {
          const target = navigation.find((item) => item.path === path);
          if (target?.ready) navigate(path);
          else setComingSoonOpen(true);
        }}
        onBrandClick={() => navigate("/fm/rental-requests")}
        user={{ name: session?.user.name ?? "", email: session?.user.email ?? "" }}
        onLogout={logout}
        theme={theme}
        onThemeToggle={onThemeToggle}
      >
        {children}
      </WorkspaceShell>
      {comingSoonOpen && <ComingSoonDialog onClose={() => setComingSoonOpen(false)} />}
    </>
  );
}
