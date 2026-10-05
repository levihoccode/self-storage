import { AlertTriangle, CheckCheck, Megaphone, Plus, Receipt, UserCheck } from "lucide-react";
import { useState } from "react";
import { authGateway } from "../app/auth";
import type { Navigate } from "../app/types";
import {
  isNotificationRead,
  markAllNotificationsRead,
  markNotificationRead,
  notificationsFor,
  type Notification,
  type NotificationGroup,
} from "../mocks/notifications";
import { Button } from "../components/ui/Button";
import { ComposeNotificationDialog } from "../components/domain/ComposeNotificationDialog";
import { DemoNotice } from "../components/ui/DemoNotice";
import { SurfaceState } from "../components/ui/SurfaceState";

const GROUP_ICON: Record<NotificationGroup, typeof AlertTriangle> = {
  approval: CheckCheck,
  invoice: Receipt,
  assignment: UserCheck,
  expiry: AlertTriangle,
  other: Megaphone,
};

const GROUP_ICON_CLASS: Record<NotificationGroup, string> = {
  approval: "bg-success/14 text-success",
  invoice: "bg-brand-soft text-brand",
  assignment: "bg-brand-soft text-accent",
  expiry: "bg-danger/14 text-danger",
  other: "bg-muted/14 text-muted",
};

export function NotificationsPage({ navigate }: { navigate: Navigate }) {
  // Read state lives in mocks/notifications.ts (module-level Set) so it
  // survives this page unmounting when the user navigates away and back.
  // This counter just forces a re-render after mutating that shared state.
  const [, refresh] = useState(0);
  const [composeOpen, setComposeOpen] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const session = authGateway.getSession();
  const role = session?.user.role ?? "CUSTOMER";
  const items = notificationsFor(role, session?.user.email);
  const unreadCount = items.filter((item) => !isNotificationRead(item.id)).length;

  function openNotification(notification: Notification) {
    markNotificationRead(notification.id);
    refresh((count) => count + 1);
    // Thông báo type OTHER không gắn resource nào — bấm chỉ để đánh dấu đã đọc.
    if (notification.path) navigate(notification.path);
  }

  function markAllRead() {
    markAllNotificationsRead(items);
    refresh((count) => count + 1);
  }

  return (
    <main>
      <div className="mb-8 flex items-start justify-between gap-5 max-[760px]:flex-col">
        <div>
          <h1 className="m-0 text-[28px] tracking-[-0.03em] text-ink">Trung tâm thông báo</h1>
          <p className="mt-2 text-[13px] text-muted">
            {unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : "Bạn đã đọc hết thông báo"}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          {role === "FM" && (
            <Button onClick={() => setComposeOpen(true)}>
              <Plus size={16} /> Tạo thông báo
            </Button>
          )}
          <Button variant="secondary" disabled={unreadCount === 0} onClick={markAllRead}>
            <CheckCheck size={16} /> Đánh dấu đã đọc tất cả
          </Button>
        </div>
      </div>

      {sentTo && <DemoNotice tone="success">Đã gửi thông báo tới {sentTo} (demo).</DemoNotice>}

      {items.length === 0 ? (
        <SurfaceState variant="empty" title="Chưa có thông báo nào" />
      ) : (
        <div className="grid gap-2.5">
          {items.map((notification) => {
            const Icon = GROUP_ICON[notification.group];
            const isRead = isNotificationRead(notification.id);
            return (
              <button
                key={notification.id}
                className={`flex items-start gap-4 border p-5 text-left transition-colors hover:border-brand max-[760px]:flex-col max-[760px]:items-stretch ${
                  isRead ? "border-border bg-surface" : "border-l-4 border-brand bg-brand-soft"
                }`}
                onClick={() => openNotification(notification)}
              >
                <span
                  className={`grid h-9 w-9 shrink-0 place-items-center rounded-full ${GROUP_ICON_CLASS[notification.group]}`}
                >
                  <Icon size={17} />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3">
                    <p
                      className={`m-0 text-[14px] ${isRead ? "font-semibold text-ink" : "font-bold text-ink"}`}
                    >
                      {notification.title}
                    </p>
                    {!isRead && <span className="h-2 w-2 shrink-0 rounded-full bg-brand" />}
                  </div>
                  <p className="mb-0 mt-1.5 text-[13px] leading-[1.5] text-muted">
                    {notification.content}
                  </p>
                  <p className="mb-0 mt-2 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                    {notification.sentAt}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {composeOpen && (
        <ComposeNotificationDialog
          onClose={() => setComposeOpen(false)}
          onCreated={(recipientName) => {
            setComposeOpen(false);
            setSentTo(recipientName);
          }}
        />
      )}
    </main>
  );
}
