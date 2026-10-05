import {
  BarChart3,
  Bell,
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
import { unreadNotificationCount } from "../../mocks/notifications";
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
    view: "fm-appointments",
    icon: CalendarDays,
    ready: true,
  },
  {
    label: "Yêu cầu trả kho",
    path: "/fm/return-requests",
    view: "fm-return-requests",
    icon: Undo2,
    ready: true,
  },
  {
    label: "Yêu cầu gia hạn",
    path: "/fm/extend-requests",
    view: "fm-extend-requests",
    icon: CalendarClock,
    ready: true,
  },
  {
    label: "Yêu cầu hỗ trợ",
    path: "/fm/support-requests",
    view: "fm-support-requests",
    icon: LifeBuoy,
    ready: true,
  },
  {
    label: "Quản lý khoang chứa",
    path: "/fm/storage-units",
    view: "fm-storage-units",
    icon: Warehouse,
    ready: true,
  },
  { label: "Nhân viên FS", path: "/fm/staff", view: "fm-staff", icon: Users, ready: true },
  { label: "Báo cáo cơ sở", path: "/fm/reports", view: "fm-reports", icon: BarChart3, ready: true },
  { label: "Hoá đơn cơ sở", path: "/fm/invoices", view: "fm-invoices", icon: Receipt, ready: true },
  {
    label: "Khách hàng & hợp đồng",
    path: "/fm/contracts",
    view: "fm-contracts",
    icon: UserSquare2,
    ready: true,
  },
  { label: "Thông báo", path: "/notifications", view: "notifications", icon: Bell, ready: true },
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
  const unreadNotifications = unreadNotificationCount("FM", session?.user.email);
  const activeItem = navigation.find((item) => item.ready && item.view === view);

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
        roleLabel="Facility Manager"
        navItems={navItems}
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
