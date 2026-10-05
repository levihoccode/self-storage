import { Bell } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { authGateway } from "../../app/auth";
import {
  isNotificationRead,
  markNotificationRead,
  notificationsFor,
  type Notification,
} from "../../mocks/notifications";

const DROPDOWN_ITEM_LIMIT = 6;

/**
 * Chuông thông báo cho shell staff (spec `fe-pages/shared/03`): dropdown 5-10
 * item gần nhất + link tới trang đầy đủ. Tự đọc session để biết role, nên
 * shell nào dùng cũng chỉ cần render component này.
 */
export function NotificationBell({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [open, setOpen] = useState(false);
  // Đọc state nằm ở mocks/notifications.ts (module-level Set); counter này chỉ
  // để re-render sau khi mutate.
  const [, refresh] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);

  const session = authGateway.getSession();
  const role = session?.user.role ?? "CUSTOMER";
  const items = notificationsFor(role, session?.user.email);
  const unreadCount = items.filter((item) => !isNotificationRead(item.id)).length;
  const latest = items.slice(0, DROPDOWN_ITEM_LIMIT);

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  function openNotification(notification: Notification) {
    markNotificationRead(notification.id);
    refresh((count) => count + 1);
    setOpen(false);
    // Thông báo không gắn resource (type OTHER) thì bấm chỉ để đánh dấu đã đọc.
    if (notification.path) onNavigate(notification.path);
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        className="relative grid h-[38px] w-[38px] place-items-center rounded-sm border border-border bg-surface text-ink"
        onClick={() => setOpen((current) => !current)}
        aria-label={unreadCount > 0 ? `Thông báo (${unreadCount} chưa đọc)` : "Thông báo"}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-brand px-1 text-[10px] font-bold text-dark">
            {unreadCount}
          </span>
        )}
      </button>
      {open && (
        <div
          className="absolute right-0 top-[calc(100%+12px)] z-40 w-[min(360px,calc(100vw-32px))] rounded-[6px] border border-border bg-surface p-2 shadow-[var(--shadow-soft),0_0_18px_rgba(53,133,142,0.18)]"
          role="menu"
        >
          <div className="flex items-center justify-between border-b border-border p-2.5">
            <strong className="text-[12px]">Thông báo</strong>
            {unreadCount > 0 && (
              <span className="text-[11px] text-muted">{unreadCount} chưa đọc</span>
            )}
          </div>
          {latest.length === 0 ? (
            <p className="m-0 p-3 text-[12px] text-muted">Chưa có thông báo nào.</p>
          ) : (
            latest.map((notification) => {
              const isRead = isNotificationRead(notification.id);
              return (
                <button
                  key={notification.id}
                  className="flex w-full items-start gap-2.5 border-0 border-b border-border bg-transparent px-2.5 py-3 text-left last:border-b-0 hover:bg-brand-soft"
                  role="menuitem"
                  onClick={() => openNotification(notification)}
                >
                  <span
                    className={`mt-[6px] h-2 w-2 shrink-0 rounded-full ${isRead ? "bg-border" : "bg-brand"}`}
                  />
                  <span className="min-w-0">
                    <span className="block text-[12px] font-bold text-ink">
                      {notification.title}
                    </span>
                    <span className="mt-0.5 block text-[11px] leading-normal text-muted">
                      {notification.content}
                    </span>
                    <span className="mt-1 block font-mono text-[10px] uppercase tracking-[0.06em] text-muted">
                      {notification.sentAt}
                    </span>
                  </span>
                </button>
              );
            })
          )}
          <button
            className="mt-1 flex w-full items-center justify-center gap-[9px] border-0 bg-transparent px-2.5 py-[11px] text-[12px] font-bold text-brand hover:bg-brand-soft"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onNavigate("/notifications");
            }}
          >
            Xem tất cả
          </button>
        </div>
      )}
    </div>
  );
}
